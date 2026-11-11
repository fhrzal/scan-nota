package com.scannota.ocr

import kotlin.math.cos
import kotlin.math.hypot
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

/** Rentang satu baris ML Kit setelah koordinatnya diputar sebesar −sudut. */
private class Rentang(val atas: Double, val bawah: Double, val kiri: Double, val kanan: Double) {
    val tengah: Double get() = (atas + bawah) / 2
}

/** Rumus rotasi sebesar −θ: x' = x·cos θ + y·sin θ, y' = −x·sin θ + y·cos θ. */
private fun rentang(baris: BarisOcr, sudutDerajat: Float): Rentang {
    val theta = Math.toRadians(sudutDerajat.toDouble())
    val cosT = cos(theta)
    val sinT = sin(theta)
    val x = baris.titikSudut.map { it.x * cosT + it.y * sinT }
    val y = baris.titikSudut.map { -it.x * sinT + it.y * cosT }
    return Rentang(atas = y.min(), bawah = y.max(), kiri = x.min(), kanan = x.max())
}

/** Panjang tepi atas (kiri atas ke kanan atas); dipakai untuk memilih potongan acuan kemiringan. */
private fun panjangTepiAtas(baris: BarisOcr): Double {
    val (a, b) = baris.titikSudut
    return hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
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
 * 1. Perkirakan kemiringan foto (median kemiringan baris), lalu urutkan potongan dari atas ke
 *    bawah setelah koordinatnya diluruskan dengan sudut itu.
 * 2. Masukkan setiap potongan ke baris nota terakhir jika [bolehMasuk]. Jika tidak, mulai baris
 *    nota baru.
 * 3. Di dalam setiap baris nota, urutkan potongan dari kiri ke kanan.
 *
 * Meluruskan koordinat tidak mengubah gambar; meluruskan gambar (deskew) tetap tugas preprocessing/.
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

    // Langkah 1.
    val sudutFoto = perkiraanKemiringan(berposisi)
    val urut = berposisi.map { it to rentang(it, sudutFoto) }
        .sortedWith(compareBy({ it.second.tengah }, { it.second.kiri }))

    // Langkah 2.
    val kelompok = mutableListOf<MutableList<Pair<BarisOcr, Rentang>>>()
    for (p in urut) {
        val terakhir = kelompok.lastOrNull()
        if (terakhir != null && bolehMasuk(p.first, terakhir.map { it.first }, ambangPersen)) {
            terakhir += p
        } else {
            kelompok += mutableListOf(p)
        }
    }

    // Langkah 3.
    return kelompok.map { isi -> BarisVisual(isi.sortedBy { it.second.kiri }.map { it.first }) } +
        tanpaPosisi.map { BarisVisual(listOf(it)) }
}

/**
 * Apakah potongan [calon] termasuk baris nota yang berisi [anggota].
 *
 * Dua aturan ditambahkan setelah diuji pada nota asli. Pada bagian bawah nota yang kertasnya
 * melengkung, kemiringan lokal (−2° sampai −4°) berbeda dari median foto (sekitar 0°), sehingga
 * baris nama tergabung dengan baris harga di bawahnya.
 * - Kemiringan lokal: posisi dibandingkan setelah diluruskan dengan kemiringan potongan terpanjang
 *   di baris itu, bukan kemiringan seluruh foto. Potongan terpanjang dipakai karena sudut
 *   potongan pendek (misalnya satu angka) kurang akurat.
 * - Tidak bertumpuk mendatar: dalam satu baris cetak, dua potongan teks tidak mungkin menempati
 *   posisi mendatar yang sama. Jika bertumpuk, keduanya pasti dari baris cetak yang berbeda.
 * Syarat tumpang tindih vertikal ([cukupTumpangTindih], ambang 50%) tetap sama.
 *
 * Hasil pada 5 foto nota asli (117 barang):
 * - baris harga yang tersusun utuh naik dari 109 menjadi 113 (prototipe, sebelum aturan ditulis);
 * - barang yang semua angkanya terbaca benar oleh parsing/ naik dari 104 menjadi 108
 *   (NotaAsliTest), dan barang yang terlewat turun dari 3 menjadi 0.
 */
private fun bolehMasuk(calon: BarisOcr, anggota: List<BarisOcr>, ambangPersen: Int): Boolean {
    val acuan = anggota.maxBy { panjangTepiAtas(it) }
    val sudutLokal = acuan.kemiringan ?: 0f
    val c = rentang(calon, sudutLokal)
    val a = anggota.map { rentang(it, sudutLokal) }
    if (!cukupTumpangTindih(c.atas, c.bawah, a.minOf { it.atas }, a.maxOf { it.bawah }, ambangPersen)) return false
    return a.none { bertumpukMendatar(c.kiri, c.kanan, it.kiri, it.kanan) }
}

/** true jika dua rentang mendatar beririsan lebih dari nol (bersentuhan di tepi masih boleh). */
internal fun bertumpukMendatar(kiri1: Double, kanan1: Double, kiri2: Double, kanan2: Double): Boolean =
    minOf(kanan1, kanan2) - maxOf(kiri1, kiri2) > 0

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
