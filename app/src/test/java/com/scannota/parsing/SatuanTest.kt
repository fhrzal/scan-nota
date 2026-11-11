package com.scannota.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

/** Menguji penyamaan satuan. Teks satuan disalin dari hasil OCR nota asli, kecuali yang ditandai sintetis. */
class SatuanTest {

    @Test
    fun satuanBaku_tetap() {
        for (s in SATUAN_DIKENAL) assertEquals(SatuanTerbaca(s, true), samakanSatuan(s))
    }

    @Test
    fun salahBacaSatuHuruf_disamakan() {
        assertEquals(SatuanTerbaca("PCS", true), samakanSatuan("FCS"))
        assertEquals(SatuanTerbaca("PCS", true), samakanSatuan("PES"))
    }

    @Test
    fun tandaSamaDenganMenempel_dibuang() {
        assertEquals(SatuanTerbaca("PCS", true), samakanSatuan("PCS="))
    }

    @Test
    fun hurufKecil_disamakan() {
        assertEquals(SatuanTerbaca("PAK", true), samakanSatuan("pak"))
    }

    @Test
    fun tidakDikenal_dibiarkanDanDitandai() {
        assertEquals("sintetis: satuan yang belum pernah muncul", SatuanTerbaca("DUS", false), samakanSatuan("DUS"))
    }

    @Test
    fun samaDekatDenganDuaSatuan_tidakDipilih() {
        // Sintetis: "PCK" berjarak 1 dari PCS dan juga 1 dari PAK, jadi tidak bisa dipastikan.
        assertEquals(SatuanTerbaca("PCK", false), samakanSatuan("PCK"))
    }
}
