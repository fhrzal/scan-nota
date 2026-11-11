package com.scannota.parsing

import com.scannota.ocr.BarisVisual

/*
 * Pembaca nota berformat Toko Oren. Aturannya disusun dari hasil OCR nota asli Toko Oren
 * (lihat app/src/test/resources/nota_asli/).
 *
 * Bentuk nota:
 *
 *   TOKO OREN                                  ← judul (saran nama pemasok)
 *   JL. ... / Telp: ...
 *   No.  : 002552/KSR/UTM/0925   12-09-2025    ← nomor dan tanggal
 *   Kasir: ...                    09:27:24
 *   Pel. : ...                                 ← baris kepala terakhir
 *   CHITATO LITE RUMPUT LAUT RTG               ← baris nama
 *   17.500,00  x 1,00  RTG =   17.500,00       ← baris harga: harga, qty, satuan, subtotal
 *   ...
 *   BARIS=16  QTY=46,00   323.550,00           ← kaki: jumlah baris, jumlah qty, total
 *   Tunai = ... / Kembali = ...
 */

/** Label baris kepala nota, termasuk variasi salah baca yang terlihat di nota asli (Fel., NO.). */
private val LABEL_KEPALA = Regex("""^(N[O0]\s*[.,:]|KAS[I1l]R|[PF]E[L1l]\s*[.,:]|TELP|JL\s*[.,])""", RegexOption.IGNORE_CASE)
private val LABEL_NOMOR = Regex("""^N[O0]\s*[.,:]*\s*:?\s*""", RegexOption.IGNORE_CASE)
private val LABEL_KAKI = Regex("""^BAR[I1l]S""", RegexOption.IGNORE_CASE)
private val LABEL_PENUTUP = Regex("""^(T[UV]NA[I1l]|KE[MN]BAL[I1l])""", RegexOption.IGNORE_CASE)
private val ISI_BARIS = Regex("""BAR[I1l]S\s*[=\-:]?\s*([0-9OoDIil]+)""", RegexOption.IGNORE_CASE)
private val ISI_QTY = Regex("""[A-Z0-9]TY\s*=\s*(\S+)""", RegexOption.IGNORE_CASE)

/**
 * Membaca nota Toko Oren dari baris visual (keluaran susunBarisVisual, atau gabungan beberapa foto).
 *
 * Langkah:
 * 1. Tentukan wilayah barang: setelah baris kepala terakhir (No./Kasir/Pel.) dan sebelum kaki
 *    nota (BARIS=) atau Tunai/Kembali. Jika kepala tidak terlihat (foto bagian tengah nota),
 *    wilayah barang dimulai dari baris pertama.
 * 2. Di wilayah barang, bedakan baris harga dan baris nama ([adalahBarisHarga]).
 * 3. Pasangkan setiap baris nama dengan baris harga sesudahnya.
 * 4. Baca kepala (judul, nomor, tanggal) dan kaki (BARIS=, QTY=, total).
 */
fun bacaNotaOren(baris: List<BarisVisual>): NotaTerbaca {
    val teks = baris.map { rapikan(it.teks) }
    val barisHarga = teks.map { adalahBarisHarga(it) }

    // Langkah 1. Kepala nota selalu di atas barang pertama, jadi dicari sebelum baris harga pertama.
    val hargaPertama = barisHarga.indexOf(true).let { if (it < 0) teks.size else it }
    val kepalaTerakhir = (0 until hargaPertama).lastOrNull { LABEL_KEPALA.containsMatchIn(teks[it]) } ?: -1
    val awalBarang = kepalaTerakhir + 1
    val kaki = (awalBarang until teks.size).firstOrNull { LABEL_KAKI.containsMatchIn(teks[it]) }
    val akhirBarang = (awalBarang until teks.size).firstOrNull {
        LABEL_KAKI.containsMatchIn(teks[it]) || LABEL_PENUTUP.containsMatchIn(teks[it])
    } ?: teks.size

    // Langkah 2 dan 3.
    val barang = pasangkanBarang((awalBarang until akhirBarang).map { teks[it] to barisHarga[it] })

    // Langkah 4.
    val wilayahKepala = (0 until awalBarang).map { teks[it] }
    val judul = wilayahKepala.firstOrNull()?.takeIf { awalBarang > 1 && !LABEL_KEPALA.containsMatchIn(it) }
    val nomorNota = wilayahKepala.firstOrNull { LABEL_KEPALA.find(it)?.value?.uppercase()?.startsWith("N") == true }
        ?.let { bacaNomorNota(it) }
    val tanggal = wilayahKepala.firstNotNullOfOrNull { bacaTanggal(it) }
    val isiKaki = kaki?.let { bacaKaki(teks[it]) }

    return NotaTerbaca(
        judul = judul,
        nomorNota = nomorNota,
        tanggal = tanggal,
        barang = barang,
        total = isiKaki?.total,
        barisTercetak = isiKaki?.baris,
        qtyTercetakPerseratus = isiKaki?.qtyPerseratus,
    )
}

private fun rapikan(teks: String) = teks.trim().replace(Regex("\\s+"), " ")

/**
 * Baris harga dikenali dari salah satu ciri:
 * - diawali angka. Nama barang di nota asli tidak pernah diawali angka.
 * - berisi satuan baku yang diapit angka di kiri dan kanannya. Ciri kedua dibutuhkan untuk baris
 *   yang harganya terhalang, misalnya di nota asli "21.,00 2,00 PCS = 42.700,00". Satuan harus
 *   persis sama dengan daftar baku, supaya nama seperti "KECAP SAWI PLB 1 RTG" (angka hanya di
 *   kiri satuan) atau "ANDALAN BARU KR 16" tidak ikut terbaca sebagai baris harga.
 */
private fun adalahBarisHarga(teks: String): Boolean {
    val token = pecahToken(teks)
    if (token.firstOrNull()?.let { uraiAngka(it) } != null) return true
    val posisiSatuan = token.indexOfFirst { t -> t.uppercase().trim { !it.isLetter() } in SATUAN_DIKENAL }
    if (posisiSatuan < 0) return false
    return token.subList(0, posisiSatuan).any { uraiAngka(it) != null } &&
        token.subList(posisiSatuan + 1, token.size).any { uraiAngka(it) != null }
}

/**
 * Memasangkan baris nama dengan baris harga sesudahnya.
 *
 * - Dua baris nama berurutan: nama pertama tidak punya baris harga (misalnya terpotong), jadi
 *   tetap dicatat dengan angka kosong supaya pengguna tahu ada barang yang belum lengkap.
 * - Baris harga tanpa nama di atasnya dicatat dengan nama kosong.
 * - Subtotal yang terpisah ke baris sendiri (terlihat di tepi bawah foto yang kertasnya
 *   melengkung): baris yang hanya berisi satu angka, tepat setelah baris harga yang belum punya
 *   subtotal, disambungkan sebagai subtotal baris itu.
 */
private fun pasangkanBarang(wilayah: List<Pair<String, Boolean>>): List<BarangTerbaca> {
    val hasil = mutableListOf<BarangTerbaca>()
    var namaMenunggu: String? = null
    for ((teks, adalahHarga) in wilayah) {
        if (!adalahHarga) {
            namaMenunggu?.let { hasil += barangTanpaHarga(it) }
            namaMenunggu = teks
            continue
        }
        val token = pecahToken(teks)
        val sebelumnya = hasil.lastOrNull()
        if (namaMenunggu == null && token.size == 1 && sebelumnya != null &&
            sebelumnya.teksBarisHarga != null && sebelumnya.subtotal == null
        ) {
            hasil[hasil.lastIndex] = sebelumnya.copy(
                subtotal = bacaUang(token.single()),
                teksBarisHarga = "${sebelumnya.teksBarisHarga} $teks",
            )
            continue
        }
        hasil += bacaBarisHarga(namaMenunggu, teks)
        namaMenunggu = null
    }
    namaMenunggu?.let { hasil += barangTanpaHarga(it) }
    return hasil
}

private fun barangTanpaHarga(nama: String) =
    BarangTerbaca(nama = nama, harga = null, qtyPerseratus = null, satuan = null, subtotal = null, teksBarisHarga = null)

/**
 * Membaca baris harga "harga x qty SATUAN = subtotal".
 *
 * Tanda x dan = sering salah baca (X, *, E, a) atau hilang, jadi patokannya satuan: kata pertama
 * (minimal 2 huruf) setelah angka pertama. Angka sebelum satuan adalah harga lalu qty, dan angka
 * pertama sesudah satuan adalah subtotal.
 * - Jika sebelum satuan hanya ada satu angka, angka itu qty jika didahului tanda kali atau tidak
 *   punya pemisah ribuan, dan harga jika sebaliknya.
 * - Jika satuan tidak terbaca, angka dibaca urut: harga, qty, subtotal (dua angka: harga dan subtotal).
 */
internal fun bacaBarisHarga(nama: String?, teksBaris: String): BarangTerbaca {
    val token = pecahToken(teksBaris)
    val angka = token.indices.filter { uraiAngka(token[it]) != null }
    val posisiSatuan = token.indices.firstOrNull { i ->
        i > (angka.firstOrNull() ?: -1) && uraiAngka(token[i]) == null && token[i].count { it.isLetter() } >= 2
    }

    var harga: Int? = null
    var qty: Int? = null
    var subtotal: Int? = null
    if (posisiSatuan != null) {
        val sebelum = angka.filter { it < posisiSatuan }
        subtotal = angka.firstOrNull { it > posisiSatuan }
        when (sebelum.size) {
            0 -> Unit
            1 -> {
                val i = sebelum.single()
                val didahuluiKali = token.subList(0, i).any { it in TANDA_KALI }
                if (didahuluiKali || uraiAngka(token[i])?.adaPemisahRibuan == false) qty = i else harga = i
            }
            else -> {
                harga = sebelum[0]
                qty = sebelum[1]
            }
        }
    } else {
        when (angka.size) {
            0 -> Unit
            1 -> harga = angka[0]
            2 -> {
                harga = angka[0]
                subtotal = angka[1]
            }
            else -> {
                harga = angka[0]
                qty = angka[1]
                subtotal = angka.last()
            }
        }
    }

    return BarangTerbaca(
        nama = nama,
        harga = harga?.let { bacaUang(token[it]) },
        qtyPerseratus = qty?.let { bacaQty(token[it]) },
        satuan = posisiSatuan?.let { samakanSatuan(token[it]) },
        subtotal = subtotal?.let { bacaUang(token[it]) },
        teksBarisHarga = teksBaris,
    )
}

/** Nomor nota: token pertama setelah label "No. :", misalnya 002552/KSR/UTM/0925. */
private fun bacaNomorNota(teksBaris: String): String? {
    val sisa = LABEL_NOMOR.replaceFirst(teksBaris, "").trimStart(':', ' ')
    return sisa.split(' ').firstOrNull()?.takeIf { t -> t.any { it in '0'..'9' } }
}

private class IsiKaki(val baris: Int?, val qtyPerseratus: Long?, val total: Long?)

/**
 * Kaki nota "BARIS=16 QTY=46,00 323.550,00". Total adalah angka pertama setelah BARIS= dan QTY=
 * dibuang. Variasi salah baca di nota asli: BARIS-26, BTY=80,D0, 9TY=95,00.
 */
private fun bacaKaki(teksBaris: String): IsiKaki {
    val baris = ISI_BARIS.find(teksBaris)?.groupValues?.get(1)?.let { bacaUang(it)?.toInt() }
    val qty = ISI_QTY.find(teksBaris)?.groupValues?.get(1)?.let { bacaQty(it) }
    val sisa = teksBaris.replace(ISI_BARIS, " ").replace(ISI_QTY, " ")
    val total = pecahToken(sisa).firstNotNullOfOrNull { bacaUang(it) }
    return IsiKaki(baris, qty, total)
}
