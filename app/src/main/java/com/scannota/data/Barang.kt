package com.scannota.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Barang dengan nama baku yang dipakai toko.
 *
 * Setiap pemasok bisa menulis nama berbeda untuk barang yang sama. Semua tulisan itu
 * diarahkan ke satu baris di tabel ini lewat [AliasBarang], sehingga riwayat harga
 * sebuah barang tidak terpecah ke beberapa nama.
 *
 * Nama unik tanpa membedakan huruf besar/kecil (NOCASE) untuk mencegah barang ganda.
 */
@Entity(
    tableName = "barang",
    indices = [Index(value = ["nama"], unique = true)],
)
data class Barang(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nama", collate = ColumnInfo.NOCASE) val nama: String,
)
