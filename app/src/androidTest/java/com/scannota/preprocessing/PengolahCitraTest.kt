package com.scannota.preprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Menguji fungsi OpenCV di perangkat dengan gambar SINTETIS yang digambar sendiri (kertas putih
 * berisi baris "TES 12345 ABCDE" di atas latar ungu), bukan foto nota.
 */
@RunWith(AndroidJUnit4::class)
class PengolahCitraTest {

    /** Kertas putih selebar [kananKertas]−[kiriKertas] berisi 12 baris teks, di atas latar ungu. */
    private fun gambarNota(
        lebar: Int = 900, tinggi: Int = 1400, kiriKertas: Int = 250, kananKertas: Int = 650, putarDerajat: Float = 0f,
        latar: Int = Color.rgb(90, 30, 70),
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(lebar, tinggi, Bitmap.Config.ARGB_8888)
        val kanvas = Canvas(bitmap)
        kanvas.drawColor(latar)
        kanvas.save()
        kanvas.rotate(putarDerajat, lebar / 2f, tinggi / 2f)
        kanvas.drawRect(kiriKertas.toFloat(), 0f, kananKertas.toFloat(), tinggi.toFloat(), Paint().apply { color = Color.WHITE })
        val cat = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.MONOSPACE
        }
        for (i in 0 until 12) kanvas.drawText("TES 12345 ABCDE", kiriKertas + 20f, 150f + i * 90f, cat)
        kanvas.restore()
        return bitmap
    }

    @Test
    fun kertasDiLatarUngu_dipotongPas() {
        val hasil = PengolahCitra.praolah(gambarNota(), 0, LangkahPraolah(potongKertas = true))
        val p = requireNotNull(hasil.potongan) { "kertas tidak ditemukan" }
        // Tepi 1% dari lebar deteksi (600 piksel) = 6 piksel deteksi = 9 piksel asli.
        assertTrue("kiri ${p.kiri}", abs(p.kiri - 250) <= 12)
        assertTrue("kanan ${p.kanan}", abs(p.kanan - 650) <= 12)
        assertEquals(p.lebar, hasil.bitmap.width)
    }

    /** Deskew dijalankan setelah potong kertas, jadi di test ini seluruh gambar adalah kertas putih. */
    @Test
    fun teksMiring5Derajat_terdeteksiDanDiluruskan() {
        val gambar = gambarNota(putarDerajat = 5f, kiriKertas = 0, kananKertas = 900, latar = Color.WHITE)
        val hasil = PengolahCitra.praolah(gambar, 0, LangkahPraolah(luruskan = true))
        assertEquals(5.0, hasil.sudutKemiringan!!, 0.5)
        assertTrue(hasil.diluruskan)
        assertTrue("kanvas diperluas, sudut gambar tidak terpotong", hasil.bitmap.width > 900)
    }

    @Test
    fun teksLurus_tidakDiputar() {
        val gambar = gambarNota(kiriKertas = 0, kananKertas = 900, latar = Color.WHITE)
        val hasil = PengolahCitra.praolah(gambar, 0, LangkahPraolah(luruskan = true))
        assertTrue(abs(hasil.sudutKemiringan!!) < SUDUT_DESKEW_MIN)
        assertFalse(hasil.diluruskan)
        assertEquals(900, hasil.bitmap.width)
    }

    @Test
    fun ambangAdaptif_hasilnyaHanyaHitamDanPutih() {
        val hasil = PengolahCitra.praolah(gambarNota(), 0, LangkahPraolah(haluskan = true, ambangAdaptif = true))
        val piksel = IntArray(hasil.bitmap.width * hasil.bitmap.height)
        hasil.bitmap.getPixels(piksel, 0, hasil.bitmap.width, 0, 0, hasil.bitmap.width, hasil.bitmap.height)
        assertTrue(piksel.all { it == Color.BLACK || it == Color.WHITE })
    }

    @Test
    fun rotasiExif_diterapkanLebihDulu() {
        val hasil = PengolahCitra.praolah(gambarNota(lebar = 900, tinggi = 1400), 90, LangkahPraolah())
        assertEquals(1400, hasil.bitmap.width)
        assertEquals(900, hasil.bitmap.height)
    }
}
