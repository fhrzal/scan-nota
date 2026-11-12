package com.scannota.merge

import com.scannota.ocr.susunBarisVisual
import com.scannota.parsing.NotaAsli
import com.scannota.parsing.bacaNotaOren
import com.scannota.parsing.evaluasi
import com.scannota.parsing.evaluasiNota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * Potongan buatan dari foto asli 003182 (3 bagian): sambungan pertama tumpang tindih 6 baris
     * (3 barang), sambungan kedua hanya 2 baris (1 barang). Keduanya harus tersambung, dan tidak
     * ada barang yang terlewat atau salah tanpa tanda.
     *
     * Satu barang tambahan memang muncul (39, bukan 38). Di tepi bawah potongan 1, OCR memecah
     * baris "6.500,00 x 1,00 PCS = 6.500,00" menjadi dua. Pecahan pertama cocok dengan baris utuh
     * di potongan 2, dan versi potongan 2 yang dipakai. Pecahan "6.500,00" tidak punya pasangan,
     * jadi tetap disimpan sesuai aturan "baris tanpa pasangan tetap disimpan". Barang itu tidak
     * lolos diam-diam: namanya kosong sehingga ditandai, dan BARIS=38 memberi peringatan.
     */
    @Test
    fun potonganBuatan003182_tigaBagian_tersambungTanpaGanda() {
        val gabungan = gabungkanFoto((1..3).map { baris("potongan_buatan_oren_003182_$it") })
        val nota = bacaNotaOren(gabungan.baris)
        val e = evaluasiNota("potongan buatan 003182", nota, NotaAsli.kunci("oren_003182"))
        println("potongan buatan 003182: barang ${e.jumlahTerbaca}, benar ${e.benar} (foto utuh: ${evaluasi("oren_003182").benar}), sambungan ${gabungan.sambungan}")

        assertTrue(gabungan.semuaBersambung)
        assertEquals(listOf(6, 2), gabungan.sambungan.map { it.barisCocok })
        assertEquals(39, e.jumlahTerbaca)
        assertEquals(0, e.terlewat)
        assertTrue(e.salahTanpaTanda.isEmpty())
        assertTrue(e.benar >= BATAS_BENAR_POTONGAN_003182)
        val tambahan = nota.barang.single { it.nama == null }
        assertEquals("6.500,00", tambahan.teksBarisHarga)
        assertTrue(tambahan.meragukan)
        assertTrue(nota.peringatanKaki().any { it.name == "JUMLAH_BARANG_BERBEDA" })
    }

    /**
     * Potongan buatan dari foto asli 002552 (2 bagian) tanpa tumpang tindih, dan barang ke-8
     * (INDOMILK) sengaja jatuh di celah. Sambungan harus ditandai tidak bersambung, nota belum
     * lengkap, dan BARIS= memberi peringatan jumlah barang berbeda.
     */
    @Test
    fun potonganBuatan002552_adaCelah_terdeteksi() {
        val gabungan = gabungkanFoto((1..2).map { baris("potongan_buatan_oren_002552_$it") })
        val nota = bacaNotaOren(gabungan.baris)

        assertFalse(gabungan.semuaBersambung)
        assertEquals(15, nota.barang.size)
        assertTrue(nota.barang.none { it.nama?.contains("INDOMILK") == true })
        assertFalse(nota.lengkap)
        assertTrue(nota.peringatanKaki().any { it.name == "JUMLAH_BARANG_BERBEDA" })
    }

    private companion object {
        /** 2026-11-12: 38 dari 38 (foto utuh 37, karena baris di tepi diambil dari potongan lain). */
        const val BATAS_BENAR_POTONGAN_003182 = 38
    }
}
