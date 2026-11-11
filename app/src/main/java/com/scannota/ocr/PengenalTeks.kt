package com.scannota.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pembungkus ML Kit Text Recognition untuk aksara Latin, memakai model terlatih tanpa
 * pelatihan ulang.
 *
 * Modelnya ikut di dalam APK (pustaka com.google.mlkit:text-recognition), jadi tidak ada unduhan
 * dan tidak perlu internet. Aplikasi ini memang tidak punya izin internet; lihat AndroidManifest.
 *
 * Cara pakai:
 * - Buat satu objek untuk semua foto dalam satu nota. Memuat model itu mahal, jadi tidak
 *   dilakukan ulang untuk setiap foto.
 * - Baca foto satu per satu ([baca] dipanggil berurutan, bukan paralel), supaya 20 foto tidak
 *   menghabiskan RAM HP.
 * - Tutup dengan [close] setelah selesai, supaya memori model dilepas.
 */
class PengenalTeks : Closeable {

    private val pengenal: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Membaca teks dari satu foto.
     *
     * @param rotasiDerajat putaran searah jarum jam yang dibutuhkan supaya foto tegak (0, 90, 180,
     *   atau 270). Nilai ini didapat dari kamera atau dari data EXIF foto. Koordinat hasil OCR
     *   selalu mengacu pada foto dalam posisi tegak (lihat [putarKeTegak]).
     */
    suspend fun baca(bitmap: Bitmap, rotasiDerajat: Int = 0): HasilOcrFoto {
        require(rotasiDerajat in ROTASI_SAH) { "rotasi harus 0, 90, 180, atau 270" }
        val gambar = InputImage.fromBitmap(bitmap, rotasiDerajat)
        val teks = pengenal.process(gambar).tunggu()
        return keHasilOcr(teks, bitmap.width, bitmap.height, rotasiDerajat)
    }

    override fun close() {
        pengenal.close()
    }

    private companion object {
        val ROTASI_SAH = setOf(0, 90, 180, 270)
    }
}

/**
 * Mengubah hasil ML Kit menjadi model data sendiri ([HasilOcrFoto]). Hanya di sinilah kelas ML
 * Kit dipakai, sehingga modul lain tidak bergantung pada ML Kit.
 *
 * Semua titik dipindahkan dari bitmap asli ke foto tegak ([putarKeTegak]), karena ML Kit
 * mengembalikan koordinat bitmap asli.
 */
internal fun keHasilOcr(teks: Text, lebarAsli: Int, tinggiAsli: Int, rotasiDerajat: Int): HasilOcrFoto {
    val keTegak = { t: Titik -> putarKeTegak(t, rotasiDerajat, lebarAsli, tinggiAsli) }
    val baris = teks.textBlocks.flatMapIndexed { indeksBlok, blok ->
        blok.lines.map { line ->
            BarisOcr(
                teks = line.text,
                keyakinan = line.confidence,
                titikSudut = titikDari(line.cornerPoints, line.boundingBox, keTegak),
                indeksBlok = indeksBlok,
                kata = line.elements.map { el ->
                    KataOcr(
                        teks = el.text,
                        keyakinan = el.confidence,
                        titikSudut = titikDari(el.cornerPoints, el.boundingBox, keTegak),
                    )
                },
            )
        }
    }
    // Pada rotasi 90/270, lebar foto tegak sama dengan tinggi bitmap asli, dan sebaliknya.
    val tertukar = rotasiDerajat == 90 || rotasiDerajat == 270
    return HasilOcrFoto(
        lebar = if (tertukar) tinggiAsli else lebarAsli,
        tinggi = if (tertukar) lebarAsli else tinggiAsli,
        baris = baris,
    )
}

/**
 * Empat titik sudut dari ML Kit (searah jarum jam mulai kiri atas teks), dipindahkan ke foto
 * tegak. Urutan titik tetap benar setelah dipindahkan, karena putaran tidak membalik arah.
 *
 * Jika titik sudut tidak ada, kotak tegak dipakai sebagai gantinya. Kotak itu tidak membawa arah
 * teks, jadi setelah dipindahkan disusun ulang mulai dari kiri atas foto tegak (kemiringan 0).
 * Jika keduanya tidak ada, hasilnya kosong.
 */
private fun titikDari(titik: Array<Point>?, kotak: Rect?, keTegak: (Titik) -> Titik): List<Titik> {
    if (titik != null && titik.size == 4) return titik.map { keTegak(Titik(it.x, it.y)) }
    if (kotak == null) return emptyList()
    val a = keTegak(Titik(kotak.left, kotak.top))
    val b = keTegak(Titik(kotak.right, kotak.bottom))
    val kiri = minOf(a.x, b.x)
    val atas = minOf(a.y, b.y)
    val kanan = maxOf(a.x, b.x)
    val bawah = maxOf(a.y, b.y)
    return listOf(Titik(kiri, atas), Titik(kanan, atas), Titik(kanan, bawah), Titik(kiri, bawah))
}

/**
 * Menunggu hasil Task ML Kit di dalam coroutine tanpa memblokir thread.
 *
 * Ditulis sendiri supaya tidak perlu pustaka tambahan (kotlinx-coroutines-play-services).
 * Callback dijalankan langsung di thread ML Kit, bukan di thread utama, supaya tidak macet jika
 * thread utama sedang menunggu.
 */
private suspend fun <T> Task<T>.tunggu(): T = suspendCancellableCoroutine { lanjutan ->
    addOnSuccessListener(LANGSUNG) { hasil -> lanjutan.resume(hasil) }
    addOnFailureListener(LANGSUNG) { galat -> lanjutan.resumeWithException(galat) }
    addOnCanceledListener(LANGSUNG) { lanjutan.cancel() }
}

private val LANGSUNG = Executor { it.run() }
