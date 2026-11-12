package com.scannota.preprocessing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Menguji aturan pengolahan citra yang tidak memakai OpenCV. Semua nilai di sini sintetis. */
class AturanPraolahTest {

    private fun b(pola: String) = BooleanArray(pola.length) { pola[it] == '1' }

    @Test
    fun deretanTerpanjang_memilihPitaKertas() {
        assertEquals(4 until 9, deretanTerpanjang(b("1100111110011")))
        assertEquals("sampai ujung", 3 until 6, deretanTerpanjang(b("010111")))
        assertNull(deretanTerpanjang(b("0000")))
    }

    @Test
    fun rentangLuar_tidakTerputusOlehBarisPadat() {
        // Baris tengah yang padat teks (0) tidak memotong nota.
        assertEquals(1 until 9, rentangLuar(b("0111001110")))
        assertNull(rentangLuar(b("000")))
    }

    @Test
    fun ukuranBlok_ganjil_minimal15_mengikutiLebarKertas() {
        assertEquals(77, ukuranBlokAmbang(756))
        assertEquals(113, ukuranBlokAmbang(1129))
        assertEquals(49, ukuranBlokAmbang(479))
        assertEquals(15, ukuranBlokAmbang(80))
        for (lebar in 1..3000) assertTrue(ukuranBlokAmbang(lebar) % 2 == 1)
    }

    @Test
    fun perluDiluruskan_batas() {
        assertFalse(perluDiluruskan(0.4))
        assertTrue("batas bawah termasuk", perluDiluruskan(0.5))
        assertTrue(perluDiluruskan(-5.0))
        assertTrue(perluDiluruskan(14.9))
        assertFalse("15° dianggap perkiraan keliru", perluDiluruskan(15.0))
    }

    @Test
    fun ketajamanProfil_barisTegasLebihBesarDaripadaTersebar() {
        val tegas = doubleArrayOf(0.0, 10.0, 0.0, 10.0, 0.0)
        val tersebar = doubleArrayOf(4.0, 4.0, 4.0, 4.0, 4.0)
        assertTrue(ketajamanProfil(tegas) > ketajamanProfil(tersebar))
        assertEquals(400.0, ketajamanProfil(tegas), 0.0)
    }

    @Test
    fun cariSudutTerbaik_menemukanPuncak() {
        assertEquals(3.7, cariSudutTerbaik { -abs(it - 3.7) }, 1e-9)
        assertEquals(-12.3, cariSudutTerbaik { -abs(it + 12.3) }, 1e-9)
    }

    @Test
    fun cariSudutTerbaik_seri_pilihYangDekatNol() {
        // Fungsi datar: tidak ada kemiringan yang lebih baik, jadi tidak diputar.
        assertEquals(0.0, cariSudutTerbaik { 1.0 }, 0.0)
    }
}
