package com.scannota.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scannota.data.Barang
import com.scannota.data.Pemasok
import com.scannota.data.Pembanding
import com.scannota.data.StatusHarga
import com.scannota.matching.AlasanPenurunan
import com.scannota.matching.KeputusanPencocokan
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Periksa: pengguna memeriksa dan membetulkan hasil baca sebelum nota disimpan. Barang yang
 * meragukan diberi bingkai merah dan harus dibetulkan atau dinyatakan benar dulu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarPeriksa(vm: AplikasiViewModel) {
    val draf = vm.draf
    if (draf == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = vm::kembali) { Text("Belum ada nota yang dibaca. Kembali ke kamera.") }
        }
        return
    }
    val daftarPemasok by vm.daftarPemasok.collectAsState()
    val daftarBarang by vm.daftarBarang.collectAsState()
    val namaBarang = remember(daftarBarang) { daftarBarang.associate { it.id to it.nama } }
    var cariUntuk by remember { mutableStateOf<Long?>(null) }
    val masalah = draf.masalahSimpan()
    // Harga sebelumnya bergantung pada pemasok dan tanggal nota; dimuat ulang jika salah satunya berubah.
    LaunchedEffect(draf.pemasokId, draf.tanggalTerbaca) { vm.muatUlangPembanding() }
    val pembanding = vm.pembanding
    val status = draf.barang.associate { it.kunci to draf.statusHarga(it, pembanding) }
    val berubah = setOf(StatusHarga.NAIK, StatusHarga.TURUN)
    val ditampilkan = draf.barang.withIndex().filter { (_, b) -> !vm.hanyaYangBerubah || status[b.kunci] in berubah }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Periksa nota") },
                navigationIcon = { TextButton(onClick = vm::kembali) { Text("Foto") } },
            )
        },
        bottomBar = { BilahSimpan(masalah, vm.sedangMenyimpan, vm::simpan) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { KartuStatus(status.values.toList(), vm) }
            item { KartuKepala(draf, daftarPemasok, vm) }
            val peringatan = buildList {
                if (vm.fotoGagal.isNotEmpty()) {
                    add("Foto ke-${vm.fotoGagal.joinToString(", ")} gagal dibaca. Kembali ke kamera untuk memotret ulang.")
                }
                if (vm.fotoBukanNota.isNotEmpty()) {
                    add("Foto ke-${vm.fotoBukanNota.joinToString(", ")} tidak tampak seperti nota dan diabaikan.")
                }
                addAll(draf.peringatan())
            }
            if (peringatan.isNotEmpty()) item { KartuPeringatan(peringatan) }
            if (draf.barang.any { !it.barangSelesai && it.pencocokan?.keputusan == KeputusanPencocokan.BARANG_BARU }) {
                item {
                    OutlinedButton(onClick = vm::jadikanSemuaYangBelumAdaBarangBaru, modifier = Modifier.fillMaxWidth()) {
                        Text("Jadikan semua yang belum ada di daftar sebagai barang baru")
                    }
                }
            }
            items(ditampilkan, key = { it.value.kunci }) { (i, b) ->
                KartuBarang(
                    i + 1, b, namaBarang, vm,
                    perluDiperiksa = draf.perluDiperiksa(b),
                    status = status[b.kunci],
                    hargaSebelumnya = draf.hargaSebelumnya(b, pembanding),
                    onCari = { cariUntuk = b.kunci },
                )
            }
            if (vm.hanyaYangBerubah && ditampilkan.isEmpty()) {
                item { Text("Tidak ada barang yang harganya naik atau turun.", Modifier.padding(8.dp)) }
            }
            item {
                OutlinedButton(onClick = { vm.ubahDraf { it.tambahBarangKosong() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tambah barang")
                }
            }
        }
    }

    cariUntuk?.let { kunci ->
        DialogCariBarang(
            daftarBarang,
            onPilih = { id -> vm.ubahBarang(kunci) { it.pilihBarang(id) }; cariUntuk = null },
            onTutup = { cariUntuk = null },
        )
    }
}

/** Warna untuk angka yang dihitung aplikasi, supaya terbedakan dari angka hasil baca. */
private val BIRU = Color(0xFF1565C0)

/** Ringkasan status harga seluruh nota, dan tombol untuk menampilkan hanya barang yang berubah. */
@Composable
private fun KartuStatus(status: List<StatusHarga?>, vm: AplikasiViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            vm.durasiBacaMs?.let { ms ->
                Text("Dibaca dalam ${ms / 1000},${(ms % 1000) / 100} detik", style = MaterialTheme.typography.bodySmall)
            }
            Text("Status harga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Naik ${status.count { it == StatusHarga.NAIK }}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Text("Turun ${status.count { it == StatusHarga.TURUN }}", color = HIJAU, fontWeight = FontWeight.SemiBold)
                Text("Tetap ${status.count { it == StatusHarga.TETAP }}")
            }
            Text(
                "Belum ada pembanding ${status.count { it == StatusHarga.BELUM_ADA_PEMBANDING }} · " +
                    "Barang belum dipilih ${status.count { it == null }}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tampilkan hanya yang naik/turun", modifier = Modifier.weight(1f))
                Switch(checked = vm.hanyaYangBerubah, onCheckedChange = { vm.hanyaYangBerubah = it })
            }
        }
    }
}

@Composable
private fun BarisStatusHarga(status: StatusHarga?, harga: Long?, sebelumnya: Long?) {
    val (teks, warna) = when (status) {
        StatusHarga.NAIK -> "NAIK Rp ${formatRupiah(abs(harga!! - sebelumnya!!))} (sebelumnya Rp ${formatRupiah(sebelumnya)})" to
            MaterialTheme.colorScheme.error
        StatusHarga.TURUN -> "TURUN Rp ${formatRupiah(abs(harga!! - sebelumnya!!))} (sebelumnya Rp ${formatRupiah(sebelumnya)})" to HIJAU
        StatusHarga.TETAP -> "Harga tetap (Rp ${formatRupiah(sebelumnya ?: 0)})" to MaterialTheme.colorScheme.onSurfaceVariant
        StatusHarga.BELUM_ADA_PEMBANDING -> "Belum ada pembanding" to Color.Gray
        null -> "Status harga muncul setelah barang dipilih" to Color.Gray
    }
    Text(teks, color = warna, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun KartuKepala(draf: DrafNota, daftarPemasok: List<Pemasok>, vm: AplikasiViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pemasok", style = MaterialTheme.typography.labelLarge)
            var bukaMenu by remember { mutableStateOf(false) }
            val namaTerpilih = draf.pemasokId?.let { id -> daftarPemasok.firstOrNull { it.id == id }?.nama }
            Box {
                OutlinedButton(onClick = { bukaMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(namaTerpilih ?: "Pemasok baru (ketuk untuk memilih)")
                }
                DropdownMenu(expanded = bukaMenu, onDismissRequest = { bukaMenu = false }) {
                    daftarPemasok.forEach { p ->
                        DropdownMenuItem(text = { Text(p.nama) }, onClick = { bukaMenu = false; vm.pilihPemasok(p.id) })
                    }
                    DropdownMenuItem(
                        text = { Text("Pemasok baru…") },
                        onClick = { bukaMenu = false; vm.pilihPemasok(null, draf.namaPemasokBaru) },
                    )
                }
            }
            if (draf.pemasokId == null) {
                OutlinedTextField(
                    value = draf.namaPemasokBaru,
                    onValueChange = { v -> vm.ubahDraf { it.copy(namaPemasokBaru = v) } },
                    label = { Text("Nama pemasok baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = draf.nomorNota,
                onValueChange = { v -> vm.ubahDraf { it.copy(nomorNota = v) } },
                label = { Text("Nomor nota") },
                supportingText = { Text("Cocokkan dengan nota kertas. Nomor sering salah baca, misalnya UTM terbaca UTH.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draf.tanggal,
                    onValueChange = { v -> vm.ubahDraf { it.copy(tanggal = v) } },
                    label = { Text("Tanggal (dd-mm-yyyy)") },
                    isError = draf.tanggalTerbaca == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draf.total,
                    onValueChange = { v -> vm.ubahDraf { it.copy(total = v) } },
                    label = { Text("Total nota") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Jumlah subtotal: Rp ${formatRupiah(draf.jumlahSubtotal)}")
            if (draf.lengkap) {
                Text("Lengkap: jumlah subtotal sama dengan total nota.", color = HIJAU, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Belum lengkap.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun KartuPeringatan(isi: List<String>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            isi.forEach { Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun KartuBarang(
    nomor: Int,
    b: BarangDraf,
    namaBarang: Map<Long, String>,
    vm: AplikasiViewModel,
    perluDiperiksa: Boolean,
    status: StatusHarga?,
    hargaSebelumnya: Long?,
    onCari: () -> Unit,
) {
    val merah = MaterialTheme.colorScheme.error
    OutlinedCard(
        Modifier.fillMaxWidth(),
        border = BorderStroke(if (perluDiperiksa) 2.dp else 1.dp, if (perluDiperiksa) merah else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (b.manual) {
                OutlinedTextField(
                    value = b.teksOcr,
                    onValueChange = { v -> vm.ubahBarang(b.kunci) { it.copy(teksOcr = v) } },
                    label = { Text("$nomor. Nama barang di nota") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = vm::cocokkanUlang, enabled = b.teksOcr.isNotBlank()) { Text("Cari barang yang mirip") }
            } else {
                Text("$nomor. ${b.teksOcr}", fontWeight = FontWeight.SemiBold)
            }

            BarisStatusHarga(status, b.hargaAngka, hargaSebelumnya)
            BagianPilihanBarang(b, namaBarang, vm, onCari)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KolomAngka("Harga", b.harga, Modifier.weight(1.3f)) { v -> vm.ubahBarang(b.kunci) { it.ubahAngka(harga = v) } }
                KolomAngka("Qty", b.qty, Modifier.weight(0.8f)) { v -> vm.ubahBarang(b.kunci) { it.ubahAngka(qty = v) } }
                OutlinedTextField(
                    value = b.satuan,
                    onValueChange = { v -> vm.ubahBarang(b.kunci) { it.ubahAngka(satuan = v) } },
                    label = { Text("Satuan") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(0.9f),
                )
            }
            KolomAngka("Subtotal", b.subtotal, Modifier.fillMaxWidth()) { v -> vm.ubahBarang(b.kunci) { it.ubahAngka(subtotal = v) } }

            val dihitung = b.dihitung
            if (dihitung != null) {
                Text(
                    "Dihitung aplikasi: ${dihitung.keterangan} (terbaca: ${b.angkaTerbaca})",
                    color = BIRU,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (b.satuanDariRiwayat) {
                Text("Satuan tidak terbaca; diisi dari pembelian terakhir.", color = BIRU, style = MaterialTheme.typography.bodySmall)
            }
            if (b.meragukan) {
                Text(
                    "Perlu diperiksa: " + b.alasanRagu.joinToString("; "),
                    color = if (b.sudahDiperiksa) MaterialTheme.colorScheme.onSurfaceVariant else merah,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (b.sudahDiperiksa) {
                Text("Sudah diperiksa", color = HIJAU, style = MaterialTheme.typography.bodySmall)
            } else if (perluDiperiksa && b.bolehDinyatakanBenar) {
                if (!b.meragukan) {
                    Text(
                        "Nota belum lengkap, jadi angka hitungan aplikasi perlu dicek sekali.",
                        color = merah,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { vm.ubahBarang(b.kunci) { it.copy(sudahDiperiksa = true) } }) {
                    Text("Angka sudah sesuai nota kertas")
                }
            }
            TextButton(onClick = { vm.ubahDraf { it.hapusBarang(b.kunci) } }) { Text("Hapus baris ini", color = merah) }
        }
    }
}

@Composable
private fun BagianPilihanBarang(b: BarangDraf, namaBarang: Map<Long, String>, vm: AplikasiViewModel, onCari: () -> Unit) {
    val terpilih = b.barangTerpilihId
    val namaBaru = b.namaBaru
    when {
        terpilih != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Barang: ${namaBarang[terpilih] ?: "…"}" + if (b.dipilihPengguna) "" else " (otomatis)",
                modifier = Modifier.weight(1f),
                color = HIJAU,
            )
            TextButton(onClick = { vm.ubahBarang(b.kunci) { it.batalkanPilihan() } }) { Text("Ganti") }
        }
        namaBaru != null -> Column {
            OutlinedTextField(
                value = namaBaru,
                onValueChange = { v -> vm.ubahBarang(b.kunci) { it.copy(namaBaru = v) } },
                label = { Text("Nama barang baru") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { vm.ubahBarang(b.kunci) { it.batalkanPilihan() } }) { Text("Batal, pilih barang lain") }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val p = b.pencocokan
            Text(
                when {
                    p == null -> "Pilih barang:"
                    p.diturunkanKarena == AlasanPenurunan.ANGKA_BERBEDA -> "Mirip, tetapi angkanya berbeda (misalnya ukuran). Pilih yang sesuai:"
                    p.diturunkanKarena == AlasanPenurunan.SKOR_KEMBAR -> "Ada dua barang yang sama miripnya. Pilih yang sesuai:"
                    p.keputusan == KeputusanPencocokan.KONFIRMASI -> "Pilih barang yang sesuai:"
                    else -> "Belum ada di daftar barang."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            p?.kandidatTeratas?.forEach { k ->
                OutlinedButton(onClick = { vm.ubahBarang(b.kunci) { it.pilihBarang(k.barangId) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("${namaBarang[k.barangId] ?: k.teks}  (${(k.skor.nilai * 100).roundToInt()}% mirip)")
                }
            }
            Row {
                TextButton(onClick = { vm.ubahBarang(b.kunci) { it.jadikanBarangBaru() } }, enabled = b.teksOcr.isNotBlank()) {
                    Text("Barang baru")
                }
                TextButton(onClick = onCari) { Text("Cari di daftar barang") }
            }
        }
    }
}

@Composable
private fun KolomAngka(label: String, nilai: String, modifier: Modifier, onUbah: (String) -> Unit) {
    OutlinedTextField(
        value = nilai,
        onValueChange = onUbah,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun BilahSimpan(masalah: List<String>, sedangMenyimpan: Boolean, onSimpan: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            masalah.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSimpan, enabled = masalah.isEmpty() && !sedangMenyimpan, modifier = Modifier.fillMaxWidth()) {
                Text(if (sedangMenyimpan) "Menyimpan…" else "Simpan nota")
            }
        }
    }
}

@Composable
private fun DialogCariBarang(daftarBarang: List<Barang>, onPilih: (Long) -> Unit, onTutup: () -> Unit) {
    var kata by remember { mutableStateOf("") }
    val hasil = remember(kata, daftarBarang) {
        val k = kata.trim()
        if (k.isEmpty()) daftarBarang else daftarBarang.filter { it.nama.contains(k, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onTutup,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onTutup) { Text("Tutup") } },
        title = { Text("Cari barang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = kata, onValueChange = { kata = it }, label = { Text("Nama barang") }, singleLine = true)
                if (daftarBarang.isEmpty()) Text("Daftar barang masih kosong.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(hasil, key = { it.id }) { barang ->
                        TextButton(onClick = { onPilih(barang.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(barang.nama, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
    )
}
