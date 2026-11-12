package com.scannota.preprocessing

import com.scannota.ocr.susunBarisVisual
import com.scannota.parsing.Evaluasi
import com.scannota.parsing.NotaAsli
import com.scannota.parsing.bacaNotaOren
import com.scannota.parsing.evaluasiNota
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Evaluasi pengolahan citra: membandingkan hasil OCR sebelum dan sesudah pengolahan pada foto nota
 * asli yang sama. Setiap varian dibaca dengan pembaca nota, lalu dibandingkan dengan kunci jawaban.
 *
 * Hasil OCR setiap varian dibuat di HP oleh alat/ekspor_ocr.sh dan disimpan sebagai
 * nota_asli/<foto>.<varian>.ocr.tsv. Varian "asli" adalah <foto>.ocr.tsv.
 */
class PraolahNotaAsliTest {

    /** Foto yang punya kunci jawaban, beserta kuncinya. */
    private val foto = linkedMapOf(
        "oren_002552" to "oren_002552",
        "oren_003182" to "oren_003182",
        "oren_004656_potongan" to "oren_004656_potongan",
        "oren_005493_a" to "oren_005493_a",
        "oren_005493_b" to "oren_005493_b",
        // Foto asli 003182 yang diputar 5° searah jarum jam, untuk menguji deskew.
        "putaran_buatan_oren_003182_5derajat" to "oren_003182",
    )

    private val varian = listOf("asli") + LangkahPraolah.VARIAN_EVALUASI.keys

    /** null jika hasil OCR varian ini belum ada (misalnya ekspor terhenti). */
    private fun evaluasi(namaFoto: String, namaVarian: String): Evaluasi? {
        val berkas = if (namaVarian == "asli") namaFoto else "$namaFoto.$namaVarian"
        if (javaClass.getResource("/nota_asli/$berkas.ocr.tsv") == null) return null
        val nota = bacaNotaOren(susunBarisVisual(NotaAsli.ocr(berkas).baris))
        return evaluasiNota(berkas, nota, NotaAsli.kunci(foto.getValue(namaFoto)))
    }

    @Test
    fun laporanSebelumDanSesudahPengolahanCitra() {
        val hasil = varian.associateWith { v -> foto.keys.associateWith { f -> evaluasi(f, v) } }
        // Jumlah hanya dihitung dari foto yang lengkap untuk semua varian, supaya adil.
        val fotoLengkap = foto.keys.filter { f -> varian.all { v -> hasil.getValue(v)[f] != null } }
        println(buildString {
            appendLine("=== Barang yang semua angkanya benar, per varian pengolahan citra ===")
            appendLine((listOf("varian") + foto.keys + listOf("jumlah*", "ditandai*", "salah_tanpa_tanda*")).joinToString("\t"))
            for (v in varian) {
                val baris = hasil.getValue(v)
                val lengkap = fotoLengkap.map { baris.getValue(it)!! }
                appendLine(
                    (
                        listOf(v) + foto.keys.map { f -> baris.getValue(f)?.let { "${it.benar}/${it.jumlahKunci}" } ?: "-" } +
                            listOf(
                                "${lengkap.sumOf { it.benar }}/${lengkap.sumOf { it.jumlahKunci }}",
                                lengkap.sumOf { it.ditandai }.toString(),
                                lengkap.sumOf { it.salahTanpaTanda.size }.toString(),
                            )
                        ).joinToString("\t"),
                )
            }
            appendLine("* hanya foto yang hasilnya ada untuk semua varian: $fotoLengkap")
            for (v in varian) {
                for ((f, e) in hasil.getValue(v)) {
                    if (e != null && e.salahTanpaTanda.isNotEmpty()) appendLine("salah tanpa tanda [$v / $f]: ${e.salahTanpaTanda}")
                }
            }
        })

        // Pemeriksaan harga × qty harus tetap menangkap semua angka salah, apa pun pengolahan citranya.
        for (v in varian) {
            for ((f, e) in hasil.getValue(v)) {
                assertTrue("salah tanpa tanda di $v / $f: ${e?.salahTanpaTanda}", e == null || e.salahTanpaTanda.isEmpty())
            }
        }
        val abu = fotoLengkap.sumOf { hasil.getValue("abu").getValue(it)!!.benar }
        assertTrue("varian abu turun dari $BATAS_BENAR_ABU menjadi $abu", abu >= BATAS_BENAR_ABU)
    }

    private companion object {
        /** 2026-11-12: grayscale saja 111 dari 117 (tanpa pengolahan 108). */
        const val BATAS_BENAR_ABU = 111
    }
}
