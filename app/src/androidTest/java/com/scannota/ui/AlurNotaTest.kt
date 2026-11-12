package com.scannota.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scannota.data.ScanNotaDatabase
import com.scannota.data.StatusHarga
import com.scannota.data.ringkasPembanding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Menguji alur aplikasi di HP, dari foto sampai database, tanpa layar: pipeline yang sama dengan
 * aplikasi (grayscale → OCR → gabung → baca nota), lalu penyimpanan ke database sementara di
 * memori, penolakan nota ganda, dan status harga dari query.
 *
 * Foto nota asli diambil dari data_uji/foto/ (ikut dikemas ke APK test). Jika foto tidak ada,
 * test dilewati. Database aplikasi yang sebenarnya tidak disentuh.
 */
@RunWith(AndroidJUnit4::class)
class AlurNotaTest {

    private val instrumentasi = InstrumentationRegistry.getInstrumentation()
    private val db = Room.inMemoryDatabaseBuilder(instrumentasi.targetContext, ScanNotaDatabase::class.java).build()

    @After
    fun tutup() = db.close()

    private fun fotoAset(nama: String): File {
        val aset = instrumentasi.context.assets
        assumeTrue("foto $nama tidak ada di data_uji/foto/", aset.list("")?.contains(nama) == true)
        val berkas = File(instrumentasi.targetContext.cacheDir, "uji_$nama")
        aset.open(nama).use { masuk -> berkas.outputStream().use { masuk.copyTo(it) } }
        return berkas
    }

    /** Semua barang dijadikan barang baru, seperti pemakaian pertama saat daftar barang masih kosong. */
    private fun siapkan(draf: DrafNota): DrafNota =
        draf.copy(barang = draf.barang.map { it.jadikanBarangBaru() })

    @Test
    fun satuFoto_dibacaLengkap() = runBlocking {
        val hasil = PemrosesFoto().proses(listOf(fotoAset("oren_002552.jpg"))) { _, _ -> }
        assertEquals(16, hasil.nota.barang.size)
        assertEquals(323550L, hasil.nota.total)
        assertTrue(hasil.fotoGagal.isEmpty())
    }

    @Test
    fun duaFotoSatuNota_digabungTanpaBarangGanda() = runBlocking {
        val hasil = PemrosesFoto().proses(listOf(fotoAset("oren_005493_b.jpg"), fotoAset("oren_005493_a.jpg"))) { _, _ -> }
        assertTrue(hasil.sambungan.single().bersambung)
        assertEquals(26, hasil.nota.barang.size)
        assertEquals(526200L, hasil.nota.total)
    }

    /** Gambar SINTETIS berisi tulisan kemasan barang (bukan nota). Tidak boleh menghasilkan isian. */
    @Test
    fun fotoBukanNota_ditolakTanpaIsian() = runBlocking {
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val kanvas = Canvas(bitmap)
        kanvas.drawColor(Color.WHITE)
        val cat = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
        listOf("Mie instan rasa soto", "Netto 75 g", "Isi 100 sachet", "BPOM RI MD 123456789")
            .forEachIndexed { i, t -> kanvas.drawText(t, 60f, 200f + i * 120f, cat) }
        val berkas = File(instrumentasi.targetContext.cacheDir, "uji_bukan_nota.jpg")
        berkas.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        val hasil = PemrosesFoto().proses(listOf(berkas)) { _, _ -> }
        assertEquals(listOf(1), hasil.fotoBukanNota)
        assertTrue(hasil.tidakAdaNota)
        assertTrue(hasil.nota.barang.isEmpty())
    }

    @Test
    fun notaTokoLain_tidakDitolak() = runBlocking {
        val hasil = PemrosesFoto().proses(listOf(fotoAset("tokolain_tanpanomor.jpg"))) { _, _ -> }
        assertTrue(hasil.fotoBukanNota.isEmpty())
    }

    @Test
    fun simpan_tolakNotaGanda_laluStatusHargaDariQuery() = runBlocking {
        val hasil = PemrosesFoto().proses(listOf(fotoAset("oren_002552.jpg"))) { _, _ -> }
        val draf = siapkan(drafDari(hasil.nota, 0, emptyList()))
        assertTrue("draf siap disimpan: ${draf.masalahSimpan()}", draf.masalahSimpan().isEmpty())

        val layanan = LayananSimpanNota(db)
        val pertama = layanan.simpan(draf) as HasilSimpan.Tersimpan
        assertEquals("nomor dan pemasok sama → ditolak", HasilSimpan.NotaGanda(draf.nomorNota), layanan.simpan(draf))

        // Nota kedua: isi sama, nomor lain, tanggal sesudahnya. Pemasok kini sudah ada di database,
        // jadi dipilih otomatis dari judul, dan barang dipilih dari daftar (bukan dibuat ulang).
        val pemasok = db.pemasokDao().amatiSemua().first()
        val barang = db.barangDao().semua().associateBy { it.nama.uppercase() }
        val kedua = drafDari(hasil.nota, 0, pemasok).let { d ->
            d.copy(
                nomorNota = d.nomorNota + "-B",
                tanggal = "13-09-2025",
                barang = d.barang.map { b -> b.pilihBarang(barang.getValue(b.teksOcr.uppercase()).id) },
            )
        }
        assertEquals(pemasok.single().id, kedua.pemasokId)

        // Status harga sudah bisa dilihat sebelum nota kedua disimpan.
        val pembanding = ringkasPembanding(db.notaDao().pembelianDariPemasok(kedua.pemasokId!!, kedua.tanggalTerbaca!!.keEpochHari()))
        assertTrue(kedua.barang.all { kedua.statusHarga(it, pembanding) == StatusHarga.TETAP })

        val notaKedua = layanan.simpan(kedua) as HasilSimpan.Tersimpan

        val statusPertama = db.notaDao().barisNotaDenganHarga(pertama.notaId).first().map { it.status() }
        val statusKedua = db.notaDao().barisNotaDenganHarga(notaKedua.notaId).first().map { it.status() }
        assertTrue(statusPertama.all { it == StatusHarga.BELUM_ADA_PEMBANDING })
        assertTrue("harga sama dengan nota sebelumnya → tetap", statusKedua.all { it == StatusHarga.TETAP })
        assertEquals(16, statusKedua.size)

        val ringkasan = db.notaDao().amatiRingkasanNota().first()
        assertEquals(2, ringkasan.size)
        assertTrue(ringkasan.all { it.lengkap() })
    }
}
