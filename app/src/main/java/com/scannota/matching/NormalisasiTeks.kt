package com.scannota.matching

private val SPASI_BERUNTUN = Regex("\\s+")
private val DERET_ANGKA = Regex("[0-9]+")

/**
 * Merapikan spasi tanpa mengubah huruf: spasi di awal dan akhir dibuang, dan spasi
 * beruntun (termasuk tab dan baris baru) dijadikan satu spasi.
 * Dipakai untuk nama baku barang yang diketik pengguna, supaya huruf besar/kecilnya tetap.
 */
fun rapikanSpasi(teks: String): String = teks.trim().replace(SPASI_BERUNTUN, " ")

/**
 * Menyeragamkan teks sebelum dibandingkan: spasi dirapikan dan semua huruf dijadikan kapital.
 *
 * Normalisasi sengaja dibuat seminimal mungkin. Tanda baca tidak dibuang dan huruf O tidak
 * diganti menjadi angka 0, karena langkah seperti itu bisa mengubah arti (misalnya "1.5L"
 * menjadi "15L"). Kesalahan satu-dua karakter dari OCR sudah ditangani oleh Levenshtein.
 *
 * uppercase() di Kotlin tidak bergantung pada bahasa HP, jadi hasilnya sama di semua perangkat.
 */
fun normalisasiTeks(teks: String): String = rapikanSpasi(teks).uppercase()

/**
 * Mengambil semua deret angka dari teks, berurutan. Contoh: "AB 250 X 12" menjadi ["250", "12"].
 * Dipakai oleh penjaga angka: varian ukuran (250 vs 500) tidak boleh dicocokkan otomatis.
 */
fun ambilAngka(teks: String): List<String> = DERET_ANGKA.findAll(teks).map { it.value }.toList()
