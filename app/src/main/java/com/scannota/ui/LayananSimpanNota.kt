package com.scannota.ui

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.scannota.data.DetailNota
import com.scannota.data.Nota
import com.scannota.data.Pemasok
import com.scannota.data.ScanNotaDatabase
import com.scannota.matching.HasilSimpanAlias
import com.scannota.matching.LayananPencocokan
import com.scannota.matching.rapikanSpasi

/** Hasil menyimpan draf nota. */
sealed interface HasilSimpan {
    /**
     * @param aliasBertentangan jumlah pilihan pengguna yang tidak bisa disimpan sebagai alias,
     *   karena teks itu sudah menjadi alias barang lain untuk pemasok ini
     */
    data class Tersimpan(val notaId: Long, val aliasBertentangan: Int) : HasilSimpan

    /** Nota dengan nomor yang sama dari pemasok yang sama sudah pernah disimpan. */
    data class NotaGanda(val nomorNota: String) : HasilSimpan
}

/**
 * Menyimpan draf nota ke database dalam satu transaksi: jika satu langkah gagal, tidak ada yang
 * tersimpan setengah jalan (misalnya barang baru sudah dibuat tetapi notanya gagal disimpan).
 */
class LayananSimpanNota(private val db: ScanNotaDatabase) {

    suspend fun simpan(draf: DrafNota): HasilSimpan {
        require(draf.masalahSimpan().isEmpty()) { "draf belum boleh disimpan: ${draf.masalahSimpan()}" }
        val nomor = draf.nomorNota.trim().ifEmpty { null }
        return try {
            db.withTransaction { simpanDalamTransaksi(draf, nomor) }
        } catch (e: SQLiteConstraintException) {
            // Pengaman terakhir: indeks unik (pemasok, nomor nota) di database.
            if (nomor != null) HasilSimpan.NotaGanda(nomor) else throw e
        }
    }

    private suspend fun simpanDalamTransaksi(draf: DrafNota, nomor: String?): HasilSimpan {
        val pemasokDao = db.pemasokDao()
        val pemasokId = draf.pemasokId ?: run {
            val nama = rapikanSpasi(draf.namaPemasokBaru)
            pemasokDao.cariBerdasarkanNama(nama)?.id ?: pemasokDao.tambah(Pemasok(nama = nama))
        }
        // Diperiksa sebelum apa pun ditulis, supaya pesannya jelas bagi pengguna.
        if (nomor != null && db.notaDao().cariBerdasarkanNomor(pemasokId, nomor) != null) {
            return HasilSimpan.NotaGanda(nomor)
        }

        val pencocokan = LayananPencocokan(db.aliasBarangDao(), db.barangDao())
        var bertentangan = 0
        val detail = draf.barang.mapIndexed { i, b ->
            val terpilih = b.barangTerpilihId
            val barangId = if (terpilih != null) {
                // Alias hanya disimpan untuk pilihan pengguna, bukan hasil otomatis.
                if (b.dipilihPengguna &&
                    pencocokan.simpanKonfirmasi(pemasokId, b.teksOcr, terpilih) == HasilSimpanAlias.BERTENTANGAN
                ) {
                    bertentangan++
                }
                terpilih
            } else {
                pencocokan.buatBarangBaru(pemasokId, b.teksOcr, requireNotNull(b.namaBaru)).barangId
            }
            DetailNota(
                barangId = barangId,
                urutan = i + 1,
                teksOcr = b.teksOcr,
                qty = requireNotNull(b.qtyPerseratus) / 100.0,
                satuan = b.satuanTerbaca?.teks,
                hargaSatuan = requireNotNull(b.hargaAngka),
                subtotal = requireNotNull(b.subtotalAngka),
            )
        }
        val nota = Nota(
            pemasokId = pemasokId,
            nomorNota = nomor,
            tanggalNota = requireNotNull(draf.tanggalTerbaca).keEpochHari(),
            totalNota = draf.totalAngka,
            dipindaiPada = System.currentTimeMillis(),
        )
        return HasilSimpan.Tersimpan(db.notaDao().simpanNotaLengkap(nota, detail), bertentangan)
    }
}
