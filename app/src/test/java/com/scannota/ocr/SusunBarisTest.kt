package com.scannota.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Menguji penyusunan baris visual dengan koordinat sintetis.
 *
 * Semua teks dan koordinat di sini adalah contoh buatan untuk menguji logika geometri, bukan
 * data nota. Aturan ini diuji ulang dengan hasil OCR nota asli di sesi parsing/.
 */
class SusunBarisTest {

    /** Baris dengan kotak tegak (tidak miring). */
    private fun baris(teks: String, kiri: Int, atas: Int, kanan: Int, bawah: Int, blok: Int = 0) =
        BarisOcr(
            teks = teks,
            keyakinan = 0.9f,
            titikSudut = listOf(Titik(kiri, atas), Titik(kanan, atas), Titik(kanan, bawah), Titik(kiri, bawah)),
            indeksBlok = blok,
            kata = emptyList(),
        )

    /** Baris yang diputar searah jarum jam sebesar [derajat] terhadap titik (0, 0), seperti foto miring. */
    private fun barisMiring(teks: String, kiri: Int, atas: Int, kanan: Int, bawah: Int, derajat: Double): BarisOcr {
        val r = Math.toRadians(derajat)
        fun putar(x: Int, y: Int) = Titik((x * cos(r) - y * sin(r)).roundToInt(), (x * sin(r) + y * cos(r)).roundToInt())
        return baris(teks, 0, 0, 0, 0).copy(
            titikSudut = listOf(putar(kiri, atas), putar(kanan, atas), putar(kanan, bawah), putar(kiri, bawah)),
        )
    }

    private fun teksPerBaris(hasil: List<BarisVisual>) = hasil.map { it.teks }

    @Test
    fun kemiringan_ujungKananLebihRendah_positif() {
        val titik = listOf(Titik(0, 0), Titik(100, 10), Titik(97, 40), Titik(-3, 30))
        assertEquals(5.71f, kemiringanDerajat(titik)!!, 0.01f)
    }

    @Test
    fun kemiringan_ujungKananLebihTinggi_negatif() {
        val titik = listOf(Titik(0, 10), Titik(100, 0), Titik(103, 30), Titik(3, 40))
        assertEquals(-5.71f, kemiringanDerajat(titik)!!, 0.01f)
    }

    @Test
    fun kemiringan_tanpaTitik_null() {
        assertNull(kemiringanDerajat(emptyList()))
    }

    @Test
    fun kotakTegak_dariTitikSudut() {
        val b = barisMiring("A", 10, 20, 110, 50, 0.0)
        assertEquals(Kotak(10, 20, 110, 50), b.kotak)
        assertNull(kotakDari(emptyList()))
    }

    @Test
    fun perkiraanKemiringan_median_tahanNilaiEkstrem() {
        val ganjil = listOf(1.0, 2.0, 30.0).map { barisMiring("A", 0, 0, 1000, 30, it) }
        assertEquals("satu baris ekstrem (30°) tidak menarik hasil", 2f, perkiraanKemiringan(ganjil), 0.05f)

        val genap = listOf(1.0, 2.0, 3.0, 4.0).map { barisMiring("A", 0, 0, 1000, 30, it) }
        assertEquals(2.5f, perkiraanKemiringan(genap), 0.05f)

        assertEquals("tanpa baris berposisi", 0f, perkiraanKemiringan(emptyList()), 0f)
    }

    @Test
    fun duaKolomDariBlokBerbeda_disatukanPerBaris() {
        // Urutan seperti keluaran ML Kit: blok kolom kiri dulu, baru blok kolom kanan.
        val masukan = listOf(
            baris("ALFA", 20, 100, 300, 130, blok = 0),
            baris("BETA", 20, 150, 300, 180, blok = 0),
            baris("111", 600, 100, 700, 130, blok = 1),
            baris("222", 600, 150, 700, 180, blok = 1),
        )
        assertEquals(listOf("ALFA 111", "BETA 222"), teksPerBaris(susunBarisVisual(masukan)))
    }

    @Test
    fun dalamSatuBaris_urutKiriKeKanan() {
        val masukan = listOf(
            baris("KANAN", 800, 100, 900, 130),
            baris("KIRI", 20, 100, 200, 130),
            baris("TENGAH", 400, 100, 600, 130),
        )
        val hasil = susunBarisVisual(masukan)
        assertEquals(1, hasil.size)
        assertEquals(listOf("KIRI", "TENGAH", "KANAN"), hasil.single().potongan.map { it.teks })
    }

    @Test
    fun barisBerdempetan_tidakDisatukan() {
        // Tumpang tindih 5 dari tinggi 30 (17%): hanya bersentuhan di tepi.
        val masukan = listOf(baris("ATAS", 20, 100, 300, 130), baris("BAWAH", 20, 125, 300, 155))
        assertEquals(listOf("ATAS", "BAWAH"), teksPerBaris(susunBarisVisual(masukan)))
    }

    @Test
    fun tumpangTindihTepat50Persen_disatukan_batasTermasuk() {
        // Tumpang tindih 15 dari tinggi 30 = tepat 50%.
        val masukan = listOf(baris("A", 20, 100, 300, 130), baris("B", 400, 115, 600, 145))
        assertEquals(1, susunBarisVisual(masukan).size)
    }

    @Test
    fun tumpangTindihDiBawah50Persen_dipisah() {
        // Tumpang tindih 14 dari tinggi 30 (47%).
        val masukan = listOf(baris("A", 20, 100, 300, 130), baris("B", 400, 116, 600, 146))
        assertEquals(2, susunBarisVisual(masukan).size)
    }

    @Test
    fun angkaKecilSebarisDenganHurufBesar_tetapSatuBaris() {
        // Pembanding adalah tinggi yang lebih pendek, jadi teks kecil di dalam rentang teks besar ikut.
        val masukan = listOf(baris("BESAR", 20, 100, 300, 140), baris("12", 400, 115, 450, 135))
        assertEquals(listOf("BESAR 12"), teksPerBaris(susunBarisVisual(masukan)))
    }

    /**
     * Foto miring 3°: dengan jarak antarbaris 45 piksel, ujung kanan baris pertama turun sekitar
     * 47 piksel, sehingga sejajar dengan baris berikutnya. Koreksi kemiringan membuatnya benar.
     */
    @Test
    fun fotoMiring3Derajat_tetapDisusunBenar() {
        val masukan = listOf(0, 1, 2).flatMap { i ->
            val atas = 100 + 45 * i
            listOf(
                barisMiring("KIRI$i", 20, atas, 300, atas + 30, 3.0),
                barisMiring("KANAN$i", 800, atas, 1000, atas + 30, 3.0),
            )
        }
        // Toleransi 0,2° karena titik sudut dibulatkan ke piksel bulat.
        assertEquals(3f, perkiraanKemiringan(masukan), 0.2f)
        assertEquals(
            listOf("KIRI0 KANAN0", "KIRI1 KANAN1", "KIRI2 KANAN2"),
            teksPerBaris(susunBarisVisual(masukan)),
        )
    }

    /**
     * Test dokumentasi: alasan posisi disimpan sebagai empat titik sudut, bukan kotak tegak.
     * Data yang sama dengan test di atas, tetapi hanya kotak tegaknya yang tersisa. Sudutnya tidak
     * bisa diketahui, jadi tidak bisa dikoreksi, dan hasilnya salah.
     */
    @Test
    fun fotoMiring_jikaHanyaAdaKotakTegak_hasilnyaSalah() {
        val masukan = listOf(0, 1, 2).flatMap { i ->
            val atas = 100 + 45 * i
            listOf(
                barisMiring("KIRI$i", 20, atas, 300, atas + 30, 3.0),
                barisMiring("KANAN$i", 800, atas, 1000, atas + 30, 3.0),
            )
        }.map { b -> b.kotak!!.let { k -> baris(b.teks, k.kiri, k.atas, k.kanan, k.bawah) } }

        assertNotEquals(
            listOf("KIRI0 KANAN0", "KIRI1 KANAN1", "KIRI2 KANAN2"),
            teksPerBaris(susunBarisVisual(masukan)),
        )
    }

    /**
     * Dua potongan di posisi mendatar yang sama tidak mungkin satu baris cetak, walaupun rentang
     * tingginya tumpang tindih 67%. Contoh dari nota asli: total dan angka Tunai yang berdempetan.
     */
    @Test
    fun potonganBertumpukMendatar_tidakDisatukan() {
        val masukan = listOf(baris("111", 600, 100, 700, 130), baris("222", 605, 110, 705, 140))
        assertEquals(listOf("111", "222"), teksPerBaris(susunBarisVisual(masukan)))
    }

    @Test
    fun bersentuhanDiTepi_bukanBertumpuk() {
        assertFalse(bertumpukMendatar(0.0, 100.0, 100.0, 200.0))
        assertTrue(bertumpukMendatar(0.0, 100.0, 99.0, 200.0))
    }

    /**
     * Kertas melengkung: lima baris lurus (median kemiringan foto 0°) dan satu baris di bawahnya
     * miring −4°. Ujung kanan baris miring itu naik sekitar 54 piksel. Dengan kemiringan seluruh
     * foto, kedua potongannya terpisah. Dengan kemiringan lokal (potongan terpanjang di baris),
     * keduanya tetap satu baris.
     */
    @Test
    fun kemiringanLokal_barisMelengkungDiFotoLurus_tetapSatuBaris() {
        val lurus = (0 until 5).flatMap { i ->
            val atas = 100 + 45 * i
            listOf(baris("KIRI$i", 20, atas, 520, atas + 30), baris("KANAN$i", 800, atas, 1000, atas + 30))
        }
        val miring = listOf(
            barisMiringDi("MIRINGKIRI", 20, 600, 520, 630, -4.0, pusatX = 20, pusatY = 600),
            barisMiringDi("MIRINGKANAN", 800, 600, 1000, 630, -4.0, pusatX = 20, pusatY = 600),
        )
        val hasil = teksPerBaris(susunBarisVisual(lurus + miring))
        assertEquals(0f, perkiraanKemiringan(lurus + miring), 0.01f)
        assertEquals(6, hasil.size)
        assertEquals("MIRINGKIRI MIRINGKANAN", hasil.last())
    }

    /** Baris yang diputar sebesar [derajat] terhadap titik pusat tertentu. */
    private fun barisMiringDi(
        teks: String, kiri: Int, atas: Int, kanan: Int, bawah: Int, derajat: Double, pusatX: Int, pusatY: Int,
    ): BarisOcr {
        val r = Math.toRadians(derajat)
        fun putar(x: Int, y: Int): Titik {
            val dx = (x - pusatX).toDouble()
            val dy = (y - pusatY).toDouble()
            return Titik((pusatX + dx * cos(r) - dy * sin(r)).roundToInt(), (pusatY + dx * sin(r) + dy * cos(r)).roundToInt())
        }
        return baris(teks, 0, 0, 0, 0).copy(
            titikSudut = listOf(putar(kiri, atas), putar(kanan, atas), putar(kanan, bawah), putar(kiri, bawah)),
        )
    }

    @Test
    fun barisTanpaPosisi_diletakkanDiAkhir_tidakDibuang() {
        val tanpaPosisi = BarisOcr("TANPA POSISI", 0.5f, emptyList(), 0, emptyList())
        val masukan = listOf(tanpaPosisi, baris("A", 20, 100, 300, 130))
        assertEquals(listOf("A", "TANPA POSISI"), teksPerBaris(susunBarisVisual(masukan)))
    }

    @Test
    fun tanpaBaris_hasilKosong() {
        assertTrue(susunBarisVisual(emptyList()).isEmpty())
    }

    @Test
    fun rentangTinggiNol_hanyaMasukJikaBersentuhan() {
        assertFalse("jauh di bawah", cukupTumpangTindih(200.0, 200.0, 100.0, 130.0, 50))
        assertTrue("di dalam rentang", cukupTumpangTindih(110.0, 110.0, 100.0, 130.0, 50))
    }

    @Test
    fun keyakinanTerendah_dariPotonganPalingLemah() {
        val bv = BarisVisual(
            listOf(
                baris("A", 20, 100, 300, 130).copy(keyakinan = 0.95f),
                baris("B", 400, 100, 600, 130).copy(keyakinan = 0.40f),
            ),
        )
        assertEquals(0.40f, bv.keyakinanTerendah, 0f)
    }
}
