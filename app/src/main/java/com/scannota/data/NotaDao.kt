package com.scannota.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Akses tabel nota dan detail_nota. Keduanya ada di satu DAO karena selalu disimpan
 * bersamaan dalam satu transaksi.
 */
@Dao
abstract class NotaDao {

    /** Jangan dipanggil langsung; pakai [simpanNotaLengkap]. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun tambahNota(nota: Nota): Long

    /** Jangan dipanggil langsung; pakai [simpanNotaLengkap]. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun tambahDetail(detail: List<DetailNota>)

    /**
     * Menyimpan nota beserta semua barisnya dalam satu transaksi. Jika satu baris gagal
     * disimpan, seluruh nota dibatalkan, sehingga tidak pernah ada nota yang tersimpan
     * setengah jadi. nota_id di setiap baris diisi otomatis di sini.
     *
     * Nomor nota yang kosong atau hanya berisi spasi disimpan sebagai null. Tanpa langkah
     * ini, dua nota bernomor "" dari pemasok yang sama akan dianggap ganda dan ditolak.
     *
     * Gagal (SQLiteConstraintException) jika pemasok ini sudah punya nota dengan nomor yang
     * sama. Cek lebih dulu dengan [cariBerdasarkanNomor] agar bisa ditampilkan pesan yang jelas.
     *
     * @return id nota yang baru disimpan
     */
    @Transaction
    open suspend fun simpanNotaLengkap(nota: Nota, detail: List<DetailNota>): Long {
        val nomorBersih = nota.nomorNota?.trim()?.ifEmpty { null }
        val notaId = tambahNota(nota.copy(nomorNota = nomorBersih))
        tambahDetail(detail.map { it.copy(notaId = notaId) })
        return notaId
    }

    /** Mencari nota dengan nomor yang sama dari pemasok yang sama, untuk mendeteksi nota ganda. */
    @Query("SELECT * FROM nota WHERE pemasok_id = :pemasokId AND nomor_nota = :nomorNota LIMIT 1")
    abstract suspend fun cariBerdasarkanNomor(pemasokId: Long, nomorNota: String): Nota?

    /** Menghapus nota; semua barisnya ikut terhapus karena foreign key CASCADE. */
    @Query("DELETE FROM nota WHERE id = :notaId")
    abstract suspend fun hapusNota(notaId: Long)

    /**
     * Daftar semua nota, terbaru di atas, beserta jumlah subtotalnya untuk verifikasi total.
     * Jumlah dihitung langsung dari detail_nota (SUM), bukan disimpan, supaya status
     * "belum lengkap" selalu sesuai dengan isi detailnya.
     */
    @Query(
        """
        SELECT n.id, p.nama AS nama_pemasok, n.nomor_nota, n.tanggal_nota, n.total_nota,
               COALESCE(SUM(d.subtotal), 0) AS jumlah_subtotal,
               COUNT(d.id) AS jumlah_baris
        FROM nota n
        JOIN pemasok p ON p.id = n.pemasok_id
        LEFT JOIN detail_nota d ON d.nota_id = n.id
        GROUP BY n.id
        ORDER BY n.tanggal_nota DESC, n.id DESC
        """
    )
    abstract fun amatiRingkasanNota(): Flow<List<RingkasanNota>>

    /**
     * Baris-baris sebuah nota beserta harga pembelian sebelumnya, untuk menentukan status
     * naik/turun/tetap.
     *
     * Harga sebelumnya adalah harga barang yang sama pada nota yang lebih awal dengan
     * syarat:
     * - dari pemasok yang sama (kesepakatan: yang diukur adalah perubahan harga dari pemasok itu);
     * - dengan satuan yang sama (`IS` dipakai agar dua satuan kosong juga dianggap sama);
     * - "lebih awal" diurutkan menurut tanggal nota. Jika tanggalnya sama, nota yang
     *   disimpan lebih dulu (id lebih kecil) dianggap lebih awal.
     * Jika pada nota sebelumnya barang itu muncul di lebih dari satu baris, yang dipakai
     * adalah baris terakhir, supaya hasilnya selalu sama setiap kali dihitung.
     */
    @Query(
        """
        SELECT d.id AS detail_id, d.urutan, b.id AS barang_id, b.nama AS nama_barang,
               d.teks_ocr, d.qty, d.satuan, d.harga_satuan, d.subtotal,
               (SELECT d2.harga_satuan
                  FROM detail_nota d2
                  JOIN nota n2 ON n2.id = d2.nota_id
                 WHERE d2.barang_id = d.barang_id
                   AND n2.pemasok_id = n.pemasok_id
                   AND d2.satuan IS d.satuan
                   AND (n2.tanggal_nota < n.tanggal_nota
                        OR (n2.tanggal_nota = n.tanggal_nota AND n2.id < n.id))
                 ORDER BY n2.tanggal_nota DESC, n2.id DESC, d2.urutan DESC
                 LIMIT 1) AS harga_sebelumnya
        FROM detail_nota d
        JOIN nota n ON n.id = d.nota_id
        JOIN barang b ON b.id = d.barang_id
        WHERE d.nota_id = :notaId
        ORDER BY d.urutan
        """
    )
    abstract fun barisNotaDenganHarga(notaId: Long): Flow<List<BarisNotaDenganHarga>>

    /** Semua pembelian satu barang dari semua pemasok, terbaru di atas. */
    @Query(
        """
        SELECT n.id AS nota_id, n.tanggal_nota, p.nama AS nama_pemasok,
               d.qty, d.satuan, d.harga_satuan
        FROM detail_nota d
        JOIN nota n ON n.id = d.nota_id
        JOIN pemasok p ON p.id = n.pemasok_id
        WHERE d.barang_id = :barangId
        ORDER BY n.tanggal_nota DESC, n.id DESC, d.urutan DESC
        """
    )
    abstract fun riwayatHargaBarang(barangId: Long): Flow<List<RiwayatHarga>>

    /**
     * Semua pembelian dari satu pemasok sampai tanggal tertentu, terbaru lebih dulu. Dipakai untuk
     * menampilkan status harga di layar periksa sebelum nota disimpan (disetujui pengguna
     * 2026-11-12), dan untuk mengisi satuan yang tidak terbaca dari pembelian terakhir.
     *
     * Urutan "terbaru" sama dengan query [barisNotaDenganHarga]: tanggal nota, lalu id nota, lalu
     * urutan baris. Nota dengan tanggal yang sama ikut dihitung sebagai pembelian sebelumnya, karena
     * nota yang sedang diperiksa akan mendapat id lebih besar saat disimpan.
     */
    @Query(
        """
        SELECT d.barang_id, d.satuan, d.harga_satuan
        FROM detail_nota d
        JOIN nota n ON n.id = d.nota_id
        WHERE n.pemasok_id = :pemasokId AND n.tanggal_nota <= :sampaiTanggal
        ORDER BY n.tanggal_nota DESC, n.id DESC, d.urutan DESC
        """
    )
    abstract suspend fun pembelianDariPemasok(pemasokId: Long, sampaiTanggal: Long): List<PembelianSebelumnya>
}
