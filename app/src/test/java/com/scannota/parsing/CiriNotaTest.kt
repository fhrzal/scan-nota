package com.scannota.parsing

import com.scannota.ocr.BarisOcr
import com.scannota.ocr.BarisVisual
import com.scannota.ocr.dariTsv
import com.scannota.ocr.susunBarisVisual
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Menguji pembeda foto nota dan bukan nota.
 * - Semua hasil OCR foto nota asli (termasuk potongan, foto miring, dan nota toko lain) harus lolos.
 * - Teks bukan nota dan daftar bertulisan tangan memakai baris SINTETIS yang ditandai, karena
 *   belum ada foto aslinya dari pengguna.
 */
class CiriNotaTest {

    private fun baris(vararg teks: String) = teks.mapIndexed { i, t -> BarisVisual(listOf(BarisOcr(t, 0.9f, emptyList(), i, emptyList()))) }

    @Test
    fun semuaFotoNotaAsli_lolos() {
        val folder = File(requireNotNull(javaClass.getResource("/nota_asli")).toURI())
        // Hanya hasil OCR foto asli (tanpa pengolahan citra): nama berkas <foto>.ocr.tsv.
        val berkas = folder.listFiles()!!.filter { it.name.endsWith(".ocr.tsv") && it.name.count { c -> c == '.' } == 2 }
        assertTrue("berkas uji ditemukan", berkas.size >= 12)
        for (b in berkas) {
            val ciri = ciriNota(susunBarisVisual(dariTsv(b.readText()).baris))
            assertTrue("${b.name}: $ciri", ciri.tampakNota)
        }
    }

    @Test
    fun notaTokoLain_formatBerbeda_tetapLolos() {
        val ciri = ciriNota(susunBarisVisual(NotaAsli.ocr("tokolain_tanpanomor").baris))
        assertTrue("baris '1 X 2000= 2.000,00' terhitung aritmetika: $ciri", ciri.barisAritmetika >= MIN_BARIS_CIRI_NOTA)
    }

    @Test
    fun teksKemasan_ditolak() {
        // Sintetis: tulisan yang biasa ada di kemasan barang.
        val ciri = ciriNota(baris("Mie instan rasa soto", "Netto 75 g", "Isi 100 sachet", "BPOM RI MD 123456789", "Kode 8992775311608"))
        assertFalse("$ciri", ciri.tampakNota)
    }

    @Test
    fun tanpaTulisan_ditolak() {
        assertFalse(ciriNota(emptyList()).tampakNota)
    }

    @Test
    fun daftarNamaDanHarga_sepertiTulisanTangan_lolos() {
        // Sintetis: bentuk daftar belanja tulisan tangan (nama di awal, harga di akhir).
        val ciri = ciriNota(baris("Gula pasir 2 kg 30.000", "Minyak goreng 1 L 18000", "Teh celup 7.500"))
        assertTrue("$ciri", ciri.tampakNota)
    }

    @Test
    fun satuBarisSaja_belumCukup() {
        // Sintetis: satu label harga di kemasan tidak membuat foto dianggap nota.
        assertFalse(ciriNota(baris("Harga eceran 3.500", "Netto 75 g")).tampakNota)
    }
}
