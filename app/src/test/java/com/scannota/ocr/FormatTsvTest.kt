package com.scannota.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Menguji format TSV hasil OCR. Semua teks dan angka di sini adalah contoh sintetis untuk
 * menguji format berkas, bukan data nota.
 */
class FormatTsvTest {

    private fun kotak(kiri: Int, atas: Int, kanan: Int, bawah: Int) =
        listOf(Titik(kiri, atas), Titik(kanan, atas), Titik(kanan, bawah), Titik(kiri, bawah))

    private val contoh = HasilOcrFoto(
        lebar = 1080,
        tinggi = 1920,
        baris = listOf(
            BarisOcr(
                teks = "ABC DEF",
                keyakinan = 0.93f,
                titikSudut = kotak(10, 20, 200, 50),
                indeksBlok = 0,
                kata = listOf(
                    KataOcr("ABC", 0.95f, kotak(10, 20, 90, 50)),
                    KataOcr("DEF", 0.91f, kotak(110, 20, 200, 50)),
                ),
            ),
            BarisOcr(
                teks = "tab\tganti\nbaris\\miring Rp é",
                keyakinan = 0f,
                titikSudut = emptyList(),
                indeksBlok = 1,
                kata = emptyList(),
            ),
            BarisOcr(
                teks = "",
                keyakinan = 1f,
                titikSudut = listOf(Titik(-5, -7), Titik(30, -2), Titik(28, 12), Titik(-7, 7)),
                indeksBlok = 1,
                kata = listOf(KataOcr("", 0.5f, emptyList())),
            ),
        ),
    )

    @Test
    fun tulisLaluBaca_hasilnyaSamaPersis() {
        assertEquals(contoh, dariTsv(keTsv(contoh)))
    }

    @Test
    fun bentukBerkas_sesuaiDokumentasi() {
        val satuBaris = contoh.copy(baris = contoh.baris.take(1))
        val diharapkan = """
            #scannota-ocr	1
            #ukuran	1080	1920
            jenis	blok	baris	x1	y1	x2	y2	x3	y3	x4	y4	keyakinan	teks
            BARIS	0	0	10	20	200	20	200	50	10	50	0.93	ABC DEF
            KATA	0	0	10	20	90	20	90	50	10	50	0.95	ABC
            KATA	0	0	110	20	200	20	200	50	110	50	0.91	DEF
        """.trimIndent() + "\n"
        assertEquals(diharapkan, keTsv(satuBaris))
    }

    /** HP berbahasa Indonesia menulis desimal dengan koma. Berkas harus tetap memakai titik. */
    @Test
    fun bahasaIndonesia_desimalTetapTitik() {
        val asal = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("id-ID"))
            val tsv = keTsv(contoh)
            assertTrue(tsv.contains("\t0.93\t"))
            assertFalse(tsv.contains("0,93"))
            assertEquals(contoh, dariTsv(tsv))
        } finally {
            Locale.setDefault(asal)
        }
    }

    @Test
    fun akhirBarisWindows_tetapTerbaca() {
        val tsv = keTsv(contoh).replace("\n", "\r\n")
        assertEquals(contoh, dariTsv(tsv))
    }

    @Test
    fun penandaSalah_ditolakDenganNomorBaris() {
        val galat = assertThrows(IllegalArgumentException::class.java) {
            dariTsv(keTsv(contoh).replaceFirst("#scannota-ocr\t1", "#scannota-ocr\t9"))
        }
        assertTrue(galat.message!!.startsWith("baris file ke-1:"))
    }

    @Test
    fun kataSebelumBaris_ditolak() {
        val tsv = keTsv(contoh).lines().filterIndexed { i, _ -> i != 3 }.joinToString("\n")
        val galat = assertThrows(IllegalArgumentException::class.java) { dariTsv(tsv) }
        assertTrue(galat.message!!.contains("KATA muncul sebelum BARIS"))
    }

    @Test
    fun titikSudutSetengahKosong_ditolak() {
        val tsv = keTsv(contoh).replaceFirst("BARIS\t0\t0\t10\t20", "BARIS\t0\t0\t\t20")
        val galat = assertThrows(IllegalArgumentException::class.java) { dariTsv(tsv) }
        assertTrue(galat.message!!.startsWith("baris file ke-4:"))
    }

    @Test
    fun urutanGarisMiringTakDikenal_ditolak() {
        val tsv = keTsv(contoh).replaceFirst("ABC DEF", "ABC\\xDEF")
        assertThrows(IllegalArgumentException::class.java) { dariTsv(tsv) }
    }
}
