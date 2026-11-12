package com.scannota.parsing

/*
 * Mengisi dan mengoreksi angka barang dari hubungan harga × qty = subtotal.
 * Disetujui pengguna (2026-11-12) untuk mempercepat pemeriksaan, menggantikan aturan lama
 * "angka tidak pernah dikoreksi otomatis".
 *
 * Pada 5 nota asli, angka (harga, qty, subtotal) yang benar naik dari 111 menjadi 116 dari 117,
 * tanpa satu pun usulan yang salah.
 */

/** Cara sebuah angka didapat. Ditampilkan ke pengguna supaya jelas angka mana yang dihitung aplikasi. */
enum class CaraHitung(val keterangan: String) {
    SUBTOTAL_DIHITUNG("subtotal = harga × qty"),
    HARGA_DIHITUNG("harga = subtotal ÷ qty"),
    QTY_DIHITUNG("qty = subtotal ÷ harga"),
    HARGA_DIKOREKSI("harga dikoreksi = subtotal ÷ qty"),
    QTY_DIKOREKSI("qty dikoreksi = subtotal ÷ harga"),
}

data class AngkaLengkap(val harga: Long, val qtyPerseratus: Long, val subtotal: Long, val cara: CaraHitung)

/**
 * Melengkapi atau mengoreksi angka satu barang.
 *
 * 1. Tepat satu angka kosong: dihitung dari dua angka lainnya, hanya jika pembagiannya pas (tanpa
 *    sisa). Pembagian yang pas adalah tanda bahwa dua angka yang terbaca memang benar; angka salah
 *    baca jarang menghasilkan pembagian yang pas.
 * 2. Ketiga angka terbaca tetapi tidak cocok: dikoreksi hanya jika [subtotalTerverifikasi], artinya
 *    jumlah subtotal nota sama dengan total. Karena subtotal sudah terbukti benar, kesalahan ada di
 *    harga atau qty. Harga atau qty dihitung ulang hanya jika tepat satu dari keduanya menghasilkan
 *    pembagian yang pas. Jika keduanya pas atau tidak ada yang pas, angka tidak diubah dan barang
 *    tetap diperiksa pengguna.
 *
 * Contoh dari nota asli 005493 (lengkap): 8.930 × 4 ≠ 35.800. Harga = 35.800 ÷ 4 = 8.950 (pas),
 * qty = 35.800 ÷ 8.930 (tidak pas), jadi harga dikoreksi menjadi 8.950, sesuai nota kertas.
 *
 * @return angka baru beserta caranya, atau null jika tidak ada yang bisa dihitung dengan yakin
 */
fun lengkapiAngka(harga: Long?, qtyPerseratus: Long?, subtotal: Long?, subtotalTerverifikasi: Boolean): AngkaLengkap? {
    val kosong = listOf(harga, qtyPerseratus, subtotal).count { it == null }
    if (kosong == 1) {
        return when {
            subtotal == null -> {
                val kali = harga!! * qtyPerseratus!!
                if (kali % 100 == 0L && kali > 0) AngkaLengkap(harga, qtyPerseratus, kali / 100, CaraHitung.SUBTOTAL_DIHITUNG) else null
            }
            harga == null -> bagiPas(subtotal * 100, qtyPerseratus!!)?.let {
                AngkaLengkap(it, qtyPerseratus, subtotal, CaraHitung.HARGA_DIHITUNG)
            }
            else -> bagiPas(subtotal * 100, harga)?.let {
                AngkaLengkap(harga, it, subtotal, CaraHitung.QTY_DIHITUNG)
            }
        }
    }
    if (kosong == 0 && subtotalTerverifikasi && harga!! * qtyPerseratus!! != subtotal!! * 100) {
        val calonHarga = bagiPas(subtotal * 100, qtyPerseratus)
        val calonQty = bagiPas(subtotal * 100, harga)
        return when {
            calonHarga != null && calonQty == null -> AngkaLengkap(calonHarga, qtyPerseratus, subtotal, CaraHitung.HARGA_DIKOREKSI)
            calonQty != null && calonHarga == null -> AngkaLengkap(harga, calonQty, subtotal, CaraHitung.QTY_DIKOREKSI)
            else -> null
        }
    }
    return null
}

/** Hasil bagi jika pas tanpa sisa dan positif, selain itu null. */
private fun bagiPas(pembilang: Long, penyebut: Long): Long? =
    if (penyebut > 0 && pembilang > 0 && pembilang % penyebut == 0L) pembilang / penyebut else null
