package com.scannota.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scannota.merge.MAKS_FOTO_PER_NOTA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Kamera: memotret nota dari atas ke bawah, maksimal 20 foto, lalu membacanya. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarKamera(vm: AplikasiViewModel) {
    val konteks = LocalContext.current
    var izinKamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(konteks, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val mintaIzin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { izinKamera = it }
    LaunchedEffect(Unit) { if (!izinKamera) mintaIzin.launch(Manifest.permission.CAMERA) }

    val penangkap = remember { buatPenangkapFoto() }
    var sedangMemotret by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foto nota (${vm.foto.size}/$MAKS_FOTO_PER_NOTA)") },
                navigationIcon = { TextButton(onClick = vm::kembali) { Text("Kembali") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                if (izinKamera) {
                    PratinjauKamera(penangkap, Modifier.fillMaxSize()) { vm.pesan = it }
                } else {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Aplikasi butuh izin kamera untuk memotret nota.", color = Color.White)
                        Button(onClick = { mintaIzin.launch(Manifest.permission.CAMERA) }) { Text("Beri izin kamera") }
                    }
                }
            }
            Text(
                "Potret dari bagian atas nota ke bawah. Jika nota panjang, sisakan 2–3 barang yang sama di foto berikutnya.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (vm.foto.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(vm.foto, key = { _, f -> f.name }) { i, f -> GambarKecil(f, i + 1) { vm.hapusFoto(i) } }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val berkas = vm.berkasFotoBaru()
                        sedangMemotret = true
                        penangkap.takePicture(
                            ImageCapture.OutputFileOptions.Builder(berkas).build(),
                            ContextCompat.getMainExecutor(konteks),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(hasil: ImageCapture.OutputFileResults) {
                                    sedangMemotret = false
                                    vm.fotoTersimpan(berkas)
                                }

                                override fun onError(galat: ImageCaptureException) {
                                    sedangMemotret = false
                                    vm.pesan = "Gagal memotret: ${galat.message}"
                                }
                            },
                        )
                    },
                    enabled = izinKamera && vm.bolehTambahFoto && !sedangMemotret,
                    modifier = Modifier.weight(1f),
                ) { Text(if (sedangMemotret) "Memotret…" else "Ambil foto") }
                Button(onClick = vm::prosesFoto, enabled = vm.foto.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Baca nota")
                }
            }
        }
    }

    when (val status = vm.proses) {
        is StatusProses.Berjalan -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Membaca nota") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { status.selesai / status.jumlah.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Foto ${minOf(status.selesai + 1, status.jumlah)} dari ${status.jumlah}…")
                }
            },
        )
        is StatusProses.Gagal -> AlertDialog(
            onDismissRequest = vm::tutupPesanProses,
            confirmButton = {
                TextButton(onClick = { vm.tutupPesanProses(); vm.prosesFoto() }) { Text("Coba lagi") }
            },
            dismissButton = { TextButton(onClick = vm::tutupPesanProses) { Text("Tutup") } },
            title = { Text("Gagal membaca") },
            text = { Text(status.pesan) },
        )
        StatusProses.Diam -> Unit
    }
}

/**
 * Pengaturan kamera: rasio 16:9 dengan sisi panjang sekitar 4080 piksel, sama dengan foto nota
 * yang dipakai saat pengujian (2296 × 4080). Resolusi tertinggi kamera HP (bisa 50 MP) tidak dipakai,
 * karena terlalu berat untuk memori dan tidak membuat OCR lebih akurat.
 */
private fun buatPenangkapFoto(): ImageCapture {
    val pemilihResolusi = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(Size(4080, 2296), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
        )
        .build()
    return ImageCapture.Builder()
        .setResolutionSelector(pemilihResolusi)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()
}

@Composable
private fun PratinjauKamera(penangkap: ImageCapture, modifier: Modifier, onGagal: (String) -> Unit) {
    val konteks = LocalContext.current
    val pemilikSiklus = LocalLifecycleOwner.current
    val tampilan = remember { PreviewView(konteks).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    DisposableEffect(pemilikSiklus) {
        val masaDepan = ProcessCameraProvider.getInstance(konteks)
        var penyedia: ProcessCameraProvider? = null
        masaDepan.addListener({
            try {
                val p = masaDepan.get()
                penyedia = p
                val pratinjau = Preview.Builder().build().also { it.setSurfaceProvider(tampilan.surfaceProvider) }
                p.unbindAll()
                p.bindToLifecycle(pemilikSiklus, CameraSelector.DEFAULT_BACK_CAMERA, pratinjau, penangkap)
            } catch (e: Exception) {
                onGagal("Kamera tidak bisa dibuka: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(konteks))
        onDispose { penyedia?.unbindAll() }
    }
    AndroidView(factory = { tampilan }, modifier = modifier)
}

@Composable
private fun GambarKecil(berkas: File, nomor: Int, onHapus: () -> Unit) {
    val gambar by produceState<ImageBitmap?>(null, berkas) {
        value = withContext(Dispatchers.IO) { muatGambarKecil(berkas, 240) }
    }
    Box(Modifier.size(width = 72.dp, height = 112.dp).background(Color.DarkGray)) {
        gambar?.let { Image(it, "Foto $nomor", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Text(
            "$nomor",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp),
        )
        TextButton(
            onClick = onHapus,
            modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.6f)),
        ) { Text("Hapus", color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}

/** Gambar kecil untuk daftar foto: dikecilkan saat dibaca supaya hemat memori, lalu ditegakkan. */
private fun muatGambarKecil(berkas: File, sisiPanjang: Int): ImageBitmap? {
    val ukuran = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(berkas.path, ukuran)
    var skala = 1
    while (maxOf(ukuran.outWidth, ukuran.outHeight) / skala > sisiPanjang * 2) skala *= 2
    val kecil = BitmapFactory.decodeFile(berkas.path, BitmapFactory.Options().apply { inSampleSize = skala }) ?: return null
    val rotasi = rotasiExif(berkas)
    val tegak: Bitmap = if (rotasi == 0) {
        kecil
    } else {
        Bitmap.createBitmap(kecil, 0, 0, kecil.width, kecil.height, Matrix().apply { postRotate(rotasi.toFloat()) }, true)
    }
    return tegak.asImageBitmap()
}
