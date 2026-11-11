package com.scannota.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Basis data SQLite lokal aplikasi (offline, tersimpan di HP).
 *
 * exportSchema = true: struktur tabel diekspor ke app/schemas/ dalam bentuk JSON setiap
 * kali build. Berkas itu bisa dijadikan dasar ERD di laporan dan menjadi catatan
 * perubahan skema.
 */
@Database(
    entities = [Pemasok::class, Barang::class, AliasBarang::class, Nota::class, DetailNota::class],
    version = 1,
    exportSchema = true,
)
abstract class ScanNotaDatabase : RoomDatabase() {

    abstract fun pemasokDao(): PemasokDao
    abstract fun barangDao(): BarangDao
    abstract fun aliasBarangDao(): AliasBarangDao
    abstract fun notaDao(): NotaDao

    companion object {
        private const val NAMA_BERKAS = "scannota.db"

        @Volatile
        private var instance: ScanNotaDatabase? = null

        /**
         * Mengambil satu-satunya objek database (singleton). Membuka database itu mahal,
         * dan dua objek untuk berkas yang sama bisa saling bertabrakan saat menulis.
         * `synchronized` mencegah dua thread membuatnya bersamaan.
         */
        fun dapatkan(context: Context): ScanNotaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScanNotaDatabase::class.java,
                    NAMA_BERKAS,
                ).build().also { instance = it }
            }
    }
}
