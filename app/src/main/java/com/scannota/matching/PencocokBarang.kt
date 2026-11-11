package com.scannota.matching

/*
 * Aturan keputusan pencocokan, ditulis sebagai fungsi murni (tanpa database) supaya bisa
 * diuji dengan unit test dan mudah dijelaskan.
 */

/**
 * Ambang kemiripan dari CLAUDE.md (0,90 dan 0,70), ditulis sebagai persen bilangan bulat
 * agar perbandingannya tepat (lihat [Skor.mencapai]).
 * JANGAN diubah tanpa instruksi pengguna.
 */
const val AMBANG_OTOMATIS_PERSEN = 90
const val AMBANG_KONFIRMASI_PERSEN = 70

/** Jumlah kandidat yang ditampilkan di layar konfirmasi. */
const val JUMLAH_KANDIDAT_DITAMPILKAN = 3

enum class KeputusanPencocokan {
    /** Barang langsung dipakai tanpa bertanya. */
    OTOMATIS,

    /** Pengguna diminta memilih dari kandidat. */
    KONFIRMASI,

    /** Tidak ada yang cukup mirip; pengguna diminta membuat barang baru (atau memilih manual). */
    BARANG_BARU,
}

/** Asal teks pembanding sebuah kandidat. */
enum class SumberKandidat { ALIAS_PEMASOK, NAMA_BAKU }

/** Alasan keputusan otomatis diturunkan menjadi konfirmasi. */
enum class AlasanPenurunan {
    /** Angka di kedua nama berbeda, kemungkinan varian ukuran (misalnya 250 vs 500). */
    ANGKA_BERBEDA,

    /** Dua barang berbeda sama-sama mendapat skor tertinggi, jadi tidak bisa dipilih secara adil. */
    SKOR_KEMBAR,
}

/** Satu teks pembanding (alias atau nama baku) milik satu barang. Teks harus sudah dinormalisasi. */
data class Kandidat(val barangId: Long, val teks: String, val sumber: SumberKandidat)

/** Kandidat beserta skornya terhadap teks yang dicari. */
data class KandidatBerskor(
    val barangId: Long,
    val teks: String,
    val sumber: SumberKandidat,
    val skor: Skor,
)

data class HasilPencocokan(
    /** Teks OCR setelah dinormalisasi; teks inilah yang disimpan sebagai alias jika dikonfirmasi. */
    val teksNormal: String,
    val keputusan: KeputusanPencocokan,
    /** Paling banyak [JUMLAH_KANDIDAT_DITAMPILKAN] barang, terbaik di depan, satu entri per barang. */
    val kandidatTeratas: List<KandidatBerskor>,
    /** true jika ditemukan lewat alias persis (sudah pernah dikonfirmasi pengguna). */
    val dariAliasPersis: Boolean = false,
    /** Terisi jika keputusan otomatis diturunkan menjadi konfirmasi. */
    val diturunkanKarena: AlasanPenurunan? = null,
) {
    /** Barang yang dipakai otomatis; null jika pengguna yang harus memilih. */
    val barangOtomatis: Long?
        get() = if (keputusan == KeputusanPencocokan.OTOMATIS) kandidatTeratas.firstOrNull()?.barangId else null
}

/**
 * Menentukan keputusan pencocokan untuk satu teks berdasarkan kemiripan Levenshtein.
 *
 * Langkah:
 * 1. Hitung skor setiap kandidat. Satu barang bisa punya beberapa teks pembanding (nama baku
 *    dan alias); yang dipakai adalah skor terbaiknya, supaya satu barang tidak muncul dua kali.
 * 2. Urutkan barang dari yang paling mirip.
 * 3. Terapkan ambang: ≥ 0,90 otomatis, 0,70 sampai < 0,90 konfirmasi, < 0,70 barang baru.
 * 4. Dua aturan tambahan yang hanya bisa menurunkan "otomatis" menjadi "konfirmasi":
 *    - penjaga angka: angka di kedua nama harus sama persis;
 *    - skor kembar: dua barang berbeda tidak boleh sama-sama menjadi yang terbaik.
 *
 * @param teksNormal teks yang dicari, sudah melalui [normalisasiTeks]
 * @param kandidat semua teks pembanding, sudah melalui [normalisasiTeks]
 */
fun cocokkan(teksNormal: String, kandidat: List<Kandidat>): HasilPencocokan {
    val peringkat = kandidat
        .map { KandidatBerskor(it.barangId, it.teks, it.sumber, hitungSkor(teksNormal, it.teks)) }
        .groupBy { it.barangId }
        .values
        .map { teksSatuBarang -> teksSatuBarang.maxWith(URUTAN_DALAM_SATU_BARANG) }
        .sortedWith(URUTAN_PERINGKAT)

    val teratas = peringkat.firstOrNull()
        ?: return HasilPencocokan(teksNormal, KeputusanPencocokan.BARANG_BARU, emptyList())
    val ditampilkan = peringkat.take(JUMLAH_KANDIDAT_DITAMPILKAN)

    if (!teratas.skor.mencapai(AMBANG_OTOMATIS_PERSEN)) {
        val keputusan = if (teratas.skor.mencapai(AMBANG_KONFIRMASI_PERSEN)) {
            KeputusanPencocokan.KONFIRMASI
        } else {
            KeputusanPencocokan.BARANG_BARU
        }
        return HasilPencocokan(teksNormal, keputusan, ditampilkan)
    }

    if (ambilAngka(teksNormal) != ambilAngka(teratas.teks)) {
        return HasilPencocokan(
            teksNormal, KeputusanPencocokan.KONFIRMASI, ditampilkan,
            diturunkanKarena = AlasanPenurunan.ANGKA_BERBEDA,
        )
    }

    val kedua = peringkat.getOrNull(1)
    if (kedua != null && kedua.skor.compareTo(teratas.skor) == 0) {
        return HasilPencocokan(
            teksNormal, KeputusanPencocokan.KONFIRMASI, ditampilkan,
            diturunkanKarena = AlasanPenurunan.SKOR_KEMBAR,
        )
    }

    return HasilPencocokan(teksNormal, KeputusanPencocokan.OTOMATIS, ditampilkan)
}

/** Di dalam satu barang: skor terbaik; jika sama, alias pemasok didahulukan daripada nama baku. */
private val URUTAN_DALAM_SATU_BARANG =
    compareBy<KandidatBerskor> { it.skor }.thenBy { it.sumber == SumberKandidat.ALIAS_PEMASOK }

/** Antarbarang: skor tertinggi di depan; jika sama, id terkecil dulu supaya urutannya selalu sama. */
private val URUTAN_PERINGKAT =
    compareByDescending<KandidatBerskor> { it.skor }.thenBy { it.barangId }
