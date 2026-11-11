package com.scannota.matching

import com.scannota.data.AliasBarang
import com.scannota.data.AliasBarangDao
import com.scannota.data.Barang
import com.scannota.data.BarangDao

/** Hasil menyimpan alias dari konfirmasi pengguna. */
enum class HasilSimpanAlias {
    /** Alias baru tersimpan. */
    TERSIMPAN,

    /** Alias yang sama persis sudah ada (misalnya barang yang sama muncul dua kali di satu nota). */
    SUDAH_ADA,

    /**
     * Teks ini sudah menjadi alias barang lain untuk pemasok yang sama, jadi alias baru tidak
     * disimpan. Layar perlu memberi tahu pengguna.
     */
    BERTENTANGAN,
}

data class HasilBarangBaru(
    val barangId: Long,
    /** true jika nama yang diketik ternyata sudah ada, sehingga barang lama yang dipakai. */
    val namaSudahAda: Boolean,
    val alias: HasilSimpanAlias,
)

/**
 * Penghubung antara aturan pencocokan ([cocokkan]) dan database.
 *
 * Menerima DAO lewat konstruktor, bukan membuat database sendiri, supaya bisa diuji di laptop
 * dengan DAO tiruan tanpa HP.
 */
class LayananPencocokan(
    private val aliasBarangDao: AliasBarangDao,
    private val barangDao: BarangDao,
) {

    /**
     * Mencocokkan semua nama barang dalam satu nota dari satu pemasok.
     *
     * Kandidat (alias pemasok ini dan nama baku semua barang) dimuat sekali per nota, bukan
     * sekali per baris, supaya database tidak dibaca berulang-ulang. Alias milik pemasok lain
     * tidak dipakai, karena alias berlaku per pemasok.
     *
     * Untuk setiap baris:
     * 1. jika teksnya persis sama dengan alias pemasok ini, hasilnya langsung otomatis, karena
     *    pasangan ini sudah pernah dikonfirmasi pengguna;
     * 2. jika tidak, keputusan diambil oleh [cocokkan] berdasarkan Levenshtein.
     */
    suspend fun cocokkanNota(pemasokId: Long, daftarTeksOcr: List<String>): List<HasilPencocokan> {
        val aliasPemasok = aliasBarangDao.semuaUntukPemasok(pemasokId)
        val semuaBarang = barangDao.semua()

        val aliasPersis: Map<String, AliasBarang> = aliasPemasok.associateBy { normalisasiTeks(it.teksAlias) }
        val kandidat = aliasPemasok.map { Kandidat(it.barangId, normalisasiTeks(it.teksAlias), SumberKandidat.ALIAS_PEMASOK) } +
            semuaBarang.map { Kandidat(it.id, normalisasiTeks(it.nama), SumberKandidat.NAMA_BAKU) }

        return daftarTeksOcr.map { teksOcr ->
            val teks = normalisasiTeks(teksOcr)
            val hasil = cocokkan(teks, kandidat)
            val persis = aliasPersis[teks]
            if (persis == null) {
                hasil
            } else {
                // Alias persis menang atas aturan lain. Kandidat lain tetap disertakan supaya
                // pengguna masih bisa mengoreksi jika alias lama ternyata keliru.
                val utama = KandidatBerskor(persis.barangId, teks, SumberKandidat.ALIAS_PEMASOK, hitungSkor(teks, teks))
                val lainnya = hasil.kandidatTeratas.filter { it.barangId != persis.barangId }
                hasil.copy(
                    keputusan = KeputusanPencocokan.OTOMATIS,
                    kandidatTeratas = (listOf(utama) + lainnya).take(JUMLAH_KANDIDAT_DITAMPILKAN),
                    dariAliasPersis = true,
                    diturunkanKarena = null,
                )
            }
        }
    }

    /** Mencocokkan satu nama, misalnya setelah pengguna menyunting teks hasil OCR. */
    suspend fun cocokkanSatu(pemasokId: Long, teksOcr: String): HasilPencocokan =
        cocokkanNota(pemasokId, listOf(teksOcr)).first()

    /**
     * Menyimpan pilihan pengguna sebagai alias (teks pemasok ini → barang), sehingga lain kali
     * teks yang sama langsung dikenali tanpa bertanya.
     *
     * Hanya dipanggil saat pengguna mengonfirmasi, bukan saat pencocokan otomatis. Dengan begitu,
     * tabel alias hanya berisi pasangan yang sudah diverifikasi manusia, dan satu kesalahan
     * otomatis tidak ikut "dipelajari".
     */
    suspend fun simpanKonfirmasi(pemasokId: Long, teksOcr: String, barangId: Long): HasilSimpanAlias {
        val teks = normalisasiTeks(teksOcr)
        val yangAda = aliasBarangDao.cariPersis(pemasokId, teks)
        return when {
            yangAda == null -> {
                aliasBarangDao.tambah(AliasBarang(barangId = barangId, pemasokId = pemasokId, teksAlias = teks))
                HasilSimpanAlias.TERSIMPAN
            }
            yangAda.barangId == barangId -> HasilSimpanAlias.SUDAH_ADA
            else -> HasilSimpanAlias.BERTENTANGAN
        }
    }

    /**
     * Membuat barang baru dari nama yang dikonfirmasi pengguna, lalu menyimpan teks OCR-nya
     * sebagai alias. Barang baru tidak pernah dibuat otomatis, supaya tabel barang tidak terisi
     * nama hasil salah baca OCR.
     *
     * Jika nama yang diketik sudah ada (tanpa membedakan huruf besar/kecil dan spasi), barang
     * lama yang dipakai, supaya tidak terjadi barang ganda.
     *
     * @param namaBaku nama yang diketik/disetujui pengguna; huruf besar/kecilnya dipertahankan
     * @throws IllegalArgumentException jika nama kosong
     */
    suspend fun buatBarangBaru(pemasokId: Long, teksOcr: String, namaBaku: String): HasilBarangBaru {
        val namaRapi = rapikanSpasi(namaBaku)
        require(namaRapi.isNotEmpty()) { "Nama barang tidak boleh kosong" }

        val kunci = normalisasiTeks(namaRapi)
        val barangLama = barangDao.semua().firstOrNull { normalisasiTeks(it.nama) == kunci }
        val barangId = barangLama?.id ?: barangDao.tambah(Barang(nama = namaRapi))

        val alias = simpanKonfirmasi(pemasokId, teksOcr, barangId)
        return HasilBarangBaru(barangId = barangId, namaSudahAda = barangLama != null, alias = alias)
    }
}
