#!/usr/bin/env bash
# Menjalankan OCR pada foto nota asli di HP/emulator, lalu mengambil hasilnya ke laptop.
#
# Pemakaian (dari folder proyek):
#   alat/ekspor_ocr.sh            hasil disimpan di data_uji/hasil_ocr/<tanggal-jam>/
#   alat/ekspor_ocr.sh tanpa_olah hasil disimpan di data_uji/hasil_ocr/tanpa_olah/
#
# Sebelum menjalankan: salin foto nota ke data_uji/foto/, lalu sambungkan satu HP (USB debugging
# aktif) atau jalankan emulator. Jika ada lebih dari satu perangkat, pilih dengan
# ANDROID_SERIAL=<serial> alat/ekspor_ocr.sh
#
# Skrip ini memasang APK dengan "adb install -r", sehingga data aplikasi di HP tetap ada.
# Jangan pakai "./gradlew connectedDebugAndroidTest" di HP toko: tugas itu menghapus aplikasi
# beserta databasenya setelah selesai.
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
PAKET=com.scannota
FOLDER_HP=/sdcard/Android/data/$PAKET/files/hasil_ocr
label="${1:-$(date +%Y%m%d-%H%M%S)}"
tujuan="data_uji/hasil_ocr/$label"

shopt -s nullglob nocaseglob
foto=(data_uji/foto/*.jpg data_uji/foto/*.jpeg data_uji/foto/*.png data_uji/foto/*.webp)
shopt -u nullglob nocaseglob
if (( ${#foto[@]} == 0 )); then
    echo "Belum ada foto di data_uji/foto/ (jpg, jpeg, png, atau webp)." >&2
    exit 1
fi
if [[ -e "$tujuan" ]]; then
    echo "Folder $tujuan sudah ada. Pakai label lain supaya hasil lama tidak tertimpa." >&2
    exit 1
fi
if [[ "$("$ADB" get-state 2>/dev/null)" != "device" ]]; then
    echo "Tidak ada HP/emulator yang tersambung (cek: $ADB devices)." >&2
    exit 1
fi

echo "== ${#foto[@]} foto ditemukan. Membangun APK aplikasi dan APK test..."
./gradlew assembleDebug assembleDebugAndroidTest

echo "== Memasang ke perangkat (data aplikasi tidak dihapus)..."
"$ADB" install -r -t app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

echo "== Menjalankan OCR..."
keluaran="$("$ADB" shell am instrument -w \
    -e class $PAKET.ocr.EksporHasilOcrNotaAsli \
    $PAKET.test/androidx.test.runner.AndroidJUnitRunner)"
echo "$keluaran"
if ! grep -q "^OK (1 test)" <<<"$keluaran"; then
    echo "Ekspor gagal. Lihat pesan di atas." >&2
    exit 1
fi

echo "== Mengambil hasil ke $tujuan ..."
mkdir -p "$tujuan"
"$ADB" pull "$FOLDER_HP/." "$tujuan/"
# "OK (1 test)" juga muncul jika test dilewati, jadi pastikan ringkasannya benar-benar ada.
if [[ ! -f "$tujuan/ringkasan.tsv" ]]; then
    echo "Ringkasan tidak ditemukan; ekspor tidak berjalan sampai selesai." >&2
    exit 1
fi
if grep -q "HABIS_WAKTU" "$tujuan/ringkasan.tsv"; then
    echo "PERINGATAN: ada foto yang OCR-nya tidak selesai dalam 60 detik (lihat ringkasan.tsv)." >&2
fi
echo "Selesai. Ringkasan waktu dan jumlah baris: $tujuan/ringkasan.tsv"
