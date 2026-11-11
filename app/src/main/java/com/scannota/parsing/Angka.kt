package com.scannota.parsing

/*
 * Membaca angka dari teks OCR nota.
 *
 * Format nota pemasok: titik pemisah ribuan, koma desimal, dua angka desimal (17.500,00; qty 1,00).
 * Dari hasil OCR nota asli, galat yang muncul antara lain:
 * - titik dan koma tertukar: 17.000.00, 30,000,00, qty 5.00
 * - spasi di dalam angka: 8.500, 00 (ditangani oleh pecahToken)
 * - huruf menggantikan angka: i0.050,00, 24.130,0D, b.500,00, l6-07-2026
 * - angka terpotong di tepi foto: 19.350,
 */

/**
 * Huruf yang bentuknya mirip angka. Penggantian hanya dilakukan di dalam token yang memang angka
 * (punya minimal satu angka asli). Untuk nama barang, huruf tidak pernah diganti (lihat
 * matching/), karena di nama "O" bisa memang huruf O.
 *
 * Isinya dari galat yang terlihat di nota asli (i, l, D, b) ditambah pasangan klasik galat OCR
 * yang dipakai sebagai alasan ambang Levenshtein (O/0, I/1, S/5, B/8).
 */
private val HURUF_MIRIP_ANGKA = mapOf(
    'O' to '0', 'o' to '0', 'D' to '0',
    'I' to '1', 'i' to '1', 'l' to '1',
    'S' to '5',
    'b' to '6',
    'B' to '8',
)

/**
 * Bagian bulat: kelompok ribuan (1–3 angka lalu kelompok 3 angka dipisah titik/koma), atau angka
 * tanpa pemisah. Bagian desimal: titik/koma terakhir diikuti 0–2 angka.
 * Contoh: "17.500,00", "17.000.00", "30,000,00", "1,00", "5.00", "2000", "19.350,".
 */
private val POLA_ANGKA = Regex("""^(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{0,2}))?$""")

/** Coretan atau noda yang kadang menempel di ujung angka, misalnya "15.000,00/". */
private const val TEPI_SAMPAH = "/'\"`~"

internal class AngkaTerurai(val bulat: Long, val desimal: String, val adaPemisahRibuan: Boolean)

/** Mengurai satu token menjadi angka, atau null jika token itu bukan angka. */
internal fun uraiAngka(token: String): AngkaTerurai? {
    val bersih = token.trim { it in TEPI_SAMPAH }
    if (bersih.none { it in '0'..'9' }) return null
    val diganti = buildString { for (c in bersih) append(HURUF_MIRIP_ANGKA[c] ?: c) }
    val cocok = POLA_ANGKA.matchEntire(diganti) ?: return null
    val bagianBulat = cocok.groupValues[1]
    val bulat = bagianBulat.filter { it in '0'..'9' }.toLongOrNull() ?: return null
    return AngkaTerurai(
        bulat = bulat,
        desimal = cocok.groupValues[2],
        adaPemisahRibuan = bagianBulat.any { it == '.' || it == ',' },
    )
}

/**
 * Membaca uang dalam rupiah bulat.
 *
 * Dua angka desimal tidak dipakai: rupiah tidak memakai sen, jadi di nota desimalnya selalu ",00"
 * dan hanya bagian dari format. Desimal yang terbaca lain (misalnya "526.200, 09") dianggap galat
 * OCR dan diabaikan, supaya total yang bagian bulatnya benar tidak ikut dianggap tak terbaca.
 */
fun bacaUang(token: String): Long? = uraiAngka(token)?.bulat

/**
 * Membaca qty dalam perseratus (1,00 → 100; 0,50 → 50).
 *
 * Disimpan sebagai bilangan bulat supaya pemeriksaan harga × qty = subtotal tepat tanpa galat
 * pembulatan desimal, sama alasannya dengan ambang Levenshtein di matching/.
 */
fun bacaQty(token: String): Long? {
    val angka = uraiAngka(token) ?: return null
    val perseratus = angka.desimal.padEnd(2, '0').toLong()
    return angka.bulat * 100 + perseratus
}

/** Tanda kali yang terlihat di nota asli: x, X, dan * (bintang). */
internal val TANDA_KALI = setOf("x", "X", "*", "×")

/**
 * Memecah teks baris menjadi token, sambil memperbaiki dua galat OCR yang mengubah batas token:
 * - "8.500, 00": spasi setelah koma desimal, jadi dua token disatukan kembali
 * - "1.700,00x": tanda kali menempel pada angka, jadi dipisah
 */
fun pecahToken(teks: String): List<String> {
    val hasil = mutableListOf<String>()
    for (t in teks.trim().split(Regex("\\s+"))) {
        if (t.isEmpty()) continue
        val sebelum = hasil.lastOrNull()
        if (sebelum != null && (sebelum.endsWith(',') || sebelum.endsWith('.')) &&
            t.length <= 2 && uraiAngka(t) != null && uraiAngka(sebelum) != null
        ) {
            hasil[hasil.lastIndex] = sebelum + t
            continue
        }
        if (t.length > 1 && t.takeLast(1) in TANDA_KALI && uraiAngka(t.dropLast(1)) != null) {
            hasil += t.dropLast(1)
            hasil += t.takeLast(1)
            continue
        }
        if (t.length > 1 && t.take(1) in TANDA_KALI && uraiAngka(t.drop(1)) != null) {
            hasil += t.take(1)
            hasil += t.drop(1)
            continue
        }
        hasil += t
    }
    return hasil
}

/** Mengganti huruf mirip angka di seluruh teks; hanya untuk mencari pola angka seperti tanggal. */
internal fun gantiHurufMiripAngka(teks: String): String =
    buildString { for (c in teks) append(HURUF_MIRIP_ANGKA[c] ?: c) }
