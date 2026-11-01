# Azenith-AI — Proyek Aplikasi Native (1 paket)

Satu paket berisi **aplikasi Android native (Jetpack Compose)** + **modul root asli (tidak ada yang dihapus)** + **cara build APK**.
WebUI tidak lagi jadi antarmuka utama; semua fitur punya layar native. Detail peta fitur: `docs/AZENITH-APP-BLUEPRINT.md`.

```
Azenith/
├── app/                       aplikasi native (package com.azenithtech.azenithai)
│   └── src/main/java/com/azenithtech/azenithai/
│       ├── MainActivity.kt    pager 8 tab + tab bar kapsul melayang + gerbang root/modul/lisensi
│       ├── core/              Paths, RootBridge (su), parser key=value
│       ├── data/              AzenithRepository (semua subcommand azenith-ai)
│       ├── domain/            Models + Specs (daftar toggle/fitur/flag A–Q)
│       ├── designsystem/      tema (Biasa/AMOLED/Liquid Glass), komponen, preferensi tampilan
│       ├── network/           ping HTTP + WebSocket (pembanding ICMP root)
│       └── features/          perangkat · monitor · kontrol · fitur · game · baterai · alat · tampilan · lisensi · onboarding
├── engine/module/             modul root ASLI v1.0.19-wifitweak (lib/*.sh, azenith-ai, azenith-thermal, webroot/ dst, utuh)
├── docs/                      blueprint + contoh struktur
├── tools/pack_module.sh       gabungkan APK baru ke zip modul
└── .github/workflows/build.yml  build otomatis di GitHub Actions
```

## Cara build (disarankan: GitHub Actions, tanpa laptop)
1. Buat repo GitHub baru, upload isi folder ini (di Termux: `pkg install git`, lalu `git init && git add . && git commit -m init && git remote add origin <url> && git push -u origin main`).
2. Buka tab **Actions** → workflow **Build APK + ZIP Modul** (jalan otomatis saat push, atau tekan *Run workflow*).
3. Unduh artifact:
   - **Azenith-AI-APK** → `app-release.apk` (bisa dipasang sendiri).
   - **Azenith-AI-Module-ZIP** → `Azenith-AI-v1.0.19-wifitweak-native.zip` (modul + APK baru di dalamnya).
4. Flash zip modul di Magisk/KernelSU/APatch → reboot → aplikasi terpasang otomatis (`lib/app_install.sh`) dan muncul di home screen.

## Build lokal (PC / Linux)
Butuh JDK 17, Android SDK (platform 34), Gradle 8.9:
```
gradle :app:assembleRelease
sh tools/pack_module.sh        # butuh: zip
```
(Di Termux, build Android sering gagal karena aapt2 arsitektur ARM; pakai GitHub Actions.)

## Catatan penting
- **Belum dikompilasi/diuji di perangkat.** Kode ditulis dengan membaca isi modul; kemungkinan ada typo kecil saat build pertama. Kirim log error Actions, saya perbaiki.
- **Ganti APK lama:** APK Companion lama (WebView) ditandatangani kunci berbeda. Kalau `app_install.sh` gagal meng-upgrade, jalankan sekali: `su -c "pm uninstall com.azenithtech.azenithai"` lalu reboot.
- APK rilis ditandatangani kunci **debug** supaya langsung bisa dipasang. Untuk rilis publik, buat keystore sendiri.
- **Lisensi:** ganti `LIC_SECRET1/2` di `engine/module/lib/license.sh` sebelum dijual, dan jangan sertakan keygen di zip rilis.
- `engine/module/update.json` masih placeholder GitHub dan versinya (v2.9.2) tidak sinkron dengan `module.prop` (v1.0.19).
- Overclock sudah dihapus dari produk; `lib/overclock.sh` hanya legacy dan tidak diekspos di app.
- Game Booster overlay (sidebar FPS in-game) belum ada; masuk fase 7 di blueprint.
