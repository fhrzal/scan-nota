package com.scannota.ocr

import kotlin.math.atan2

/*
 * Model data hasil OCR.
 *
 * Kelas-kelas ini sengaja dibuat sendiri, bukan memakai kelas ML Kit atau android.graphics.Rect.
 * Kelas Android tidak berfungsi di unit test laptop (JVM biasa), padahal parsing/ dan merge/
 * harus bisa diuji di laptop. Konversi dari ML Kit hanya terjadi di satu tempat: PengenalTeks.
 */

/** Satu titik dalam piksel foto. Sumbu y mengarah ke bawah, sama seperti koordinat layar. */
data class Titik(val x: Int, val y: Int)

/** Kotak tegak (sejajar sumbu) yang membungkus sebuah teks, dalam piksel foto. */
data class Kotak(val kiri: Int, val atas: Int, val kanan: Int, val bawah: Int) {
    val lebar: Int get() = kanan - kiri
    val tinggi: Int get() = bawah - atas
}

/**
 * Satu kata (Element di ML Kit).
 *
 * @param titikSudut empat titik sudut, searah jarum jam mulai dari kiri atas teks, atau kosong
 *   jika ML Kit tidak memberikan posisi
 */
data class KataOcr(
    val teks: String,
    val keyakinan: Float,
    val titikSudut: List<Titik>,
) {
    init {
        require(titikSudut.isEmpty() || titikSudut.size == 4) { "titik sudut harus 0 atau 4" }
    }

    val kotak: Kotak? get() = kotakDari(titikSudut)
}

/**
 * Satu baris teks (Line di ML Kit). Satu baris nota di foto bisa terpecah menjadi beberapa
 * BarisOcr, misalnya nama barang dan harganya; penyatuannya dikerjakan oleh susunBarisVisual.
 *
 * Posisi disimpan sebagai empat titik sudut, bukan hanya kotak tegak. Pada foto yang miring,
 * kotak tegak sebuah baris panjang jauh lebih tinggi daripada hurufnya, sehingga tinggi teks
 * yang sebenarnya hanya bisa diketahui dari titik sudut.
 *
 * @param keyakinan skor dari ML Kit (0–1), diteruskan apa adanya
 * @param indeksBlok nomor blok ML Kit asal baris ini, disimpan untuk penelusuran
 */
data class BarisOcr(
    val teks: String,
    val keyakinan: Float,
    val titikSudut: List<Titik>,
    val indeksBlok: Int,
    val kata: List<KataOcr>,
) {
    init {
        require(titikSudut.isEmpty() || titikSudut.size == 4) { "titik sudut harus 0 atau 4" }
    }

    val kotak: Kotak? get() = kotakDari(titikSudut)

    /** Kemiringan baris dalam derajat; lihat [kemiringanDerajat]. */
    val kemiringan: Float? get() = kemiringanDerajat(titikSudut)
}

/**
 * Hasil OCR satu foto.
 *
 * @param lebar lebar foto dalam posisi tegak (setelah rotasi diterapkan)
 * @param tinggi tinggi foto dalam posisi tegak
 * @param baris baris-baris dalam urutan asli ML Kit (per blok, lalu per baris)
 */
data class HasilOcrFoto(
    val lebar: Int,
    val tinggi: Int,
    val baris: List<BarisOcr>,
)

/** Kotak tegak terkecil yang memuat semua titik, atau null jika tidak ada titik. */
fun kotakDari(titik: List<Titik>): Kotak? {
    if (titik.isEmpty()) return null
    return Kotak(
        kiri = titik.minOf { it.x },
        atas = titik.minOf { it.y },
        kanan = titik.maxOf { it.x },
        bawah = titik.maxOf { it.y },
    )
}

/**
 * Memindahkan satu titik dari bitmap asli ke foto dalam posisi tegak.
 *
 * Alasannya: ML Kit memakai nilai rotasi untuk membaca teks, tetapi koordinat yang dikembalikan
 * tetap mengacu pada bitmap asli yang belum diputar. Ini terbukti lewat smoke test
 * `rotasiDiterapkan_koordinatMengacuFotoTegak` di HP. Supaya semua modul memakai satu acuan,
 * koordinat dipindahkan ke foto tegak, yaitu foto seperti yang dilihat pengguna.
 *
 * Rumusnya adalah putaran gambar searah jarum jam sebesar [rotasiDerajat]:
 * - 90°:  (x, y) → (tinggi − y, x)
 * - 180°: (x, y) → (lebar − x, tinggi − y)
 * - 270°: (x, y) → (y, lebar − x)
 *
 * @param lebarAsli lebar bitmap asli (sebelum diputar)
 * @param tinggiAsli tinggi bitmap asli (sebelum diputar)
 */
fun putarKeTegak(titik: Titik, rotasiDerajat: Int, lebarAsli: Int, tinggiAsli: Int): Titik =
    when (rotasiDerajat) {
        0 -> titik
        90 -> Titik(tinggiAsli - titik.y, titik.x)
        180 -> Titik(lebarAsli - titik.x, tinggiAsli - titik.y)
        270 -> Titik(titik.y, lebarAsli - titik.x)
        else -> throw IllegalArgumentException("rotasi harus 0, 90, 180, atau 270")
    }

/**
 * Kemiringan teks dalam derajat, dihitung dari tepi atasnya (titik kiri atas ke kanan atas).
 *
 * Positif berarti ujung kanan teks lebih rendah, atau miring searah jarum jam di layar. Arah
 * ini ditetapkan sendiri dari titik sudut, tidak bergantung pada definisi sudut versi ML Kit,
 * supaya artinya pasti dan bisa diuji.
 *
 * @return null jika titik sudut tidak ada
 */
fun kemiringanDerajat(titikSudut: List<Titik>): Float? {
    if (titikSudut.size != 4) return null
    val kiriAtas = titikSudut[0]
    val kananAtas = titikSudut[1]
    val radian = atan2(
        (kananAtas.y - kiriAtas.y).toDouble(),
        (kananAtas.x - kiriAtas.x).toDouble(),
    )
    return Math.toDegrees(radian).toFloat()
}
