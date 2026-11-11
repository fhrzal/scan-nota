// Berkas build tingkat proyek. Plugin hanya dideklarasikan di sini (apply false),
// lalu dipakai oleh modul :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
