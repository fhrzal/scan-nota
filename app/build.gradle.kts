import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // dipakai Room untuk membuat kode DAO saat kompilasi
}

android {
    namespace = "com.scannota"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scannota"
        // minSdk 24 = Android 7.0. Sudah di atas syarat minimum ML Kit, CameraX, dan OpenCV (21).
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Hanya arsitektur ARM yang dipakai HP sungguhan. Tanpa filter ini, library native
        // OpenCV dan ML Kit untuk 4 arsitektur membuat APK sekitar 189 MB.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            // Laptop pengembang ber-CPU Intel (x86_64), jadi emulatornya juga x86_64.
            // x86_64 hanya ditambahkan ke build debug; APK release untuk HP toko tetap ARM saja.
            ndk {
                abiFilters += "x86_64"
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Foto nota asli untuk alat ekspor OCR (alat/ekspor_ocr.sh). Folder ini tidak masuk git,
        // dan hanya dikemas ke APK test, tidak pernah ke APK aplikasi.
        getByName("androidTest") {
            assets.srcDir(rootProject.file("data_uji/foto"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room mengekspor struktur tabel (JSON) ke app/schemas/, bisa dipakai sebagai dasar ERD.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Dasar Android + Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // data/ : basis data lokal
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ui/ : kamera
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ocr/ : ML Kit, model terlatih tanpa pelatihan ulang
    implementation(libs.mlkit.text.recognition)

    // preprocessing/ : OpenCV
    implementation(libs.opencv)

    // Unit test untuk fungsi murni (Levenshtein, parsing, merge)
    testImplementation(libs.junit)

    // Instrumented test di HP/emulator: smoke test ML Kit dan alat ekspor OCR nota asli
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
