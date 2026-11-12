package com.scannota.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

/** Menguji tanggal nota. Teks tanggal disalin dari hasil OCR nota asli, kecuali yang ditandai sintetis. */
class TanggalTest {

    @Test
    fun bacaTanggal_dariBarisNomor() {
        assertEquals(Tanggal(12, 9, 2025), bacaTanggal("No. : 002552/KSR/UTM/0925 12-09-2025"))
    }

    @Test
    fun bacaTanggal_hurufLTerbacaSebagaiAngka1() {
        assertEquals(Tanggal(16, 7, 2026), bacaTanggal("No. : 003182/KSR/UTH/0726 l6-07-2026"))
    }

    @Test
    fun bacaTanggal_tidakAda_null() {
        assertNull(bacaTanggal("Kasir: BANANA 10:37:40"))
        assertNull("sintetis: tanggal 31 Februari tidak ada", bacaTanggal("31-02-2026"))
        assertNull("sintetis: tahun di luar rentang nota", bacaTanggal("12-09-7025"))
    }

    @Test
    fun tanggalTidakSah_ditolak() {
        assertThrows(IllegalArgumentException::class.java) { Tanggal(29, 2, 2025) }
        assertThrows(IllegalArgumentException::class.java) { Tanggal(31, 4, 2026) }
        assertEquals("sintetis: 2028 tahun kabisat", 29, Tanggal(29, 2, 2028).hari)
    }

    /** Rumus epoch hari dicocokkan dengan java.time untuk setiap hari dari 2000 sampai 2099. */
    @Test
    fun epochHari_bolakBalik_samaDenganJavaTime_setiapHari2000Sampai2099() {
        var hari = LocalDate.of(2000, 1, 1)
        val akhir = LocalDate.of(2099, 12, 31)
        while (!hari.isAfter(akhir)) {
            val tanggal = Tanggal(hari.dayOfMonth, hari.monthValue, hari.year)
            assertEquals(hari.toString(), hari.toEpochDay(), tanggal.keEpochHari())
            assertEquals(hari.toString(), tanggal, Tanggal.dariEpochHari(hari.toEpochDay()))
            hari = hari.plusDays(1)
        }
    }

    @Test
    fun toString_formatNota() {
        assertEquals("05-08-2026", Tanggal(5, 8, 2026).toString())
    }
}
