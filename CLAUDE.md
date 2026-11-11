# Proyek: Aplikasi Pemindai Nota Cetak (Skripsi)

## Tujuan
Aplikasi Android native untuk memindai nota cetak dari pemasok, membaca barang dan harga dengan OCR,
lalu menentukan status harga beli barang (naik, turun, atau tetap) dibanding nota sebelumnya.
Pengguna: pemilik Toko Kelontong Pak Cipto, Kediri.

## Stack (tidak boleh diganti tanpa persetujuan)
- Bahasa: Kotlin
- UI: Jetpack Compose
- OCR: ML Kit Text Recognition (script Latin), model terlatih, TANPA pelatihan ulang.
  Pakai varian bundled `com.google.mlkit:text-recognition` (model di dalam APK) agar benar-benar offline.
  Jangan pakai varian Play Services, karena varian itu mengunduh model lewat internet.
- Pengolahan citra: OpenCV Android (grayscale, deskew, adaptive threshold, denoise)
- Basis data: Room (SQLite lokal)
- Pencocokan nama: Levenshtein Distance yang ditulis manual, bukan library
- Versi pustaka dikunci di `gradle/libs.versions.toml`. AGP harus tetap didukung Android Studio Narwhal (2025.1) yang terpasang.

## Aturan
- Aplikasi berjalan offline. Jangan menambahkan server atau layanan cloud.
- Hanya Android. iOS di luar ruang lingkup.
- Setiap nota boleh terdiri dari maksimal 20 foto. Foto digabung di level teks, bukan menyambung gambar.
- Ambang Levenshtein memakai skor kemiripan = 1 − (jarak Levenshtein / panjang nama yang lebih panjang), skala 0–1:
  - otomatis jika kemiripan ≥ 0,90
  - konfirmasi jika 0,70 ≤ kemiripan < 0,90
  - barang baru jika kemiripan < 0,70
  Jangan mengubah nilai ambang tanpa instruksi saya.
- Verifikasi total: jumlah subtotal harus sama dengan total nota. Jika tidak, tandai "belum lengkap".
  Nota yang totalnya tidak terbaca juga "belum lengkap".
- Status harga: dibandingkan dengan pembelian sebelumnya untuk barang yang sama, dari pemasok yang sama,
  dengan satuan yang sama. Ada 4 status: naik, turun, tetap, belum ada pembanding.
  Status harga dan status kelengkapan dihitung lewat query, tidak disimpan di tabel.
- Nota ganda ditolak oleh database: pasangan (pemasok, nomor nota) unik, tanpa membedakan huruf besar/kecil.
  Nota tanpa nomor tetap boleh disimpan.
- Jangan membuat data nota fiktif atau mengisi angka uji secara otomatis.
  Data uji parsing harus berasal dari nota asli yang diberikan pengguna.
  Test logika (Levenshtein, status harga, verifikasi total) boleh memakai kata atau angka contoh sintetis
  yang ditandai jelas, hanya di kode test, dan tidak pernah masuk database aplikasi.
- Komentar kode dan teks antarmuka dalam Bahasa Indonesia.
- Setiap fungsi penting diberi komentar singkat yang menjelaskan alasannya, karena saya harus bisa menjelaskannya saat sidang.

## Alasan nilai ambang (dipilih Claude atas permintaan pengguna, untuk dijelaskan saat sidang)
- Biaya kesalahan tidak seimbang. Salah cocok otomatis menempelkan harga ke barang lain, sehingga status
  naik/turun keliru tanpa disadari. Konfirmasi hanya butuh satu ketukan, dan cukup sekali per pemasok karena
  hasilnya disimpan sebagai alias. Karena itu ambang otomatis dibuat ketat.
- 0,90 berarti paling banyak 1 kesalahan karakter per 10 karakter. Cukup untuk galat OCR khas pada teks cetak
  (karakter yang bentuknya mirip, seperti O/0, I/1/l, S/5, B/8), tetapi tidak cukup untuk perbedaan kata.
- 0,70 berarti paling banyak 3 kesalahan per 10 karakter, sehingga singkatan atau ejaan yang berbeda masih
  muncul sebagai kandidat untuk dikonfirmasi. Di bawah itu, kemiripan lebih mungkin kebetulan.
- Ini nilai awal yang wajib diuji dengan nota asli di bab pengujian. Jika hasil uji menyarankan perubahan,
  laporkan dulu; pengguna yang memutuskan.
- Ambang dibandingkan dengan bilangan bulat (100 × jarak ≤ (100 − p) × panjang), bukan desimal.
  Buktinya ada di test `galatDesimal_alasanMemakaiBilanganBulat`: dengan desimal, nama 10 huruf yang salah
  1 huruf (tepat 0,90) bisa salah jatuh ke konfirmasi karena 1 − 0,90 dihitung 0,09999999999999998.

## Aturan pencocokan tambahan (disetujui pengguna, tidak mengubah nilai ambang)
- Penjaga angka: otomatis hanya jika deret angka di kedua nama sama persis. Kalau tidak, turun ke konfirmasi.
  Ini mencegah varian ukuran (250 vs 500) tercocokkan otomatis.
- Skor kembar: jika dua barang berbeda sama-sama mendapat skor tertinggi di zona otomatis, turun ke konfirmasi.
- Alias persis milik pemasok yang sama selalu otomatis, karena pasangan itu sudah pernah dikonfirmasi pengguna.
- Kandidat Levenshtein: alias milik pemasok ini dan nama baku semua barang (bukan alias pemasok lain).
- Alias hanya disimpan saat pengguna mengonfirmasi, tidak pernah dari pencocokan otomatis.
  Barang baru tidak pernah dibuat otomatis.
- Normalisasi minimal: huruf kapital dan spasi dirapikan. Tanda baca tidak dibuang dan O tidak diganti 0.

## Struktur modul (paket `com.scannota`, folder `app/src/main/java/com/scannota/`)
1. `preprocessing/` : OpenCV, grayscale, deskew, threshold, denoise
2. `ocr/` : pembungkus ML Kit, mengembalikan daftar baris beserta skor keyakinan, dan menyusun
   potongan teks yang sejajar menjadi baris nota (baris visual)
3. `parsing/` : mengubah baris teks menjadi nama barang, qty, harga, subtotal, dan total
4. `merge/` : penggabungan beberapa foto nota berdasarkan tumpang tindih baris
5. `matching/` : Levenshtein dan alias barang per pemasok
6. `data/` : entity dan DAO Room untuk pemasok, barang, alias_barang, nota, detail_nota
   (tabel pemasok ditambahkan atas persetujuan pengguna karena alias berlaku per pemasok)
7. `ui/` : layar kamera, hasil OCR, daftar barang, status harga

Unit test ada di `app/src/test/java/com/scannota/`.

## Urutan pengerjaan (usulan, satu modul per sesi)
0. Kerangka proyek: selesai (2026-11-11)
1. `data/`: selesai (2026-11-11). Query DAO belum diuji di perangkat; uji instrumented menyusul bersama `ui/`.
2. `matching/`: selesai (2026-11-11). Catatan untuk `ui/`: memperbaiki alias lama yang keliru
   (`HasilSimpanAlias.BERTENTANGAN`) butuh query ubah/hapus alias di `data/`. Minta persetujuan dulu.
3. `ocr/`: selesai (2026-11-12). Smoke test lulus di HP (vivo Y27s, Android 15). Alat ekspor teruji
   dengan 6 foto nota asli dari pengguna (hasil di `data_uji/hasil_ocr/tanpa_olah/`), termasuk foto
   dengan rotasi EXIF 270°. Waktu OCR sekitar 0,3–1,5 detik per foto di HP itu.
   Temuan untuk `parsing/`: pada bagian bawah nota yang kertasnya melengkung, kemiringan lokal
   (−2° sampai −4°) berbeda dari median foto, sehingga beberapa baris nota tergabung.
4. `parsing/`: aturan dibangun dari hasil OCR nota asli
5. `merge/`
6. `preprocessing/`: dievaluasi dengan membandingkan hasil OCR sebelum dan sesudah pengolahan citra pada foto yang sama
7. `ui/`

## Catatan ML Kit (terbukti di perangkat, jangan diubah tanpa uji ulang)
- Pustaka bundled `com.google.mlkit:text-recognition` menarik `play-services-mlkit-text-recognition`.
  Itu dependensi resmi Google: hanya kelas API (sekitar 3 KB) tanpa model dan tanpa unduhan.
  Modelnya ada di aset pustaka bundled. Jangan dihapus atau dikecualikan.
- ML Kit memakai nilai rotasi untuk membaca teks, tetapi koordinat yang dikembalikan mengacu pada
  bitmap asli yang belum diputar. `putarKeTegak` memindahkannya ke foto tegak. Buktinya ada di
  smoke test `rotasiDiterapkan_koordinatMengacuFotoTegak`.
- Kemiringan (`kemiringanDerajat`) dihitung sendiri dari titik sudut: positif = ujung kanan lebih rendah.
- `AMBANG_TUMPANG_TINDIH_PERSEN = 50` untuk menyusun baris visual adalah nilai awal. Uji dengan
  hasil OCR nota asli di sesi `parsing/`, dan laporkan dulu sebelum mengubahnya.
- Skor keyakinan diteruskan apa adanya. Belum ada ambang yang dibuat darinya; diputuskan di `ui/` berdasarkan data.

## Cara bekerja
- Kerjakan satu modul per sesi. Jangan mengerjakan dua modul sekaligus.
- Sebelum menulis kode, jelaskan rencanamu dalam beberapa poin dan tunggu persetujuan saya.
- Setelah selesai, jelaskan setiap file yang dibuat atau diubah.
- Tambahkan unit test untuk fungsi murni seperti Levenshtein dan parsing.

## Laptop pengembang (semua pengaturan build disesuaikan dengan ini)
- Intel Core i5-11300H (4 core / 8 thread, x86_64), RAM 8 GB (7 GB terbaca), Intel Iris Xe,
  SSD NVMe dengan sisa sekitar 14 GB, Ubuntu 25.04, Android Studio 2025.1.3 (Narwhal), JDK 17.
- RAM adalah batas utama. `gradle.properties` membatasi Gradle ke satu JVM 1,5 GB (Kotlin in-process),
  4 worker, dan daemon yang berhenti setelah 30 menit menganggur. Jangan menaikkan nilai ini tanpa alasan.
- Gradle 8.13 + AGP 8.11.1. Jangan upgrade wrapper ke Gradle 9: AGP 8 tidak cocok dengan Gradle 9.6+,
  dan Android Studio 2025.1.3 belum mendukung AGP 9.
- Ekstensi Java VS Code (memakai JDK 25 bawaannya) pernah mengubah wrapper ke 9.7.1. Karena itu,
  impor Gradle-nya dimatikan untuk folder ini di `.vscode/settings.json`. Build tetap lewat
  Android Studio atau `./gradlew`.
- ABI: build debug berisi arm64-v8a, armeabi-v7a, dan x86_64 (untuk emulator). Build release ARM saja.
- Emulator: AVD `Uji_Nota_API36` (Android 16, x86_64, RAM 2 GB, 4 core, data 4 GB).
  Kamera belakang memakai webcam laptop. RAM laptop tidak cukup untuk menjalankan emulator bersamaan
  dengan Android Studio dan banyak tab Chrome. Untuk uji OCR sungguhan, utamakan HP fisik.
  Pada 2026-11-12 emulator macet (thread CPU tidak merespons, lalu crash) saat swap laptop sekitar 4,6 GB,
  walau `./gradlew --stop` sudah dijalankan. Instrumented test akhirnya dijalankan di HP.
- HP uji: vivo Y27s (model V2322, Android 15, arm64-v8a). Foto nota dari pengguna juga diambil dengan HP ini. Saat pemasangan lewat USB, layar HP harus
  terbuka, supaya konfirmasi pemasangan bisa muncul.

## Perintah
- Build APK debug: `./gradlew assembleDebug`
- Unit test: `./gradlew testDebugUnitTest`
- Jalankan emulator: `~/Android/Sdk/emulator/emulator -avd Uji_Nota_API36`
- Pasang ke emulator/HP: `./gradlew installDebug`
- Instrumented test tanpa menghapus aplikasi: pasang APK debug dan APK test dengan `adb install -r -t`,
  lalu `adb shell am instrument -w -e class com.scannota.ocr.PengenalTeksSmokeTest com.scannota.test/androidx.test.runner.AndroidJUnitRunner`
- Ekspor OCR nota asli: salin foto ke `data_uji/foto/`, lalu `alat/ekspor_ocr.sh <label>`
- Jangan pakai `./gradlew connectedDebugAndroidTest` di HP toko: tugas itu menghapus aplikasi
  beserta databasenya setelah selesai.
