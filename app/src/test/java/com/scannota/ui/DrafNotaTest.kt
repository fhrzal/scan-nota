package com.scannota.ui

import com.scannota.data.Pemasok
import com.scannota.matching.Kandidat
import com.scannota.matching.SumberKandidat
import com.scannota.matching.cocokkan
import com.scannota.ocr.susunBarisVisual
import com.scannota.parsing.NotaAsli
import com.scannota.parsing.bacaNotaOren
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji aturan draf nota. Test yang memakai nota sungguhan membaca hasil OCR nota asli
 * (nota_asli/). Test aturan sunting memakai angka dan nama SINTETIS yang ditandai.
 */
class DrafNotaTest {

    private fun drafAsli(nama: String, pemasok: List<Pemasok> = emptyList()) =
        drafDari(bacaNotaOren(susunBarisVisual(NotaAsli.ocr(nama).baris)), 0, pemasok)

    /** Barang sintetis yang angkanya benar: 17.500 × 2 = 35.000. */
    private fun barangBenar() =
        BarangDraf(kunci = 1, teksOcr = "ALFA BETA", harga = "17.500", qty = "2", satuan = "PCS", subtotal = "35.000")

    @Test
    fun notaAsli002552_angkaBersih_tinggalMemilihBarang() {
        val draf = drafAsli("oren_002552")
        assertEquals(16, draf.barang.size)
        assertTrue(draf.barang.none { it.meragukan })
        assertTrue(draf.lengkap)
        assertTrue(draf.peringatan().isEmpty())
        assertEquals("TOKO OREN", draf.namaPemasokBaru)
        assertEquals("12-09-2025", draf.tanggal)
        assertEquals(listOf("16 barang belum dipilih barangnya."), draf.masalahSimpan())
    }

    @Test
    fun pemasokDipilihOtomatis_jikaJudulSamaPersis() {
        // Sintetis: data pemasok yang seolah sudah tersimpan.
        val draf = drafAsli("oren_002552", listOf(Pemasok(id = 3, nama = "Toko Oren")))
        assertEquals(3L, draf.pemasokId)
        assertEquals("", draf.namaPemasokBaru)
    }

    @Test
    fun notaAsli003182_totalSalahBaca_belumLengkapDanDiperingatkan() {
        val draf = drafAsli("oren_003182")
        assertFalse(draf.lengkap)
        assertTrue(draf.peringatan().any { it.contains("tidak sama dengan total 242.150") })
    }

    @Test
    fun ubahAngka_diperiksaUlang_danTandaSudahDiperiksaDihapus() {
        val b = barangBenar()
        assertFalse(b.meragukan)

        val salah = b.ubahAngka(subtotal = "30.000")
        assertTrue(salah.meragukan)
        assertEquals(listOf("17.500 × 2 = 35.000, bukan 30.000"), salah.alasanRagu)

        val diperiksa = salah.copy(sudahDiperiksa = true)
        assertFalse(diperiksa.perluDiperiksa)
        assertTrue("sunting lagi → harus diperiksa lagi", diperiksa.ubahAngka(qty = "3").perluDiperiksa)
    }

    @Test
    fun angkaKosong_tidakBolehDinyatakanBenar() {
        val b = barangBenar().ubahAngka(harga = "")
        assertFalse(b.bolehDinyatakanBenar)
        assertTrue(b.alasanRagu.contains("harga belum terbaca"))
    }

    @Test
    fun satuanTidakDikenal_meragukan_tetapiBolehDinyatakanBenar() {
        val b = barangBenar().ubahAngka(satuan = "DUS")
        assertTrue(b.alasanRagu.contains("satuan \"DUS\" tidak dikenal"))
        assertTrue(b.bolehDinyatakanBenar)
    }

    @Test
    fun masalahSimpan_lengkap() {
        val kosong = DrafNota()
        assertEquals(
            listOf("Pilih pemasok atau isi nama pemasok baru.", "Isi tanggal nota dengan format dd-mm-yyyy.", "Belum ada barang."),
            kosong.masalahSimpan(),
        )

        val siap = DrafNota(
            namaPemasokBaru = "Pemasok Contoh",
            tanggal = "12-09-2025",
            total = "35.000",
            barang = listOf(barangBenar().pilihBarang(7)),
        )
        assertTrue(siap.masalahSimpan().isEmpty())
        assertTrue(siap.lengkap)

        val ragu = siap.ubahBarang(1) { it.ubahAngka(subtotal = "30.000") }
        assertEquals(listOf("1 barang meragukan belum diperiksa."), ragu.masalahSimpan())
        assertTrue(ragu.ubahBarang(1) { it.copy(sudahDiperiksa = true) }.masalahSimpan().isEmpty())
    }

    @Test
    fun pencocokanUlang_tidakMenimpaPilihanPengguna() {
        // Sintetis: barang 7 cocok otomatis dengan teks "ABCDEFGHIJ".
        val otomatis = cocokkan("ABCDEFGHIJ", listOf(Kandidat(7, "ABCDEFGHIJ", SumberKandidat.NAMA_BAKU)))

        val belumDipilih = barangBenar().denganPencocokan(otomatis)
        assertEquals(7L, belumDipilih.barangTerpilihId)
        assertFalse("hasil otomatis tidak dianggap pilihan pengguna", belumDipilih.dipilihPengguna)

        val dipilih = barangBenar().pilihBarang(9).denganPencocokan(otomatis)
        assertEquals(9L, dipilih.barangTerpilihId)

        val baru = barangBenar().jadikanBarangBaru().denganPencocokan(otomatis)
        assertNull(baru.barangTerpilihId)
        assertEquals("ALFA BETA", baru.namaBaru)
    }

    @Test
    fun tambahBarangKosong_manual_kunciUnik() {
        val draf = DrafNota(barang = listOf(barangBenar())).tambahBarangKosong().tambahBarangKosong()
        assertEquals(listOf(1L, 2L, 3L), draf.barang.map { it.kunci })
        assertTrue(draf.barang.drop(1).all { it.manual })
    }
}
