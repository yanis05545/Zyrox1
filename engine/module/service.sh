#!/system/bin/sh
# Azenith-AI v2 - service.sh (late_start): hanya meluncurkan supervisor.
MODDIR=${0%/*}
[ -f "$MODDIR/disable" ] && exit 0
# --- Gerbang lisensi: HP tidak terdaftar = semua fitur service tetap mati. ---
. "$MODDIR/lib/license.sh"
if ! lic_check; then
  lic_log "BOOT: service.sh dihentikan - $(lic_status 2>&1)"
  mkdir -p /data/adb/azenith-ai && touch /data/adb/azenith-ai/license.lock
  exit 0
fi
rm -f /data/adb/azenith-ai/license.lock
# -----------------------------------------------------------------------------
# tunggu boot selesai supaya dumpsys/ps stabil
n=0
while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$n" -lt 180 ]; do sleep 2; n=$((n + 2)); done
sleep 5
# Downscale (lib/downscale.sh) safety-net: wm size/density adalah override
# GLOBAL yang Android simpan ke settings - bisa BERTAHAN setelah reboot kalau
# sesi game sebelumnya terputus paksa (reboot manual/crash) sebelum
# stop_game() sempat jalan. TANPA SYARAT conf apapun: reset dulu sebelum
# fitur lain mulai, supaya device tidak pernah nyangkut di resolusi kecil
# permanen gara-gara sesi yang tidak sempat dibersihkan.
wm size reset >/dev/null 2>&1
wm density reset >/dev/null 2>&1
mkdir -p /data/adb/azenith-ai/run
rm -f /data/adb/azenith-ai/run/stop
sh "$MODDIR/lib/supervisor.sh" >/dev/null 2>&1 &
# Disable Thermal: SEKARANG opt-in, mengikuti pilihan user di conf/WebUI.
# v1.0.9-fix: TIDAK lagi memaksa THERMAL_KILL=1 kalau user belum pernah
# mengaktifkannya sendiri - sebelumnya ini aktif otomatis dari boot pertama
# dan membunuh service *thermal* tiap 2 dtk tanpa henti, yang di sebagian
# chipset MTK combo-HAL (satu binary melayani beberapa interface sekaligus)
# ikut menjatuhkan proses non-thermal yang nebeng di binder yang sama ->
# crash & koneksi jaringan putus-nyambung. Hanya jalankan penjaga kalau
# user memang sudah set THERMAL_KILL=1 secara eksplisit DAN belum tk_off.
TK_CONF=/data/adb/azenith-ai/azenith-ai.conf
if [ ! -f /data/adb/azenith-ai/tk_off ] && [ -f "$TK_CONF" ] \
   && grep -q '^THERMAL_KILL=1' "$TK_CONF"; then
  sed -i 's/^BYPASS_THERMAL=1/BYPASS_THERMAL=0/' "$TK_CONF" 2>/dev/null
  nohup sh "$MODDIR/lib/tkloop.sh" >/dev/null 2>&1 &
fi
# Thermal Lock Daily tahan reboot: kalau THERMAL_DAILY=1 di conf, terapkan ulang
# (fitur A non-stop, daily + gaming - node emul/freeze non-persisten, hilang tiap
# reboot) lalu jalankan watchdog-nya lagi.
if grep -q '^THERMAL_DAILY=1' /data/adb/azenith-ai/azenith-ai.conf 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf; scan_if_needed >/dev/null 2>&1; [ "$THERMAL_MODE" != off ] && td_apply >/dev/null 2>&1 || flog THERMAL_DAILY ERR "boot: terapkan ulang gagal" ) >/dev/null 2>&1 &
  nohup sh "$MODDIR/lib/tdloop.sh" >/dev/null 2>&1 &
fi
# Bypass Charging tahan reboot: kalau CHARGE_BYPASS=1 di conf, jalankan watchdog-nya lagi
# (node charger non-persisten, hilang tiap reboot - harus ditulis ulang + diverifikasi lagi)
if grep -q '^CHARGE_BYPASS=1' /data/adb/azenith-ai/azenith-ai.conf 2>/dev/null; then
  nohup sh "$MODDIR/lib/chargeloop.sh" >/dev/null 2>&1 &
fi
# Fast Charger (v1.0.15) = Disable Thermal saja: tahan reboot lewat THERMAL_KILL=1 di atas
# (tkloop.sh), tidak ada watchdog arus lagi. Kunci mode balance/saver tidak ikut reboot -
# pasang lagi kalau FAST_CHARGE masih 1.
if grep -q '^FAST_CHARGE=1' /data/adb/azenith-ai/azenith-ai.conf 2>/dev/null; then
  : > /data/adb/azenith-ai/run/fc_mode_lock 2>/dev/null
  # kunci suhu zona thermal (chmod) hilang tiap reboot -> pasang lagi setelah boot selesai
  ( . "$MODDIR/common.sh"; load_conf
    fb_n=0; while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$fb_n" -lt 180 ]; do sleep 2; fb_n=$((fb_n + 2)); done
    sleep 15
    [ "$FAST_CHARGE" = 1 ] && { : > "$FC_TEMP_BK"; fc_temp_lock >/dev/null 2>&1; }
  ) >/dev/null 2>&1 &
fi
# Network Booster tahan reboot: sysctl & node non-persisten hilang tiap
# reboot - diterapkan ulang di sini (late_start, setelah interface jaringan &
# WiFi sudah siap). Dijalankan via common.sh (bukan nohup biasa) supaya
# fungsi-fungsi lib/netboost.sh kebaca dulu.
NB_CONF=/data/adb/azenith-ai/azenith-ai.conf
if grep -q '^NETBOOST=1' "$NB_CONF" 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf
    [ "$NETBOOST" = 1 ] && flog_run NETBOOST "boot: terapkan ulang" nb_on >/dev/null 2>&1
  ) >/dev/null 2>&1 &
fi
# WiFi Low Latency tahan reboot: force-low-latency-mode & settings Wi-Fi hilang tiap
# reboot - terapkan ulang setelah boot selesai (service Wi-Fi harus sudah hidup).
if grep -q '^WIFILL=1' /data/adb/azenith-ai/azenith-ai.conf 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf
    wl_n=0; while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$wl_n" -lt 180 ]; do sleep 2; wl_n=$((wl_n + 2)); done
    sleep 8
    [ "$WIFILL" = 1 ] && flog_run WIFILL "boot: terapkan ulang" wl_on >/dev/null 2>&1
  ) >/dev/null 2>&1 &
fi
# WiFi Tweak tahan reboot: pengaturan `cmd wifi` hilang/direset tiap reboot - terapkan ulang
# setelah service Wi-Fi hidup (sama pola dengan WiFi Low Latency).
if grep -q '^WIFITWEAK=1' /data/adb/azenith-ai/azenith-ai.conf 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf
    wt_n=0; while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$wt_n" -lt 180 ]; do sleep 2; wt_n=$((wt_n + 2)); done
    sleep 12
    [ "$WIFITWEAK" = 1 ] && flog_run WIFITWEAK "boot: terapkan ulang" wt_on >/dev/null 2>&1
  ) >/dev/null 2>&1 &
fi
# Perf Lite tahan reboot: tweak sysctl/sysfs/prop non-persisten hilang tiap reboot - terapkan
# ulang setelah boot selesai (+ FSTrim sekali). pl_boot_apply juga memastikan Fast Charger mati
# dan mode saver/extreme tidak ikut dipulihkan bersama Perf Lite (aturan bentrok).
if grep -q '^PERFLITE=1' /data/adb/azenith-ai/azenith-ai.conf 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf
    pl_n=0; while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$pl_n" -lt 180 ]; do sleep 2; pl_n=$((pl_n + 2)); done
    sleep 25
    [ "$PERFLITE" = 1 ] && flog_run PERFLITE "boot: terapkan ulang" pl_boot_apply >/dev/null 2>&1
  ) >/dev/null 2>&1 &
fi
# Graphics API Universal tahan reboot: kalau GFX_UNIVERSAL=1 di conf,
# terapkan ulang (debug.hwui.renderer & Settings.Global angle_gl_driver_all_angle
# non-persisten, hilang tiap reboot) - berlaku SELURUH SISTEM, bukan per-game.
GU_CONF=/data/adb/azenith-ai/azenith-ai.conf
if grep -q '^GFX_UNIVERSAL=1' "$GU_CONF" 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf
    [ "$GFX_UNIVERSAL" = 1 ] && flog_run GFXAPI "boot: terapkan ulang" gu_on "$GFX_UNIVERSAL_API" >/dev/null 2>&1
  ) >/dev/null 2>&1 &
fi
# zRAM tahan reboot (opsional): kalau ZRAM_BOOT=1 di zram.conf, terapkan ulang pengaturan terakhir
# setelah boot selesai (menunggu init selesai mengatur swap bawaan dulu).
if grep -q '^ZRAM_BOOT=1' /data/adb/azenith-ai/zram.conf 2>/dev/null; then
  ( . "$MODDIR/common.sh"; load_conf
    zb_n=0; while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$zb_n" -lt 180 ]; do sleep 2; zb_n=$((zb_n + 2)); done
    sleep 20
    flog_run ZRAM "boot: terapkan ulang" zr_boot_apply >/dev/null 2>&1
  ) >/dev/null 2>&1 &
fi
# Companion App: pasang / upgrade otomatis dari app/AzenithAI-Companion.apk (di-background supaya tidak menahan boot)
sh "$MODDIR/lib/app_install.sh" >/dev/null 2>&1 &
# azenith-thermal: properti sys.azenith.thermal.state non-persisten (hilang
# tiap reboot) - pulihkan mode terakhir (balance/saver/gaming/extreme) kalau
# user pernah memilih salah satunya.
[ -f /data/adb/azenith-ai/azenith_thermal_mode ] && PATH="$MODDIR/system/bin:$PATH" nohup sh "$MODDIR/system/bin/azenith-thermal" restore >/dev/null 2>&1 &
exit 0
