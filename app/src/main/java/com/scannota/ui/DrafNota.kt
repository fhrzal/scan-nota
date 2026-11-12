package com.scannota.ui

import com.scannota.data.Pemasok
import com.scannota.data.Pembanding
import com.scannota.data.StatusHarga
import com.scannota.data.apakahNotaLengkap
import com.scannota.data.tentukanStatusHarga
import com.scannota.matching.HasilPencocokan
import com.scannota.matching.normalisasiTeks
import com.scannota.matching.rapikanSpasi
import com.scannota.parsing.CaraHitung
import com.scannota.parsing.NotaTerbaca
import com.scannota.parsing.SatuanTerbaca
import com.scannota.parsing.Tanggal
import com.scannota.parsing.bacaQty
import com.scannota.parsing.bacaTanggal
import com.scannota.parsing.bacaUang
import com.scannota.parsing.lengkapiAngka
import com.scannota.parsing.samakanSatuan

/*
 * Draf nota yang sedang diperiksa pengguna sebelum disimpan. Semua angka disimpan sebagai teks
 * yang bisa disunting, lalu dibaca ulang dengan aturan parsing/ yang sama. Dengan begitu, angka
 * yang diketik pengguna diperiksa dengan cara yang sama seperti angka hasil OCR.
 *
 * Bagian ini fungsi murni (tanpa Android), sehingga aturan simpannya bisa diuji di laptop.
 */

/**
 * Satu barang dalam draf.
 *
 * @param kunci penanda unik dalam draf (untuk daftar di layar), bukan id database
 * @param teksOcr nama barang seperti tercetak di nota; disimpan sebagai alias jika pengguna memilih
 *   barangnya sendiri
 * @param barangTerpilihId barang lama yang dipakai (hasil pencocokan otomatis atau pilihan pengguna)
 * @param dipilihPengguna true jika pengguna memilih barangnya sendiri. Hanya pilihan ini yang
 *   disimpan sebagai alias; hasil otomatis tidak (aturan matching/).
 * @param namaBaru diisi jika pengguna memutuskan membuat barang baru. Barang baru tidak pernah
 *   dibuat otomatis.
 * @param sudahDiperiksa pengguna menyatakan barang meragukan ini sudah benar
 * @param manual nama barang diketik pengguna (barang ditambah manual, atau OCR tidak membaca
 *   namanya), sehingga kolom namanya tetap bisa disunting
 * @param dihitung cara angka diisi atau dikoreksi aplikasi (lihat lengkapiAngka), null jika apa
 *   adanya dari OCR atau dari pengguna
 * @param angkaTerbaca angka asli hasil OCR sebelum dihitung, untuk ditampilkan ke pengguna
 * @param satuanDariRiwayat satuan tidak terbaca dan diisi dari pembelian terakhir barang ini
 */
data class BarangDraf(
    val kunci: Long,
    val teksOcr: String,
    val harga: String,
    val qty: String,
    val satuan: String,
    val subtotal: String,
    val pencocokan: HasilPencocokan? = null,
    val barangTerpilihId: Long? = null,
    val dipilihPengguna: Boolean = false,
    val namaBaru: String? = null,
    val sudahDiperiksa: Boolean = false,
    val manual: Boolean = false,
    val dihitung: CaraHitung? = null,
    val angkaTerbaca: String? = null,
    val satuanDariRiwayat: Boolean = false,
) {
    val hargaAngka: Long? get() = bacaUang(tanpaSpasi(harga))
    val qtyPerseratus: Long? get() = bacaQty(tanpaSpasi(qty))
    val subtotalAngka: Long? get() = bacaUang(tanpaSpasi(subtotal))
    val satuanTerbaca: SatuanTerbaca? get() = satuan.takeIf { it.isNotBlank() }?.let { samakanSatuan(it) }

    val angkaLengkap: Boolean get() = hargaAngka != null && qtyPerseratus != null && subtotalAngka != null

    /** Alasan barang ini meragukan, dalam kalimat untuk pengguna. Kosong berarti tidak meragukan. */
    val alasanRagu: List<String>
        get() = buildList {
            if (teksOcr.isBlank()) add("nama barang kosong")
            if (hargaAngka == null) add("harga belum terbaca")
            if (qtyPerseratus == null) add("qty belum terbaca")
            if (subtotalAngka == null) add("subtotal belum terbaca")
            val h = hargaAngka
            val q = qtyPerseratus
            val s = subtotalAngka
            // Pemeriksaan yang sama dengan parsing/: bilangan bulat, qty dalam perseratus.
            if (h != null && q != null && s != null && h * q != s * 100) {
                add("${formatRupiah(h)} × ${formatQty(q)} = ${formatRupiah(h * q / 100)}, bukan ${formatRupiah(s)}")
            }
            val st = satuanTerbaca
            if (st == null) add("satuan kosong") else if (!st.dikenali) add("satuan \"${st.teks}\" tidak dikenal")
        }

    val meragukan: Boolean get() = alasanRagu.isNotEmpty()

    /** "Sudah benar" hanya boleh untuk barang yang nama dan angkanya lengkap. */
    val bolehDinyatakanBenar: Boolean get() = teksOcr.isNotBlank() && angkaLengkap

    val barangSelesai: Boolean get() = barangTerpilihId != null || !namaBaru.isNullOrBlank()

    /**
     * Mengubah angka atau satuan. Tanda "sudah diperiksa" dan tanda "dihitung" dihapus, karena
     * pernyataan pengguna dan hitungan aplikasi berlaku untuk angka lama.
     */
    fun ubahAngka(
        harga: String = this.harga,
        qty: String = this.qty,
        satuan: String = this.satuan,
        subtotal: String = this.subtotal,
    ): BarangDraf = copy(
        harga = harga, qty = qty, satuan = satuan, subtotal = subtotal,
        sudahDiperiksa = false, dihitung = null, angkaTerbaca = null, satuanDariRiwayat = false,
    )

    /** Mengisi atau mengoreksi angka dengan lengkapiAngka; angka asli disimpan untuk ditampilkan. */
    fun lengkapi(subtotalTerverifikasi: Boolean): BarangDraf {
        val a = lengkapiAngka(hargaAngka, qtyPerseratus, subtotalAngka, subtotalTerverifikasi) ?: return this
        return copy(
            harga = formatRupiah(a.harga),
            qty = formatQty(a.qtyPerseratus),
            subtotal = formatRupiah(a.subtotal),
            dihitung = a.cara,
            angkaTerbaca = angkaTerbaca ?: "${harga.ifBlank { "?" }} × ${qty.ifBlank { "?" }} = ${subtotal.ifBlank { "?" }}",
        )
    }

    fun pilihBarang(barangId: Long): BarangDraf = copy(barangTerpilihId = barangId, dipilihPengguna = true, namaBaru = null)

    fun jadikanBarangBaru(): BarangDraf =
        copy(barangTerpilihId = null, dipilihPengguna = false, namaBaru = rapikanSpasi(teksOcr))

    fun batalkanPilihan(): BarangDraf = copy(barangTerpilihId = null, dipilihPengguna = false, namaBaru = null)

    /**
     * Memasang hasil pencocokan. Pilihan yang sudah dibuat pengguna tidak ditimpa; hanya daftar
     * kandidatnya yang diperbarui.
     */
    fun denganPencocokan(hasil: HasilPencocokan): BarangDraf =
        if (dipilihPengguna || namaBaru != null) {
            copy(pencocokan = hasil)
        } else {
            copy(pencocokan = hasil, barangTerpilihId = hasil.barangOtomatis)
        }
}

/**
 * Draf satu nota.
 *
 * @param pemasokId pemasok lama yang dipilih, atau null jika memakai [namaPemasokBaru]
 * @param jumlahTidakBersambung jumlah sambungan antarfoto yang tidak ditemukan tumpang tindihnya
 */
data class DrafNota(
    val pemasokId: Long? = null,
    val namaPemasokBaru: String = "",
    val nomorNota: String = "",
    val tanggal: String = "",
    val total: String = "",
    val barang: List<BarangDraf> = emptyList(),
    val jumlahTidakBersambung: Int = 0,
    val barisTercetak: Int? = null,
    val qtyTercetakPerseratus: Long? = null,
) {
    val tanggalTerbaca: Tanggal? get() = bacaTanggal(tanggal.trim())
    val totalAngka: Long? get() = bacaUang(tanpaSpasi(total))
    val jumlahSubtotal: Long get() = barang.sumOf { it.subtotalAngka ?: 0L }

    /** Aturan kelengkapan yang sama dengan database: total terbaca dan sama dengan jumlah subtotal. */
    val lengkap: Boolean
        get() = barang.isNotEmpty() && barang.all { it.subtotalAngka != null } && apakahNotaLengkap(totalAngka, jumlahSubtotal)

    /** Peringatan yang tidak menghalangi penyimpanan, tetapi perlu diketahui pengguna. */
    fun peringatan(): List<String> = buildList {
        if (jumlahTidakBersambung > 0) {
            add("$jumlahTidakBersambung sambungan foto tidak bersambung. Mungkin ada barang yang terlewat di antara foto.")
        }
        barisTercetak?.let { if (it != barang.size) add("Nota mencetak BARIS=$it, tetapi ada ${barang.size} barang di daftar.") }
        qtyTercetakPerseratus?.let { qtyNota ->
            val semuaQty = barang.map { it.qtyPerseratus }
            if (semuaQty.any { it == null } || semuaQty.sumOf { it ?: 0L } != qtyNota) {
                add("Nota mencetak QTY=${formatQty(qtyNota)}, tetapi jumlah qty di daftar ${formatQty(semuaQty.sumOf { it ?: 0L })}.")
            }
        }
        if (!lengkap) {
            val total = totalAngka
            if (total == null) {
                add("Total nota kosong, jadi nota akan berstatus belum lengkap.")
            } else {
                add("Jumlah subtotal ${formatRupiah(jumlahSubtotal)} tidak sama dengan total ${formatRupiah(total)}, jadi nota akan berstatus belum lengkap.")
            }
        }
    }

    /**
     * Apakah barang ini harus diperiksa pengguna sebelum nota disimpan.
     *
     * - Barang meragukan (angka tidak cocok, kosong, satuan tidak dikenal): ya.
     * - Angka yang dihitung aplikasi atau satuan dari riwayat: hanya jika nota belum lengkap. Jika
     *   jumlah subtotal sama dengan total, subtotal terbukti benar, sehingga hitungan dari subtotal
     *   ikut terpercaya.
     */
    fun perluDiperiksa(b: BarangDraf): Boolean {
        if (b.sudahDiperiksa) return false
        if (b.meragukan) return true
        return (b.dihitung != null || b.satuanDariRiwayat) && !lengkap
    }

    /**
     * Mengisi angka yang kosong, lalu, jika nota ternyata lengkap, mengoreksi angka yang tidak
     * cocok. Urutannya penting: subtotal yang dihitung ikut dijumlahkan dulu, baru kelengkapan
     * nota diperiksa.
     */
    fun lengkapiAngkaOtomatis(): DrafNota {
        val terisi = copy(barang = barang.map { it.lengkapi(subtotalTerverifikasi = false) })
        if (!terisi.lengkap) return terisi
        return terisi.copy(barang = terisi.barang.map { it.lengkapi(subtotalTerverifikasi = true) })
    }

    /** Satuan yang kosong diisi dari pembelian terakhir barang yang sama dari pemasok ini. */
    fun isiSatuanDariRiwayat(pembanding: Map<Long, Pembanding>): DrafNota = copy(
        barang = barang.map { b ->
            val satuan = b.barangTerpilihId?.let { pembanding[it]?.satuanTerakhir }
            if (b.satuan.isBlank() && satuan != null) b.copy(satuan = satuan, satuanDariRiwayat = true) else b
        },
    )

    /** Harga sebelumnya dari pemasok yang sama, untuk barang dan satuan yang sama. */
    fun hargaSebelumnya(b: BarangDraf, pembanding: Map<Long, Pembanding>): Long? {
        if (pemasokId == null) return null
        val id = b.barangTerpilihId ?: return null
        return pembanding[id]?.hargaSebelumnya(b.satuanTerbaca?.teks)
    }

    /**
     * Status harga barang sebelum nota disimpan. Aturannya sama dengan query database
     * (tentukanStatusHarga). null jika belum bisa ditentukan: harga kosong, atau barangnya belum dipilih.
     */
    fun statusHarga(b: BarangDraf, pembanding: Map<Long, Pembanding>): StatusHarga? {
        val harga = b.hargaAngka ?: return null
        if (b.barangTerpilihId == null) return if (!b.namaBaru.isNullOrBlank()) StatusHarga.BELUM_ADA_PEMBANDING else null
        return tentukanStatusHarga(hargaSebelumnya(b, pembanding), harga)
    }

    /** Hal yang harus dibereskan sebelum nota bisa disimpan. Kosong berarti boleh disimpan. */
    fun masalahSimpan(): List<String> = buildList {
        if (pemasokId == null && namaPemasokBaru.isBlank()) add("Pilih pemasok atau isi nama pemasok baru.")
        if (tanggalTerbaca == null) add("Isi tanggal nota dengan format dd-mm-yyyy.")
        if (barang.isEmpty()) add("Belum ada barang.")
        barang.count { !it.barangSelesai }.let { if (it > 0) add("$it barang belum dipilih barangnya.") }
        barang.count { !it.angkaLengkap }.let { if (it > 0) add("$it barang angkanya belum lengkap.") }
        barang.count { it.angkaLengkap && perluDiperiksa(it) }.let { if (it > 0) add("$it barang perlu diperiksa.") }
    }

    fun ubahBarang(kunci: Long, ubah: (BarangDraf) -> BarangDraf): DrafNota =
        copy(barang = barang.map { if (it.kunci == kunci) ubah(it) else it })

    fun hapusBarang(kunci: Long): DrafNota = copy(barang = barang.filterNot { it.kunci == kunci })

    fun tambahBarangKosong(): DrafNota {
        val kunci = (barang.maxOfOrNull { it.kunci } ?: -1) + 1
        return copy(barang = barang + BarangDraf(kunci, teksOcr = "", harga = "", qty = "", satuan = "", subtotal = "", manual = true))
    }
}

/**
 * Membuat draf dari hasil pembacaan nota.
 *
 * Pemasok dipilih otomatis hanya jika judul nota (misalnya "TOKO OREN") sama persis dengan nama
 * pemasok yang sudah ada, tanpa membedakan huruf besar/kecil dan spasi. Jika tidak, judul hanya
 * diisikan sebagai saran nama pemasok baru.
 */
fun drafDari(nota: NotaTerbaca, jumlahTidakBersambung: Int, daftarPemasok: List<Pemasok>): DrafNota {
    val judul = nota.judul?.let { rapikanSpasi(it) }.orEmpty()
    val pemasokCocok = daftarPemasok.firstOrNull { judul.isNotEmpty() && normalisasiTeks(it.nama) == normalisasiTeks(judul) }
    return DrafNota(
        pemasokId = pemasokCocok?.id,
        namaPemasokBaru = if (pemasokCocok == null) judul else "",
        nomorNota = nota.nomorNota.orEmpty(),
        tanggal = nota.tanggal?.toString().orEmpty(),
        total = nota.total?.let { formatRupiah(it) }.orEmpty(),
        barang = nota.barang.mapIndexed { i, b ->
            BarangDraf(
                kunci = i.toLong(),
                teksOcr = b.nama.orEmpty(),
                harga = b.harga?.let { formatRupiah(it) }.orEmpty(),
                qty = b.qtyPerseratus?.let { formatQty(it) }.orEmpty(),
                satuan = b.satuan?.teks.orEmpty(),
                subtotal = b.subtotal?.let { formatRupiah(it) }.orEmpty(),
                manual = b.nama == null,
            )
        },
        jumlahTidakBersambung = jumlahTidakBersambung,
        barisTercetak = nota.barisTercetak,
        qtyTercetakPerseratus = nota.qtyTercetakPerseratus,
    )
}

private fun tanpaSpasi(teks: String) = teks.filterNot { it.isWhitespace() }
