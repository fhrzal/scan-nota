package com.scannota.preprocessing

import android.graphics.Bitmap
import android.os.SystemClock
import com.scannota.ocr.Kotak
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Langkah pengolahan citra yang dinyalakan. Grayscale selalu dilakukan. Urutannya tetap:
 * rotasi EXIF → potong ke kertas → grayscale → luruskan → median → ambang adaptif.
 *
 * Setiap langkah bisa dinyalakan sendiri, supaya pengaruh masing-masing terhadap hasil OCR bisa
 * diukur terpisah (evaluasi sebelum dan sesudah).
 */
data class LangkahPraolah(
    val potongKertas: Boolean = false,
    val luruskan: Boolean = false,
    val haluskan: Boolean = false,
    val ambangAdaptif: Boolean = false,
) {
    companion object {
        /** Varian yang dibandingkan dalam evaluasi. Nama varian dipakai di nama berkas hasil ekspor. */
        val VARIAN_EVALUASI: Map<String, LangkahPraolah> = linkedMapOf(
            "abu" to LangkahPraolah(),
            "abu_potong" to LangkahPraolah(potongKertas = true),
            "abu_potong_lurus" to LangkahPraolah(potongKertas = true, luruskan = true),
            "abu_potong_lurus_median" to LangkahPraolah(potongKertas = true, luruskan = true, haluskan = true),
            "abu_potong_lurus_median_ambang" to
                LangkahPraolah(potongKertas = true, luruskan = true, haluskan = true, ambangAdaptif = true),
        )
    }
}

/**
 * @param bitmap gambar hasil, sudah tegak; OCR membacanya dengan rotasi 0
 * @param potongan wilayah kertas dalam koordinat foto tegak, atau null jika tidak dipotong. Bersama
 *   [sudutKemiringan] dan [diluruskan], ini dibutuhkan ui/ untuk memetakan hasil OCR ke foto asli.
 * @param sudutKemiringan perkiraan kemiringan (positif = ujung kanan lebih rendah), null jika
 *   langkah luruskan tidak dinyalakan
 * @param diluruskan true jika gambar benar-benar diputar (lihat perluDiluruskan)
 */
data class HasilPraolah(
    val bitmap: Bitmap,
    val potongan: Kotak?,
    val sudutKemiringan: Double?,
    val diluruskan: Boolean,
    val waktuMs: Long,
)

/** Pengolahan citra dengan OpenCV. Hanya berjalan di HP, karena pustaka native OpenCV untuk Android. */
object PengolahCitra {

    /** Deteksi kertas dan sudut dikerjakan pada salinan kecil supaya cepat; hasilnya diskalakan balik. */
    private const val LEBAR_DETEKSI = 600

    @Volatile
    private var siap = false

    /** Memuat pustaka native OpenCV yang ikut di dalam APK (tanpa unduhan). */
    fun pastikanSiap() {
        if (siap) return
        check(OpenCVLoader.initLocal()) { "OpenCV gagal dimuat" }
        siap = true
    }

    /**
     * Mengolah satu foto.
     *
     * @param rotasiDerajat putaran searah jarum jam agar foto tegak (dari EXIF atau kamera)
     */
    fun praolah(bitmap: Bitmap, rotasiDerajat: Int, langkah: LangkahPraolah): HasilPraolah {
        pastikanSiap()
        val mulai = SystemClock.elapsedRealtime()

        val warna = Mat()
        Utils.bitmapToMat(bitmap, warna) // RGBA
        val tegak = putarTegak(warna, rotasiDerajat)

        val potongan = if (langkah.potongKertas) cariKertas(tegak) else null
        val wilayah = potongan?.let { tegak.submat(Rect(it.kiri, it.atas, it.lebar, it.tinggi)) } ?: tegak
        var abu = Mat()
        Imgproc.cvtColor(wilayah, abu, Imgproc.COLOR_RGBA2GRAY)
        if (wilayah !== tegak) wilayah.release()
        tegak.release()

        var sudut: Double? = null
        var diluruskan = false
        if (langkah.luruskan) {
            val s = perkirakanKemiringan(abu)
            sudut = s
            if (perluDiluruskan(s)) {
                abu = putarDenganKanvasLuas(abu, s)
                diluruskan = true
            }
        }
        if (langkah.haluskan) abu = ganti(abu) { hasil -> Imgproc.medianBlur(abu, hasil, 3) }
        if (langkah.ambangAdaptif) {
            val blok = ukuranBlokAmbang(abu.cols())
            abu = ganti(abu) { hasil ->
                Imgproc.adaptiveThreshold(
                    abu, hasil, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, blok, SELISIH_AMBANG_ADAPTIF,
                )
            }
        }

        val keluaran = Bitmap.createBitmap(abu.cols(), abu.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(abu, keluaran)
        abu.release()
        return HasilPraolah(keluaran, potongan, sudut, diluruskan, SystemClock.elapsedRealtime() - mulai)
    }

    /** Menjalankan satu operasi ke Mat baru, lalu melepas Mat lama (memori native tidak dibersihkan GC). */
    private inline fun ganti(lama: Mat, operasi: (Mat) -> Unit): Mat {
        val baru = Mat()
        operasi(baru)
        lama.release()
        return baru
    }

    private fun putarTegak(rgba: Mat, rotasiDerajat: Int): Mat {
        val kode = when (rotasiDerajat) {
            0 -> return rgba
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> throw IllegalArgumentException("rotasi harus 0, 90, 180, atau 270")
        }
        return ganti(rgba) { hasil -> Core.rotate(rgba, hasil, kode) }
    }

    private fun kecilkan(sumber: Mat): Pair<Mat, Double> {
        val skala = if (sumber.cols() > LEBAR_DETEKSI) LEBAR_DETEKSI.toDouble() / sumber.cols() else 1.0
        val kecil = Mat()
        Imgproc.resize(
            sumber, kecil,
            Size((sumber.cols() * skala).roundToInt().toDouble(), (sumber.rows() * skala).roundToInt().toDouble()),
            0.0, 0.0, Imgproc.INTER_AREA,
        )
        return kecil to skala
    }

    /**
     * Mencari kertas nota.
     *
     * Kertas nota putih dan pucat (saturasi rendah, terang), sedangkan latar foto biasanya
     * berwarna. Karena nota berbentuk pita tegak, kertas dicari per kolom: kolom yang minimal 50%
     * pikselnya putih-pucat ([AMBANG_PROPORSI_KERTAS]). Deretan kolom terpanjang adalah lebar
     * kertas. Mencari wilayah terang terbesar tidak dipakai, karena pada foto 005493_a motif bunga
     * putih di kain menempel pada kertas dan ikut terambil.
     *
     * "Terang" ditentukan dengan ambang Otsu pada kanal kecerahan, sehingga menyesuaikan
     * pencahayaan setiap foto.
     *
     * @return kotak kertas (dengan tepi 1% lebar), atau null jika tidak ditemukan kertas selebar
     *   minimal 10% foto
     */
    private fun cariKertas(rgba: Mat): Kotak? {
        val (kecil, skala) = kecilkan(rgba)
        val hsv = Mat()
        Imgproc.cvtColor(kecil, hsv, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
        val v = Mat()
        Core.extractChannel(hsv, v, 2)
        val buangan = Mat()
        val ambangTerang = Imgproc.threshold(v, buangan, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val masker = Mat()
        Core.inRange(
            hsv, Scalar(0.0, 0.0, ambangTerang), Scalar(255.0, SATURASI_MAKS_KERTAS - 1.0, 255.0), masker,
        )

        val lebar = masker.cols()
        val tinggi = masker.rows()
        val kolom = proporsi(masker, sumbu = 0, jumlah = lebar)
        val rentangKolom = deretanTerpanjang(BooleanArray(lebar) { kolom[it] >= AMBANG_PROPORSI_KERTAS })
        var hasil: Kotak? = null
        if (rentangKolom != null && rentangKolom.count() >= 0.10 * lebar) {
            val pita = masker.submat(0, tinggi, rentangKolom.first, rentangKolom.last + 1)
            val baris = proporsi(pita, sumbu = 1, jumlah = tinggi)
            pita.release()
            val rentangBaris = rentangLuar(BooleanArray(tinggi) { baris[it] >= AMBANG_PROPORSI_KERTAS })
            if (rentangBaris != null) {
                val tepi = (0.01 * lebar).roundToInt()
                val kiri = ((rentangKolom.first - tepi).coerceAtLeast(0) / skala).roundToInt()
                val atas = ((rentangBaris.first - tepi).coerceAtLeast(0) / skala).roundToInt()
                val kanan = ((rentangKolom.last + 1 + tepi).coerceAtMost(lebar) / skala).roundToInt().coerceAtMost(rgba.cols())
                val bawah = ((rentangBaris.last + 1 + tepi).coerceAtMost(tinggi) / skala).roundToInt().coerceAtMost(rgba.rows())
                hasil = Kotak(kiri, atas, kanan, bawah)
            }
        }
        listOf(kecil, hsv, v, buangan, masker).forEach { it.release() }
        return hasil
    }

    /** Proporsi piksel bernilai 255 per kolom (sumbu 0) atau per baris (sumbu 1), skala 0–1. */
    private fun proporsi(masker: Mat, sumbu: Int, jumlah: Int): FloatArray {
        val rata = Mat()
        Core.reduce(masker, rata, sumbu, Core.REDUCE_AVG, CvType.CV_32F)
        val nilai = FloatArray(jumlah)
        rata.get(0, 0, nilai)
        rata.release()
        for (i in nilai.indices) nilai[i] /= 255f
        return nilai
    }

    /**
     * Memperkirakan kemiringan dengan profil proyeksi: gambar diputar ke beberapa sudut, dan
     * dipilih sudut yang membuat baris teks paling tegas ([ketajamanProfil], [cariSudutTerbaik]).
     * Sudut positif berarti ujung kanan lebih rendah, sama seperti di ocr/.
     */
    private fun perkirakanKemiringan(abu: Mat): Double {
        val (kecil, _) = kecilkan(abu)
        val biner = Mat()
        // Tinta = 1, kertas = 0. Otsu memilih ambang otomatis dari histogram foto.
        Imgproc.threshold(kecil, biner, 0.0, 1.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
        val pusat = Point(biner.cols() / 2.0, biner.rows() / 2.0)
        val diputar = Mat()
        val profil = Mat()
        val nilaiProfil = FloatArray(biner.rows())
        val sudut = cariSudutTerbaik { s ->
            // getRotationMatrix2D: sudut positif memutar berlawanan arah jarum jam, yang
            // meluruskan teks yang miring searah jarum jam sebesar s.
            val m = Imgproc.getRotationMatrix2D(pusat, s, 1.0)
            Imgproc.warpAffine(biner, diputar, m, biner.size(), Imgproc.INTER_NEAREST)
            m.release()
            Core.reduce(diputar, profil, 1, Core.REDUCE_SUM, CvType.CV_32F)
            profil.get(0, 0, nilaiProfil)
            ketajamanProfil(DoubleArray(nilaiProfil.size) { nilaiProfil[it].toDouble() })
        }
        listOf(kecil, biner, diputar, profil).forEach { it.release() }
        return sudut
    }

    /** Memutar gambar abu-abu tanpa memotong sudutnya; daerah baru diisi putih seperti kertas. */
    private fun putarDenganKanvasLuas(abu: Mat, sudut: Double): Mat {
        val rad = Math.toRadians(sudut)
        val w = abu.cols()
        val h = abu.rows()
        val lebarBaru = (w * abs(cos(rad)) + h * abs(sin(rad))).roundToInt()
        val tinggiBaru = (w * abs(sin(rad)) + h * abs(cos(rad))).roundToInt()
        val m = Imgproc.getRotationMatrix2D(Point(w / 2.0, h / 2.0), sudut, 1.0)
        m.put(0, 2, m.get(0, 2)[0] + (lebarBaru - w) / 2.0)
        m.put(1, 2, m.get(1, 2)[0] + (tinggiBaru - h) / 2.0)
        val hasil = ganti(abu) { baru ->
            Imgproc.warpAffine(
                abu, baru, m, Size(lebarBaru.toDouble(), tinggiBaru.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0),
            )
        }
        m.release()
        return hasil
    }
}
