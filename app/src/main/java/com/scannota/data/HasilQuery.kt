package com.scannota.data

import androidx.room.ColumnInfo

/*
 * Bentuk data hasil query gabungan (JOIN). Kelas-kelas ini bukan tabel; isinya dihitung
 * dari tabel lain setiap kali dibaca.
 */

/** Satu nota untuk daftar nota, beserta jumlah subtotal yang dihitung dari detailnya. */
data class RingkasanNota(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "nama_pemasok") val namaPemasok: String,
    @ColumnInfo(name = "nomor_nota") val nomorNota: String?,
    @ColumnInfo(name = "tanggal_nota") val tanggalNota: Long,
    @ColumnInfo(name = "total_nota") val totalNota: Long?,
    @ColumnInfo(name = "jumlah_subtotal") val jumlahSubtotal: Long,
    @ColumnInfo(name = "jumlah_baris") val jumlahBaris: Int,
) {
    /** Hasil verifikasi total; false berarti nota ditandai "belum lengkap". */
    fun lengkap(): Boolean = apakahNotaLengkap(totalNota, jumlahSubtotal)
}

/** Satu baris nota beserta harga pembelian sebelumnya sebagai pembanding. */
data class BarisNotaDenganHarga(
    @ColumnInfo(name = "detail_id") val detailId: Long,
    @ColumnInfo(name = "urutan") val urutan: Int,
    @ColumnInfo(name = "barang_id") val barangId: Long,
    @ColumnInfo(name = "nama_barang") val namaBarang: String,
    @ColumnInfo(name = "teks_ocr") val teksOcr: String,
    @ColumnInfo(name = "qty") val qty: Double,
    @ColumnInfo(name = "satuan") val satuan: String?,
    @ColumnInfo(name = "harga_satuan") val hargaSatuan: Long,
    @ColumnInfo(name = "subtotal") val subtotal: Long,

    /** Null jika belum pernah dibeli dari pemasok ini dengan satuan yang sama. */
    @ColumnInfo(name = "harga_sebelumnya") val hargaSebelumnya: Long?,
) {
    fun status(): StatusHarga = tentukanStatusHarga(hargaSebelumnya, hargaSatuan)
}

/** Satu pembelian sebuah barang, untuk menampilkan riwayat harganya. */
data class RiwayatHarga(
    @ColumnInfo(name = "nota_id") val notaId: Long,
    @ColumnInfo(name = "tanggal_nota") val tanggalNota: Long,
    @ColumnInfo(name = "nama_pemasok") val namaPemasok: String,
    @ColumnInfo(name = "qty") val qty: Double,
    @ColumnInfo(name = "satuan") val satuan: String?,
    @ColumnInfo(name = "harga_satuan") val hargaSatuan: Long,
)

/**
 * Satu pembelian dari seorang pemasok, untuk mencari pembanding harga nota yang belum disimpan.
 * Lihat [NotaDao.pembelianDariPemasok] dan [ringkasPembanding].
 */
data class PembelianSebelumnya(
    @ColumnInfo(name = "barang_id") val barangId: Long,
    @ColumnInfo(name = "satuan") val satuan: String?,
    @ColumnInfo(name = "harga_satuan") val hargaSatuan: Long,
)
