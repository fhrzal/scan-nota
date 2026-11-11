package com.scannota.ocr

/*
 * Format teks (TSV, dipisah tab) untuk menyimpan hasil OCR satu foto.
 *
 * Alasan format ini dibuat:
 * - ML Kit hanya berjalan di Android, sedangkan test parsing/ berjalan di laptop. Hasil OCR nota
 *   asli diekspor dari HP ke format ini, lalu dibaca kembali oleh test di laptop.
 * - Bisa dibuka di spreadsheet untuk lampiran bab pengujian.
 * - Ditulis manual tanpa pustaka JSON, karena formatnya sederhana. Pustaka JSON bawaan Android
 *   (org.json) juga tidak berfungsi di unit test laptop.
 *
 * Contoh isi berkas:
 *
 *   #scannota-ocr  1
 *   #ukuran        1080  1920
 *   jenis  blok  baris  x1  y1  x2  y2  x3  y3  x4  y4  keyakinan  teks
 *   BARIS  0     0      10  20  200 20  200 50  10  50  0.93       ABC DEF
 *   KATA   0     0      10  20  90  20  90  50  10  50  0.95       ABC
 *   KATA   0     0      110 20  200 20  200 50  110 50  0.91       DEF
 *
 * Baris KATA selalu milik baris BARIS terakhir di atasnya. Titik sudut yang tidak ada ditulis
 * sebagai sel kosong.
 */

const val VERSI_FORMAT_TSV = 1

private const val PENANDA = "#scannota-ocr"
private const val PENANDA_UKURAN = "#ukuran"
private const val JUDUL_KOLOM = "jenis\tblok\tbaris\tx1\ty1\tx2\ty2\tx3\ty3\tx4\ty4\tkeyakinan\tteks"
private const val JUMLAH_KOLOM = 13
private const val JENIS_BARIS = "BARIS"
private const val JENIS_KATA = "KATA"

/**
 * Menulis hasil OCR ke teks TSV.
 *
 * Angka ditulis dengan toString() bawaan Kotlin, yang selalu memakai titik desimal. Fungsi
 * format seperti String.format mengikuti bahasa HP, sehingga di HP berbahasa Indonesia 0,93
 * ditulis dengan koma, dan berkasnya tidak bisa dibaca ulang di laptop.
 */
fun keTsv(hasil: HasilOcrFoto): String = buildString {
    append(PENANDA).append('\t').append(VERSI_FORMAT_TSV).append('\n')
    append(PENANDA_UKURAN).append('\t').append(hasil.lebar).append('\t').append(hasil.tinggi).append('\n')
    append(JUDUL_KOLOM).append('\n')
    hasil.baris.forEachIndexed { nomor, b ->
        tulisBarisData(JENIS_BARIS, b.indeksBlok, nomor, b.titikSudut, b.keyakinan, b.teks)
        for (k in b.kata) {
            tulisBarisData(JENIS_KATA, b.indeksBlok, nomor, k.titikSudut, k.keyakinan, k.teks)
        }
    }
}

private fun StringBuilder.tulisBarisData(
    jenis: String,
    blok: Int,
    nomorBaris: Int,
    titik: List<Titik>,
    keyakinan: Float,
    teks: String,
) {
    append(jenis).append('\t').append(blok).append('\t').append(nomorBaris)
    if (titik.isEmpty()) {
        repeat(8) { append('\t') }
    } else {
        for (t in titik) append('\t').append(t.x).append('\t').append(t.y)
    }
    append('\t').append(keyakinan.toString())
    append('\t').append(lindungiTeks(teks))
    append('\n')
}

/**
 * Membaca kembali teks TSV buatan [keTsv].
 *
 * Pemeriksaannya ketat: berkas yang rusak atau tersunting salah langsung ditolak dengan nomor
 * barisnya, daripada diam-diam menghasilkan data uji yang keliru.
 *
 * @throws IllegalArgumentException jika format tidak sesuai
 */
fun dariTsv(isi: String): HasilOcrFoto {
    // Akhir baris \r\n diterima, karena berkas yang disimpan ulang di Windows memakainya.
    val semuaBaris = isi.split('\n').map { it.removeSuffix("\r") }
    val barisFile = semuaBaris.withIndex().filter { it.value.isNotEmpty() }
    fun salah(nomorFile: Int, pesan: String): Nothing =
        throw IllegalArgumentException("baris file ke-$nomorFile: $pesan")

    if (barisFile.size < 3) throw IllegalArgumentException("berkas terlalu pendek, bukan hasil ekspor OCR")

    val (iPenanda, penanda) = barisFile[0]
    if (penanda != "$PENANDA\t$VERSI_FORMAT_TSV") salah(iPenanda + 1, "penanda format/versi tidak dikenal")

    val (iUkuran, ukuran) = barisFile[1]
    val selUkuran = ukuran.split('\t')
    if (selUkuran.size != 3 || selUkuran[0] != PENANDA_UKURAN) salah(iUkuran + 1, "baris ukuran tidak sesuai")
    val lebar = selUkuran[1].toIntOrNull() ?: salah(iUkuran + 1, "lebar bukan angka")
    val tinggi = selUkuran[2].toIntOrNull() ?: salah(iUkuran + 1, "tinggi bukan angka")

    val (iJudul, judul) = barisFile[2]
    if (judul != JUDUL_KOLOM) salah(iJudul + 1, "judul kolom tidak sesuai")

    val hasil = mutableListOf<BarisOcr>()
    var barisSekarang: BarisOcr? = null
    var kataSekarang = mutableListOf<KataOcr>()
    fun tutupBaris() {
        barisSekarang?.let { hasil += it.copy(kata = kataSekarang.toList()) }
    }

    for ((indeks, teksBaris) in barisFile.drop(3)) {
        val nomorFile = indeks + 1
        val sel = teksBaris.split('\t')
        if (sel.size != JUMLAH_KOLOM) salah(nomorFile, "jumlah kolom ${sel.size}, seharusnya $JUMLAH_KOLOM")
        val blok = sel[1].toIntOrNull() ?: salah(nomorFile, "blok bukan angka")
        val nomorBaris = sel[2].toIntOrNull() ?: salah(nomorFile, "baris bukan angka")
        val titik = bacaTitik(sel.subList(3, 11)) ?: salah(nomorFile, "titik sudut harus lengkap 8 angka atau kosong semua")
        val keyakinan = sel[11].toFloatOrNull() ?: salah(nomorFile, "keyakinan bukan angka")
        val teks = try {
            pulihkanTeks(sel[12])
        } catch (e: IllegalArgumentException) {
            salah(nomorFile, e.message ?: "teks rusak")
        }

        when (sel[0]) {
            JENIS_BARIS -> {
                if (nomorBaris != hasil.size + (if (barisSekarang == null) 0 else 1)) {
                    salah(nomorFile, "nomor baris tidak berurutan")
                }
                tutupBaris()
                barisSekarang = BarisOcr(teks, keyakinan, titik, blok, emptyList())
                kataSekarang = mutableListOf()
            }
            JENIS_KATA -> {
                val induk = barisSekarang ?: salah(nomorFile, "KATA muncul sebelum BARIS")
                if (nomorBaris != hasil.size || blok != induk.indeksBlok) {
                    salah(nomorFile, "KATA tidak cocok dengan BARIS di atasnya")
                }
                kataSekarang += KataOcr(teks, keyakinan, titik)
            }
            else -> salah(nomorFile, "jenis '${sel[0]}' tidak dikenal")
        }
    }
    tutupBaris()
    return HasilOcrFoto(lebar, tinggi, hasil)
}

/** 8 sel → 4 titik, 8 sel kosong → tanpa titik, campuran → null (rusak). */
private fun bacaTitik(sel: List<String>): List<Titik>? {
    if (sel.all { it.isEmpty() }) return emptyList()
    val angka = sel.map { it.toIntOrNull() ?: return null }
    return angka.chunked(2) { (x, y) -> Titik(x, y) }
}

/**
 * Tab dan ganti baris di dalam teks diubah menjadi \t dan \n, supaya tidak merusak kolom.
 * Garis miring terbalik juga dilindungi (\\), supaya teks aslinya bisa dipulihkan persis.
 */
private fun lindungiTeks(teks: String): String = buildString {
    for (c in teks) {
        when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(c)
        }
    }
}

/** Kebalikan [lindungiTeks]. */
private fun pulihkanTeks(sel: String): String = buildString {
    var i = 0
    while (i < sel.length) {
        val c = sel[i]
        if (c != '\\') {
            append(c)
            i++
            continue
        }
        require(i + 1 < sel.length) { "garis miring terbalik di akhir teks" }
        when (sel[i + 1]) {
            '\\' -> append('\\')
            't' -> append('\t')
            'n' -> append('\n')
            'r' -> append('\r')
            else -> throw IllegalArgumentException("urutan \\${sel[i + 1]} tidak dikenal")
        }
        i += 2
    }
}
