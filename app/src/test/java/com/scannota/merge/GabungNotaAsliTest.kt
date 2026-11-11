package com.scannota.merge

import com.scannota.ocr.susunBarisVisual
import com.scannota.parsing.NotaAsli
import com.scannota.parsing.bacaNotaOren
import com.scannota.parsing.evaluasi
import com.scannota.parsing.evaluasiNota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Menguji penggabungan pada hasil OCR foto nota asli. */
class GabungNotaAsliTest {

    private fun baris(nama: String) = susunBarisVisual(NotaAsli.ocr(nama).baris)

    /**
     * Nota 005493 difoto dua kali: foto b hanya bagian atas tetapi lebih jernih, foto a utuh.
     * Gabungan b lalu a harus berisi 26 barang tanpa ganda, dan tidak lebih buruk daripada foto a
     * saja, karena bagian atas tumpang tindih diambil dari foto b.
     */
    @Test
    fun nota005493_gabunganFotoBDanA() {
        val gabungan = gabungkanFoto(listOf(baris("oren_005493_b"), baris("oren_005493_a")))
        assertTrue(gabungan.semuaBersambung)

        val nota = bacaNotaOren(gabungan.baris)
        val hasilGabung = evaluasiNota("oren_005493 b+a", nota, NotaAsli.kunci("oren_005493_a"))
        val hasilFotoA = evaluasi("oren_005493_a")
        println("005493 b+a: barang ${hasilGabung.jumlahTerbaca}, benar ${hasilGabung.benar} (foto a saja: ${hasilFotoA.benar}), sambungan ${gabungan.sambungan}")

        assertEquals("tidak ada barang ganda atau hilang", 26, hasilGabung.jumlahTerbaca)
        assertEquals(0, hasilGabung.terlewat)
        assertTrue(hasilGabung.salahTanpaTanda.isEmpty())
        assertTrue("gabungan tidak boleh lebih buruk dari foto a saja", hasilGabung.benar >= hasilFotoA.benar)
        assertEquals(526200L, nota.total)
        assertTrue(nota.peringatanKaki().none { it.name == "JUMLAH_BARANG_BERBEDA" })
    }
}
