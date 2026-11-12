package com.scannota.parsing

import com.scannota.ocr.BarisVisual

/**
 * Batas ciri nota: sebuah foto dianggap nota jika punya minimal 2 baris bercirikan nota.
 * Dua, bukan satu, supaya satu kebetulan saja tidak cukup. Angka ini sejalan dengan petunjuk di
 * kamera untuk menyisakan 2–3 barang yang tumpang tindih, sehingga foto bagian terakhir nota pun
 * memuat minimal 2 barang. Foto nota asli dengan ciri paling sedikit punya 7 baris.
 */
const val MIN_BARIS_CIRI_NOTA = 2

/**
 * @param barisAritmetika baris yang memuat tiga angka dengan dua angka pertama dikali sama dengan
 *   angka terakhir (harga × qty = subtotal), dalam format apa pun
 * @param barisNamaHarga baris yang diawali nama (huruf) dan diakhiri angka harga, seperti daftar
 *   belanja tulisan tangan ("Gula 2 kg 30.000")
 */
data class CiriNota(val barisAritmetika: Int, val barisNamaHarga: Int) {
    val tampakNota: Boolean get() = barisAritmetika >= MIN_BARIS_CIRI_NOTA || barisNamaHarga >= MIN_BARIS_CIRI_NOTA
}

/**
 * Menilai apakah baris-baris satu foto tampak seperti nota, supaya foto yang bukan nota (kemasan,
 * kertas lain) tidak menghasilkan isian.
 *
 * Foto ditolak hanya jika tidak tampak seperti nota sama sekali (permintaan pengguna). Nota
 * format lain, termasuk tulisan tangan, tetap diterima jika memuat baris nama dan harga.
 *
 * Hasil pada data (2026-11-12): 11 foto nota Toko Oren 7–37 baris aritmetika; nota toko lain
 * 5 baris aritmetika ("1 X 2000= 2.000,00"); teks kemasan 0.
 */
fun ciriNota(baris: List<BarisVisual>): CiriNota {
    var aritmetika = 0
    var namaHarga = 0
    for (b in baris) {
        val token = pecahToken(b.teks)
        if (adaPerkalian(token.mapNotNull { bacaUang(it) })) aritmetika++
        if (diawaliNamaDiakhiriHarga(token)) namaHarga++
    }
    // Pembaca Toko Oren juga menangkap qty pecahan (0,50), yang bagian bulatnya 0.
    val barisOren = bacaNotaOren(baris).barang.count { it.aritmetikaCocok == true }
    return CiriNota(maxOf(aritmetika, barisOren), namaHarga)
}

/** Ada dua angka sebelum angka terakhir yang hasil kalinya sama dengan angka terakhir (minimal 100). */
private fun adaPerkalian(angka: List<Long>): Boolean {
    if (angka.size < 3) return false
    val hasil = angka.last()
    if (hasil < 100) return false
    val faktor = angka.dropLast(1)
    for (i in faktor.indices) {
        for (j in i + 1 until faktor.size) {
            // Dibandingkan lewat pembagian supaya tidak meluap (overflow) pada angka salah baca yang sangat besar.
            if (faktor[i] > 0 && hasil % faktor[i] == 0L && hasil / faktor[i] == faktor[j]) return true
        }
    }
    return false
}

/**
 * Token terakhir berupa angka harga (100 sampai 10 juta, paling banyak 8 angka) dan token lain
 * memuat minimal 3 huruf. Angka yang bukan harga tidak terhitung: "Netto 75 g" (di bawah 100),
 * nomor BPOM atau barcode (terlalu panjang), dan angka di tengah kalimat ("Isi 100 sachet").
 */
private fun diawaliNamaDiakhiriHarga(token: List<String>): Boolean {
    if (token.size < 2) return false
    val terakhir = uraiAngka(token.last()) ?: return false
    val jumlahAngka = terakhir.bulat.toString().length
    if (terakhir.bulat !in 100..10_000_000 || jumlahAngka > 8) return false
    return token.dropLast(1).sumOf { t -> t.count { it.isLetter() } } >= 3
}
