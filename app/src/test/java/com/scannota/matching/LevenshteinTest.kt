package com.scannota.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Menguji jarak Levenshtein dan skor kemiripan. Semua teks adalah kata contoh, bukan data nota. */
class LevenshteinTest {

    @Test
    fun contohBakuLiteratur_kittenKeSitting_jarak3() {
        // kitten → sitten (ganti k→s) → sittin (ganti e→i) → sitting (sisip g)
        assertEquals(3, jarakLevenshtein("kitten", "sitting"))
    }

    @Test
    fun contohBakuLiteratur_flawKeLawn_jarak2() {
        // flaw → law (hapus f) → lawn (sisip n)
        assertEquals(2, jarakLevenshtein("flaw", "lawn"))
    }

    @Test
    fun teksIdentik_jarak0() {
        assertEquals(0, jarakLevenshtein("ABCDEF", "ABCDEF"))
    }

    @Test
    fun teksKosong_jarakSamaDenganPanjangTeksLain() {
        assertEquals(0, jarakLevenshtein("", ""))
        assertEquals(5, jarakLevenshtein("", "ABCDE"))
        assertEquals(5, jarakLevenshtein("ABCDE", ""))
    }

    @Test
    fun satuOperasi_masingMasingBerbiaya1() {
        assertEquals("sisip", 1, jarakLevenshtein("ABC", "ABXC"))
        assertEquals("hapus", 1, jarakLevenshtein("ABXC", "ABC"))
        assertEquals("ganti", 1, jarakLevenshtein("ABC", "AXC"))
    }

    @Test
    fun jarakSimetris() {
        val pasangan = listOf("kitten" to "sitting", "flaw" to "lawn", "A" to "ABCDEFG", "ABCD" to "DCBA")
        for ((a, b) in pasangan) {
            assertEquals("$a vs $b", jarakLevenshtein(a, b), jarakLevenshtein(b, a))
        }
    }

    /**
     * Implementasi dua baris dibandingkan dengan implementasi tabel penuh (versi buku teks) pada
     * 2.000 pasangan teks acak. Benih acak tetap (42), jadi hasilnya selalu bisa diulang.
     * Abjad kecil ("AB " dan angka) dipakai supaya banyak karakter yang sama dan berbeda bercampur.
     */
    @Test
    fun samaDenganTabelPenuh_pada2000PasanganAcak() {
        val acak = kotlin.random.Random(42)
        val abjad = "AB 1"
        fun teksAcak() = (1..acak.nextInt(0, 13)).map { abjad[acak.nextInt(abjad.length)] }.joinToString("")
        repeat(2000) {
            val a = teksAcak()
            val b = teksAcak()
            assertEquals("\"$a\" vs \"$b\"", levenshteinTabelPenuh(a, b), jarakLevenshtein(a, b))
        }
    }

    /** Rujukan: tabel (n+1) × (m+1) lengkap, langsung dari definisi rekursif. */
    private fun levenshteinTabelPenuh(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val ganti = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + ganti)
            }
        }
        return d[a.length][b.length]
    }

    @Test
    fun kemiripan_satuDikurangiJarakDibagiPanjangTerpanjang() {
        val skor = hitungSkor("kitten", "sitting")
        assertEquals(3, skor.jarak)
        assertEquals(7, skor.panjangMaks)
        assertEquals(1.0 - 3.0 / 7.0, skor.nilai, 1e-12)
    }

    @Test
    fun duaTeksKosong_dianggapIdentik() {
        assertEquals(1.0, hitungSkor("", "").nilai, 0.0)
    }

    @Test
    fun ambangTepatDiBatas_termasuk() {
        assertTrue("1/10 → 0,90", Skor(1, 10).mencapai(90))
        assertTrue("2/20 → 0,90", Skor(2, 20).mencapai(90))
        assertFalse("2/10 → 0,80", Skor(2, 10).mencapai(90))
        assertTrue("3/10 → 0,70", Skor(3, 10).mencapai(70))
        assertFalse("4/10 → 0,60", Skor(4, 10).mencapai(70))
    }

    @Test
    fun perbandinganSkor_pecahanSetaraDianggapSama() {
        assertEquals(0, Skor(1, 10).compareTo(Skor(2, 20)))
        assertTrue("jarak lebih kecil = lebih mirip", Skor(1, 10) > Skor(2, 10))
        assertTrue("panjang lebih besar dengan jarak sama = lebih mirip", Skor(1, 20) > Skor(1, 10))
    }

    /**
     * Test dokumentasi: menunjukkan mengapa ambang dibandingkan dengan bilangan bulat.
     *
     * Nama 10 huruf dengan 1 kesalahan memiliki kemiripan tepat 0,90, jadi harus "otomatis".
     * Dengan bilangan desimal, rumus yang secara matematika setara memberi hasil berbeda:
     * komputer menghitung 1 − 0,90 sebagai 0,09999999999999998, sedangkan 1/10 sebagai 0,1,
     * sehingga rumus B salah menolak. Rumus bilangan bulat selalu benar.
     */
    @Test
    fun galatDesimal_alasanMemakaiBilanganBulat() {
        val jarak = 1
        val panjang = 10
        val rumusA = 1.0 - jarak.toDouble() / panjang >= 0.90
        val rumusB = jarak.toDouble() / panjang <= 1.0 - 0.90
        assertTrue("rumus A kebetulan benar", rumusA)
        assertFalse("rumus B salah karena galat pembulatan", rumusB)
        assertTrue("rumus bilangan bulat benar", Skor(jarak, panjang).mencapai(90))
    }
}
