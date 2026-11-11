package com.scannota.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tulisan nama barang sebagaimana tercetak di nota seorang pemasok, beserta barang baku
 * yang dimaksud.
 *
 * Pasangan (pemasok, teks_alias) dibuat unik: teks yang sama dari pemasok yang sama pasti
 * berarti barang yang sama. Karena itu pencocokan dimulai dengan mencari alias yang persis
 * sama, dan Levenshtein baru dipakai kalau tidak ketemu. Setiap konfirmasi pengguna disimpan
 * di sini, sehingga pertanyaan yang sama tidak muncul lagi untuk pemasok itu.
 *
 * Normalisasi teks (huruf besar, spasi dirapikan) dikerjakan di modul matching/;
 * tabel ini hanya menyimpan hasilnya.
 *
 * Jika barang atau pemasoknya dihapus, aliasnya ikut terhapus (CASCADE), karena alias
 * tanpa barang atau tanpa pemasok tidak ada artinya.
 */
@Entity(
    tableName = "alias_barang",
    foreignKeys = [
        ForeignKey(
            entity = Barang::class,
            parentColumns = ["id"],
            childColumns = ["barang_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Pemasok::class,
            parentColumns = ["id"],
            childColumns = ["pemasok_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pemasok_id", "teks_alias"], unique = true),
        Index(value = ["barang_id"]),
    ],
)
data class AliasBarang(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "barang_id") val barangId: Long,
    @ColumnInfo(name = "pemasok_id") val pemasokId: Long,
    @ColumnInfo(name = "teks_alias", collate = ColumnInfo.NOCASE) val teksAlias: String,
)
