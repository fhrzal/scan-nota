package com.scannota.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Menguji pemindahan titik dari bitmap asli ke foto tegak.
 *
 * Patokannya definisi putaran searah jarum jam. Contohnya, jika bitmap diputar 90°, sudut kiri
 * atasnya pindah ke kanan atas dan tepi atasnya menjadi tepi kanan. Ukuran bitmap contoh
 * (200 × 100) adalah angka sintetis.
 */
class PutarKeTegakTest {

    private val lebar = 200
    private val tinggi = 100
    private val kiriAtas = Titik(0, 0)
    private val kananAtas = Titik(lebar, 0)

    private fun putar(t: Titik, rotasi: Int) = putarKeTegak(t, rotasi, lebar, tinggi)

    @Test
    fun rotasi0_tidakBerubah() {
        assertEquals(Titik(37, 54), putar(Titik(37, 54), 0))
    }

    @Test
    fun rotasi90_kiriAtasKeKananAtas_tepiAtasMenjadiTepiKanan() {
        // Foto tegak berukuran 100 × 200.
        assertEquals(Titik(tinggi, 0), putar(kiriAtas, 90))
        assertEquals(Titik(tinggi, lebar), putar(kananAtas, 90))
    }

    @Test
    fun rotasi180_kiriAtasKeKananBawah() {
        assertEquals(Titik(lebar, tinggi), putar(kiriAtas, 180))
        assertEquals(Titik(0, tinggi), putar(kananAtas, 180))
    }

    @Test
    fun rotasi270_kiriAtasKeKiriBawah_tepiAtasMenjadiTepiKiri() {
        // Foto tegak berukuran 100 × 200.
        assertEquals(Titik(0, lebar), putar(kiriAtas, 270))
        assertEquals(Titik(0, 0), putar(kananAtas, 270))
    }

    /**
     * Teks yang di bitmap asli berjalan dari bawah ke atas (bitmap perlu diputar 90° agar tegak)
     * harus menjadi mendatar setelah dipindahkan, yaitu kemiringan 0.
     */
    @Test
    fun teksDiBitmapMiring90_menjadiMendatar() {
        // Empat titik sudut teks di bitmap asli, searah jarum jam mulai kiri atas teks.
        val titikAsli = listOf(Titik(20, 90), Titik(20, 40), Titik(40, 40), Titik(40, 90))
        val titikTegak = titikAsli.map { putar(it, 90) }
        assertEquals(listOf(Titik(10, 20), Titik(60, 20), Titik(60, 40), Titik(10, 40)), titikTegak)
        assertEquals(0f, kemiringanDerajat(titikTegak)!!, 0f)
    }

    @Test
    fun rotasiTidakSah_ditolak() {
        assertThrows(IllegalArgumentException::class.java) { putar(kiriAtas, 45) }
    }
}
