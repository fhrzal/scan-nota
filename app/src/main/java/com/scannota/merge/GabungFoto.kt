package com.scannota.merge

import com.scannota.matching.AMBANG_KONFIRMASI_PERSEN
import com.scannota.matching.hitungSkor
import com.scannota.matching.normalisasiTeks
import com.scannota.ocr.BarisVisual

/** Aturan proyek: satu nota paling banyak 20 foto. */
const val MAKS_FOTO_PER_NOTA = 20

/**
 * Sambungan dua foto diterima jika nilai penjajarannya minimal 2, artinya minimal dua baris
 * cocok setelah dikurangi baris yang tidak cocok. Satu baris cocok saja tidak cukup, karena di
 * nota asli baris harga yang identik bisa muncul berulang (misalnya "1.700,00 x 5,00 PCS =
 * 8.500,00" untuk dua barang berbeda).
 */
const val NILAI_MINIMAL_SAMBUNGAN = 2

/**
 * Keterangan satu sambungan antara foto ke-k dan ke-(k+1).
 *
 * @param bersambung false berarti tidak ditemukan tumpang tindih; layar perlu memperingatkan
 *   bahwa mungkin ada barang yang terlewat di antara kedua foto
 * @param barisCocok jumlah pasangan baris yang dianggap sama di daerah tumpang tindih
 * @param nilai nilai penjajaran (lihat [sambungkan])
 */
data class Sambungan(val bersambung: Boolean, val barisCocok: Int, val nilai: Int)

data class HasilGabung(val baris: List<BarisVisual>, val sambungan: List<Sambungan>) {
    val semuaBersambung: Boolean get() = sambungan.all { it.bersambung }
}

/**
 * Menggabungkan baris visual dari beberapa foto satu nota menjadi satu daftar baris. Penggabungan
 * dilakukan di level teks, bukan dengan menyambung gambar.
 *
 * Foto dianggap berurutan dari atas ke bawah sesuai urutan pengambilan. Urutan tidak ditebak
 * otomatis; jika keliru, pengguna mengubahnya di layar.
 *
 * @param foto baris visual setiap foto, urut dari foto paling atas
 */
fun gabungkanFoto(foto: List<List<BarisVisual>>): HasilGabung {
    require(foto.isNotEmpty()) { "minimal satu foto" }
    require(foto.size <= MAKS_FOTO_PER_NOTA) { "paling banyak $MAKS_FOTO_PER_NOTA foto per nota" }
    var hasil = foto.first()
    val sambungan = mutableListOf<Sambungan>()
    for (berikut in foto.drop(1)) {
        val (gabungan, keterangan) = sambungkan(hasil, berikut)
        hasil = gabungan
        sambungan += keterangan
    }
    return HasilGabung(hasil, sambungan)
}

/** Langkah penjajaran, urut dari atas ke bawah. */
private sealed interface Langkah {
    /** Baris atas[i] dan bawah[j] dianggap baris yang sama. */
    data class Cocok(val i: Int, val j: Int) : Langkah

    /** Baris atas[i] dan bawah[j] berpasangan tetapi tidak mirip; keduanya disimpan. */
    data class Beda(val i: Int, val j: Int) : Langkah

    /** Baris hanya ada di foto atas. */
    data class HanyaAtas(val i: Int) : Langkah

    /** Baris hanya ada di foto bawah. */
    data class HanyaBawah(val j: Int) : Langkah
}

/**
 * Menyambung dua daftar baris: mencari ekor [atas] yang paling cocok dengan kepala [bawah].
 *
 * Caranya pemrograman dinamis, dengan prinsip yang sama seperti Levenshtein di matching/, tetapi
 * satuannya baris, bukan huruf. Pencocokan baris per baris saja tidak cukup. Pada dua foto nota
 * 005493, baris yang sama kemiripannya 0,50–1,00, sedangkan baris berbeda bisa mencapai 0,84
 * atau bahkan identik. Urutan baris yang membedakannya.
 *
 * Nilai:
 * - dua baris dianggap sama jika kemiripannya ≥ 0,70 (ambang konfirmasi matching/): +1
 * - berpasangan tetapi tidak mirip: −1
 * - baris yang hanya ada di satu foto (misalnya tidak terdeteksi OCR di foto lain): −1
 *
 * Tabel n[i][j] = nilai terbaik untuk menjajarkan ekor atas[..i) dengan kepala bawah[0, j).
 * - n[i][0] = 0 untuk semua i, karena tumpang tindih boleh mulai di baris mana saja dari foto atas.
 * - n[0][j] = −j.
 * - Tumpang tindih harus berakhir di baris terakhir foto atas, jadi yang dipilih adalah
 *   n[jumlah baris atas][j] terbesar. Jika seri, dipilih j terkecil, supaya tidak ada baris foto
 *   bawah yang tertelan.
 *
 * Hasil penggabungan:
 * - baris atas sebelum tumpang tindih, lalu daerah tumpang tindih, lalu sisa baris bawah
 * - di daerah tumpang tindih, pasangan yang cocok diambil satu versinya: setengah atas dari
 *   foto atas dan setengah bawah dari foto bawah, sehingga setiap baris diambil dari foto tempat
 *   posisinya lebih jauh dari tepi (di nota asli, galat paling banyak terjadi di tepi foto)
 * - baris yang tidak punya pasangan cocok tetap disimpan semua, karena lebih baik ada baris ganda
 *   yang nanti ditandai daripada barang yang hilang diam-diam
 * - jika nilai < [NILAI_MINIMAL_SAMBUNGAN], kedua foto dianggap tidak bersambung dan barisnya
 *   digabung berurutan
 */
internal fun sambungkan(atas: List<BarisVisual>, bawah: List<BarisVisual>): Pair<List<BarisVisual>, Sambungan> {
    val n = atas.size
    val m = bawah.size
    if (n == 0 || m == 0) return (atas + bawah) to Sambungan(bersambung = false, barisCocok = 0, nilai = 0)

    val teksAtas = atas.map { normalisasiTeks(it.teks) }
    val teksBawah = bawah.map { normalisasiTeks(it.teks) }
    val sama = Array(n) { i -> BooleanArray(m) { j -> hitungSkor(teksAtas[i], teksBawah[j]).mencapai(AMBANG_KONFIRMASI_PERSEN) } }
    fun nilaiPasangan(i: Int, j: Int) = if (sama[i][j]) 1 else -1

    val nilai = Array(n + 1) { IntArray(m + 1) }
    for (j in 1..m) nilai[0][j] = -j
    for (i in 1..n) {
        for (j in 1..m) {
            nilai[i][j] = maxOf(
                nilai[i - 1][j - 1] + nilaiPasangan(i - 1, j - 1),
                nilai[i - 1][j] - 1,
                nilai[i][j - 1] - 1,
            )
        }
    }
    val jAkhir = (1..m).maxWith(compareBy<Int> { nilai[n][it] }.thenByDescending { it })
    val nilaiTerbaik = nilai[n][jAkhir]
    if (nilaiTerbaik < NILAI_MINIMAL_SAMBUNGAN) {
        return (atas + bawah) to Sambungan(bersambung = false, barisCocok = 0, nilai = nilaiTerbaik)
    }

    // Telusur balik dari (n, jAkhir) sampai kepala foto bawah (j = 0).
    val langkah = ArrayDeque<Langkah>()
    var i = n
    var j = jAkhir
    while (j > 0) {
        if (i > 0 && nilai[i][j] == nilai[i - 1][j - 1] + nilaiPasangan(i - 1, j - 1)) {
            langkah.addFirst(if (sama[i - 1][j - 1]) Langkah.Cocok(i - 1, j - 1) else Langkah.Beda(i - 1, j - 1))
            i--
            j--
        } else if (i > 0 && nilai[i][j] == nilai[i - 1][j] - 1) {
            langkah.addFirst(Langkah.HanyaAtas(i - 1))
            i--
        } else {
            langkah.addFirst(Langkah.HanyaBawah(j - 1))
            j--
        }
    }
    val awalTumpangTindih = i

    val jumlahCocok = langkah.count { it is Langkah.Cocok }
    var urutanCocok = 0
    val hasil = atas.subList(0, awalTumpangTindih).toMutableList()
    for (l in langkah) {
        when (l) {
            is Langkah.Cocok -> {
                hasil += if (urutanCocok * 2 < jumlahCocok) atas[l.i] else bawah[l.j]
                urutanCocok++
            }
            is Langkah.Beda -> {
                hasil += atas[l.i]
                hasil += bawah[l.j]
            }
            is Langkah.HanyaAtas -> hasil += atas[l.i]
            is Langkah.HanyaBawah -> hasil += bawah[l.j]
        }
    }
    hasil += bawah.subList(jAkhir, m)
    return hasil to Sambungan(bersambung = true, barisCocok = jumlahCocok, nilai = nilaiTerbaik)
}
