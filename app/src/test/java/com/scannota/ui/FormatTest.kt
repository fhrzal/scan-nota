package com.scannota.ui

import com.scannota.parsing.bacaQty
import com.scannota.parsing.bacaUang
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Menguji format angka tampilan. Angka di sini contoh sintetis. */
class FormatTest {

    @Test
    fun rupiah_titikRibuan() {
        assertEquals("0", formatRupiah(0))
        assertEquals("500", formatRupiah(500))
        assertEquals("17.500", formatRupiah(17500))
        assertEquals("742.150", formatRupiah(742150))
        assertEquals("1.000.000", formatRupiah(1_000_000))
        assertEquals("-2.500", formatRupiah(-2500))
    }

    @Test
    fun qty_tanpaDesimalNolDiBelakang() {
        assertEquals("1", formatQty(100))
        assertEquals("10", formatQty(1000))
        assertEquals("0,5", formatQty(50))
        assertEquals("1,25", formatQty(125))
        assertEquals("0,05", formatQty(5))
    }

    /** Angka yang ditampilkan harus terbaca kembali sama persis oleh aturan parsing/. */
    @Test
    fun formatLaluBaca_bolakBalik() {
        for (n in listOf(0L, 7L, 999L, 1000L, 17500L, 742150L, 1_234_567L)) assertEquals(n, bacaUang(formatRupiah(n)))
        for (q in listOf(5L, 50L, 100L, 125L, 1000L, 9999L)) assertEquals(q, bacaQty(formatQty(q)))
    }

    @Test
    fun tidakTerpengaruhBahasaHp() {
        val asal = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("17.500", formatRupiah(17500))
            assertEquals("12-09-2025", formatTanggal(com.scannota.parsing.Tanggal(12, 9, 2025).keEpochHari()))
        } finally {
            Locale.setDefault(asal)
        }
    }
}
