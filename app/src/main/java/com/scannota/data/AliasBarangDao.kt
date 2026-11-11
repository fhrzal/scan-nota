package com.scannota.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AliasBarangDao {

    /**
     * Menyimpan alias hasil konfirmasi pengguna. Gagal (ABORT) jika pemasok itu sudah punya
     * alias dengan teks yang sama, karena satu tulisan dari satu pemasok hanya boleh
     * menunjuk satu barang.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun tambah(alias: AliasBarang): Long

    /**
     * Langkah pertama pencocokan: cari alias yang persis sama untuk pemasok ini.
     * Jika ketemu, barangnya sudah pasti dan Levenshtein tidak perlu dijalankan.
     */
    @Query("SELECT * FROM alias_barang WHERE pemasok_id = :pemasokId AND teks_alias = :teks LIMIT 1")
    suspend fun cariPersis(pemasokId: Long, teks: String): AliasBarang?

    /** Semua alias milik satu pemasok, dipakai modul matching/ sebagai kandidat Levenshtein. */
    @Query("SELECT * FROM alias_barang WHERE pemasok_id = :pemasokId")
    suspend fun semuaUntukPemasok(pemasokId: Long): List<AliasBarang>
}
