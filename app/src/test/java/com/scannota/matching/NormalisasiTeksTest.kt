package com.scannota.matching

import org.junit.Assert.assertEquals
import org.junit.Test

/** Menguji normalisasi teks. Semua teks adalah kata contoh, bukan data nota. */
class NormalisasiTeksTest {

    @Test
    fun hurufKapitalDanSpasiDirapikan() {
        assertEquals("ABC DEF GHI", normalisasiTeks("  abc   def\tghi\n"))
    }

    @Test
    fun tandaBacaTidakDibuang_supayaArtiAngkaTidakBerubah() {
        assertEquals("A.B-C/1.5X", normalisasiTeks("a.b-c/1.5x"))
    }

    @Test
    fun hurufODanAngka0TidakDitukar() {
        assertEquals("O0 I1", normalisasiTeks("o0 i1"))
    }

    @Test
    fun normalisasiBerulang_hasilnyaSama() {
        val sekali = normalisasiTeks("  aBc  dEf ")
        assertEquals(sekali, normalisasiTeks(sekali))
    }

    @Test
    fun rapikanSpasi_hurufBesarKecilDipertahankan() {
        assertEquals("Abc Def", rapikanSpasi("  Abc    Def "))
    }

    @Test
    fun ambilAngka_berurutan() {
        assertEquals(listOf("250", "12"), ambilAngka("AB 250 X 12"))
        assertEquals(listOf("1", "5"), ambilAngka("1.5X"))
        assertEquals(emptyList<String>(), ambilAngka("ABC"))
    }
}
