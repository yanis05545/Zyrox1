# AZENITH-AI — BLUEPRINT APLIKASI NATIVE

> Sumber: `Azenith-AI-v1_0_19-wifitweak.zip` (id `azenith-ai`, versionCode 1142)
> Bentuk struktur mengikuti `contoh.md` (Android multi-modul: app / core / data / domain / features / design-system / network / security / testing).
> Tujuan: semua fitur modul sekarang dipindah ke **UI native**. WebUI (`webroot/index.html`) tidak lagi jadi antarmuka utama.
> **Aturan emas: tidak ada fitur yang dihapus.** Setiap fitur di dokumen ini punya ID dan harus tercentang di bagian 11 (Parity Checklist) sebelum WebUI dipensiunkan.

---

## 1. Keputusan arsitektur (baca dulu)

1. **Engine tetap di modul root, UI pindah ke app.**
   Daemon game, watchdog thermal, restore mode saat boot, dan Fast Charger/Bypass harus tetap hidup walau app tertutup. Jadi `service.sh`, `lib/*.sh`, `system/bin/azenith-ai` dan `azenith-thermal` tetap jadi mesin. App native menggantikan `webroot/index.html` saja.
2. **Satu-satunya jembatan: `RootBridge`.**
   App memanggil `sh /data/adb/modules/azenith-ai/system/bin/azenith-ai <perintah>` lewat shell root (libsu), lalu parse output `key=value` / `STATUS|GRUP|NAMA|DETAIL`. Ini sama persis dengan yang dilakukan WebUI sekarang (`const CLI = ...`).
3. **Konfigurasi = satu sumber kebenaran:** `/data/adb/azenith-ai/azenith-ai.conf` (format `KEY=NILAI`). App membaca dan menulis lewat `azenith-ai set KEY NILAI`, tidak edit file langsung.
4. **Port ke Kotlin dilakukan bertahap dan opsional** (fase 7). Fitur yang cuma membaca sysfs (monitor, devinfo) boleh dipindah ke Kotlin lebih dulu untuk mengurangi fork shell.
5. **Companion APK sudah ada** (`app/AzenithAI-Companion.apk`, package `com.azenithtech.azenithai`, isinya WebView membungkus `webroot`). APK baru ini menggantikannya dengan package yang sama, sehingga `lib/app_install.sh` (auto-install saat boot, cek checksum, grant root Magisk) tetap dipakai tanpa diubah.

---

## 2. Struktur proyek (gaya `contoh.md`)

```
Azenith/
│
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml            (package com.azenithtech.azenithai)
│   │   ├── java/com/azenithtech/azenithai/
│   │   │   ├── AzenithApplication.kt
│   │   │   ├── MainActivity.kt            (host NavHost + tab bar melayang)
│   │   │   ├── AppInitializer.kt          (cek root, cek modul, cek lisensi)
│   │   │   └── navigation/AzenithNavGraph.kt
│   │   └── res/
│   └── build.gradle.kts
│
├── core/
│   ├── common/            (konstanta path: MODDIR, DATADIR, RUNDIR, CLI)
│   ├── utils/             (parser key=value, parser STATUS|GRUP|NAMA|DETAIL, format ukuran/suhu)
│   ├── extensions/
│   ├── logger/            (log app + penampil feature_logs OK/ERR/BUG/INFO)
│   ├── coroutine/         (dispatcher IO root, polling adaptif)
│   ├── result/            (AzResult: Ok / Err(kode, pesan CLI) / Unsupported)
│   └── root/              ◄ RootBridge
│       ├── RootBridge.kt          (exec(cmd): AzResult<String>, stream(cmd): Flow<String>)
│       ├── AzenithCli.kt          (pembungkus tiap subcommand, lihat bagian 6)
│       ├── RootState.kt           (Granted / Denied / Absent)
│       └── ModuleProbe.kt         (modul terpasang? versi? daemon up/down? heartbeat_age)
│
├── data/
│   ├── local/
│   │   ├── database/              (Room: riwayat monitor, cache daftar app)
│   │   ├── datastore/             (preferensi UI: tema, AMOLED, blur, interval polling)
│   │   └── preferences/
│   ├── remote/
│   │   ├── api/                   (update.json, opsional)
│   │   └── dto/
│   ├── root/                      ◄ sumber data dari CLI
│   │   ├── ConfDataSource.kt      (baca azenith-ai.conf, tulis lewat `set`)
│   │   ├── DevInfoDataSource.kt   (devinfo, devinfo live, gpuinfo)
│   │   ├── MonitorDataSource.kt   (monitor, netinfo, ping)
│   │   ├── ThermalDataSource.kt   (azenith-thermal, thermalkill, thermal daily)
│   │   ├── GameDataSource.kt      (game add/del/list/listuser, downscale, cooling, apps)
│   │   ├── PowerDataSource.kt     (charge, fastcharge, zram, cleaner)
│   │   ├── NetDataSource.kt       (netboost, wifill, wifitweak)
│   │   ├── KernelDataSource.kt    (kernel scan/features/match/apply/revert)
│   │   └── LogDataSource.kt       (flog, log, doctor, selftest, test)
│   ├── repository/
│   └── mapper/
│
├── domain/
│   ├── model/                     (lihat bagian 5)
│   ├── repository/
│   └── usecase/                   (satu usecase per aksi, lihat bagian 6)
│
├── features/
│   ├── perangkat/        (tab "Perangkat")
│   ├── monitor/          (tab "Monitor")
│   ├── kontrol/          (tab "Kontrol")
│   ├── fitur/            (tab "Fitur")
│   ├── game/             (tab "Game")
│   ├── baterai-ram/      (tab "Battery & RAM")
│   ├── alat/             (tab "Alat": terminal, log, scan kernel)
│   ├── tampilan/         (tab "Tampilan")
│   ├── lisensi/          (gerbang "Lisensi Diperlukan")
│   ├── onboarding/       (root belum diizinkan / modul belum terpasang)
│   └── tentang/          (versi, update, changelog)
│
├── design-system/
│   ├── components/       (AzCard, AzToggleCard, AzSlider, AzStatusPill, AzChart, AzTabBar)
│   ├── theme/            (Biasa, AMOLED, Liquid Glass, background custom + overlay)
│   ├── typography/
│   ├── animation/
│   └── icons/            (ikon tab dari WebUI: ponsel, grafik, slider, petir, gamepad, baterai, kunci inggris, palet)
│
├── network/
│   ├── client/           (HTTP fallback latensi, cek update)
│   ├── interceptor/
│   ├── websocket/        (Cara A: ping-pong server echo, server bisa diatur user)
│   └── connectivity/     (tipe jaringan aktif: WiFi / seluler)
│
├── security/
│   ├── license/          (port lib/license.sh: hash dari serial perangkat)
│   ├── integrity/        (cek checksum APK vs modul, cek versi modul cocok)
│   ├── keystore/
│   └── encryption/
│
├── engine/               ◄ BARU: mesin shell yang dibundel (SUMBER KEBENARAN, jangan dihapus)
│   └── module/           (salinan isi zip modul: lib/, system/bin/, service.sh, dst — lihat bagian 3)
│
├── testing/
│   ├── unit/             (parser, usecase, aturan bentrok mode)
│   ├── integration/      (RootBridge dengan sysfs tiruan, variabel TX_ROOT di core.sh)
│   └── fixtures/         (sampel output devinfo/monitor/kernel features)
│
├── build-logic/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 3. Isi modul yang tetap jadi mesin (jangan dihapus)

```
azenith-ai/                         (id=azenith-ai, v1.0.19-wifitweak)
├── module.prop · update.json · customize.sh · post-fs-data.sh · service.sh
├── common.sh · support.sh · action.sh · safe_trigger.sh · uninstall.sh
├── azenith-ai.conf.default         (konfigurasi bawaan, lihat bagian 7)
├── fpsgo_profile.conf              (katalog node FPSGO: nama|nilai|kelas|grup)
├── sys_cpuinfo_max
├── app/AzenithAI-Companion.apk     (diganti APK native; mekanisme install tetap)
├── webroot/index.html              (DIPERTAHANKAN dulu sebagai cadangan, hapus hanya setelah parity 100%)
├── META-INF/com/google/android/    (update-binary, updater-script)
├── system/bin/azenith-ai           (CLI utama, ±40 subcommand)
├── system/bin/azenith-thermal      (mode balance | saver | extreme=ZAI-Turbo)
└── lib/
    ├── core.sh            fondasi: path, logging, konfigurasi, safe_write (backup+verifikasi+stack)
    ├── daemon.sh          daemon tunggal + siklus hidup game (pidfile, deteksi game murah)
    ├── daemon_run.sh · supervisor.sh   restart bila crash (maks 5x/10 menit), bersihkan state
    ├── scan.sh            scanner device dinamis (/sys, /proc, getprop) → profile.conf
    ├── devinfo.sh         info perangkat (key=value, statis + live)
    ├── thermal.sh         thermal lock/inject berbasis hasil scan
    ├── thermaldaily.sh · tdloop.sh     Thermal Lock Daily + watchdog
    ├── thermalkill.sh · tkloop.sh      Disable Thermal agresif + watchdog
    ├── bypass.sh          Bypass Thermal (jalur halus, beda dari thermalkill)
    ├── cooling.sh         Cooling SoC per game (slider 0-4)
    ├── modelock.sh        kunci CPU/GPU per mode (balance/saver/gaming/extreme)
    ├── perf.sh · perflite.sh · powersave.sh · profile.sh
    ├── fps.sh · fpsgo.sh · fpsgo_parse.awk · kernel_match.sh · kernel_scan.sh
    ├── downscale.sh · gfxapi.sh · killsec.sh
    ├── netboost.sh · wifill.sh · wifitweak.sh
    ├── charge.sh · chargeloop.sh       Bypass Charging + Fast Charger
    ├── cleaner.sh · zram.sh · freeze.sh · dnd.sh · misc.sh
    ├── license.sh · app_install.sh
    └── overclock.sh       (fitur OC sudah dihapus dari produk; file legacy, tandai deprecated, jangan diekspos di app)
```

---

## 4. Peta UI: 8 tab + 2 layar penjaga

Tab bar lama (WebUI): Perangkat · Monitor · Kontrol · Fitur · Game · Battery & RAM · Alat · Tampilan. Di app native tetap 8 destinasi yang sama, dengan tab bar kapsul melayang (bisa digeser dan diketuk), seperti permintaan sebelumnya.

### Tab 1 — Perangkat (`features/perangkat`)
| ID | Kartu / elemen | Isi | Sumber |
|---|---|---|---|
| UI-1.1 | Banner AZT + foto profil | identitas aplikasi | statis |
| UI-1.2 | Perangkat Saya | nama perangkat, kernel, SoC, status root | `devinfo` |
| UI-1.3 | Ringkasan Perangkat | SoC, RAM, Storage, Baterai, Suhu perangkat, Frekuensi SoC | `devinfo`, `devinfo live` |
| UI-1.4 | Status perangkat lanjutan | tipe ROM (stock/AOSP/port), versi Android, vendor GPU | `devinfo`, `gpuinfo` |

### Tab 2 — Monitor (`features/monitor`)
| ID | Kartu | Isi | Sumber |
|---|---|---|---|
| UI-2.1 | CPU | beban rata-rata, beban total (%), per cluster, per core | `monitor` |
| UI-2.2 | GPU | vendor/model, clock, beban, clock % dari maks, suhu GPU | `monitor`, `gpuinfo` |
| UI-2.3 | RAM | total, tersedia, terpakai | `monitor` |
| UI-2.4 | Jaringan | unduh/unggah realtime, jenis jaringan, SSID/RSSI/link speed atau operator/2G-5G/dBm | `monitor`, `netinfo` |
| UI-2.5 | Latensi | ms, jitter, loss, host; ICMP asli via root (Cara C, acuan utama), HTTP fallback, WebSocket (Cara A) | `ping`, `network/websocket` |
| UI-2.6 | Tabel adaptasi latensi | <30 ms Sangat Baik · 30-80 Normal · 80-150 Lag (indikator kuning, polling diturunkan) · >150 Kritis (peringatan + matikan animasi berat) | logika UI |
| UI-2.7 | Pengaturan server WebSocket | simpan / reset | `datastore` |
| UI-2.8 | Suhu | CPU/SoC, GPU, Baterai, Skin | `monitor` |

### Tab 3 — Kontrol (`features/kontrol`)
| ID | Kartu | Isi | Sumber |
|---|---|---|---|
| UI-3.1 | Mode Thermal (Azenith) | pilih Balanced / PowerSaver / ZAI-Turbo + status | `azenith-thermal` |
| UI-3.2 | Perf Lite | toggle + status; aturan bentrok (lihat bagian 8) | `perflite on/off/status` |
| UI-3.3 | Network Booster | toggle (NSS, TCP/IP, bbr3 ↔ cubic) | `netboost on/off/status` |
| UI-3.4 | WiFi Low Latency | toggle + latensi saat ini + penjelasan "Apa itu WiFi Low Latency?" | `wifill on/off/status` |
| UI-3.5 | WiFi Tweak | toggle (15 perintah `cmd wifi`, cek dukungan ROM + revert) | `wifitweak on/off/status` |

### Tab 4 — Fitur (`features/fitur`)
| ID | Kartu | Isi | Sumber |
|---|---|---|---|
| UI-4.1 | Tweaks Performance | paket A · D · F/G · F · J · L · AKS | `set FEAT_*`, `killsec` |
| UI-4.2 | K · Touch Boost | toggle + status | `touch on/off/status` |
| UI-4.3 | Otomatisasi In-Game | DND In-Game, Freeze In-Game, opsi "ikut bekukan app sistem (whitelist)" | `set FEAT_DND_INGAME`, `set FEAT_FREEZE_INGAME`, `set FREEZE_INCLUDE_SYSTEM` |

### Tab 5 — Game (`features/game`)
| ID | Kartu | Isi | Sumber |
|---|---|---|---|
| UI-5.1 | Daftar game | daftar + "Tambah dari aplikasi terpasang" (picker dengan pencarian, jumlah terpilih) | `game list/listuser/add/del`, `apps` |
| UI-5.2 | Slider per game: Downscale | rasio render per game (GameManagerService, fallback `wm size`+`density`), Terapkan / Batal | `game downscale set/off/get/list` |
| UI-5.3 | Slider per game: Cooling SoC | 0 Mati · 1 Ringan · 2 Sedang · 3 Kuat · 4 Maksimal | `game cooling set/off/get/list`, `cooling status/reset` |
| UI-5.4 | Graphics API Universal | Vulkan / SkiaVk / SkiaGl, berlaku seluruh sistem, gagal-apa-adanya | `gfx universal set/off/status` |
| UI-5.5 | (rencana) Game Booster overlay | sidebar in-game FPS/CPU/GPU/suhu — permintaan sebelumnya, cocok jadi fitur native (overlay) | baru |

### Tab 6 — Battery & RAM (`features/baterai-ram`)
| ID | Kartu | Isi | Sumber |
|---|---|---|---|
| UI-6.1 | Bypass Charging | ON = baterai tidak menerima daya, status terverifikasi dari status + `current_now`, tombol "Diagnosa node charger" | `charge on/off/status/diag` |
| UI-6.2 | Fast Charger | matikan thermal + kunci suhu zona, mati otomatis bila baterai panas, "Diagnosa thermal" | `fastcharge on/off/status/diag` |
| UI-6.3 | Pembersih Sistem | 8 kategori (cache app internal, cache app penyimpanan, package_cache, Dalvik/OAT + /cache, log crash, sisa OTA, file sementara, sampah app terhapus) + total; tombol Pindai, Bersihkan (Soft), Bersihkan (Agresif) | `cleaner scan`, `cleaner clean <soft|agresif|cache|trash|dalvik|crash|ota|tmp>` |
| UI-6.4 | zRAM | status, algoritma aktif, disksize, data asli → terkompresi, memori fisik terpakai, swap terpakai/total, swappiness; kontrol: algoritma, ukuran, swappiness, "terapkan saat boot"; tombol Terapkan, Rekomendasi, Matikan | `zram status/apply/auto/off/boot` |

### Tab 7 — Alat (`features/alat`)
| ID | Kartu | Isi | Sumber |
|---|---|---|---|
| UI-7.1 | Terminal | root shell dengan riwayat, `cd`, `help`, Jalankan, Hapus layar | `RootBridge.stream` |
| UI-7.2 | Log Fitur | filter fitur + level (OK/ERR/BUG/INFO), Muat, Salin, Hapus | `flog` |
| UI-7.3 | Scan Kernel | Scan Sekarang, Cek Fitur & Batas Kernel (BISA/SEBAGIAN/TIDAK BISA), Upload & Scan | `kernel scan`, `kernel features` |
| UI-7.4 | Deteksi driver dari berkas (lanjutan) | Upload & cocokkan, Terapkan hasil (node cocok saja), Batalkan ke profil sebelumnya | `kernel match/apply/revert` |

### Tab 8 — Tampilan (`features/tampilan`)
| ID | Elemen | Isi |
|---|---|---|
| UI-8.1 | Mode layar | Biasa / AMOLED |
| UI-8.2 | Background transparan | kaca buram |
| UI-8.3 | Liquid Glass | tab, tombol, kartu, zoom |
| UI-8.4 | Kegelapan overlay | slider (bawaan 30%) |
| UI-8.5 | Background custom | Pasang / Hapus |

### Layar penjaga
| ID | Layar | Fungsi |
|---|---|---|
| UI-G.1 | Lisensi Diperlukan | gerbang: fitur terkunci sampai kunci valid (serial perangkat) |
| UI-G.2 | Koneksi Kritis | popup saat latensi >150 ms |
| UI-G.3 | Onboarding | root belum diizinkan / modul belum terpasang / versi modul tidak cocok |

---

## 5. Model domain (`domain/model`)

```
DeviceInfo        name, soc, ramTotal, storage, kernel, romType, androidVersion, rootType, gpuVendor, gpuModel
LiveStats         cpuLoad, cpuClusters[], cpuCores[], gpuClock, gpuLoad, ramUsed, ramAvail, tempSoc, tempGpu, tempBatt, tempSkin
NetworkInfo       type(WIFI|CELLULAR), ssid, rssi, linkSpeed, operator, generation, dbm
LatencySample     ms, jitter, loss, host, method(ICMP|HTTP|WS)
ThermalMode       BALANCE | SAVER | ZAI_TURBO(extreme)
FeatureToggle     key, enabled, supported(BISA|SEBAGIAN|TIDAK), reason
GameEntry         pkg, label, isUser, downscale, coolingLevel(0..4)
GfxApi            VULKAN | SKIAVK | SKIAGL | OFF
ChargeState       bypassOn, fastChargeOn, battPct, battTemp, nodeDetected, customNode
CleanerReport     perKategori[8], total
ZramState         algo, disksize, origData, comprData, memUsed, swapUsed, swapTotal, swappiness, bootApply
KernelFeature     status, grup, nama, detail      // dari STATUS|GRUP|NAMA|DETAIL
FeatureLog        fitur, level(OK|ERR|BUG|INFO), waktu, pesan
LicenseState      VALID | INVALID | LOCKED
ModuleState       installed, version, versionCode, daemonUp, heartbeatAge, mode
```

---

## 6. Peta fungsi: CLI → Usecase → Layar

| Fungsi (CLI) | Usecase Kotlin | Layar |
|---|---|---|
| `devinfo` / `devinfo live` | `GetDeviceInfo` / `ObserveDeviceLive` | UI-1.x |
| `gpuinfo` | `GetGpuInfo` | UI-1.4, 2.2 |
| `monitor` (poll ±2 dtk) | `ObserveLiveStats` | UI-2.x |
| `netinfo` | `GetNetworkInfo` | UI-2.4 |
| `ping` | `MeasureLatency` | UI-2.5 |
| `status [raw]` · `doctor` · `selftest` · `report` | `GetModuleStatus`, `RunDoctor`, `RunSelfTest` | Tentang/Alat |
| `on` `off` `auto` `toggle` `start` `stop` `restart` `enable` | `SetEngineState` | Kontrol (tersembunyi/lanjutan) |
| `preset <auto|cool|balanced|extreme>` | `SetPreset` | Fitur |
| `azenith-thermal balance|saver|extreme` · `profile <default|saver|azetturbo>` | `SetThermalMode` | UI-3.1 |
| `set <KEY> <nilai>` | `SetConfig` | semua toggle |
| `perflite on|off|status` | `TogglePerfLite` | UI-3.2 |
| `netboost on|off|status` | `ToggleNetBoost` | UI-3.3 |
| `wifill on|off|status` | `ToggleWifiLowLatency` | UI-3.4 |
| `wifitweak on|off|status` | `ToggleWifiTweak` | UI-3.5 |
| `touch on|off|status` | `ToggleTouchBoost` | UI-4.2 |
| `killsec on|off|status` | `ToggleAks` | UI-4.1 |
| `dnd status` + `set FEAT_DND_INGAME` | `ToggleDndInGame` | UI-4.3 |
| `freeze status/addsys/delsys/listsys/keep/unkeep/listkeep` | `ManageFreeze*` | UI-4.3 |
| `game add/del/list/listuser` · `apps` | `ManageGameList`, `ListInstalledApps` | UI-5.1 |
| `game downscale set/off/get/list` | `SetGameDownscale` | UI-5.2 |
| `game cooling set/off/get/list` · `cooling status/reset` | `SetGameCooling` | UI-5.3 |
| `gfx universal set/off/status` | `SetGraphicsApi` | UI-5.4 |
| `fpsgo [nodes|reset [paket]]` | `GetFpsgoEvidence`, `ResetFpsgo` | Alat/Game (lanjutan) |
| `charge on|off|status|diag` | `ToggleBypassCharging`, `DiagChargeNodes` | UI-6.1 |
| `fastcharge on|off|status|diag` | `ToggleFastCharger`, `DiagThermal` | UI-6.2 |
| `cleaner scan` / `cleaner clean …` | `ScanCleaner`, `RunCleaner` | UI-6.3 |
| `zram status/apply/auto/off/boot` | `GetZram`, `ApplyZram`, `RecommendZram`, `DisableZram`, `SetZramBoot` | UI-6.4 |
| `thermalkill on [quick]|off|now|status` | `ToggleDisableThermal` | Kontrol/Lanjutan |
| `thermal daily on|off|status` | `ToggleThermalDaily` | Kontrol/Lanjutan |
| `bypass on` (Bypass Thermal halus) | `ToggleBypassThermal` | Kontrol/Lanjutan |
| `kernel scan` · `kernel features` | `ScanKernel`, `CheckKernelFeatures` | UI-7.3 |
| `kernel match <berkas>` · `apply [force]` · `revert` | `MatchKernel`, `ApplyKernelMatch`, `RevertKernel` | UI-7.4 |
| `flog [fitur|all|list|summary|clear] [ok|err|bug|info] [n]` · `log [n]` | `GetFeatureLog`, `ClearFeatureLog` | UI-7.2 |
| `test gfxapi|perf|cleaner|charge|all` | `RunFeatureTest` | Alat |
| `safe on|off` · `freq status cpu|gpu` | `SetSafeMode`, `GetFreqStatus` | Lanjutan |
| `webui hide|show|status` | `SetWebUiButton` | Tentang (tetap ada sampai WebUI dipensiunkan) |

Pola semua usecase: `invoke(args) → AzResult<T>`. Hasil CLI yang gagal verifikasi **tidak ditutupi** (sesuai filosofi modul: tulis, baca-balik, laporkan jujur).

---

## 7. Konfigurasi (`azenith-ai.conf`) → kontrol di app

Semua kunci di bawah sudah ada di modul. App wajib bisa membaca dan mengubah yang ditandai UI.

| Kelompok | Kunci | Kontrol di app |
|---|---|---|
| Umum | `CONF_VERSION`, `PRESET` (auto/cool/balanced/extreme), `NOTIFY` | Fitur |
| Thermal | `THERMAL_MODE` (off/soft/auto/hard), `THERMAL_KILL`, `TK_BATT_MAX`, `TK_SKIN_MAX`, `TK_HOT_SEC`, `TK_TICK`, `TK_BLOCK`, `TK_BLOCK_AFTER`, `TK_KERNEL`, `TK_TRIP_C`, `TK_KILL_PROCS`, `INJECT_C`, `TRIP_RAISE_C` | Lanjutan |
| Pengaman suhu | `SAFE_SOC_C`, `SAFE_BATT_C`, `SAFE_SKIN_C`, `SAFE_HOT_SECONDS`, `COOLDOWN_SEC`, `HARD_*` | Lanjutan (hanya baca bila perlu) |
| Fitur A–Q | `FEAT_THERMAL`, `FEAT_TEMP_MONITOR` (jangan dimatikan), `FEAT_FREQ`, `FEAT_SCHED`, `FEAT_PRIORITY`, `FEAT_FPS_ENGINE`, `FEAT_FPSGO_TUNE`, `FEAT_SURFACEFLINGER`, `FEAT_LOG_REDUCE`, `FEAT_TOUCH`, `FEAT_IO`, `FEAT_REFRESH`, `FEAT_NETWORK`, `FEAT_RAM`, `FEAT_DOWNSCALE`, `FEAT_COOLING` | UI-4.1 |
| AKS | `AKS_ON` | UI-4.1 |
| Cooling | `COOL_STEP_SEC`, `COOL_STEP_PCT`, `COOL_HYST_C` | UI-5.3 (lanjutan) |
| Mesin FPS | `FPS_TICK`, `FPS_DROP_PCT`, `FPS_STABLE_SAMPLES`, `SAWTOOTH_*` | Lanjutan |
| FPSGO | `FPSGO_AUDITION`, `FPSGO_DISCOVER`, `FPSGO_APPLY_UNVERIFIED`, `FPSGO_AB_*`, `FPSGO_RELEARN_DAYS` | Lanjutan |
| Deteksi | `POLL_INTERVAL`, `FG_CHECK_INGAME`, `EXTRA_GAME_PACKAGES` | UI-5.1 |
| Prioritas/RAM/Jaringan | `PRIO_NICE`, `PRIO_RT`, `RAM_KILL_BG`, `RAM_DROP_CACHES`, `NET_PING_LOG`, `NET_PING_HOST`, `NET_PING_INTERVAL`, `SF_BOOT_PROPS` | Lanjutan |
| In-Game | `FEAT_DND_INGAME`, `FEAT_FREEZE_INGAME`, `FREEZE_INCLUDE_SYSTEM` | UI-4.3 |
| Charging | `CHARGE_BYPASS`, `CHARGE_MIN_PCT`, `CHARGE_TICK`, `CHARGE_VERIFY_TRIES`, `CHARGE_CUSTOM_NODE`, `CHARGE_CUSTOM_METHOD`, `CHARGE_FAKE_ICON`, `FAST_CHARGE`, `FC_BATT_MAX_C` | UI-6.1, 6.2 |
| Jaringan | `NETBOOST`, `NET_CONGESTION`, `WIFITWEAK` | UI-3.3, 3.5 |
| Perf Lite | `PERFLITE`, `PL_SWAPPINESS`, `PL_VFS_PRESSURE`, `PL_READAHEAD_KB`, `PL_UP_US`, `PL_DOWN_US`, `PL_TOP_UCLAMP_MIN`, `PL_BG_UCLAMP_MAX` | UI-3.2 (lanjutan) |
| Grafis | `GFX_UNIVERSAL`, `GFX_UNIVERSAL_API` | UI-5.4 |
| Boot aman | `BOOT_FAIL_LIMIT`, `BOOT_OK_DELAY`, `SAFE_WINDOW_SEC`, `STAGE_STEP_DELAY`, `OC_BOOT_APPLY`, `OC_BOOT_DELAY` | Lanjutan (hanya baca) |

Kunci per game disimpan modul di `/data/adb/azenith-ai/` (daftar game, downscale, cooling, hasil audisi FPSGO di `learn/<paket>.fpsgo`). App hanya lewat CLI, tidak menyentuh file ini langsung.

---

## 8. Aturan bisnis yang harus dijaga di UI (jangan hilang saat migrasi)

1. **Perf Lite** boleh bareng Balanced. Selama aktif: PowerSaver dan ZAI-Turbo **ditolak/terkunci**, Fast Charger **otomatis mati** dan tidak bisa dinyalakan.
2. **Fast Charger** aktif: mode Balanced dan PowerSaver **tidak direspon**. Mati otomatis bila suhu baterai ≥ `FC_BATT_MAX_C`.
3. **Bypass Charging** mati otomatis bila baterai ≤ `CHARGE_MIN_PCT`. Status ditampilkan dari verifikasi nyata (status + arus), bukan sekadar toggle.
4. **Graphics API Universal**: bila backend yang diminta gagal diverifikasi, tampilkan GAGAL apa adanya, jangan dialihkan diam-diam.
5. **Freeze In-Game**: app inti selalu ditolak; app sistem hanya dari whitelist `addsys`.
6. **Disable Thermal / TK_BLOCK / TK_KILL_PROCS**: berisiko, beri peringatan jelas dan default mati.
7. **Downscale**: tampilkan tingkat yang dipakai (per-app vs global `wm size`) karena efeknya beda.
8. **Latensi**: urutan sumber ICMP (utama) → HTTP → WebSocket; tabel adaptasi (bagian UI-2.6) mengubah interval polling dan animasi.
9. **Semua toggle**: baca ulang status dari CLI setelah menulis; kalau beda, tampilkan nilai kernel yang nyata.
10. **Fitur tidak didukung** (hasil `kernel features`): tampilkan sebagai nonaktif dengan alasan, bukan disembunyikan.

---

## 9. Siklus hidup, izin, dan keamanan

- **Boot:** `service.sh` memulai supervisor, daemon, watchdog, lalu `lib/app_install.sh` memasang/upgrade APK (cek checksum). Mode terakhir dipulihkan lewat `azenith-thermal restore`.
- **Root:** Magisk (izin otomatis via policies db), KernelSU/APatch (dialog izin di manager). App terkunci sampai root diizinkan (UI-G.3).
- **Proteksi boot:** `BOOT_FAIL_LIMIT` boot tak sehat berturut-turut → modul menonaktifkan diri; tombol volume (jendela `SAFE_WINDOW_SEC`) memicu safe mode. App harus menampilkan status ini.
- **Lisensi:** skema offline berbasis serial perangkat (`lib/license.sh`). Catatan jujur dari file itu sendiri: skemanya simetris, jadi orang yang paham bisa mengambil secret-nya. Di app, taruh verifikasi di modul `security/license`, **jangan** sertakan `keygen` di APK/zip rilis, dan ganti `LIC_SECRET1/2` sebelum dijual.
- **Izin Android app:** root (libsu), `QUERY_ALL_PACKAGES` (picker game/freeze), `PACKAGE_USAGE_STATS` bila perlu, `SYSTEM_ALERT_WINDOW` (overlay Game Booster), `POST_NOTIFICATIONS`, `INTERNET` (HTTP/WebSocket latensi, cek update).
- **Update:** `update.json` (versi, `zipUrl`, `changelog`) masih placeholder `GANTI_USER_GITHUB/GANTI_REPO` dan versinya (v2.9.2) tidak sinkron dengan `module.prop` (v1.0.19). Perbaiki sebelum dipakai fitur cek update.

---

## 10. Urutan pengerjaan (fase)

| Fase | Isi | Hasil |
|---|---|---|
| 0 | Gradle multi-modul, `design-system`, `core/root` (RootBridge, parser, AzResult), `ModuleProbe`, onboarding root/modul | app terbuka, tahu root + modul + daemon |
| 1 | Layar baca-saja: Perangkat, Monitor (polling adaptif + grafik) | UI-1.x, UI-2.x |
| 2 | Kontrol + Fitur: semua toggle lewat `SetConfig`/CLI + aturan bentrok bagian 8 | UI-3.x, UI-4.x |
| 3 | Game: daftar, picker app, downscale, cooling, Graphics API | UI-5.x |
| 4 | Battery & RAM: Bypass, Fast Charger, Pembersih, zRAM | UI-6.x |
| 5 | Alat: Terminal, Log Fitur, Scan Kernel, Match | UI-7.x |
| 6 | Tampilan (Biasa/AMOLED/Liquid Glass/background), Lisensi, Tentang/Update, tab bar melayang | UI-8.x, UI-G.x |
| 7 | (opsional) Game Booster overlay native; port bagian read-only ke Kotlin | UI-5.5 |
| 8 | Parity 100% → `webui hide` jadi default, `webroot/` dipertahankan sebagai cadangan | rilis |

Build: Gradle lewat Termux + GitHub Actions (sesuai alur companion sebelumnya). APK dibundel ke `app/AzenithAI-Companion.apk` di zip modul supaya `app_install.sh` tidak perlu diubah.

---

## 11. Parity Checklist (centang semua sebelum WebUI dipensiunkan)

**Tab Perangkat:** [ ] UI-1.1 [ ] UI-1.2 [ ] UI-1.3 [ ] UI-1.4
**Tab Monitor:** [ ] UI-2.1 [ ] UI-2.2 [ ] UI-2.3 [ ] UI-2.4 [ ] UI-2.5 [ ] UI-2.6 [ ] UI-2.7 [ ] UI-2.8
**Tab Kontrol:** [ ] UI-3.1 [ ] UI-3.2 [ ] UI-3.3 [ ] UI-3.4 [ ] UI-3.5
**Tab Fitur:** [ ] UI-4.1 [ ] UI-4.2 [ ] UI-4.3
**Tab Game:** [ ] UI-5.1 [ ] UI-5.2 [ ] UI-5.3 [ ] UI-5.4
**Tab Battery & RAM:** [ ] UI-6.1 [ ] UI-6.2 [ ] UI-6.3 [ ] UI-6.4
**Tab Alat:** [ ] UI-7.1 [ ] UI-7.2 [ ] UI-7.3 [ ] UI-7.4
**Tab Tampilan:** [ ] UI-8.1 [ ] UI-8.2 [ ] UI-8.3 [ ] UI-8.4 [ ] UI-8.5
**Penjaga:** [ ] UI-G.1 Lisensi [ ] UI-G.2 Koneksi Kritis [ ] UI-G.3 Onboarding

**Fitur mesin (tetap di modul, harus bisa dikontrol/dilihat dari app):**
[ ] Daemon + supervisor + deteksi game · [ ] FPS Sustain Engine + anti-sawtooth · [ ] Tuning FPSGO + audisi A/B · [ ] Thermal Lock (A) · [ ] Thermal Lock Daily · [ ] Disable Thermal (thermalkill) · [ ] Bypass Thermal · [ ] Mode balance/saver/gaming/extreme (modelock) · [ ] Battery Saver menempel ke mode (powersave) · [ ] AKS (killsec) · [ ] Scanner device dinamis · [ ] Kernel scan/match · [ ] Lock refresh rate (M) · [ ] I/O read-ahead/UFS (L) · [ ] SurfaceFlinger boot props (I) · [ ] Kurangi log (J) · [ ] RAM manager (O) · [ ] Prioritas nice/ionice (E) · [ ] Safe boot (volume key + BOOT_FAIL_LIMIT) · [ ] Auto-install APK · [ ] Uninstall bersih (`uninstall.sh`) · [ ] `action.sh` (tombol Action di manager)

**Dihapus dari produk (bukan target migrasi):** Overclock slider/badge/baca OC dari .ko (`lib/overclock.sh` hanya legacy).

---

## 12. Risiko dan catatan jujur

- **Belum diuji di UI nyata:** dokumen ini hasil membaca isi zip (struktur, CLI, config, WebUI), belum menjalankan modul di perangkat.
- Kartu di WebUI dirender sebagian lewat JavaScript, jadi daftar kartu di bagian 4 diambil dari teks statis + pemanggilan CLI. Kalau ada kartu kondisional yang terlewat, tambahkan ID baru dan centang di bagian 11.
- Output `monitor`/`devinfo` berformat `key=value`: tulis parser toleran (kunci hilang = null, bukan crash) dan simpan sampel di `testing/fixtures`.
- Polling `monitor` tiap ±2 dtk memakai fork shell. Pakai satu sesi root persisten (libsu `Shell`) dan hentikan polling saat layar tidak terlihat.
- WebView lama tidak boleh dibawa ke app baru; yang dipertahankan hanya `webroot/` di modul sebagai cadangan sementara.
