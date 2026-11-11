package com.scannota.parsing

import com.scannota.matching.AMBANG_KONFIRMASI_PERSEN
import com.scannota.matching.AMBANG_OTOMATIS_PERSEN
import com.scannota.matching.hitungSkor
import com.scannota.matching.normalisasiTeks
import com.scannota.ocr.susunBarisVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Menguji pembaca nota pada hasil OCR nota asli (tanpa pengolahan citra), lalu membandingkannya
 * dengan kunci jawaban. Laporan akurasinya dicetak ke keluaran test dan bisa dipakai di bab pengujian.
 */
class NotaAsliTest {

    private fun baca(nama: String) = bacaNotaOren(susunBarisVisual(NotaAsli.ocr(nama).baris))

    @Test
    fun laporanAkurasi() {
        val semua = NotaAsli.DAFTAR.map { evaluasi(it) }
        println(laporan(semua))

        val benar = semua.sumOf { it.benar }
        val jumlah = semua.sumOf { it.jumlahKunci }
        // Batas bawah = hasil saat ini, supaya perubahan kode yang menurunkan akurasi langsung ketahuan.
        // Jika pembaca nota diperbaiki, naikkan angka ini.
        assertTrue("barang benar $benar/$jumlah turun dari batas", benar >= BATAS_BARANG_BENAR)
    }

    /**
     * Ukuran keamanan terpenting: barang yang angkanya salah tetapi tidak ditandai meragukan akan
     * masuk ke database tanpa diperiksa, sehingga status harga bisa keliru tanpa disadari.
     */
    @Test
    fun tidakAdaBarangSalahTanpaTanda() {
        for (nama in NotaAsli.DAFTAR) {
            val e = evaluasi(nama)
            assertTrue("$nama: salah tanpa tanda ${e.salahTanpaTanda}", e.salahTanpaTanda.isEmpty())
        }
    }

    @Test
    fun nota002552_semuaBarangBenar_danLengkap() {
        val e = evaluasi("oren_002552")
        assertEquals(16, e.benar)
        val nota = baca("oren_002552")
        assertTrue(nota.lengkap)
        assertEquals(323550L, nota.total)
        assertEquals("TOKO OREN", nota.judul)
        assertEquals("002552/KSR/UTM/0925", nota.nomorNota)
        assertEquals(Tanggal(12, 9, 2025), nota.tanggal)
        assertTrue(nota.peringatanKaki().isEmpty())
    }

    /** Total 742.150 terbaca 242.150 oleh OCR. Verifikasi total harus menandainya belum lengkap. */
    @Test
    fun nota003182_totalSalahBaca_belumLengkap() {
        val nota = baca("oren_003182")
        assertEquals(242150L, nota.total)
        assertFalse(nota.lengkap)
    }

    @Test
    fun nota004656_qtyPecahan_terbacaDanCocok() {
        val adem = baca("oren_004656_potongan").barang.single { it.nama?.startsWith("ADEM SARI") == true }
        assertEquals(50L, adem.qtyPerseratus)
        assertEquals(51000L, adem.harga)
        assertEquals(true, adem.aritmetikaCocok)
    }

    /** Harga terhalang di foto: baris harga tetap dikenali, harga kosong, dan barang ditandai. */
    @Test
    fun nota004656_hargaTerhalang_tetapSatuBarangDanDitandai() {
        val djiSamSoe = baca("oren_004656_potongan").barang.single { it.subtotal == 42700L }
        assertEquals("DJI 3AM SOE REFILL 12 PREMIUM", djiSamSoe.nama)
        assertNull(djiSamSoe.harga)
        assertEquals(200L, djiSamSoe.qtyPerseratus)
        assertTrue(djiSamSoe.meragukan)
    }

    /**
     * Nota 005493 foto a: semua subtotal dan total terbaca benar, jadi lengkap, walaupun beberapa
     * harga salah baca. Pemeriksaan harga × qty yang menandai barang-barang itu, dan QTY= ikut
     * memberi peringatan karena ada qty yang tidak terbaca.
     */
    @Test
    fun nota005493a_lengkap_tetapiHargaSalahBacaTetapDitandai() {
        val nota = baca("oren_005493_a")
        assertTrue(nota.lengkap)
        val paku = nota.barang.single { it.subtotal == 35800L }
        assertEquals("8.950 terbaca 8.930", 8930L, paku.harga)
        assertEquals(false, paku.aritmetikaCocok)
        assertTrue(paku.meragukan)
        assertEquals(listOf(PeringatanKaki.JUMLAH_QTY_BERBEDA), nota.peringatanKaki())
    }

    @Test
    fun fotoPotongan_tanpaKakiNota_belumLengkap() {
        for (nama in listOf("oren_004656_potongan", "oren_005493_b")) {
            val nota = baca(nama)
            assertNull(nama, nota.total)
            assertFalse(nama, nota.lengkap)
        }
    }

    @Test
    fun barangTerakhirTerpotong_tetapDicatatDanDitandai() {
        val terakhir = baca("oren_005493_b").barang.last()
        assertEquals("NABATI M KEJU 37 GR", terakhir.nama)
        assertNull(terakhir.harga)
        assertTrue(terakhir.meragukan)
    }

    private companion object {
        /** 2026-11-12: 108 dari 117 barang (104 sebelum perbaikan susunBarisVisual). */
        const val BATAS_BARANG_BENAR = 108
    }
}

/** Hasil perbandingan satu nota dengan kunci jawabannya. */
internal data class Evaluasi(
    val nama: String,
    val jumlahKunci: Int,
    val jumlahTerbaca: Int,
    val benar: Int,
    val ditandai: Int,
    val terlewat: Int,
    val salahTanpaTanda: List<String>,
    val namaOtomatis: Int,
    val namaKonfirmasi: Int,
    val namaRendah: Int,
    val nomorBenar: Boolean,
    val tanggalBenar: Boolean,
    val totalBenar: Boolean?,
    val lengkap: Boolean,
)

/**
 * Membandingkan hasil baca dengan kunci.
 *
 * Barang dipasangkan berurutan berdasarkan kemiripan nama, karena jumlah barang terbaca bisa
 * berbeda dari kunci (barang terlewat atau baris tambahan). Untuk setiap barang kunci, dicari
 * barang terbaca paling mirip (kemiripan ≥ 0,5) di antara 3 barang berikutnya. Angka 0,5 dan 3
 * hanya untuk penilaian test ini, bukan bagian aplikasi.
 */
internal fun evaluasi(nama: String): Evaluasi =
    evaluasiNota(nama, bacaNotaOren(susunBarisVisual(NotaAsli.ocr(nama).baris)), NotaAsli.kunci(nama))

/** Seperti [evaluasi], untuk nota yang dibaca dengan cara lain (misalnya gabungan beberapa foto). */
internal fun evaluasiNota(nama: String, nota: NotaTerbaca, kunci: KunciNota): Evaluasi {
    val hasil = nota.barang

    val pasangan = mutableMapOf<Int, Int>()
    var mulai = 0
    kunci.barang.forEachIndexed { i, k ->
        val j = (mulai until minOf(mulai + 3, hasil.size))
            .filter { kemiripan(hasil[it].nama, k.nama) >= 0.5 }
            .maxByOrNull { kemiripan(hasil[it].nama, k.nama) }
        if (j != null) {
            pasangan[i] = j
            mulai = j + 1
        }
    }

    var benar = 0
    val salahTanpaTanda = mutableListOf<String>()
    var otomatis = 0
    var konfirmasi = 0
    var rendah = 0
    for ((i, j) in pasangan) {
        val k = kunci.barang[i]
        val h = hasil[j]
        val angkaSama = h.harga == k.harga && h.qtyPerseratus == k.qtyPerseratus &&
            h.satuan?.teks == k.satuan && h.subtotal == k.subtotal
        if (angkaSama && k.adaAngka) benar++
        if (!angkaSama && !h.meragukan) salahTanpaTanda += "${k.nama}: terbaca ${h.harga}×${h.qty} ${h.satuan?.teks} = ${h.subtotal}"

        val skor = hitungSkor(normalisasiTeks(h.nama ?: ""), normalisasiTeks(k.nama))
        when {
            skor.mencapai(AMBANG_OTOMATIS_PERSEN) -> otomatis++
            skor.mencapai(AMBANG_KONFIRMASI_PERSEN) -> konfirmasi++
            else -> rendah++
        }
    }
    // Barang terbaca yang tidak punya pasangan di kunci adalah baris hantu; harus ditandai.
    for (j in hasil.indices) {
        if (j !in pasangan.values && !hasil[j].meragukan) salahTanpaTanda += "tambahan: ${hasil[j].nama}"
    }

    return Evaluasi(
        nama = nama,
        jumlahKunci = kunci.barang.count { it.adaAngka },
        jumlahTerbaca = hasil.size,
        benar = benar,
        ditandai = hasil.count { it.meragukan },
        terlewat = kunci.barang.size - pasangan.size,
        salahTanpaTanda = salahTanpaTanda,
        namaOtomatis = otomatis,
        namaKonfirmasi = konfirmasi,
        namaRendah = rendah,
        nomorBenar = nota.nomorNota == kunci.nomorNota,
        tanggalBenar = nota.tanggal?.toString() == kunci.tanggal,
        totalBenar = kunci.total?.let { nota.total == it },
        lengkap = nota.lengkap,
    )
}

private fun kemiripan(a: String?, b: String): Double =
    if (a == null) 0.0 else hitungSkor(normalisasiTeks(a), normalisasiTeks(b)).nilai

private fun laporan(semua: List<Evaluasi>): String = buildString {
    appendLine("=== Laporan pembaca nota pada hasil OCR nota asli (tanpa pengolahan citra) ===")
    appendLine("nota\tbarang_kunci\tbarang_terbaca\tangka_benar\tditandai\tterlewat\tsalah_tanpa_tanda\tnama>=0,90\tnama_0,70-0,90\tnama<0,70\tnomor\ttanggal\ttotal\tlengkap")
    for (e in semua) {
        appendLine(
            listOf(
                e.nama, e.jumlahKunci, e.jumlahTerbaca, e.benar, e.ditandai, e.terlewat, e.salahTanpaTanda.size,
                e.namaOtomatis, e.namaKonfirmasi, e.namaRendah,
                ya(e.nomorBenar), ya(e.tanggalBenar), e.totalBenar?.let { ya(it) } ?: "-", ya(e.lengkap),
            ).joinToString("\t"),
        )
    }
    appendLine("JUMLAH\t${semua.sumOf { it.jumlahKunci }}\t${semua.sumOf { it.jumlahTerbaca }}\t${semua.sumOf { it.benar }}\t${semua.sumOf { it.ditandai }}\t${semua.sumOf { it.terlewat }}\t${semua.sumOf { it.salahTanpaTanda.size }}\t${semua.sumOf { it.namaOtomatis }}\t${semua.sumOf { it.namaKonfirmasi }}\t${semua.sumOf { it.namaRendah }}")
}

private fun ya(b: Boolean) = if (b) "ya" else "tidak"
