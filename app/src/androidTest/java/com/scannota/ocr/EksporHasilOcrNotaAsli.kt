package com.scannota.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

/**
 * Alat ekspor, bukan test biasa: menjalankan OCR pada foto nota asli lalu menyimpan hasilnya
 * sebagai TSV, supaya bisa dipakai test parsing/ di laptop dan dilampirkan di bab pengujian.
 *
 * Jalankan lewat skrip alat/ekspor_ocr.sh, bukan lewat `./gradlew connectedDebugAndroidTest`.
 * Tugas Gradle itu menghapus aplikasi dari HP setelah selesai, sehingga hasil ekspor dan
 * database di HP ikut terhapus.
 *
 * - Masukan: foto di folder data_uji/foto/ di laptop, ikut dikemas ke APK test saat build.
 * - Keluaran: Android/data/com.scannota/files/hasil_ocr/ di perangkat, berisi satu .tsv per foto
 *   dan ringkasan.tsv.
 * - Foto dibaca apa adanya, tanpa pengolahan citra. Hasilnya menjadi pembanding "sebelum" saat
 *   modul preprocessing/ dievaluasi.
 */
@RunWith(AndroidJUnit4::class)
class EksporHasilOcrNotaAsli {

    @Test
    fun eksporSemuaFoto() = runBlocking {
        val aset = InstrumentationRegistry.getInstrumentation().context.assets
        val daftarFoto = (aset.list("") ?: emptyArray())
            .filter { it.substringAfterLast('.', "").lowercase() in EKSTENSI_FOTO }
            .sorted()
        assumeTrue("Belum ada foto di data_uji/foto/, ekspor dilewati", daftarFoto.isNotEmpty())

        val namaKeluaran = daftarFoto.map { it.substringBeforeLast('.') }
        check(namaKeluaran.toSet().size == namaKeluaran.size) {
            "Ada foto dengan nama sama tetapi ekstensi berbeda; ganti salah satu namanya"
        }

        val konteks = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(konteks.getExternalFilesDir(null), "hasil_ocr")
        // Hasil lama dihapus dulu, supaya yang diambil ke laptop hanya hasil percobaan ini.
        folder.deleteRecursively()
        check(folder.mkdirs()) { "Folder keluaran tidak bisa dibuat: $folder" }

        val ringkasan = StringBuilder()
        var pengenal = PengenalTeks()
        try {
            // Pemanasan: panggilan pertama ikut memuat model. Tanpa ini, waktu foto pertama
            // tidak sebanding dengan foto lainnya.
            val mulaiPemanasan = SystemClock.elapsedRealtime()
            pengenal.baca(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
            ringkasan.append("#pemanasan_model_ms\t").append(SystemClock.elapsedRealtime() - mulaiPemanasan).append('\n')
            ringkasan.append("foto\tlebar\ttinggi\trotasi_exif\tjumlah_baris\tjumlah_kata\twaktu_ocr_ms\tpercobaan_ulang\n")

            for ((foto, nama) in daftarFoto.zip(namaKeluaran)) {
                val rotasi = aset.open(foto).use { rotasiDariExif(it) }
                val bitmap = aset.open(foto).use { BitmapFactory.decodeStream(it) }
                    ?: error("Foto $foto tidak bisa dibuka")

                // Pada 2026-11-12, ML Kit kadang tidak pernah mengembalikan hasil (proses diam,
                // CPU 0%): 1 dari 11 foto di dua kali ekspor, dan fotonya berbeda setiap kali.
                // Karena itu ada batas waktu. Jika habis, pengenal dibuat ulang dan foto dicoba
                // sekali lagi. Jika masih habis, foto dicatat HABIS_WAKTU.
                var hasil: HasilOcrFoto? = null
                var ulang = 0
                var waktu = 0L
                while (true) {
                    val mulai = SystemClock.elapsedRealtime()
                    hasil = withTimeoutOrNull(BATAS_WAKTU_PER_FOTO_MS) { pengenal.baca(bitmap, rotasi) }
                    waktu = SystemClock.elapsedRealtime() - mulai
                    if (hasil != null || ulang == MAKS_PERCOBAAN_ULANG) break
                    pengenal.close()
                    pengenal = PengenalTeks()
                    ulang++
                }

                if (hasil == null) {
                    // Bitmap tidak di-recycle, karena ML Kit mungkin masih memakainya.
                    ringkasan.append(foto).append("\t-\t-\t").append(rotasi).append("\t-\t-\tHABIS_WAKTU\t").append(ulang).append('\n')
                    pengenal.close()
                    pengenal = PengenalTeks()
                    continue
                }
                bitmap.recycle()

                File(folder, "$nama.tsv").writeText(keTsv(hasil))
                ringkasan.append(foto).append('\t')
                    .append(hasil.lebar).append('\t').append(hasil.tinggi).append('\t')
                    .append(rotasi).append('\t')
                    .append(hasil.baris.size).append('\t').append(hasil.baris.sumOf { it.kata.size }).append('\t')
                    .append(waktu).append('\t').append(ulang).append('\n')
            }
        } finally {
            pengenal.close()
        }
        File(folder, "ringkasan.tsv").writeText(ringkasan.toString())
    }

    /**
     * Foto dari kamera HP sering disimpan miring, dengan catatan EXIF berapa derajat harus diputar
     * agar tegak. BitmapFactory mengabaikan catatan itu, jadi rotasinya diteruskan ke ML Kit.
     * Varian cermin (EXIF 2, 4, 5, 7) tidak dihasilkan kamera belakang, sehingga dianggap 0.
     */
    private fun rotasiDariExif(aliran: InputStream): Int =
        when (ExifInterface(aliran).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    private companion object {
        val EKSTENSI_FOTO = setOf("jpg", "jpeg", "png", "webp")

        /** Foto terlama di HP uji butuh sekitar 1,5 detik, jadi 60 detik pasti berarti macet. */
        const val BATAS_WAKTU_PER_FOTO_MS = 60_000L
        const val MAKS_PERCOBAAN_ULANG = 1
    }
}
