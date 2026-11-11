package com.scannota.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Menguji pembacaan angka. Token di sini disalin dari hasil OCR nota asli
 * (app/src/test/resources/nota_asli/), kecuali yang ditandai "sintetis".
 */
class AngkaTest {

    @Test
    fun uang_formatNormal() {
        assertEquals(17500L, bacaUang("17.500,00"))
        assertEquals(323550L, bacaUang("323.550,00"))
        assertEquals(1700L, bacaUang("1.700,00"))
    }

    @Test
    fun uang_titikKomaTertukar() {
        assertEquals(17000L, bacaUang("17.000.00"))
        assertEquals(30000L, bacaUang("30,000,00"))
        assertEquals("angka salah baca tetap terurai; pemeriksaan total yang menangkapnya", 242150L, bacaUang("242.150.00"))
    }

    @Test
    fun uang_hurufMiripAngka() {
        assertEquals(10050L, bacaUang("i0.050,00"))
        assertEquals(6500L, bacaUang("b.500,00"))
        assertEquals(11000L, bacaUang("11.000,D0"))
        assertEquals(1700L, bacaUang("i.700,10"))
    }

    @Test
    fun uang_desimalTerpotongAtauSalah_bagianBulatTetapDipakai() {
        assertEquals(19350L, bacaUang("19.350,"))
        assertEquals(24150L, bacaUang("24.150,0"))
        assertEquals(526200L, bacaUang("526.200,09"))
    }

    @Test
    fun uang_coretanDiUjung_dibuang() {
        assertEquals(15000L, bacaUang("15.000,00/"))
    }

    @Test
    fun bukanAngka_null() {
        assertNull("bagian terhalang", bacaUang("21.,00"))
        assertNull("huruf yang tidak mirip angka", bacaUang("3,0Ü"))
        assertNull(bacaUang("PCS"))
        assertNull("nama salah baca, bukan angka", bacaUang("13ARUM"))
        assertNull("sintetis: tanpa satu pun angka asli, huruf tidak diganti", bacaUang("OI"))
        assertNull(bacaUang("x"))
    }

    @Test
    fun qty_perseratus() {
        assertEquals(100L, bacaQty("1,00"))
        assertEquals(1000L, bacaQty("10,00"))
        assertEquals("qty pecahan (ADEM SARI)", 50L, bacaQty("0,50"))
        assertEquals("koma terbaca titik", 500L, bacaQty("5.00"))
        assertEquals(8000L, bacaQty("80,D0"))
    }

    @Test
    fun pecahToken_spasiSetelahKomaDisatukan() {
        assertEquals(listOf("8.500,00"), pecahToken("8.500, 00"))
        assertEquals(listOf("526.200,09", "526.200,00"), pecahToken("526.200, 09 526.200, 00"))
        assertEquals(listOf("15.500,0D"), pecahToken("15.500, 0D"))
    }

    @Test
    fun pecahToken_tandaKaliMenempelDipisah() {
        assertEquals(listOf("1.700,00", "x", "5,00", "PCS", "=", "8.500,00"), pecahToken("1.700,00x 5,00 PCS = 8.500,00"))
    }

    @Test
    fun pecahToken_kataBiasaTidakDiubah() {
        assertEquals(listOf("TAJIMAS", "KING", "12"), pecahToken("  TAJIMAS   KING 12 "))
    }
}
