package com.scannota.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Satu baris barang di sebuah nota.
 *
 * Uang (harga_satuan, subtotal) disimpan sebagai rupiah bilangan bulat (Long), bukan
 * desimal (Double). Bilangan desimal di komputer punya galat pembulatan, padahal
 * verifikasi total membutuhkan perbandingan yang persis sama.
 *
 * Status harga (naik/turun/tetap) sengaja tidak disimpan. Status itu dihitung lewat query
 * setiap kali ditampilkan (lihat [NotaDao.barisNotaDenganHarga]), supaya tetap benar
 * walaupun nota bertanggal lebih awal baru dimasukkan belakangan.
 *
 * Jika nota dihapus, barisnya ikut terhapus (CASCADE). Barang yang sudah punya baris di
 * sini tidak bisa dihapus (RESTRICT), supaya riwayat harganya tidak hilang.
 */
@Entity(
    tableName = "detail_nota",
    foreignKeys = [
        ForeignKey(
            entity = Nota::class,
            parentColumns = ["id"],
            childColumns = ["nota_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Barang::class,
            parentColumns = ["id"],
            childColumns = ["barang_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["nota_id"]),
        Index(value = ["barang_id"]),
    ],
)
data class DetailNota(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Diisi otomatis oleh [NotaDao.simpanNotaLengkap]; boleh 0 saat objek dibuat. */
    @ColumnInfo(name = "nota_id") val notaId: Long = 0,

    @ColumnInfo(name = "barang_id") val barangId: Long,

    /** Urutan baris di nota (mulai dari 1), supaya tampilan sama dengan nota aslinya. */
    @ColumnInfo(name = "urutan") val urutan: Int,

    /**
     * Nama barang persis seperti yang dibaca OCR, sebelum dicocokkan. Disimpan sebagai
     * bukti untuk bab pengujian: membandingkan bacaan OCR dengan barang sebenarnya.
     */
    @ColumnInfo(name = "teks_ocr") val teksOcr: String,

    /** Boleh desimal, untuk barang timbangan (misalnya 2,5 kg). */
    @ColumnInfo(name = "qty") val qty: Double,

    /**
     * Satuan pembelian (DUS, PCS, KG, ...), kosong jika tidak tercetak atau tidak terbaca.
     * Harga hanya dibandingkan dengan pembelian bersatuan sama, karena perbedaan harga
     * per dus dan per pcs bukan kenaikan harga.
     */
    @ColumnInfo(name = "satuan", collate = ColumnInfo.NOCASE) val satuan: String?,

    @ColumnInfo(name = "harga_satuan") val hargaSatuan: Long,
    @ColumnInfo(name = "subtotal") val subtotal: Long,
)
