package com.scannota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scannota.ui.AplikasiScanNota

/** Titik masuk aplikasi. Semua layar ada di paket ui/. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AplikasiScanNota() }
    }
}
