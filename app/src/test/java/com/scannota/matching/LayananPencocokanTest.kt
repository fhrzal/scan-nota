package com.scannota.matching

import com.scannota.data.AliasBarang
import com.scannota.data.AliasBarangDao
import com.scannota.data.Barang
import com.scannota.data.BarangDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji LayananPencocokan dengan DAO tiruan di memori, sehingga bisa dijalankan di laptop
 * tanpa HP. Semua nama adalah kata contoh sintetis, bukan data nota.
 */
class LayananPencocokanTest {

    private val pemasok1 = 1L
    private val pemasok2 = 2L

    private val barangDao = BarangDaoTiruan()
    private val aliasDao = AliasBarangDaoTiruan()
    private val layanan = LayananPencocokan(aliasDao, barangDao)

    @Test
    fun aliasPersis_langsungOtomatis_walauPenulisanBerbeda() = runBlocking {
        val id = barangDao.tambah(Barang(nama = "Nama Baku Satu"))
        aliasDao.tambah(AliasBarang(barangId = id, pemasokId = pemasok1, teksAlias = "ALFA BETA GAMA"))

        val hasil = layanan.cocokkanSatu(pemasok1, "  alfa   beta gama ")

        assertEquals(KeputusanPencocokan.OTOMATIS, hasil.keputusan)
        assertTrue(hasil.dariAliasPersis)
        assertEquals(id, hasil.barangOtomatis)
    }

    @Test
    fun aliasPemasokLain_tidakDipakai() = runBlocking {
        val id = barangDao.tambah(Barang(nama = "ZZZZZZZZ"))
        aliasDao.tambah(AliasBarang(barangId = id, pemasokId = pemasok2, teksAlias = "ALFA BETA GAMA"))

        val hasil = layanan.cocokkanSatu(pemasok1, "ALFA BETA GAMA")

        assertFalse(hasil.dariAliasPersis)
        assertEquals(KeputusanPencocokan.BARANG_BARU, hasil.keputusan)
    }

    @Test
    fun tanpaAlias_cocokLewatNamaBaku() = runBlocking {
        val id = barangDao.tambah(Barang(nama = "Abcdefghij"))

        val hasil = layanan.cocokkanSatu(pemasok1, "abcdefghix")

        assertEquals(KeputusanPencocokan.OTOMATIS, hasil.keputusan)
        assertEquals(id, hasil.barangOtomatis)
        assertEquals(SumberKandidat.NAMA_BAKU, hasil.kandidatTeratas.first().sumber)
    }

    @Test
    fun kandidatDimuatSekaliPerNota_bukanPerBaris() = runBlocking {
        barangDao.tambah(Barang(nama = "Abcdefghij"))

        val hasil = layanan.cocokkanNota(pemasok1, listOf("ABCDEFGHIJ", "ABCDEFGHIX", "KLMNOPQRST"))

        assertEquals(3, hasil.size)
        assertEquals(1, barangDao.jumlahPanggilanSemua)
        assertEquals(1, aliasDao.jumlahPanggilanSemuaUntukPemasok)
    }

    @Test
    fun simpanKonfirmasi_tersimpan_laluSudahAda_laluBertentangan() = runBlocking {
        val id1 = barangDao.tambah(Barang(nama = "Barang Satu"))
        val id2 = barangDao.tambah(Barang(nama = "Barang Dua"))

        assertEquals(HasilSimpanAlias.TERSIMPAN, layanan.simpanKonfirmasi(pemasok1, " alfa  beta ", id1))
        assertEquals("teks disimpan dalam bentuk normal", "ALFA BETA", aliasDao.isi.single().teksAlias)
        assertEquals(HasilSimpanAlias.SUDAH_ADA, layanan.simpanKonfirmasi(pemasok1, "ALFA BETA", id1))
        assertEquals(HasilSimpanAlias.BERTENTANGAN, layanan.simpanKonfirmasi(pemasok1, "ALFA BETA", id2))
        assertEquals("alias lama tidak tertimpa", 1, aliasDao.isi.size)

        val hasil = layanan.cocokkanSatu(pemasok1, "alfa beta")
        assertTrue("setelah dikonfirmasi, teks ini dikenali lewat alias persis", hasil.dariAliasPersis)
        assertEquals(id1, hasil.barangOtomatis)
    }

    @Test
    fun buatBarangBaru_namaBaru_barangDanAliasTersimpan() = runBlocking {
        val hasil = layanan.buatBarangBaru(pemasok1, "alfa beta", "  Nama   Baru ")

        assertFalse(hasil.namaSudahAda)
        assertEquals(HasilSimpanAlias.TERSIMPAN, hasil.alias)
        assertEquals("spasi dirapikan, huruf besar/kecil dipertahankan", "Nama Baru", barangDao.isi.single().nama)
        assertEquals(hasil.barangId, layanan.cocokkanSatu(pemasok1, "ALFA BETA").barangOtomatis)
    }

    @Test
    fun buatBarangBaru_namaSudahAda_barangLamaDipakai() = runBlocking {
        val idLama = barangDao.tambah(Barang(nama = "Nama Baku Satu"))

        val hasil = layanan.buatBarangBaru(pemasok1, "alfa beta", "nama  baku SATU")

        assertTrue(hasil.namaSudahAda)
        assertEquals(idLama, hasil.barangId)
        assertEquals("tidak ada barang ganda", 1, barangDao.isi.size)
    }

    @Test
    fun buatBarangBaru_namaKosong_ditolak() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { layanan.buatBarangBaru(pemasok1, "alfa", "   ") }
        }
    }
}

/** DAO barang tiruan di memori. Meniru aturan nama unik tanpa membedakan huruf besar/kecil. */
private class BarangDaoTiruan : BarangDao {
    val isi = mutableListOf<Barang>()
    var jumlahPanggilanSemua = 0
    private var idBerikutnya = 1L

    override suspend fun tambah(barang: Barang): Long {
        check(isi.none { it.nama.equals(barang.nama, ignoreCase = true) }) { "nama barang ganda" }
        val baru = barang.copy(id = idBerikutnya++)
        isi += baru
        return baru.id
    }

    override suspend fun cariBerdasarkanId(id: Long): Barang? = isi.firstOrNull { it.id == id }

    override suspend fun semua(): List<Barang> {
        jumlahPanggilanSemua++
        return isi.sortedBy { it.nama }
    }

    override fun amatiSemua(): Flow<List<Barang>> = flowOf(isi.toList())

    override suspend fun hapus(barang: Barang) {
        isi.removeAll { it.id == barang.id }
    }
}

/** DAO alias tiruan di memori. Meniru aturan unik (pemasok, teks) tanpa membedakan huruf besar/kecil. */
private class AliasBarangDaoTiruan : AliasBarangDao {
    val isi = mutableListOf<AliasBarang>()
    var jumlahPanggilanSemuaUntukPemasok = 0
    private var idBerikutnya = 1L

    override suspend fun tambah(alias: AliasBarang): Long {
        check(cariPersisTanpaHitung(alias.pemasokId, alias.teksAlias) == null) { "alias ganda" }
        val baru = alias.copy(id = idBerikutnya++)
        isi += baru
        return baru.id
    }

    override suspend fun cariPersis(pemasokId: Long, teks: String): AliasBarang? =
        cariPersisTanpaHitung(pemasokId, teks)

    override suspend fun semuaUntukPemasok(pemasokId: Long): List<AliasBarang> {
        jumlahPanggilanSemuaUntukPemasok++
        return isi.filter { it.pemasokId == pemasokId }
    }

    private fun cariPersisTanpaHitung(pemasokId: Long, teks: String): AliasBarang? =
        isi.firstOrNull { it.pemasokId == pemasokId && it.teksAlias.equals(teks, ignoreCase = true) }
}
