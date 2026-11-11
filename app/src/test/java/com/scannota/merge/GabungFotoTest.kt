package com.scannota.merge

import com.scannota.ocr.BarisOcr
import com.scannota.ocr.BarisVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji logika penggabungan foto dengan teks baris SINTETIS, bukan data nota.
 *
 * Setiap baris contoh berupa satu huruf yang diulang 10 kali ditambah angka versi, misalnya
 * "AAAAAAAAAA1". Baris dengan huruf berbeda pasti tidak mirip (kemiripan 0,09). Baris dengan huruf
 * sama dan versi berbeda ("AAAAAAAAAA1" dan "AAAAAAAAAA2", kemiripan 0,91) mewakili baris yang sama
 * yang terbaca sedikit berbeda di dua foto.
 */
class GabungFotoTest {

    private fun b(huruf: Char, versi: Int = 1) = huruf.toString().repeat(10) + versi

    private fun foto(vararg teks: String) =
        teks.map { BarisVisual(listOf(BarisOcr(it, 0.9f, emptyList(), 0, emptyList()))) }

    private fun teks(hasil: HasilGabung) = hasil.baris.map { it.teks }

    @Test
    fun tumpangTindihBeberapaBaris_tidakAdaBarisGanda() {
        val hasil = gabungkanFoto(
            listOf(
                foto(b('A'), b('B'), b('C'), b('D'), b('E'), b('F')),
                foto(b('D'), b('E'), b('F'), b('G'), b('H')),
            ),
        )
        assertEquals(listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H').map { b(it) }, teks(hasil))
        assertEquals(Sambungan(bersambung = true, barisCocok = 3, nilai = 3), hasil.sambungan.single())
    }

    @Test
    fun tanpaTumpangTindih_digabungBerurutan_ditandaiTidakBersambung() {
        val hasil = gabungkanFoto(listOf(foto(b('A'), b('B')), foto(b('C'), b('D'))))
        assertEquals(listOf(b('A'), b('B'), b('C'), b('D')), teks(hasil))
        assertFalse(hasil.semuaBersambung)
    }

    @Test
    fun barisTidakTerbacaDiSatuFoto_tetapDisimpanSekali() {
        // Baris D tidak terdeteksi di foto kedua. Nilai: B, C, E cocok (+3), D hanya di foto atas (−1) = 2.
        val hasil = gabungkanFoto(listOf(foto(b('A'), b('B'), b('C'), b('D'), b('E')), foto(b('B'), b('C'), b('E'), b('F'))))
        assertEquals(listOf('A', 'B', 'C', 'D', 'E', 'F').map { b(it) }, teks(hasil))
        assertEquals(Sambungan(bersambung = true, barisCocok = 3, nilai = 2), hasil.sambungan.single())
    }

    /** Dua baris cocok tetapi satu baris hilang: nilainya 1, belum cukup untuk disambung. */
    @Test
    fun duaBarisCocokDenganSatuBarisHilang_belumCukup() {
        val hasil = gabungkanFoto(listOf(foto(b('A'), b('B'), b('C'), b('D'), b('E')), foto(b('C'), b('E'), b('F'))))
        assertEquals(1, hasil.sambungan.single().nilai)
        assertFalse(hasil.semuaBersambung)
    }

    /**
     * Baris harga identik berulang (P), seperti dua barang dengan harga sama di nota asli. Baris
     * nama di sekitarnya (N1, N2, N3) yang menentukan letak sambungan yang benar.
     */
    @Test
    fun barisIdentikBerulang_disejajarkanLewatBarisTetangga() {
        val p = b('P')
        val hasil = gabungkanFoto(listOf(foto(b('K'), p, b('L'), p), foto(b('L'), p, b('M'), p)))
        assertEquals(listOf(b('K'), p, b('L'), p, b('M'), p), teks(hasil))
    }

    /** Keterbatasan yang disengaja: tumpang tindih satu baris tidak cukup untuk disambung. */
    @Test
    fun tumpangTindihSatuBaris_ditolak_barisGandaTetapAda() {
        val hasil = gabungkanFoto(listOf(foto(b('A'), b('B'), b('C')), foto(b('C'), b('D'))))
        assertEquals(listOf(b('A'), b('B'), b('C'), b('C'), b('D')), teks(hasil))
        assertEquals(1, hasil.sambungan.single().nilai)
        assertFalse(hasil.semuaBersambung)
    }

    @Test
    fun versiBaris_setengahAtasDariFotoAtas_setengahBawahDariFotoBawah() {
        val hasil = gabungkanFoto(
            listOf(
                foto(b('A'), b('C', 1), b('D', 1), b('E', 1), b('F', 1)),
                foto(b('C', 2), b('D', 2), b('E', 2), b('F', 2), b('G')),
            ),
        )
        assertEquals(listOf(b('A'), b('C', 1), b('D', 1), b('E', 2), b('F', 2), b('G')), teks(hasil))
    }

    @Test
    fun barisBerpasanganTetapiTidakMirip_keduanyaDisimpan() {
        // Baris ketiga terbaca sangat berbeda (X di foto atas, Y di foto bawah).
        val hasil = gabungkanFoto(
            listOf(foto(b('A'), b('B'), b('C'), b('X'), b('D'), b('E')), foto(b('B'), b('C'), b('Y'), b('D'), b('E'), b('F'))),
        )
        assertEquals(listOf('A', 'B', 'C', 'X', 'Y', 'D', 'E', 'F').map { b(it) }, teks(hasil))
        assertEquals(Sambungan(bersambung = true, barisCocok = 4, nilai = 3), hasil.sambungan.single())
    }

    @Test
    fun fotoBawahSeluruhnyaSudahAdaDiFotoAtas_tidakMenambahBaris() {
        val hasil = gabungkanFoto(listOf(foto(b('A'), b('B'), b('C'), b('D')), foto(b('C'), b('D'))))
        assertEquals(listOf('A', 'B', 'C', 'D').map { b(it) }, teks(hasil))
        assertTrue(hasil.semuaBersambung)
    }

    @Test
    fun tigaFoto_disambungBerurutan() {
        val hasil = gabungkanFoto(
            listOf(foto(b('A'), b('B'), b('C')), foto(b('B'), b('C'), b('D'), b('E')), foto(b('D'), b('E'), b('F'))),
        )
        assertEquals(listOf('A', 'B', 'C', 'D', 'E', 'F').map { b(it) }, teks(hasil))
        assertEquals(2, hasil.sambungan.size)
        assertTrue(hasil.semuaBersambung)
    }

    @Test
    fun fotoKosong_tidakBersambung() {
        val hasil = gabungkanFoto(listOf(foto(b('A'), b('B')), foto()))
        assertEquals(listOf(b('A'), b('B')), teks(hasil))
        assertFalse(hasil.semuaBersambung)
    }

    @Test
    fun satuFoto_tanpaSambungan() {
        val hasil = gabungkanFoto(listOf(foto(b('A'))))
        assertEquals(listOf(b('A')), teks(hasil))
        assertTrue(hasil.sambungan.isEmpty())
    }

    @Test
    fun jumlahFoto_dibatasi1Sampai20() {
        assertThrows(IllegalArgumentException::class.java) { gabungkanFoto(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { gabungkanFoto(List(21) { foto(b('A')) }) }
        gabungkanFoto(List(20) { foto(b('A')) })
    }
}
