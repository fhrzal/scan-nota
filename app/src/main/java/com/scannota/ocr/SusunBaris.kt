package com.scannota.ocr

import kotlin.math.cos
import kotlin.math.sin

/**
 * Dua potongan teks dianggap satu baris nota jika rentang tingginya bertumpang tindih minimal
 * sekian persen dari tinggi potongan yang lebih pendek.
 *
 * 50% adalah nilai awal: huruf yang sebaris tumpang tindih hampir 100%, sedangkan baris
 * berdempetan biasanya hanya bersentuhan di tepi. Nilai ini wajib diuji ulang dengan hasil OCR
 * nota asli di sesi parsing/.
 */
const val AMBANG_TUMPANG_TINDIH_PERSEN = 50

/**
 * Satu baris nota seperti terlihat di foto, misalnya nama barang, qty, harga, dan subtotal.
 *
 * @param potongan baris-baris ML Kit yang sejajar, urut dari kiri ke kanan. Potongan tidak
 *   digabung menjadi satu teks, supaya parsing/ masih tahu batas antarkolom.
 */
data class BarisVisual(val potongan: List<BarisOcr>) {
    init {
        require(potongan.isNotEmpty()) { "baris visual tidak boleh kosong" }
    }

    /** Semua potongan digabung dengan satu spasi, untuk ditampilkan ke pengguna. */
    val teks: String get() = potongan.joinToString(" ") { it.teks }

    /** Keyakinan potongan paling lemah; satu potongan salah baca sudah cukup membuat baris meragukan. */
    val keyakinanTerendah: Float get() = potongan.minOf { it.keyakinan }
}

/**
 * Perkiraan kemiringan seluruh foto: median kemiringan semua baris, dalam derajat.
 *
 * Dipakai median, bukan rata-rata, karena baris pendek (misalnya satu angka) sudutnya kurang
 * akurat. Median tidak terpengaruh oleh beberapa nilai ekstrem seperti itu.
 *
 * @return 0 jika tidak ada baris yang punya posisi
 */
fun perkiraanKemiringan(baris: List<BarisOcr>): Float {
    val sudut = baris.mapNotNull { it.kemiringan }.sorted()
    if (sudut.isEmpty()) return 0f
    val tengah = sudut.size / 2
    return if (sudut.size % 2 == 1) sudut[tengah] else (sudut[tengah - 1] + sudut[tengah]) / 2f
}

/** Rentang satu baris ML Kit setelah koordinatnya diluruskan. */
private class Rentang(val baris: BarisOcr, val atas: Double, val bawah: Double, val kiri: Double) {
    val tengah: Double get() = (atas + bawah) / 2
}

/**
 * Menyusun ulang baris ML Kit menjadi baris nota.
 *
 * Alasannya: ML Kit mengelompokkan teks per blok berdasarkan kedekatan. Pada nota, kolom nama
 * barang dan kolom harga dipisahkan ruang kosong yang lebar, sehingga sering keluar sebagai blok
 * terpisah. Urutan asli ML Kit menjadi "semua nama barang, lalu semua harga", tidak bisa
 * langsung dibaca per baris nota.
 *
 * Langkah:
 * 1. Perkirakan kemiringan foto (median kemiringan baris), lalu putar keempat titik sudut setiap
 *    baris ke arah sebaliknya. Tanpa langkah ini, pada foto yang miring sedikit, ujung kanan
 *    sebuah baris nota sejajar dengan baris berikutnya. Ini hanya meluruskan koordinat;
 *    meluruskan gambar (deskew) tetap tugas preprocessing/.
 * 2. Urutkan dari atas ke bawah berdasarkan titik tengah vertikalnya.
 * 3. Masukkan setiap potongan ke baris nota terakhir jika tumpang tindihnya cukup
 *    ([cukupTumpangTindih]). Jika tidak, mulai baris nota baru.
 * 4. Di dalam setiap baris nota, urutkan potongan dari kiri ke kanan.
 *
 * Baris tanpa posisi tidak bisa ditempatkan, jadi diletakkan di akhir, masing-masing sebagai
 * baris tersendiri. Baris itu tidak dibuang, karena teks yang hilang diam-diam bisa membuat
 * verifikasi total salah.
 */
fun susunBarisVisual(
    baris: List<BarisOcr>,
    ambangPersen: Int = AMBANG_TUMPANG_TINDIH_PERSEN,
): List<BarisVisual> {
    require(ambangPersen in 1..100) { "ambang harus 1–100 persen" }
    val (berposisi, tanpaPosisi) = baris.partition { it.titikSudut.isNotEmpty() }

    // Langkah 1. Rumus rotasi sebesar −θ: x' = x·cos θ + y·sin θ, y' = −x·sin θ + y·cos θ
    val theta = Math.toRadians(perkiraanKemiringan(berposisi).toDouble())
    val cosT = cos(theta)
    val sinT = sin(theta)
    val rentang = berposisi.map { b ->
        val xBaru = b.titikSudut.map { it.x * cosT + it.y * sinT }
        val yBaru = b.titikSudut.map { -it.x * sinT + it.y * cosT }
        Rentang(b, atas = yBaru.min(), bawah = yBaru.max(), kiri = xBaru.min())
    }

    // Langkah 2 dan 3.
    val kelompok = mutableListOf<MutableList<Rentang>>()
    var atasKelompok = 0.0
    var bawahKelompok = 0.0
    for (r in rentang.sortedWith(compareBy({ it.tengah }, { it.kiri }))) {
        val terakhir = kelompok.lastOrNull()
        if (terakhir != null && cukupTumpangTindih(r.atas, r.bawah, atasKelompok, bawahKelompok, ambangPersen)) {
            terakhir += r
            atasKelompok = minOf(atasKelompok, r.atas)
            bawahKelompok = maxOf(bawahKelompok, r.bawah)
        } else {
            kelompok += mutableListOf(r)
            atasKelompok = r.atas
            bawahKelompok = r.bawah
        }
    }

    // Langkah 4.
    return kelompok.map { isi -> BarisVisual(isi.sortedBy { it.kiri }.map { it.baris }) } +
        tanpaPosisi.map { BarisVisual(listOf(it)) }
}

/**
 * Apakah dua rentang vertikal cukup tumpang tindih untuk dianggap satu baris.
 *
 * Pembandingnya tinggi rentang yang lebih pendek. Dengan begitu, angka kecil yang sebaris dengan
 * nama barang yang hurufnya lebih besar tetap masuk baris yang sama. Rentang yang tidak
 * bersentuhan sama sekali selalu ditolak, termasuk jika salah satunya bertinggi nol.
 */
internal fun cukupTumpangTindih(
    atas1: Double,
    bawah1: Double,
    atas2: Double,
    bawah2: Double,
    ambangPersen: Int,
): Boolean {
    val irisanAtas = maxOf(atas1, atas2)
    val irisanBawah = minOf(bawah1, bawah2)
    if (irisanAtas > irisanBawah) return false
    val tinggiTerkecil = minOf(bawah1 - atas1, bawah2 - atas2)
    return (irisanBawah - irisanAtas) * 100 >= ambangPersen * tinggiTerkecil
}
