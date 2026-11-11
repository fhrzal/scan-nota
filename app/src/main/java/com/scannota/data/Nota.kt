package com.scannota.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Satu nota pembelian dari satu pemasok. Satu nota bisa berasal dari beberapa foto,
 * tetapi penggabungannya terjadi di level teks (modul merge/), jadi di sini tetap satu baris.
 *
 * Status "belum lengkap" sengaja tidak disimpan. Status itu dihitung dari jumlah subtotal
 * [DetailNota] setiap kali ditampilkan (lihat [RingkasanNota]), supaya tidak bisa
 * berbeda dari isi detailnya.
 *
 * Pemasok yang masih punya nota tidak bisa dihapus (RESTRICT), supaya riwayat pembelian
 * tidak hilang.
 *
 * Pasangan (pemasok, nomor_nota) unik untuk mencegah nota yang sama terpindai dua kali.
 * Nota ganda akan membuat riwayat harga tercatat dobel dan statusnya salah menjadi "tetap".
 * Aturan ini dijaga oleh database, bukan hanya aplikasi, sehingga tidak bisa terlewat.
 * Nota tanpa nomor (null) tetap boleh disimpan, karena SQLite menganggap setiap null berbeda.
 */
@Entity(
    tableName = "nota",
    foreignKeys = [
        ForeignKey(
            entity = Pemasok::class,
            parentColumns = ["id"],
            childColumns = ["pemasok_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        // Mempercepat pencarian "nota sebelumnya dari pemasok yang sama".
        Index(value = ["pemasok_id", "tanggal_nota"]),
        // Menolak nota ganda dari pemasok yang sama.
        Index(value = ["pemasok_id", "nomor_nota"], unique = true),
    ],
)
data class Nota(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pemasok_id") val pemasokId: Long,

    /**
     * Nomor yang tercetak di nota; null jika tidak tercetak atau tidak terbaca.
     * Tidak membedakan huruf besar/kecil (NOCASE), karena OCR bisa membaca huruf yang sama
     * secara berbeda.
     */
    @ColumnInfo(name = "nomor_nota", collate = ColumnInfo.NOCASE) val nomorNota: String?,

    /**
     * Tanggal yang tercetak di nota, disimpan sebagai jumlah hari sejak 1 Januari 1970
     * (epoch day). Isinya hanya tanggal, tanpa jam dan zona waktu, sehingga urutan
     * "nota sebelumnya" tidak bisa bergeser karena perbedaan jam.
     */
    @ColumnInfo(name = "tanggal_nota") val tanggalNota: Long,

    /**
     * Total yang tercetak di nota, dalam rupiah bilangan bulat. Kosong jika tidak terbaca;
     * nota seperti itu otomatis dianggap belum lengkap karena tidak bisa diverifikasi.
     */
    @ColumnInfo(name = "total_nota") val totalNota: Long?,

    /** Waktu nota dipindai (milidetik sejak 1970). Hanya untuk catatan, bukan untuk urutan harga. */
    @ColumnInfo(name = "dipindai_pada") val dipindaiPada: Long,
)
