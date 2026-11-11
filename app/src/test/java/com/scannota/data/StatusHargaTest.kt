package com.scannota.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji aturan status harga dan verifikasi total.
 *
 * Catatan: angka di sini adalah contoh sintetis untuk menguji logika perbandingan saja,
 * bukan data nota. Angka ini hanya ada di kode test dan tidak pernah masuk ke database
 * aplikasi (disetujui pengguna).
 */
class StatusHargaTest {

    @Test
    fun hargaLebihTinggiDariSebelumnya_statusNaik() {
        assertEquals(StatusHarga.NAIK, tentukanStatusHarga(hargaSebelumnya = 1000, hargaSekarang = 1200))
    }

    @Test
    fun hargaLebihRendahDariSebelumnya_statusTurun() {
        assertEquals(StatusHarga.TURUN, tentukanStatusHarga(hargaSebelumnya = 1200, hargaSekarang = 1000))
    }

    @Test
    fun hargaSamaDenganSebelumnya_statusTetap() {
        assertEquals(StatusHarga.TETAP, tentukanStatusHarga(hargaSebelumnya = 1000, hargaSekarang = 1000))
    }

    @Test
    fun selisihSatuRupiah_tetapTerhitungBerubah() {
        // Harga disimpan sebagai bilangan bulat, jadi selisih sekecil apa pun terdeteksi.
        assertEquals(StatusHarga.NAIK, tentukanStatusHarga(hargaSebelumnya = 1000, hargaSekarang = 1001))
        assertEquals(StatusHarga.TURUN, tentukanStatusHarga(hargaSebelumnya = 1000, hargaSekarang = 999))
    }

    @Test
    fun belumPernahDibeli_statusBelumAdaPembanding() {
        assertEquals(StatusHarga.BELUM_ADA_PEMBANDING, tentukanStatusHarga(hargaSebelumnya = null, hargaSekarang = 1000))
    }

    @Test
    fun jumlahSubtotalSamaDenganTotal_notaLengkap() {
        assertTrue(apakahNotaLengkap(totalNota = 5000, jumlahSubtotal = 5000))
    }

    @Test
    fun jumlahSubtotalBedaDenganTotal_notaBelumLengkap() {
        // Kurang: ada baris yang belum terbaca. Lebih: ada baris terbaca ganda atau angka salah baca.
        assertFalse(apakahNotaLengkap(totalNota = 5000, jumlahSubtotal = 4000))
        assertFalse(apakahNotaLengkap(totalNota = 5000, jumlahSubtotal = 6000))
    }

    @Test
    fun totalTidakTerbaca_notaBelumLengkap() {
        assertFalse(apakahNotaLengkap(totalNota = null, jumlahSubtotal = 5000))
    }
}
