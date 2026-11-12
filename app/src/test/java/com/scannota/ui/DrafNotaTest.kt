package com.scannota.ui

import com.scannota.data.Pemasok
import com.scannota.data.Pembanding
import com.scannota.data.StatusHarga
import com.scannota.matching.Kandidat
import com.scannota.matching.SumberKandidat
import com.scannota.matching.cocokkan
import com.scannota.ocr.susunBarisVisual
import com.scannota.parsing.BarangTerbaca
import com.scannota.parsing.CaraHitung
import com.scannota.parsing.NotaAsli
import com.scannota.parsing.bacaNotaOren
import com.scannota.parsing.evaluasiNota
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

        val draf = DrafNota(barang = listOf(salah.copy(sudahDiperiksa = true)))
        assertFalse(draf.perluDiperiksa(draf.barang.single()))
        assertTrue("sunting lagi → harus diperiksa lagi", draf.perluDiperiksa(draf.barang.single().ubahAngka(qty = "3")))
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
        assertEquals(listOf("1 barang perlu diperiksa."), ragu.masalahSimpan())
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

    @Test
    fun notaAsli005493a_hargaSalahBacaDikoreksi_tanpaPerluDiperiksaKarenaNotaLengkap() {
        val draf = drafAsli("oren_005493_a").lengkapiAngkaOtomatis()
        assertTrue(draf.lengkap)
        val paku = draf.barang.single { it.subtotalAngka == 35800L }
        assertEquals(8950L, paku.hargaAngka)
        assertEquals(CaraHitung.HARGA_DIKOREKSI, paku.dihitung)
        assertEquals("8.930 × 4 = 35.800", paku.angkaTerbaca)
        assertFalse(draf.perluDiperiksa(paku))
    }

    @Test
    fun notaAsli004656_hargaDihitung_tetapPerluDiperiksaKarenaNotaBelumLengkap() {
        val draf = drafAsli("oren_004656_potongan").lengkapiAngkaOtomatis()
        assertFalse("foto potongan tanpa total", draf.lengkap)
        val dji = draf.barang.single { it.subtotalAngka == 42700L }
        assertEquals(21350L, dji.hargaAngka)
        assertEquals(CaraHitung.HARGA_DIHITUNG, dji.dihitung)
        assertTrue(draf.perluDiperiksa(dji))
    }

    /**
     * Akurasi pada 5 nota asli setelah isi dan koreksi otomatis, dengan penilaian yang sama seperti
     * NotaAsliTest (harga, qty, satuan, dan subtotal semuanya harus benar).
     */
    @Test
    fun notaAsli_akurasiSetelahIsiOtomatis() {
        var benar = 0
        var jumlah = 0
        var salahTanpaTanda = 0
        for (nama in NotaAsli.DAFTAR) {
            val draf = drafAsli(nama).lengkapiAngkaOtomatis()
            val sebagaiNota = bacaNotaOren(susunBarisVisual(NotaAsli.ocr(nama).baris)).copy(
                barang = draf.barang.map {
                    BarangTerbaca(it.teksOcr.ifBlank { null }, it.hargaAngka, it.qtyPerseratus, it.satuanTerbaca, it.subtotalAngka, null)
                },
            )
            val e = evaluasiNota(nama, sebagaiNota, NotaAsli.kunci(nama))
            benar += e.benar
            jumlah += e.jumlahKunci
            salahTanpaTanda += e.salahTanpaTanda.size
        }
        println("Setelah isi otomatis: $benar dari $jumlah barang benar; salah tanpa tanda $salahTanpaTanda")
        assertEquals("hitungan otomatis tidak boleh membuat angka salah yang lolos", 0, salahTanpaTanda)
        assertTrue("$benar dari $jumlah", benar >= BATAS_BENAR_SETELAH_ISI_OTOMATIS)
    }

    @Test
    fun statusHargaSebelumDisimpan_dariPembanding() {
        // Sintetis: barang 7 terakhir dibeli Rp 17.000 per PCS dan Rp 170.000 per PAK.
        val pembanding = mapOf(7L to Pembanding("PCS", mapOf("PCS" to 17000L, "PAK" to 170000L)))
        val draf = DrafNota(pemasokId = 1, barang = listOf(barangBenar().pilihBarang(7)))
        val b = draf.barang.single()
        assertEquals("17.500 > 17.000", StatusHarga.NAIK, draf.statusHarga(b, pembanding))
        assertEquals(17000L, draf.hargaSebelumnya(b, pembanding))
        assertEquals(StatusHarga.TURUN, draf.statusHarga(b.ubahAngka(satuan = "PAK"), pembanding))
        assertEquals("barang belum dipilih", null, draf.statusHarga(barangBenar(), pembanding))
        assertEquals(StatusHarga.BELUM_ADA_PEMBANDING, draf.statusHarga(barangBenar().jadikanBarangBaru(), pembanding))
        assertEquals("pemasok baru", StatusHarga.BELUM_ADA_PEMBANDING, draf.copy(pemasokId = null).statusHarga(b, pembanding))
    }

    @Test
    fun satuanKosong_diisiDariRiwayat_danPerluDicekJikaNotaBelumLengkap() {
        // Sintetis
        val pembanding = mapOf(7L to Pembanding("PCS", mapOf("PCS" to 17000L)))
        val draf = DrafNota(barang = listOf(barangBenar().ubahAngka(satuan = "").pilihBarang(7))).isiSatuanDariRiwayat(pembanding)
        val b = draf.barang.single()
        assertEquals("PCS", b.satuan)
        assertTrue(b.satuanDariRiwayat)
        assertTrue("total kosong → nota belum lengkap → perlu dicek", draf.perluDiperiksa(b))
        val lengkap = draf.copy(total = "35.000")
        assertFalse(lengkap.perluDiperiksa(b))
    }

    private companion object {
        /** 2026-11-12: 113 dari 117 (pembaca nota saja 108). Sisanya 3 satuan tidak terbaca dan 1 subtotal salah baca. */
        const val BATAS_BENAR_SETELAH_ISI_OTOMATIS = 113
    }
}
