package com.scannota.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scannota.data.RingkasanNota

/** Beranda: daftar nota yang sudah disimpan, terbaru di atas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarBeranda(vm: AplikasiViewModel) {
    val daftar by vm.ringkasanNota.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pemindai Nota") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = vm::mulaiPindai) { Text("Pindai nota") }
        },
    ) { padding ->
        if (daftar.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada nota tersimpan.\nKetuk \"Pindai nota\" untuk memotret nota dari pemasok.",
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(daftar, key = { it.id }) { r -> KartuNota(r) { vm.bukaDetail(r) } }
            }
        }
    }
}

@Composable
private fun KartuNota(r: RingkasanNota, onKetuk: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onKetuk)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.namaPemasok, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("No. ${r.nomorNota ?: "(tanpa nomor)"} · ${formatTanggal(r.tanggalNota)}")
            Text("Total ${r.totalNota?.let { "Rp " + formatRupiah(it) } ?: "tidak terbaca"} · ${r.jumlahBaris} barang")
            if (r.lengkap()) {
                Text("Lengkap", color = HIJAU, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Belum lengkap", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
