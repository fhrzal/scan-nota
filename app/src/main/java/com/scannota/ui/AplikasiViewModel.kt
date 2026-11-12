package com.scannota.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scannota.data.Barang
import com.scannota.data.Pemasok
import com.scannota.data.Pembanding
import com.scannota.data.RingkasanNota
import com.scannota.data.ScanNotaDatabase
import com.scannota.data.ringkasPembanding
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
    /** @param bisaDiulang false jika mengulang tidak akan membantu (misalnya foto bukan nota) */
    data class Gagal(val pesan: String, val bisaDiulang: Boolean = true) : StatusProses
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

    /** Nomor foto (mulai 1) yang tidak tampak seperti nota pada proses terakhir. */
    var fotoBukanNota by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Nama berkas foto yang tidak tampak seperti nota, untuk menandai gambar kecilnya di layar kamera. */
    var berkasBukanNota by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Harga sebelumnya per barang dari pemasok yang dipilih, untuk status harga sebelum disimpan. */
    var pembanding by mutableStateOf<Map<Long, Pembanding>>(emptyMap())
        private set

    /** Layar periksa hanya menampilkan barang yang harganya naik atau turun. */
    var hanyaYangBerubah by mutableStateOf(false)

    /** Lama proses dari ketuk "Baca nota" sampai status harga tampil, untuk ditampilkan dan dicatat. */
    var durasiBacaMs by mutableStateOf<Long?>(null)
        private set

    private var mulaiBaca = 0L
    private var jumlahFotoDibaca = 0

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
        fotoBukanNota = emptyList()
        berkasBukanNota = emptySet()
        pembanding = emptyMap()
        hanyaYangBerubah = false
        durasiBacaMs = null
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
        if (indeks in foto.indices) {
            val berkas = foto.removeAt(indeks)
            berkasBukanNota = berkasBukanNota - berkas.name
            berkas.delete()
        }
    }

    private fun hapusFotoSementara() {
        foto.clear()
        folderFoto.listFiles()?.forEach { it.delete() }
    }

    // ---------- Proses ----------

    fun prosesFoto() {
        if (foto.isEmpty() || proses is StatusProses.Berjalan) return
        val daftar = foto.toList()
        mulaiBaca = SystemClock.elapsedRealtime()
        jumlahFotoDibaca = daftar.size
        viewModelScope.launch {
            proses = StatusProses.Berjalan(0, daftar.size)
            try {
                val hasil = pemroses.proses(daftar) { selesai, jumlah ->
                    withContext(Dispatchers.Main) { proses = StatusProses.Berjalan(selesai, jumlah) }
                }
                fotoGagal = hasil.fotoGagal
                fotoBukanNota = hasil.fotoBukanNota
                berkasBukanNota = hasil.fotoBukanNota.map { daftar[it - 1].name }.toSet()
                if (hasil.tidakAdaNota) {
                    proses = if (hasil.fotoBukanNota.isEmpty()) {
                        StatusProses.Gagal("Foto gagal dibaca. Coba lagi.")
                    } else {
                        StatusProses.Gagal(
                            "Tidak ada nota yang dikenali di foto. Pastikan yang difoto adalah nota pemasok, terang, dan tidak buram.",
                            bisaDiulang = false,
                        )
                    }
                    return@launch
                }
                // Urutan: isi/koreksi angka → cocokkan nama → cari harga sebelumnya → isi satuan dari riwayat.
                val drafBaru = drafDari(hasil.nota, hasil.sambungan.count { !it.bersambung }, daftarPemasok.value)
                    .lengkapiAngkaOtomatis()
                val tercocok = terapkanPencocokan(drafBaru, cocokkan(drafBaru))
                val p = pembandingUntuk(tercocok)
                pembanding = p
                draf = tercocok.isiSatuanDariRiwayat(p)
                hanyaYangBerubah = false
                durasiBacaMs = SystemClock.elapsedRealtime() - mulaiBaca
                catatWaktu("status_tampil", durasiBacaMs!!)
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

    /**
     * Mengubah satu barang. Jika barangnya diganti, satuan yang tadinya diisi dari riwayat barang
     * lama dikosongkan, lalu diisi ulang dari riwayat barang yang baru dipilih.
     */
    fun ubahBarang(kunci: Long, ubah: (BarangDraf) -> BarangDraf) {
        val d = draf ?: return
        val lama = d.barang.firstOrNull { it.kunci == kunci } ?: return
        var baru = ubah(lama)
        if (baru.satuanDariRiwayat && baru.barangTerpilihId != lama.barangTerpilihId) {
            baru = baru.copy(satuan = "", satuanDariRiwayat = false)
        }
        draf = d.ubahBarang(kunci) { baru }.isiSatuanDariRiwayat(pembanding)
    }

    /** Pemasok baru atau tanggal belum terbaca: pembanding diambil sampai hari ini (tanpa batas). */
    private suspend fun pembandingUntuk(d: DrafNota): Map<Long, Pembanding> {
        val pemasokId = d.pemasokId ?: return emptyMap()
        val sampai = d.tanggalTerbaca?.keEpochHari() ?: Long.MAX_VALUE
        return ringkasPembanding(db.notaDao().pembelianDariPemasok(pemasokId, sampai))
    }

    /** Dipanggil layar periksa setiap kali pemasok atau tanggal nota berubah. */
    fun muatUlangPembanding() {
        val d = draf ?: return
        viewModelScope.launch {
            val p = pembandingUntuk(d)
            pembanding = p
            draf = draf?.isiSatuanDariRiwayat(p)
        }
    }

    /**
     * Mencatat lama proses ke berkas catatan_waktu.tsv di folder aplikasi
     * (Android/data/com.scannota/files/), untuk bab pengujian. Diambil ke laptop dengan
     * `adb pull /sdcard/Android/data/com.scannota/files/catatan_waktu.tsv`.
     * Kegagalan menulis catatan tidak boleh mengganggu pemakaian, jadi galatnya diabaikan.
     */
    private fun catatWaktu(peristiwa: String, durasiMs: Long) {
        val d = draf ?: return
        val baris = listOf(
            System.currentTimeMillis(), peristiwa, jumlahFotoDibaca, d.barang.size, durasiMs,
            d.barang.count { d.perluDiperiksa(it) }, d.barang.count { it.dihitung != null },
        ).joinToString("\t")
        val folder = getApplication<Application>().getExternalFilesDir(null) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val berkas = File(folder, "catatan_waktu.tsv")
                if (!berkas.exists()) {
                    berkas.writeText("waktu_ms\tperistiwa\tjumlah_foto\tjumlah_barang\tdurasi_ms\tbarang_perlu_diperiksa\tbarang_dihitung\n")
                }
                berkas.appendText(baris + "\n")
            }
        }
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
                        catatWaktu("tersimpan", SystemClock.elapsedRealtime() - mulaiBaca)
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
