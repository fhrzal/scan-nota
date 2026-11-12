package com.scannota.preprocessing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Aturan pengolahan citra yang tidak bergantung pada OpenCV, sehingga bisa diuji di laptop.
 * Bagian yang memakai OpenCV ada di PengolahCitra.kt dan hanya berjalan di HP.
 */

/**
 * Kolom atau baris dianggap kertas jika minimal 50% pikselnya putih-pucat. Dari 6 foto asli,
 * kolom kertas berproporsi 0,69–1,00, sedangkan kolom latar paling tinggi 0,34. Nilai 0,5
 * berada di celah itu.
 */
const val AMBANG_PROPORSI_KERTAS = 0.5

/** Piksel dianggap putih-pucat jika saturasinya di bawah ini (skala HSV OpenCV 0–255). */
const val SATURASI_MAKS_KERTAS = 60

/**
 * Deskew hanya dilakukan jika |sudut| minimal 0,5°. Di bawah itu, pada nota selebar 40 huruf,
 * ujung kanan baris bergeser kurang dari seperlima tinggi huruf, sehingga memutar gambar hanya
 * menambah buram tanpa manfaat.
 */
const val SUDUT_DESKEW_MIN = 0.5

/** Di atas 15°, perkiraan sudut kemungkinan keliru (foto nota dipotret tegak), jadi tidak diputar. */
const val SUDUT_DESKEW_MAKS = 15.0

/**
 * Selisih terhadap rata-rata lingkungan pada ambang adaptif. Piksel dianggap tinta jika minimal
 * 10 tingkat (dari 255) lebih gelap daripada sekitarnya, cukup di atas derau tekstur kertas.
 */
const val SELISIH_AMBANG_ADAPTIF = 10.0

/**
 * Deretan true terpanjang. Dipakai untuk kolom kertas: nota adalah pita tegak, sehingga kolom
 * kertas membentuk satu deretan panjang, sedangkan bagian latar yang kebetulan terang hanya
 * deretan pendek.
 *
 * @return rentang indeks (ujung tidak termasuk), atau null jika tidak ada true
 */
fun deretanTerpanjang(nilai: BooleanArray): IntRange? {
    var terbaikAwal = -1
    var terbaikPanjang = 0
    var awal = -1
    for (i in 0..nilai.size) {
        val benar = i < nilai.size && nilai[i]
        if (benar && awal < 0) awal = i
        if (!benar && awal >= 0) {
            if (i - awal > terbaikPanjang) {
                terbaikPanjang = i - awal
                terbaikAwal = awal
            }
            awal = -1
        }
    }
    return if (terbaikPanjang == 0) null else terbaikAwal until terbaikAwal + terbaikPanjang
}

/**
 * Dari true pertama sampai true terakhir. Dipakai untuk baris kertas: baris teks yang padat di
 * tengah nota bisa berproporsi rendah, dan aturan ini tidak memotong nota di situ.
 */
fun rentangLuar(nilai: BooleanArray): IntRange? {
    val awal = nilai.indexOfFirst { it }
    if (awal < 0) return null
    return awal until nilai.indexOfLast { it } + 1
}

/**
 * Ukuran blok ambang adaptif: sekitar 2× tinggi huruf, supaya setiap blok memuat huruf dan kertas
 * di sekitarnya. Tinggi huruf diperkirakan lebar kertas / 20 (nota Toko Oren memuat sekitar 40
 * huruf per baris, dan tinggi huruf sekitar 2× lebarnya). Ukuran blok dihitung dari lebar kertas,
 * bukan angka tetap, karena lebar foto uji berkisar 479–2296 piksel. Harus ganjil (syarat OpenCV),
 * minimal 15.
 */
fun ukuranBlokAmbang(lebarKertas: Int): Int {
    val blok = max(15, (lebarKertas / 10.0).roundToInt())
    return if (blok % 2 == 0) blok + 1 else blok
}

/** true jika sudut layak diluruskan (lihat [SUDUT_DESKEW_MIN] dan [SUDUT_DESKEW_MAKS]). */
fun perluDiluruskan(sudut: Double): Boolean = abs(sudut) >= SUDUT_DESKEW_MIN && abs(sudut) < SUDUT_DESKEW_MAKS

/**
 * Ketajaman profil proyeksi: jumlah kuadrat selisih antarbaris berurutan. Jika baris teks lurus,
 * piksel tinta menumpuk pada baris-baris tertentu dan celah antarbaris kosong, sehingga profilnya
 * naik-turun tajam dan nilainya besar. Jika miring, tinta tersebar dan profilnya landai.
 */
fun ketajamanProfil(profil: DoubleArray): Double {
    var jumlah = 0.0
    for (i in 1 until profil.size) {
        val d = profil[i] - profil[i - 1]
        jumlah += d * d
    }
    return jumlah
}

/**
 * Mencari sudut dengan nilai terbesar: kasar per 1° dalam ±[batas], lalu halus per 0,1° di
 * sekitar sudut kasar terbaik. Dua tahap supaya cukup 52 kali percobaan, bukan 301.
 * Jika seri, dipilih sudut yang lebih dekat ke 0.
 */
fun cariSudutTerbaik(batas: Double = SUDUT_DESKEW_MAKS, nilai: (Double) -> Double): Double {
    fun terbaik(kandidat: List<Double>): Double =
        kandidat.maxWith(compareBy<Double> { nilai(it) }.thenByDescending { abs(it) })
    val kasar = terbaik((-batas.toInt()..batas.toInt()).map { it.toDouble() })
    val halus = terbaik((-10..10).map { kasar + it / 10.0 })
    return (halus * 10).roundToInt() / 10.0
}
