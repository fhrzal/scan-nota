package com.scannota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Titik masuk aplikasi.
 *
 * Untuk sekarang hanya menampilkan teks penanda, agar kerangka proyek bisa
 * dipasang di HP dan dipastikan semua pustaka (Compose, Room, ML Kit, OpenCV,
 * CameraX) terbaca. Layar sebenarnya dibuat di modul ui/.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.placeholder_beranda))
                    }
                }
            }
        }
    }
}
