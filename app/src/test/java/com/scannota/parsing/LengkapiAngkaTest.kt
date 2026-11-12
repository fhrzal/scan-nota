package com.scannota.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Menguji isi dan koreksi angka. Kasus "nota asli" memakai angka yang benar-benar terbaca OCR dari
 * nota pengguna; kasus lain memakai angka sintetis yang ditandai.
 */
class LengkapiAngkaTest {

    @Test
    fun notaAsli_qtyTidakTerbaca_dihitungDariSubtotalDibagiHarga() {
        // 005493 GG DJAYA 12: "13.600,00 3,0Ü PCS = 40.800,00"
        assertEquals(AngkaLengkap(13600, 300, 40800, CaraHitung.QTY_DIHITUNG), lengkapiAngka(13600, null, 40800, false))
    }

    @Test
    fun notaAsli_hargaTerhalang_dihitungDariSubtotalDibagiQty() {
        // 004656 DJI SAM SOE REFILL: "21.,00 2,00 PCS = 42.700,00"
        assertEquals(AngkaLengkap(21350, 200, 42700, CaraHitung.HARGA_DIHITUNG), lengkapiAngka(null, 200, 42700, false))
    }

    @Test
    fun notaAsli_hargaSalahBaca_dikoreksiHanyaJikaSubtotalTerverifikasi() {
        // 005493 PAKU ALAM: 8.950 terbaca 8.930 × 4 = 35.800
        assertEquals(AngkaLengkap(8950, 400, 35800, CaraHitung.HARGA_DIKOREKSI), lengkapiAngka(8930, 400, 35800, true))
        assertNull("nota belum lengkap: subtotal belum terbukti benar", lengkapiAngka(8930, 400, 35800, false))
    }

    @Test
    fun notaAsli_subtotalSalahBaca_tidakDikoreksiJikaNotaBelumLengkap() {
        // 003182 DJI SAM SOE 12 LAMA: subtotal 38.700 terbaca 30.700, total nota juga salah baca.
        assertNull(lengkapiAngka(19350, 200, 30700, false))
    }

    @Test
    fun subtotalKosong_dihitungDariHargaKaliQty() {
        // Sintetis
        assertEquals(AngkaLengkap(1700, 500, 8500, CaraHitung.SUBTOTAL_DIHITUNG), lengkapiAngka(1700, 500, null, false))
        assertEquals("qty pecahan", AngkaLengkap(51000, 50, 25500, CaraHitung.SUBTOTAL_DIHITUNG), lengkapiAngka(51000, 50, null, false))
    }

    @Test
    fun pembagianTidakPas_tidakDiisi() {
        // Sintetis: 10.000 ÷ 3 tidak pas.
        assertNull(lengkapiAngka(null, 300, 10000, false))
        assertNull(lengkapiAngka(3333, null, 10000, false))
    }

    @Test
    fun duaKemungkinanSamaPas_tidakDikoreksi() {
        // Sintetis: 8.800 × 8 ≠ 26.400. Harga 26.400 ÷ 8 = 3.300 (pas) dan qty 26.400 ÷ 8.800 = 3 (pas).
        // Tidak bisa dipastikan mana yang salah baca, jadi tetap diperiksa pengguna.
        assertNull(lengkapiAngka(8800, 800, 26400, true))
    }

    @Test
    fun duaAngkaKosong_atauSudahCocok_tidakDiubah() {
        // Sintetis
        assertNull(lengkapiAngka(null, null, 8500, true))
        assertNull(lengkapiAngka(1700, 500, 8500, true))
    }

    @Test
    fun angkaNolAtauNegatif_tidakDipakai() {
        // Sintetis: qty 0 akan membuat pembagian dengan nol.
        assertNull(lengkapiAngka(null, 0, 8500, false))
        assertNull(lengkapiAngka(1700, 0, null, false))
    }
}
