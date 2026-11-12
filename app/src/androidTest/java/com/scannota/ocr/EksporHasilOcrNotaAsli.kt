package com.scannota.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scannota.preprocessing.HasilPraolah
import com.scannota.preprocessing.LangkahPraolah
import com.scannota.preprocessing.PengolahCitra
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
 * - Keluaran: Android/data/com.scannota/files/hasil_ocr/ di perangkat, berisi ringkasan.tsv dan
 *   satu .tsv per foto per varian:
 *   - varian "asli" (foto apa adanya, pembanding "sebelum"): <nama>.tsv
 *   - varian pengolahan citra (LangkahPraolah.VARIAN_EVALUASI): <nama>.<varian>.tsv
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

        // Ringkasan ditulis per baris, supaya waktu proses tetap tersimpan walau ekspor terhenti
        // (misalnya aplikasi dipasang ulang dari Android Studio saat ekspor berjalan).
        val ringkasan = File(folder, "ringkasan.tsv").bufferedWriter()
        PembacaDenganBatasWaktu().use { pembaca ->
            // Pemanasan: panggilan pertama ikut memuat model. Tanpa ini, waktu foto pertama
            // tidak sebanding dengan foto lainnya.
            val mulaiPemanasan = SystemClock.elapsedRealtime()
            pembaca.baca(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }, 0)
            PengolahCitra.pastikanSiap()
            ringkasan.append("#pemanasan_model_ms\t").append((SystemClock.elapsedRealtime() - mulaiPemanasan).toString()).append('\n')
            ringkasan.append(
                "foto\tvarian\tlebar\ttinggi\trotasi_exif\tpotongan\tsudut\tdiluruskan\twaktu_praolah_ms\t" +
                    "jumlah_baris\tjumlah_kata\twaktu_ocr_ms\tpercobaan_ulang\n",
            )

            for ((foto, nama) in daftarFoto.zip(namaKeluaran)) {
                val rotasi = aset.open(foto).use { rotasiDariExif(it) }
                val asli = aset.open(foto).use { BitmapFactory.decodeStream(it) }
                    ?: error("Foto $foto tidak bisa dibuka")

                // Varian asli: tanpa pengolahan citra, rotasi EXIF diteruskan ke ML Kit.
                val bacaAsli = pembaca.baca(asli, rotasi)
                tulis(folder, "$nama.tsv", bacaAsli.hasil)
                ringkasan.baris(foto, "asli", rotasi, null, bacaAsli)

                for ((varian, langkah) in LangkahPraolah.VARIAN_EVALUASI) {
                    val olahan = PengolahCitra.praolah(asli, rotasi, langkah)
                    val baca = pembaca.baca(olahan.bitmap, 0)
                    tulis(folder, "$nama.$varian.tsv", baca.hasil)
                    ringkasan.baris(foto, varian, rotasi, olahan, baca)
                    if (baca.hasil != null) olahan.bitmap.recycle()
                }
                if (bacaAsli.hasil != null) asli.recycle()
                ringkasan.flush()
            }
        }
        ringkasan.close()
    }

    private fun tulis(folder: File, berkas: String, hasil: HasilOcrFoto?) {
        if (hasil != null) File(folder, berkas).writeText(keTsv(hasil))
    }

    private fun Appendable.baris(foto: String, varian: String, rotasi: Int, olahan: HasilPraolah?, baca: HasilBaca) {
        val h = baca.hasil
        val potongan = olahan?.potongan?.let { "${it.kiri},${it.atas},${it.kanan},${it.bawah}" } ?: "-"
        val kolom = listOf(
            foto, varian, h?.lebar ?: "-", h?.tinggi ?: "-", rotasi, potongan,
            olahan?.sudutKemiringan ?: "-", olahan?.diluruskan ?: "-", olahan?.waktuMs ?: 0,
            h?.baris?.size ?: "-", h?.baris?.sumOf { it.kata.size } ?: "-",
            if (h == null) "HABIS_WAKTU" else baca.waktuMs, baca.percobaanUlang,
        )
        append(kolom.joinToString("\t")).append('\n')
    }

    private class HasilBaca(val hasil: HasilOcrFoto?, val waktuMs: Long, val percobaanUlang: Int)

    /**
     * Pada 2026-11-12, ML Kit kadang tidak pernah mengembalikan hasil (proses diam, CPU 0%):
     * 2 dari 28 panggilan, pada foto yang berbeda-beda. Karena itu ada batas waktu. Jika habis,
     * pengenal dibuat ulang dan foto dicoba sekali lagi. Jika masih habis, hasilnya null
     * (dicatat HABIS_WAKTU), dan bitmap tidak di-recycle karena ML Kit mungkin masih memakainya.
     */
    private class PembacaDenganBatasWaktu : AutoCloseable {
        private var pengenal = PengenalTeks()

        suspend fun baca(bitmap: Bitmap, rotasi: Int): HasilBaca {
            var ulang = 0
            while (true) {
                val mulai = SystemClock.elapsedRealtime()
                val hasil = withTimeoutOrNull(BATAS_WAKTU_PER_FOTO_MS) { pengenal.baca(bitmap, rotasi) }
                val waktu = SystemClock.elapsedRealtime() - mulai
                if (hasil != null) return HasilBaca(hasil, waktu, ulang)
                pengenal.close()
                pengenal = PengenalTeks()
                if (ulang == MAKS_PERCOBAAN_ULANG) return HasilBaca(null, waktu, ulang)
                ulang++
            }
        }

        override fun close() = pengenal.close()
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
