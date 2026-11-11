package com.scannota.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test ML Kit di perangkat (emulator atau HP).
 *
 * Teks yang dibaca adalah tulisan contoh "TES OCR 12345" yang digambar sendiri ke bitmap, bukan
 * foto nota. Tujuannya membuktikan bahwa model bawaan APK bisa dimuat dan dipakai tanpa internet,
 * serta memastikan arti koordinat dan sudut sesuai yang diasumsikan modul lain.
 */
@RunWith(AndroidJUnit4::class)
class PengenalTeksSmokeTest {

    private val konteks = InstrumentationRegistry.getInstrumentation().targetContext

    private fun gambarTeks(teks: String, putarDerajat: Float = 0f): Bitmap {
        val bitmap = Bitmap.createBitmap(LEBAR, TINGGI, Bitmap.Config.ARGB_8888)
        val kanvas = Canvas(bitmap)
        kanvas.drawColor(Color.WHITE)
        val cat = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.DEFAULT_BOLD
        }
        // Putaran positif pada Canvas Android = searah jarum jam di layar.
        kanvas.rotate(putarDerajat, LEBAR / 2f, TINGGI / 2f)
        kanvas.drawText(teks, (LEBAR - cat.measureText(teks)) / 2f, TINGGI / 2f + 25f, cat)
        return bitmap
    }

    private fun teksTerbaca(hasil: HasilOcrFoto) =
        hasil.baris.joinToString(" ") { it.teks }.trim().replace(Regex("\\s+"), " ")

    /** Bukti offline: aplikasi tidak punya izin internet, jadi OCR pasti berjalan di perangkat. */
    @Test
    fun aplikasiTidakPunyaIzinInternet() {
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            konteks.checkSelfPermission(Manifest.permission.INTERNET),
        )
    }

    @Test
    fun teksContohTerbaca_beserta4TitikDanKeyakinan() = runBlocking {
        PengenalTeks().use { pengenal ->
            val hasil = pengenal.baca(gambarTeks(TEKS))
            assertEquals(TEKS, teksTerbaca(hasil))
            assertEquals(LEBAR, hasil.lebar)
            assertEquals(TINGGI, hasil.tinggi)
            for (b in hasil.baris) {
                assertEquals("setiap baris punya 4 titik sudut", 4, b.titikSudut.size)
                assertTrue("keyakinan ${b.keyakinan} harus 0–1", b.keyakinan in 0f..1f)
                assertTrue("baris punya kata", b.kata.isNotEmpty())
            }
        }
    }

    /** Memastikan arah sudut: teks yang diputar searah jarum jam menghasilkan kemiringan positif. */
    @Test
    fun teksMiringSearahJarumJam_kemiringanPositif() = runBlocking {
        PengenalTeks().use { pengenal ->
            val hasil = pengenal.baca(gambarTeks(TEKS, putarDerajat = 5f))
            assertEquals(TEKS, teksTerbaca(hasil))
            assertEquals(5f, perkiraanKemiringan(hasil.baris), 2f)
        }
    }

    /**
     * Memastikan arti rotasi. Bitmap diputar 90° searah jarum jam, sehingga butuh putaran 270°
     * agar tegak kembali. Teks harus terbaca, ukuran foto harus kembali ke ukuran tegak, dan
     * koordinat harus mengacu pada foto tegak (x bisa melebihi lebar bitmap yang diputar).
     *
     * Test ini pernah gagal sebelum [putarKeTegak] ditambahkan (x terbesar 148, kurang dari 240).
     * Kegagalan itu membuktikan bahwa ML Kit mengembalikan koordinat bitmap asli.
     */
    @Test
    fun rotasiDiterapkan_koordinatMengacuFotoTegak() = runBlocking {
        val tegak = gambarTeks(TEKS)
        val diputar = Bitmap.createBitmap(tegak, 0, 0, LEBAR, TINGGI, Matrix().apply { postRotate(90f) }, true)
        assertEquals(TINGGI, diputar.width)

        PengenalTeks().use { pengenal ->
            val hasil = pengenal.baca(diputar, rotasiDerajat = 270)
            assertEquals(TEKS, teksTerbaca(hasil))
            assertEquals(LEBAR, hasil.lebar)
            assertEquals(TINGGI, hasil.tinggi)
            val titik = hasil.baris.flatMap { it.titikSudut }
            val xTerbesar = titik.maxOf { it.x }
            assertTrue("x terbesar $xTerbesar harus > lebar bitmap diputar ($TINGGI)", xTerbesar > TINGGI)
            assertTrue(
                "semua titik harus di dalam foto tegak: $titik",
                titik.all { it.x in -10..LEBAR + 10 && it.y in -10..TINGGI + 10 },
            )
            assertEquals("teks tegak, jadi kemiringannya sekitar 0", 0f, perkiraanKemiringan(hasil.baris), 2f)
        }
    }

    private companion object {
        const val TEKS = "TES OCR 12345"
        const val LEBAR = 900
        const val TINGGI = 240
    }
}
