package com.scannota.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PemasokDao {

    /**
     * Menambah pemasok baru dan mengembalikan id-nya. Gagal (ABORT) jika namanya sudah ada,
     * supaya pemasok ganda tidak pernah tersimpan diam-diam.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun tambah(pemasok: Pemasok): Long

    /** Pencarian tidak membedakan huruf besar/kecil karena kolom nama memakai NOCASE. */
    @Query("SELECT * FROM pemasok WHERE nama = :nama LIMIT 1")
    suspend fun cariBerdasarkanNama(nama: String): Pemasok?

    /** Flow: daftar di layar ikut diperbarui otomatis setiap ada perubahan. */
    @Query("SELECT * FROM pemasok ORDER BY nama")
    fun amatiSemua(): Flow<List<Pemasok>>
}
