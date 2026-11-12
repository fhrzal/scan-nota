package com.scannota.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import com.scannota.merge.Sambungan
import com.scannota.merge.gabungkanFoto
import com.scannota.ocr.BarisVisual
import com.scannota.ocr.HasilOcrFoto
import com.scannota.ocr.PengenalTeks
import com.scannota.ocr.susunBarisVisual
import com.scannota.parsing.NotaTerbaca
import com.scannota.parsing.bacaNotaOren
import com.scannota.preprocessing.LangkahPraolah
import com.scannota.preprocessing.PengolahCitra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Pengolahan citra yang dipakai aplikasi: grayscale saja. Dipilih dari evaluasi pada foto nota
 * asli yang sama (PraolahNotaAsliTest), dengan barang benar dari 117:
 * asli 108, grayscale 111, + potong kertas 99, + ambang adaptif 85. Grayscale memberi hasil
 * terbaik tanpa risiko memotong teks. Keputusan ini disetujui pengguna.
 */
val LANGKAH_APLIKASI = LangkahPraolah()

/**
 * ML Kit kadang tidak pernah mengembalikan hasil (2 dari 28 panggilan saat uji di HP). Foto
 * terlama butuh sekitar 1,5 detik, jadi 60 detik pasti berarti macet. Jika macet, pengenal dibuat
 * ulang dan foto dicoba sekali lagi.
 */
private const val BATAS_WAKTU_OCR_MS = 60_000L
private const val MAKS_PERCOBAAN_ULANG = 1

/** Sisi terpanjang foto yang dibaca. Foto lebih besar dikecilkan supaya memori HP cukup. */
private const val SISI_MAKS = 4096

/**
 * @param fotoGagal nomor foto (mulai 1) yang tidak berhasil dibaca walau sudah dicoba ulang
 */
class HasilProses(val nota: NotaTerbaca, val sambungan: List<Sambungan>, val fotoGagal: List<Int>)

/**
 * Alur dari foto ke nota terbaca: setiap foto diolah (grayscale), dibaca OCR, disusun menjadi baris
 * visual, lalu semua foto digabung dan dibaca sebagai nota Toko Oren.
 *
 * Foto diproses satu per satu, bukan bersamaan, supaya 20 foto tidak menghabiskan RAM HP.
 */
class PemrosesFoto {

    suspend fun proses(foto: List<File>, kemajuan: suspend (selesai: Int, jumlah: Int) -> Unit): HasilProses =
        withContext(Dispatchers.Default) {
            val barisPerFoto = mutableListOf<List<BarisVisual>>()
            val gagal = mutableListOf<Int>()
            var pengenal = PengenalTeks()
            try {
                for ((i, berkas) in foto.withIndex()) {
                    kemajuan(i, foto.size)
                    val asli = muatFoto(berkas)
                    val olahan = PengolahCitra.praolah(asli, rotasiExif(berkas), LANGKAH_APLIKASI)
                    asli.recycle()

                    var hasil: HasilOcrFoto? = null
                    for (percobaan in 0..MAKS_PERCOBAAN_ULANG) {
                        hasil = withTimeoutOrNull(BATAS_WAKTU_OCR_MS) { pengenal.baca(olahan.bitmap, 0) }
                        if (hasil != null) break
                        pengenal.close()
                        pengenal = PengenalTeks()
                    }
                    if (hasil == null) {
                        // Bitmap tidak di-recycle, karena ML Kit mungkin masih memakainya.
                        gagal += i + 1
                        barisPerFoto.add(emptyList())
                    } else {
                        olahan.bitmap.recycle()
                        barisPerFoto.add(susunBarisVisual(hasil.baris))
                    }
                }
                kemajuan(foto.size, foto.size)
            } finally {
                pengenal.close()
            }
            val gabungan = gabungkanFoto(barisPerFoto)
            HasilProses(bacaNotaOren(gabungan.baris), gabungan.sambungan, gagal)
        }

    /** Memuat foto; jika sisi terpanjangnya lebih dari [SISI_MAKS], dikecilkan dengan inSampleSize. */
    private fun muatFoto(berkas: File): Bitmap {
        val ukuran = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(berkas.path, ukuran)
        var skala = 1
        while (maxOf(ukuran.outWidth, ukuran.outHeight) / skala > SISI_MAKS) skala *= 2
        return BitmapFactory.decodeFile(berkas.path, BitmapFactory.Options().apply { inSampleSize = skala })
            ?: error("Foto ${berkas.name} tidak bisa dibuka")
    }
}

/**
 * Kamera menyimpan foto apa adanya dari sensor, ditambah catatan EXIF berapa derajat foto harus
 * diputar agar tegak. Varian cermin (EXIF 2, 4, 5, 7) tidak dihasilkan kamera belakang.
 */
fun rotasiExif(berkas: File): Int =
    when (ExifInterface(berkas.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
