package com.scannota.parsing

import com.scannota.matching.jarakLevenshtein

/**
 * Satuan yang muncul di nota asli Toko Oren. Satuan baru ditambahkan ke sini hanya setelah
 * terlihat di nota asli.
 */
val SATUAN_DIKENAL = listOf("PCS", "RTG", "PAK", "KG")

/**
 * @param teks satuan baku jika dikenali, atau teks OCR (huruf kapital) jika tidak
 * @param dikenali false berarti satuan perlu diperiksa pengguna
 */
data class SatuanTerbaca(val teks: String, val dikenali: Boolean)

/**
 * Menyamakan satuan hasil OCR dengan daftar satuan baku.
 *
 * Alasannya: status harga hanya membandingkan pembelian dengan satuan yang sama, jadi "FCS" yang
 * dibiarkan apa adanya akan dianggap berbeda dari "PCS" dan pembandingnya hilang.
 *
 * Aturannya: teks yang hanya berbeda 1 huruf (jarak Levenshtein ≤ 1) dari tepat satu satuan baku
 * disamakan dengan satuan itu. Ambang kemiripan nama barang (0,90 dan 0,70) tidak dipakai di sini:
 * satuan hanya 2–3 huruf, sehingga satu huruf salah sudah menurunkan kemiripan ke 0,67.
 * Jika ada dua satuan baku yang sama dekatnya, teks dibiarkan dan ditandai, karena tidak bisa
 * dipastikan mana yang benar.
 */
fun samakanSatuan(teksOcr: String): SatuanTerbaca {
    val teks = teksOcr.uppercase().trim { !it.isLetter() }
    if (teks in SATUAN_DIKENAL) return SatuanTerbaca(teks, dikenali = true)
    val calon = SATUAN_DIKENAL.filter { jarakLevenshtein(teks, it) <= 1 }
    return if (calon.size == 1) SatuanTerbaca(calon.single(), dikenali = true) else SatuanTerbaca(teks, dikenali = false)
}
