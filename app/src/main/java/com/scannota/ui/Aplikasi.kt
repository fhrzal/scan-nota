package com.scannota.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Hijau untuk keadaan baik (lengkap, harga turun, sudah diperiksa). Merah memakai warna error tema. */
val HIJAU = Color(0xFF2E7D32)

/**
 * Akar tampilan. Layar dipilih dari [AplikasiViewModel.layar]; tombol kembali HP memanggil
 * [AplikasiViewModel.kembali]. Pesan singkat ditampilkan sebagai snackbar di bagian bawah.
 */
@Composable
fun AplikasiScanNota(vm: AplikasiViewModel = viewModel()) {
    MaterialTheme {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.pesan) {
            val p = vm.pesan ?: return@LaunchedEffect
            snackbar.showSnackbar(p)
            vm.pesan = null
        }
        BackHandler(enabled = vm.layar != Layar.Beranda) { vm.kembali() }

        Box(Modifier.fillMaxSize()) {
            when (val layar = vm.layar) {
                Layar.Beranda -> LayarBeranda(vm)
                Layar.Kamera -> LayarKamera(vm)
                Layar.Periksa -> LayarPeriksa(vm)
                is Layar.Detail -> LayarDetail(vm, layar.ringkasan)
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp),
            )
        }
    }
}
