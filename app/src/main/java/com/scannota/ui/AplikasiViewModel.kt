package com.scannota.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scannota.data.Barang
import com.scannota.data.Pemasok
import com.scannota.data.RingkasanNota
import com.scannota.data.ScanNotaDatabase
import com.scannota.matching.HasilPencocokan
import com.scannota.matching.KeputusanPencocokan
import com.scannota.matching.LayananPencocokan
import com.scannota.merge.MAKS_FOTO_PER_NOTA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Layar yang sedang tampil. Perpindahan layar ditulis sendiri, tanpa pustaka navigasi. */
sealed interface Layar {
    data object Beranda : Layar
    data object Kamera : Layar
    data object Periksa : Layar
    data class Detail(val ringkasan: RingkasanNota) : Layar
}

sealed interface StatusProses {
    data object Diam : StatusProses
    data class Berjalan(val selesai: Int, val jumlah: Int) : StatusProses
    data class Gagal(val pesan: String) : StatusProses
}

/**
 * Keadaan seluruh aplikasi dan aksi pengguna. ViewModel dipakai supaya foto yang sudah diambil
 * dan draf yang sedang diperiksa tidak hilang saat HP diputar.
 */
class AplikasiViewModel(aplikasi: Application) : AndroidViewModel(aplikasi) {

    private val db = ScanNotaDatabase.dapatkan(aplikasi)
    private val pemroses = PemrosesFoto()
    private val layananSimpan = LayananSimpanNota(db)
    private val folderFoto = File(aplikasi.cacheDir, "foto_pindai")

    var layar by mutableStateOf<Layar>(Layar.Beranda)
        private set

    /** Foto nota yang sudah diambil, urut dari bagian atas nota. Disimpan sementara di cache HP. */
    val foto = mutableStateListOf<File>()

    var proses by mutableStateOf<StatusProses>(StatusProses.Diam)
        private set

    var draf by mutableStateOf<DrafNota?>(null)
        private set

    var fotoGagal by mutableStateOf<List<Int>>(emptyList())
        private set

    var sedangMenyimpan by mutableStateOf(false)
        private set

    /** Pesan singkat untuk pengguna (ditampilkan sebagai snackbar), lalu dikosongkan. */
    var pesan by mutableStateOf<String?>(null)

    val ringkasanNota: StateFlow<List<RingkasanNota>> =
        db.notaDao().amatiRingkasanNota().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daftarPemasok: StateFlow<List<Pemasok>> =
        db.pemasokDao().amatiSemua().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daftarBarang: StateFlow<List<Barang>> =
        db.barangDao().amatiSemua().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- Perpindahan layar ----------

    fun mulaiPindai() {
        hapusFotoSementara()
        draf = null
        fotoGagal = emptyList()
        proses = StatusProses.Diam
        layar = Layar.Kamera
    }

    fun bukaDetail(ringkasan: RingkasanNota) {
        layar = Layar.Detail(ringkasan)
    }

    /**
     * Tombol kembali. Dari layar periksa kembali ke kamera (foto masih ada, bisa ditambah lalu
     * diproses ulang); dari layar lain kembali ke beranda.
     */
    fun kembali() {
        layar = when (layar) {
            Layar.Periksa -> Layar.Kamera
            else -> Layar.Beranda
        }
    }

    // ---------- Kamera ----------

    fun berkasFotoBaru(): File {
        folderFoto.mkdirs()
        return File(folderFoto, "foto_${System.currentTimeMillis()}.jpg")
    }

    val bolehTambahFoto: Boolean get() = foto.size < MAKS_FOTO_PER_NOTA

    fun fotoTersimpan(berkas: File) {
        if (bolehTambahFoto) foto += berkas else berkas.delete()
    }

    fun hapusFoto(indeks: Int) {
        if (indeks in foto.indices) foto.removeAt(indeks).delete()
    }

    private fun hapusFotoSementara() {
        foto.clear()
        folderFoto.listFiles()?.forEach { it.delete() }
    }

    // ---------- Proses ----------

    fun prosesFoto() {
        if (foto.isEmpty() || proses is StatusProses.Berjalan) return
        val daftar = foto.toList()
        viewModelScope.launch {
            proses = StatusProses.Berjalan(0, daftar.size)
            try {
                val hasil = pemroses.proses(daftar) { selesai, jumlah ->
                    withContext(Dispatchers.Main) { proses = StatusProses.Berjalan(selesai, jumlah) }
                }
                val drafBaru = drafDari(hasil.nota, hasil.sambungan.count { !it.bersambung }, daftarPemasok.value)
                draf = terapkanPencocokan(drafBaru, cocokkan(drafBaru))
                fotoGagal = hasil.fotoGagal
                proses = StatusProses.Diam
                layar = Layar.Periksa
            } catch (e: Exception) {
                proses = StatusProses.Gagal("Foto gagal dibaca: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun tutupPesanProses() {
        proses = StatusProses.Diam
    }

    // ---------- Periksa ----------

    fun ubahDraf(ubah: (DrafNota) -> DrafNota) {
        draf = draf?.let(ubah)
    }

    fun ubahBarang(kunci: Long, ubah: (BarangDraf) -> BarangDraf) {
        draf = draf?.ubahBarang(kunci, ubah)
    }

    /** Ganti pemasok, lalu cocokkan ulang, karena alias berlaku per pemasok. */
    fun pilihPemasok(pemasokId: Long?, namaBaru: String = "") {
        val d = draf ?: return
        draf = d.copy(pemasokId = pemasokId, namaPemasokBaru = namaBaru)
        if (pemasokId != d.pemasokId) cocokkanUlang()
    }

    fun cocokkanUlang() {
        val d = draf ?: return
        viewModelScope.launch {
            val hasil = cocokkan(d)
            // Dipasang ke draf terbaru, bukan salinan lama, supaya sunting pengguna selama
            // pencocokan berjalan tidak tertimpa.
            draf = draf?.let { terapkanPencocokan(it, hasil) }
        }
    }

    /** Hanya barang yang punya nama yang dicocokkan; barang tanpa nama diisi pengguna dulu. */
    private suspend fun cocokkan(d: DrafNota): Map<Long, HasilPencocokan> {
        val bernama = d.barang.filter { it.teksOcr.isNotBlank() }
        if (bernama.isEmpty()) return emptyMap()
        val layanan = LayananPencocokan(db.aliasBarangDao(), db.barangDao())
        // Pemasok baru belum punya alias; id 0 tidak dipakai Room, jadi tidak ada alias yang terbawa.
        val hasil = layanan.cocokkanNota(d.pemasokId ?: 0L, bernama.map { it.teksOcr })
        return bernama.map { it.kunci }.zip(hasil).toMap()
    }

    private fun terapkanPencocokan(d: DrafNota, hasil: Map<Long, HasilPencocokan>): DrafNota =
        d.copy(barang = d.barang.map { b -> hasil[b.kunci]?.let { b.denganPencocokan(it) } ?: b })

    /** Barang yang belum ada di daftar dijadikan barang baru sekaligus, atas perintah pengguna. */
    fun jadikanSemuaYangBelumAdaBarangBaru() {
        ubahDraf { d ->
            d.copy(barang = d.barang.map { b ->
                if (!b.barangSelesai && b.teksOcr.isNotBlank() &&
                    b.pencocokan?.keputusan == KeputusanPencocokan.BARANG_BARU
                ) {
                    b.jadikanBarangBaru()
                } else {
                    b
                }
            })
        }
    }

    fun simpan() {
        val d = draf ?: return
        if (d.masalahSimpan().isNotEmpty() || sedangMenyimpan) return
        sedangMenyimpan = true
        viewModelScope.launch {
            try {
                when (val hasil = layananSimpan.simpan(d)) {
                    is HasilSimpan.Tersimpan -> {
                        pesan = if (hasil.aliasBertentangan > 0) {
                            "Nota tersimpan. ${hasil.aliasBertentangan} pilihan barang tidak diingat, karena teksnya sudah dipakai untuk barang lain."
                        } else {
                            "Nota tersimpan."
                        }
                        hapusFotoSementara()
                        draf = null
                        layar = Layar.Beranda
                    }
                    is HasilSimpan.NotaGanda ->
                        pesan = "Nota nomor ${hasil.nomorNota} dari pemasok ini sudah pernah disimpan. Periksa nomornya, karena nomor sering salah baca."
                }
            } catch (e: Exception) {
                pesan = "Gagal menyimpan: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                sedangMenyimpan = false
            }
        }
    }

    // ---------- Detail ----------

    fun barisNota(notaId: Long) = db.notaDao().barisNotaDenganHarga(notaId)

    fun hapusNota(notaId: Long) {
        viewModelScope.launch {
            db.notaDao().hapusNota(notaId)
            pesan = "Nota dihapus."
            layar = Layar.Beranda
        }
    }
}
