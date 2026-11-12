package com.scannota.ui

import com.scannota.parsing.Tanggal
import kotlin.math.abs

/*
 * Format angka untuk ditampilkan. Ditulis manual, tidak memakai NumberFormat, karena hasil
 * NumberFormat mengikuti bahasa HP. Format nota Toko Oren tetap: titik pemisah ribuan, koma desimal.
 */

/** 17500 → "17.500" */
fun formatRupiah(nilai: Long): String {
    val kelompok = abs(nilai).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (nilai < 0) "-$kelompok" else kelompok
}

/** Qty perseratus: 100 → "1", 50 → "0,5", 125 → "1,25" */
fun formatQty(perseratus: Long): String {
    val bulat = perseratus / 100
    val sisa = perseratus % 100
    if (sisa == 0L) return bulat.toString()
    return "$bulat," + sisa.toString().padStart(2, '0').trimEnd('0')
}

/** Kolom tanggal_nota di database (jumlah hari sejak 1-1-1970) → "dd-mm-yyyy" */
fun formatTanggal(epochHari: Long): String = Tanggal.dariEpochHari(epochHari).toString()
