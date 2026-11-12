package com.scannota.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scannota.data.BarisNotaDenganHarga
import com.scannota.data.RingkasanNota
import com.scannota.data.StatusHarga
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Detail nota: setiap barang beserta status harganya dibanding pembelian sebelumnya dari pemasok
 * yang sama dengan satuan yang sama. Status dihitung oleh query database, tidak disimpan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarDetail(vm: AplikasiViewModel, r: RingkasanNota) {
    val baris by remember(r.id) { vm.barisNota(r.id) }.collectAsState(initial = emptyList())
    var konfirmasiHapus by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r.namaPemasok) },
                navigationIcon = { TextButton(onClick = vm::kembali) { Text("Kembali") } },
                actions = { TextButton(onClick = { konfirmasiHapus = true }) { Text("Hapus") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("No. ${r.nomorNota ?: "(tanpa nomor)"}")
                        Text("Tanggal ${formatTanggal(r.tanggalNota)}")
                        Text("Total nota: ${r.totalNota?.let { "Rp " + formatRupiah(it) } ?: "tidak terbaca"}")
                        Text("Jumlah subtotal: Rp ${formatRupiah(r.jumlahSubtotal)}")
                        if (r.lengkap()) {
                            Text("Lengkap", color = HIJAU, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Belum lengkap", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            items(baris, key = { it.detailId }) { d ->
                BarisDetail(d)
                HorizontalDivider()
            }
        }
    }

    if (konfirmasiHapus) {
        AlertDialog(
            onDismissRequest = { konfirmasiHapus = false },
            confirmButton = {
                TextButton(onClick = { konfirmasiHapus = false; vm.hapusNota(r.id) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { konfirmasiHapus = false }) { Text("Batal") } },
            title = { Text("Hapus nota ini?") },
            text = { Text("Harga dari nota ini tidak lagi dipakai sebagai pembanding untuk nota lain.") },
        )
    }
}

@Composable
private fun BarisDetail(d: BarisNotaDenganHarga) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${d.urutan}. ${d.namaBarang}", fontWeight = FontWeight.SemiBold)
        Text(
            "${formatQty((d.qty * 100).roundToLong())} ${d.satuan.orEmpty()} × Rp ${formatRupiah(d.hargaSatuan)} = Rp ${formatRupiah(d.subtotal)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val sebelumnya = d.hargaSebelumnya
        val (teks, warna) = when (d.status()) {
            StatusHarga.NAIK -> "Harga NAIK Rp ${formatRupiah(abs(d.hargaSatuan - sebelumnya!!))} (sebelumnya Rp ${formatRupiah(sebelumnya)})" to
                MaterialTheme.colorScheme.error
            StatusHarga.TURUN -> "Harga TURUN Rp ${formatRupiah(abs(d.hargaSatuan - sebelumnya!!))} (sebelumnya Rp ${formatRupiah(sebelumnya)})" to
                HIJAU
            StatusHarga.TETAP -> "Harga tetap" to MaterialTheme.colorScheme.onSurfaceVariant
            StatusHarga.BELUM_ADA_PEMBANDING -> "Belum ada pembanding" to Color.Gray
        }
        Text(teks, color = warna, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
