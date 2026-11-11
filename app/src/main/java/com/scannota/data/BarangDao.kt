package com.scannota.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BarangDao {

    /** Gagal (ABORT) jika nama barang sudah ada, supaya barang ganda tidak tersimpan. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun tambah(barang: Barang): Long

    @Query("SELECT * FROM barang WHERE id = :id")
    suspend fun cariBerdasarkanId(id: Long): Barang?

    /** Semua nama baku, dipakai modul matching/ sebagai kandidat pencocokan Levenshtein. */
    @Query("SELECT * FROM barang ORDER BY nama")
    suspend fun semua(): List<Barang>

    @Query("SELECT * FROM barang ORDER BY nama")
    fun amatiSemua(): Flow<List<Barang>>

    /**
     * Menghapus barang. Gagal (SQLiteConstraintException) jika barang sudah tercatat di
     * nota mana pun, karena riwayat harganya tidak boleh hilang.
     */
    @Delete
    suspend fun hapus(barang: Barang)
}
