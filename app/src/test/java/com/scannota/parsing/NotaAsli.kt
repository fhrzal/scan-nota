package com.scannota.parsing

import com.scannota.ocr.HasilOcrFoto
import com.scannota.ocr.dariTsv
import java.math.BigDecimal

/**
 * Data uji dari nota asli pengguna, di app/src/test/resources/nota_asli/:
 * - <nama>.ocr.tsv: hasil OCR foto di HP (alat/ekspor_ocr.sh), tanpa pengolahan citra
 * - <nama>.kunci.tsv: isi nota sebenarnya, disalin manual dari foto dan diperiksa pengguna
 *   dengan nota kertasnya. Untuk nota lengkap, angkanya sudah lolos pemeriksaan silang:
 *   harga × qty = subtotal, jumlah subtotal = total, jumlah barang = BARIS=, jumlah qty = QTY=.
 */
internal object NotaAsli {

    val DAFTAR = listOf(
        "oren_002552",
        "oren_003182",
        "oren_004656_potongan",
        "oren_005493_a",
        "oren_005493_b",
    )

    fun ocr(nama: String): HasilOcrFoto = dariTsv(baca("$nama.ocr.tsv"))

    fun kunci(nama: String): KunciNota {
        val baris = baca("$nama.kunci.tsv").split('\n').filter { it.isNotEmpty() }
        require(baris.first() == "#kunci-nota\t1") { "$nama: bukan berkas kunci" }
        val kepala = baris.filter { it.startsWith('#') }.associate { it.substringBefore('\t') to it.substringAfter('\t') }
        val barang = baris.filterNot { it.startsWith('#') }.drop(1).map { b ->
            val s = b.split('\t')
            KunciBarang(
                nama = s[0],
                harga = s[1].ifEmpty { null }?.toLong(),
                qtyPerseratus = s[2].ifEmpty { null }?.let { BigDecimal(it).movePointRight(2).longValueExact() },
                satuan = s[3].ifEmpty { null },
                subtotal = s[4].ifEmpty { null }?.toLong(),
                catatan = s.getOrElse(5) { "" },
            )
        }
        return KunciNota(
            nomorNota = kepala.getValue("#nomor_nota"),
            tanggal = kepala.getValue("#tanggal"),
            total = kepala.getValue("#total").ifEmpty { null }?.toLong(),
            barisTercetak = kepala.getValue("#baris_tercetak").ifEmpty { null }?.toInt(),
            barang = barang,
        )
    }

    private fun baca(berkas: String): String =
        requireNotNull(javaClass.getResource("/nota_asli/$berkas")) { "berkas uji $berkas tidak ada" }.readText()
}

internal data class KunciNota(
    val nomorNota: String,
    val tanggal: String,
    val total: Long?,
    val barisTercetak: Int?,
    val barang: List<KunciBarang>,
)

internal data class KunciBarang(
    val nama: String,
    val harga: Long?,
    val qtyPerseratus: Long?,
    val satuan: String?,
    val subtotal: Long?,
    val catatan: String,
) {
    /** Barang yang baris harganya terlihat di foto (bukan terpotong). */
    val adaAngka: Boolean get() = harga != null
}
