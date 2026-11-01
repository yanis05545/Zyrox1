#!/system/bin/sh
# Azenith-AI v2 - customize.sh (di-source oleh installer Magisk / KernelSU / APatch)
SKIPUNZIP=0
TXVER=$(sed -n 's/^version=//p' "$MODPATH/module.prop" 2>/dev/null)
ui_print "*********************************************"
ui_print "  Azenith-AI Gaming Booster ${TXVER:-v2.6.1}"
ui_print "  scan dinamis + FPS sustain (FPSGO) + WebUI"
ui_print "*********************************************"

[ -n "$API" ] && [ "$API" -lt 28 ] && abort "! Butuh Android 9 (API 28) atau lebih baru."

if   [ -n "$KSU" ];    then ui_print "- Root manager: KernelSU"
elif [ -n "$APATCH" ]; then ui_print "- Root manager: APatch"
else                        ui_print "- Root manager: Magisk ${MAGISK_VER:-}"; fi

ui_print "- Mengatur permission"
set_perm_recursive "$MODPATH" 0 0 0755 0644
for f in post-fs-data.sh service.sh uninstall.sh safe_trigger.sh support.sh common.sh action.sh \
         lib/core.sh lib/profile.sh lib/scan.sh lib/thermal.sh lib/perf.sh lib/fps.sh lib/fpsgo.sh lib/misc.sh lib/daemon.sh \
         lib/supervisor.sh lib/daemon_run.sh lib/kernel_match.sh lib/kernel_scan.sh lib/devinfo.sh lib/thermalkill.sh lib/tkloop.sh lib/bypass.sh lib/app_install.sh lib/overclock.sh system/bin/azenith-ai system/bin/azenith-thermal; do
  [ -f "$MODPATH/$f" ] && set_perm "$MODPATH/$f" 0 0 0755
done

DATA=/data/adb/azenith-ai
mkdir -p "$DATA"
# konfigurasi v1 (kunci berbeda) disimpan sebagai cadangan, config baru dibuat saat boot
if [ -f "$DATA/azenith-ai.conf" ] && ! grep -q '^CONF_VERSION=2' "$DATA/azenith-ai.conf"; then
  mv -f "$DATA/azenith-ai.conf" "$DATA/azenith-ai.conf.v1.bak"
  ui_print "- Config lama dicadangkan: azenith-ai.conf.v1.bak (format berubah di v2)"
fi
rm -f "$DATA/boot_count" "$DATA/safe_mode"
# v1.0.9: penanda tk_off (sisa "safe on"/toggle lama) membuat Disable Thermal TIDAK dijalankan saat boot
# walau toggle tampil ON -> dihapus saat pasang supaya default permanen ON benar-benar berlaku.
rm -f "$DATA/tk_off"
rm -f "$DATA/scan/profile.conf"   # paksa scan ulang

# v1.0.9-fix: Disable Thermal TIDAK lagi dipaksa permanen - paksa-ON tiap
# boot/instal inilah yang kill -9 service *thermal* tiap 2 dtk tanpa henti
# dan di sebagian chipset MTK combo-HAL ikut menjatuhkan proses non-thermal
# yang nebeng binary sama (crash + jaringan blokir). Sekarang murni opt-in:
# conf lama yang sudah THERMAL_KILL=1 dibiarkan apa adanya (pilihan user),
# yang belum ada/masih 0 TIDAK diubah jadi 1 di sini lagi.
if [ -f "$DATA/azenith-ai.conf" ]; then
  # v1.0.3: nilai aman (penyebab app crash/freeze): matikan prop SF, BLOCK bind-mount, kill proses, kill-bg
  for kv in SF_BOOT_PROPS=0 TK_BLOCK=0 TK_KERNEL=0 TK_KILL_PROCS=0 RAM_KILL_BG=0; do
    k=${kv%%=*}
    if grep -q "^$k=" "$DATA/azenith-ai.conf"; then sed -i "s/^$k=.*/$kv/" "$DATA/azenith-ai.conf"; else echo "$kv" >> "$DATA/azenith-ai.conf"; fi
  done
  ui_print "- v1.0.3: fitur penyebab crash/freeze dimatikan secara default"
  ui_print "- v1.0.9-fix: Disable Thermal sekarang opt-in (azenith-ai thermalkill on), tidak dipaksa tiap boot"
fi

if [ -f "$MODPATH/app/AzenithAI-Companion.apk" ]; then
  ui_print "- Companion App: ikut di zip ini, dipasang otomatis saat boot (pm install)"
  ui_print "  (app butuh izin root dari Manager saat pertama dibuka)"
else
  ui_print "- Companion App: APK tidak ada di zip ini (taruh di app/AzenithAI-Companion.apk)"
fi
ui_print "- Selesai. Reboot, lalu cek:  su -c azenith-ai status"
ui_print "  Tahan Volume UP saat boot = safe mode (skip)."
