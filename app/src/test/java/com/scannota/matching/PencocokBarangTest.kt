package com.scannota.matching

import com.scannota.matching.KeputusanPencocokan.BARANG_BARU
import com.scannota.matching.KeputusanPencocokan.KONFIRMASI
import com.scannota.matching.KeputusanPencocokan.OTOMATIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji aturan keputusan pencocokan.
 *
 * Teks contoh dibuat sintetis dengan jarak yang diketahui pasti. "ABCDEFGHIJ" (10 huruf):
 * mengganti 1 huruf → kemiripan 0,90; 2 huruf → 0,80; 3 huruf → 0,70; 4 huruf → 0,60.
 */
class PencocokBarangTest {

    private val dasar = "ABCDEFGHIJ"

    private fun nama(barangId: Long, teks: String) = Kandidat(barangId, teks, SumberKandidat.NAMA_BAKU)
    private fun alias(barangId: Long, teks: String) = Kandidat(barangId, teks, SumberKandidat.ALIAS_PEMASOK)

    @Test
    fun tepatSama_otomatis() {
        val hasil = cocokkan(dasar, listOf(nama(1, dasar)))
        assertEquals(OTOMATIS, hasil.keputusan)
        assertEquals(1L, hasil.barangOtomatis)
    }

    @Test
    fun kemiripanTepat090_otomatis_batasTermasuk() {
        val hasil = cocokkan(dasar, listOf(nama(1, "ABCDEFGHIX")))
        assertEquals(OTOMATIS, hasil.keputusan)
    }

    @Test
    fun kemiripan080_konfirmasi() {
        val hasil = cocokkan(dasar, listOf(nama(1, "ABCDEFGHXX")))
        assertEquals(KONFIRMASI, hasil.keputusan)
        assertNull("konfirmasi tidak memilih barang sendiri", hasil.barangOtomatis)
    }

    @Test
    fun kemiripanTepat070_konfirmasi_batasTermasuk() {
        val hasil = cocokkan(dasar, listOf(nama(1, "ABCDEFGXXX")))
        assertEquals(KONFIRMASI, hasil.keputusan)
    }

    @Test
    fun kemiripan060_barangBaru() {
        val hasil = cocokkan(dasar, listOf(nama(1, "ABCDEFXXXX")))
        assertEquals(BARANG_BARU, hasil.keputusan)
        assertEquals("kandidat tetap disertakan untuk pilihan manual", 1, hasil.kandidatTeratas.size)
    }

    @Test
    fun belumAdaBarangSamaSekali_barangBaru() {
        val hasil = cocokkan(dasar, emptyList())
        assertEquals(BARANG_BARU, hasil.keputusan)
        assertTrue(hasil.kandidatTeratas.isEmpty())
    }

    @Test
    fun penjagaAngka_variUkuranDiturunkanKeKonfirmasi() {
        // 20 karakter, beda 2 karakter pada angka → kemiripan 0,90 (zona otomatis),
        // tetapi angkanya berbeda (250 vs 500), jadi harus dikonfirmasi.
        val teks = "ABCDEFGHIJKLMNOP 250"
        val hasil = cocokkan(teks, listOf(nama(1, "ABCDEFGHIJKLMNOP 500")))
        assertEquals(KONFIRMASI, hasil.keputusan)
        assertEquals(AlasanPenurunan.ANGKA_BERBEDA, hasil.diturunkanKarena)
    }

    @Test
    fun penjagaAngka_angkaSama_tetapOtomatis() {
        val hasil = cocokkan("ABCDEFGHIJKLMNOP 250", listOf(nama(1, "ABCDEFGHIJKLMNOX 250")))
        assertEquals(OTOMATIS, hasil.keputusan)
        assertNull(hasil.diturunkanKarena)
    }

    @Test
    fun skorKembarDuaBarang_diturunkanKeKonfirmasi() {
        val hasil = cocokkan(dasar, listOf(nama(1, "ABCDEFGHIX"), nama(2, "ABCDEFGHIY")))
        assertEquals(KONFIRMASI, hasil.keputusan)
        assertEquals(AlasanPenurunan.SKOR_KEMBAR, hasil.diturunkanKarena)
        assertEquals(2, hasil.kandidatTeratas.size)
    }

    @Test
    fun duaTeksMilikSatuBarang_bukanSkorKembar_aliasDidahulukan() {
        val hasil = cocokkan(dasar, listOf(alias(1, "ABCDEFGHIX"), nama(1, "ABCDEFGHIY")))
        assertEquals(OTOMATIS, hasil.keputusan)
        assertEquals("satu barang hanya muncul sekali", 1, hasil.kandidatTeratas.size)
        assertEquals(SumberKandidat.ALIAS_PEMASOK, hasil.kandidatTeratas.first().sumber)
    }

    @Test
    fun kandidatTeratas_palingBanyakTiga_urutDariPalingMirip() {
        val hasil = cocokkan(
            dasar,
            listOf(
                nama(1, "ABCDEFXXXX"), // jarak 4
                nama(2, "ABCDEFGHIX"), // jarak 1
                nama(3, "ABCDEFGXXX"), // jarak 3
                nama(4, "ABCDEFGHXX"), // jarak 2
                nama(5, "ABCDEXXXXX"), // jarak 5
            ),
        )
        assertEquals(listOf(2L, 4L, 3L), hasil.kandidatTeratas.map { it.barangId })
    }
}
