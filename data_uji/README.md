# Data uji dari nota asli

Folder ini berisi bahan uji yang berasal dari nota asli Toko Kelontong Pak Cipto.
Isi folder `foto/` dan `hasil_ocr/` tidak masuk git.

## `foto/`
Foto nota asli yang akan dibaca OCR.

- Taruh langsung di `foto/`, bukan di subfolder.
- Format yang dibaca: jpg, jpeg, png, atau webp.
- Nama file jangan diawali titik atau garis bawah, karena file seperti itu tidak ikut dikemas ke APK.
- Saran penamaan: `<pemasok>_<nomor nota>_<urutan foto>.jpg`, misalnya `pemasokA_123_01.jpg`.

## `hasil_ocr/`
Hasil OCR yang diambil dari HP oleh `alat/ekspor_ocr.sh`. Setiap kali skrip dijalankan, hasilnya
masuk ke subfolder sendiri:

- satu berkas `.tsv` per foto: semua baris dan kata, beserta titik sudut dan skor keyakinannya
- `ringkasan.tsv`: ukuran foto, rotasi EXIF, jumlah baris dan kata, dan waktu OCR per foto

Berkas `.tsv` bisa dibuka di spreadsheet. Jika kolom angka tampil aneh, impor dengan pemisah
desimal titik.

## Cara menjalankan
```bash
alat/ekspor_ocr.sh tanpa_olah
```
Label (`tanpa_olah`) menjadi nama subfolder hasil. Saat modul `preprocessing/` dievaluasi, foto
yang sama dijalankan lagi dengan label lain, lalu kedua hasilnya dibandingkan.
