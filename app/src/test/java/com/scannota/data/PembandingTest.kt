package com.scannota.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Menguji ringkasan pembanding harga. Semua angka di sini sintetis. */
class PembandingTest {

    @Test
    fun pembelianTerakhir_perBarangDanPerSatuan() {
        // Terbaru lebih dulu, sesuai urutan query pembelianDariPemasok.
        val p = ringkasPembanding(
            listOf(
                PembelianSebelumnya(barangId = 1, satuan = "PCS", hargaSatuan = 8100),
                PembelianSebelumnya(barangId = 1, satuan = "PAK", hargaSatuan = 81000),
                PembelianSebelumnya(barangId = 1, satuan = "PCS", hargaSatuan = 7900),
                PembelianSebelumnya(barangId = 2, satuan = null, hargaSatuan = 500),
            ),
        )
        assertEquals("PCS", p.getValue(1).satuanTerakhir)
        assertEquals("yang terbaru, bukan yang lama", 8100L, p.getValue(1).hargaSebelumnya("PCS"))
        assertEquals(81000L, p.getValue(1).hargaSebelumnya("pak"))
        assertNull("satuan lain tidak dibandingkan", p.getValue(1).hargaSebelumnya("RTG"))
        assertEquals(500L, p.getValue(2).hargaSebelumnya(null))
        assertNull(p[3])
    }
}
