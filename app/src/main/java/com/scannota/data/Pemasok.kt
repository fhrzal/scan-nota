package com.scannota.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pemasok (distributor/sales) yang mengirim nota ke toko.
 *
 * Dibuat sebagai tabel tersendiri, bukan teks di setiap nota, karena alias barang berlaku
 * per pemasok. Kalau nama pemasok diketik ulang di setiap nota, satu salah ketik saja
 * membuat pemasok yang sama dianggap dua, dan aliasnya terpecah.
 *
 * Nama unik tanpa membedakan huruf besar/kecil (NOCASE), supaya "Maju" dan "MAJU"
 * tidak tersimpan sebagai dua pemasok.
 */
@Entity(
    tableName = "pemasok",
    indices = [Index(value = ["nama"], unique = true)],
)
data class Pemasok(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "nama", collate = ColumnInfo.NOCASE) val nama: String,
)
