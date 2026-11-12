package com.scannota.data

/**
 * Status harga beli sebuah barang dibanding pembelian sebelumnya dari pemasok yang sama
 * dengan satuan yang sama.
 *
 * Teks yang tampil di layar diatur oleh modul ui/, bukan di sini, supaya lapisan data
 * tidak bergantung pada tampilan.
 */
enum class StatusHarga {
    NAIK,
    TURUN,
    TETAP,

    /** Pembelian pertama barang ini (dari pemasok ini, dengan satuan ini): belum bisa dibandingkan. */
    BELUM_ADA_PEMBANDING,
}

/**
 * Menentukan status harga dari harga sebelumnya dan harga sekarang.
 *
 * Dibuat sebagai fungsi murni (tanpa database) supaya aturannya bisa diuji dengan unit test
 * dan mudah dijelaskan. Perbandingannya persis: selisih satu rupiah pun dihitung naik atau
 * turun, karena harga disimpan sebagai bilangan bulat tanpa galat pembulatan.
 *
 * @param hargaSebelumnya harga satuan pada pembelian sebelumnya, atau null jika belum pernah dibeli
 * @param hargaSekarang harga satuan pada nota yang sedang dilihat
 */
fun tentukanStatusHarga(hargaSebelumnya: Long?, hargaSekarang: Long): StatusHarga = when {
    hargaSebelumnya == null -> StatusHarga.BELUM_ADA_PEMBANDING
    hargaSekarang > hargaSebelumnya -> StatusHarga.NAIK
    hargaSekarang < hargaSebelumnya -> StatusHarga.TURUN
    else -> StatusHarga.TETAP
}

/**
 * Verifikasi total: nota dianggap lengkap hanya jika jumlah semua subtotal sama persis
 * dengan total yang tercetak di nota.
 *
 * Jika total tidak terbaca (null), nota dianggap belum lengkap, karena tidak ada angka
 * pembanding untuk memastikan semua baris sudah terbaca.
 */
fun apakahNotaLengkap(totalNota: Long?, jumlahSubtotal: Long): Boolean =
    totalNota != null && totalNota == jumlahSubtotal

/**
 * Pembanding satu barang dari satu pemasok.
 *
 * @param satuanTerakhir satuan pada pembelian terakhir, untuk mengisi satuan yang tidak terbaca
 * @param hargaPerSatuan harga terakhir untuk setiap satuan (kunci huruf kapital, atau null jika
 *   satuannya kosong), karena status harga hanya membandingkan satuan yang sama
 */
data class Pembanding(val satuanTerakhir: String?, val hargaPerSatuan: Map<String?, Long>) {
    fun hargaSebelumnya(satuan: String?): Long? = hargaPerSatuan[satuan?.uppercase()]
}

/**
 * Meringkas daftar pembelian (terbaru lebih dulu) menjadi pembanding per barang: yang diambil
 * hanya pembelian pertama untuk setiap barang dan untuk setiap pasangan (barang, satuan).
 * Fungsi murni, supaya aturan "pembelian terakhir dengan satuan yang sama" bisa diuji di laptop.
 */
fun ringkasPembanding(pembelianTerbaruDulu: List<PembelianSebelumnya>): Map<Long, Pembanding> {
    val satuanTerakhir = mutableMapOf<Long, String?>()
    val harga = mutableMapOf<Long, MutableMap<String?, Long>>()
    for (p in pembelianTerbaruDulu) {
        if (p.barangId !in satuanTerakhir) satuanTerakhir[p.barangId] = p.satuan
        harga.getOrPut(p.barangId) { mutableMapOf() }.putIfAbsent(p.satuan?.uppercase(), p.hargaSatuan)
    }
    return satuanTerakhir.mapValues { (id, satuan) -> Pembanding(satuan, harga.getValue(id)) }
}
