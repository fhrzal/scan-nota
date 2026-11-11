package com.scannota.parsing

import com.scannota.data.apakahNotaLengkap

/**
 * Satu barang hasil membaca nota. Setiap angka bisa null jika tidak terbaca; nilai yang tidak
 * terbaca tidak pernah ditebak.
 *
 * @param nama teks nama barang hasil OCR (spasi dirapikan), belum dicocokkan ke daftar barang
 * @param qtyPerseratus qty × 100 (lihat [bacaQty])
 * @param teksBarisHarga teks baris harga asli, untuk ditampilkan saat pengguna memeriksa
 */
data class BarangTerbaca(
    val nama: String?,
    val harga: Long?,
    val qtyPerseratus: Long?,
    val satuan: SatuanTerbaca?,
    val subtotal: Long?,
    val teksBarisHarga: String?,
) {
    val qty: Double? get() = qtyPerseratus?.let { it / 100.0 }

    /**
     * Pemeriksaan harga × qty = subtotal, dihitung dengan bilangan bulat (qty dalam perseratus).
     * null jika salah satu angka tidak terbaca.
     *
     * Ini pemeriksaan utama untuk menemukan angka salah baca. Dari nota asli terlihat bahwa skor
     * keyakinan ML Kit untuk baris yang salah baca tidak berbeda jelas dari baris yang benar.
     */
    val aritmetikaCocok: Boolean?
        get() {
            val h = harga ?: return null
            val q = qtyPerseratus ?: return null
            val s = subtotal ?: return null
            return h * q == s * 100
        }

    /** true jika baris ini perlu diperiksa pengguna sebelum disimpan. */
    val meragukan: Boolean
        get() = nama == null || aritmetikaCocok != true || satuan?.dikenali != true
}

/** Peringatan tambahan dari kaki nota Toko Oren; tidak mengubah aturan kelengkapan. */
enum class PeringatanKaki {
    /** Jumlah barang terbaca berbeda dari BARIS= yang tercetak. */
    JUMLAH_BARANG_BERBEDA,

    /** Jumlah qty terbaca berbeda dari QTY= yang tercetak, atau ada qty yang tidak terbaca. */
    JUMLAH_QTY_BERBEDA,
}

/**
 * Hasil membaca satu nota (dari satu foto atau gabungan beberapa foto).
 *
 * @param judul baris paling atas, misalnya "TOKO OREN"; hanya saran nama pemasok
 * @param nomorNota teks nomor nota apa adanya; pengguna bisa menyuntingnya
 * @param barisTercetak angka BARIS= di kaki nota, jika ada
 * @param qtyTercetakPerseratus angka QTY= di kaki nota × 100, jika ada
 */
data class NotaTerbaca(
    val judul: String?,
    val nomorNota: String?,
    val tanggal: Tanggal?,
    val barang: List<BarangTerbaca>,
    val total: Long?,
    val barisTercetak: Int?,
    val qtyTercetakPerseratus: Long?,
) {
    /** Subtotal yang tidak terbaca dihitung 0; nota seperti itu sudah pasti tidak lengkap. */
    val jumlahSubtotal: Long get() = barang.sumOf { it.subtotal ?: 0L }

    /**
     * Aturan kelengkapan yang sama dengan database (apakahNotaLengkap): total terbaca dan sama
     * dengan jumlah subtotal. Ditambah syarat semua subtotal terbaca, karena nota yang subtotalnya
     * hilang tidak bisa diverifikasi.
     */
    val lengkap: Boolean
        get() = barang.all { it.subtotal != null } && apakahNotaLengkap(total, jumlahSubtotal)

    /** Pemeriksaan BARIS= dan QTY=, sebagai peringatan tambahan saja. */
    fun peringatanKaki(): List<PeringatanKaki> = buildList {
        if (barisTercetak != null && barisTercetak != barang.size) add(PeringatanKaki.JUMLAH_BARANG_BERBEDA)
        val qtyTercetak = qtyTercetakPerseratus
        if (qtyTercetak != null) {
            val semuaQty = barang.map { it.qtyPerseratus }
            if (semuaQty.any { it == null } || semuaQty.sumOf { it ?: 0L } != qtyTercetak) {
                add(PeringatanKaki.JUMLAH_QTY_BERBEDA)
            }
        }
    }
}
