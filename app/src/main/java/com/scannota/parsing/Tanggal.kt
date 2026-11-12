package com.scannota.parsing

/**
 * Tanggal nota.
 *
 * Dibuat sendiri, bukan java.time.LocalDate, karena java.time baru tersedia di Android 8 (API 26),
 * sedangkan aplikasi ini mendukung mulai Android 7 (minSdk 24). Menambah pustaka pengganti
 * (desugaring) tidak sepadan untuk satu konversi.
 *
 * Tahun dibatasi 2000–2099. Nota pemasok tidak mungkin di luar rentang itu, jadi angka di luar
 * rentang pasti salah baca OCR.
 */
data class Tanggal(val hari: Int, val bulan: Int, val tahun: Int) {
    init {
        require(tahun in 2000..2099) { "tahun di luar 2000–2099" }
        require(bulan in 1..12) { "bulan harus 1–12" }
        require(hari in 1..jumlahHari(bulan, tahun)) { "tanggal tidak ada di kalender" }
    }

    /**
     * Jumlah hari sejak 1 Januari 1970, sesuai kolom tanggal_nota di database.
     *
     * Memakai rumus "days from civil" (Howard Hinnant): tahun dimulai dari Maret, sehingga hari
     * kabisat 29 Februari selalu jatuh di akhir tahun hitungan dan rumusnya tidak perlu kasus
     * khusus. Kebenarannya diuji terhadap java.time di test laptop untuk setiap hari 2000–2099.
     */
    fun keEpochHari(): Long {
        val y = (if (bulan <= 2) tahun - 1 else tahun).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val tahunDalamEra = y - era * 400
        val bulanDariMaret = (bulan + 9) % 12
        val hariDalamTahun = (153 * bulanDariMaret + 2) / 5 + hari - 1
        val hariDalamEra = tahunDalamEra * 365 + tahunDalamEra / 4 - tahunDalamEra / 100 + hariDalamTahun
        return era * 146097 + hariDalamEra - 719468
    }

    companion object {
        /**
         * Kebalikan [keEpochHari], untuk menampilkan tanggal dari database. Rumus "civil from days"
         * (Howard Hinnant), diuji terhadap java.time untuk setiap hari 2000–2099.
         */
        fun dariEpochHari(epochHari: Long): Tanggal {
            val z = epochHari + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val hariDalamEra = z - era * 146097
            val tahunDalamEra = (hariDalamEra - hariDalamEra / 1460 + hariDalamEra / 36524 - hariDalamEra / 146096) / 365
            val hariDalamTahun = hariDalamEra - (365 * tahunDalamEra + tahunDalamEra / 4 - tahunDalamEra / 100)
            val bulanDariMaret = (5 * hariDalamTahun + 2) / 153
            val hari = hariDalamTahun - (153 * bulanDariMaret + 2) / 5 + 1
            val bulan = if (bulanDariMaret < 10) bulanDariMaret + 3 else bulanDariMaret - 9
            val tahun = tahunDalamEra + era * 400 + if (bulan <= 2) 1 else 0
            return Tanggal(hari.toInt(), bulan.toInt(), tahun.toInt())
        }
    }

    /** Ditulis tanpa String.format, yang hasilnya mengikuti bahasa HP. */
    override fun toString(): String =
        "${hari.toString().padStart(2, '0')}-${bulan.toString().padStart(2, '0')}-$tahun"
}

private fun kabisat(tahun: Int) = (tahun % 4 == 0 && tahun % 100 != 0) || tahun % 400 == 0

private fun jumlahHari(bulan: Int, tahun: Int): Int = when (bulan) {
    2 -> if (kabisat(tahun)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private val POLA_TANGGAL = Regex("""(?<!\d)(\d{2})-(\d{2})-(\d{4})(?!\d)""")

/**
 * Mencari tanggal berformat dd-mm-yyyy di dalam teks. Huruf mirip angka diganti dulu, karena di
 * nota asli "16-07-2026" pernah terbaca "l6-07-2026".
 *
 * @return tanggal pertama yang sah, atau null
 */
fun bacaTanggal(teks: String): Tanggal? {
    for (cocok in POLA_TANGGAL.findAll(gantiHurufMiripAngka(teks))) {
        val (h, b, t) = cocok.destructured
        val tanggal = runCatching { Tanggal(h.toInt(), b.toInt(), t.toInt()) }.getOrNull()
        if (tanggal != null) return tanggal
    }
    return null
}
