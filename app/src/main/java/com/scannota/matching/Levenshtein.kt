package com.scannota.matching

/**
 * Jarak Levenshtein: jumlah minimum operasi satu karakter (sisip, hapus, atau ganti,
 * masing-masing berbiaya 1) untuk mengubah teks [a] menjadi [b].
 *
 * Ditulis manual dengan pemrograman dinamis. Misalkan d[i][j] adalah jarak antara
 * i karakter pertama teks panjang dan j karakter pertama teks pendek:
 *   d[i][0] = i                       (hapus i karakter)
 *   d[0][j] = j                       (sisip j karakter)
 *   d[i][j] = minimum dari:
 *     d[i-1][j]   + 1                 (hapus)
 *     d[i][j-1]   + 1                 (sisip)
 *     d[i-1][j-1] + (0 jika karakternya sama, 1 jika berbeda)   (ganti)
 *
 * Setiap baris tabel hanya bergantung pada baris tepat di atasnya, jadi yang disimpan cukup
 * dua baris, bukan seluruh tabel. Waktu O(n × m), memori O(panjang teks yang lebih pendek).
 */
fun jarakLevenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    // Jarak Levenshtein simetris, jadi teks boleh ditukar. Teks pendek dijadikan kolom
    // supaya baris tabel sependek mungkin.
    val panjang = if (a.length >= b.length) a else b
    val pendek = if (a.length >= b.length) b else a

    var barisSebelumnya = IntArray(pendek.length + 1) { j -> j }
    var barisSekarang = IntArray(pendek.length + 1)

    for (i in 1..panjang.length) {
        barisSekarang[0] = i
        for (j in 1..pendek.length) {
            val biayaGanti = if (panjang[i - 1] == pendek[j - 1]) 0 else 1
            barisSekarang[j] = minOf(
                barisSebelumnya[j] + 1,              // hapus
                barisSekarang[j - 1] + 1,            // sisip
                barisSebelumnya[j - 1] + biayaGanti, // ganti (atau sama)
            )
        }
        val tukar = barisSebelumnya
        barisSebelumnya = barisSekarang
        barisSekarang = tukar
    }
    return barisSebelumnya[pendek.length]
}

/**
 * Skor kemiripan dua teks: kemiripan = 1 − jarak / panjangMaks.
 *
 * Skor disimpan sebagai pecahan bilangan bulat (jarak, panjangMaks), bukan sebagai bilangan
 * desimal. Dengan begitu, pemeriksaan ambang dan perbandingan dua skor selalu tepat, termasuk
 * untuk nilai yang persis di batas. Dengan bilangan desimal, rumus yang secara matematika
 * setara bisa memberi hasil berbeda karena galat pembulatan (lihat test
 * `galatDesimal_alasanMemakaiBilanganBulat`).
 */
class Skor(val jarak: Int, val panjangMaks: Int) : Comparable<Skor> {

    init {
        require(panjangMaks > 0) { "panjangMaks harus lebih dari 0" }
        require(jarak in 0..panjangMaks) { "jarak harus di antara 0 dan panjangMaks" }
    }

    /** Nilai kemiripan 0..1, hanya untuk ditampilkan. Keputusan tidak memakai nilai ini. */
    val nilai: Double get() = 1.0 - jarak.toDouble() / panjangMaks

    /**
     * true jika kemiripan ≥ ambangPersen / 100.
     * Diturunkan tanpa pembagian: 1 − jarak/panjang ≥ p/100  ⇔  100 × jarak ≤ (100 − p) × panjang.
     */
    fun mencapai(ambangPersen: Int): Boolean =
        jarak.toLong() * 100 <= (100L - ambangPersen) * panjangMaks

    /**
     * Skor lebih besar berarti lebih mirip. Dua pecahan dibandingkan dengan perkalian silang:
     * jarak1/panjang1 < jarak2/panjang2  ⇔  jarak1 × panjang2 < jarak2 × panjang1.
     * Akibatnya, 1/10 dan 2/20 dianggap sama persis.
     */
    override fun compareTo(other: Skor): Int =
        (other.jarak.toLong() * panjangMaks).compareTo(jarak.toLong() * other.panjangMaks)

    override fun toString(): String = "Skor(jarak=$jarak, panjangMaks=$panjangMaks, nilai=${"%.3f".format(nilai)})"
}

/**
 * Menghitung skor kemiripan dua teks. Dua teks kosong dianggap identik; panjangMaks minimal 1
 * supaya tidak pernah terjadi pembagian dengan nol.
 */
fun hitungSkor(a: String, b: String): Skor =
    Skor(jarak = jarakLevenshtein(a, b), panjangMaks = maxOf(a.length, b.length, 1))
