#!/system/bin/sh
# Azenith-AI v2 - uninstall.sh: kembalikan semua state lalu hapus data.
MODDIR=/data/adb/modules/azenith-ai
DATADIR=/data/adb/azenith-ai
if [ -f "$MODDIR/common.sh" ]; then
  . "$MODDIR/common.sh"
  load_conf 2>/dev/null
  cleanup_all 2>/dev/null
  tk_loop_stop 2>/dev/null; tk_restore_all 2>/dev/null   # lepas BLOCK + hidupkan lagi service thermal
  chg_loop_stop 2>/dev/null; chg_restore_node 2>/dev/null  # WAJIB: jangan sampai device tidak bisa charge / salah lapor status baterai lagi setelah uninstall (menutup mode hardware maupun software/dumpsys)
  fc_restore_node 2>/dev/null    # bersihkan sisa kunci mode Fast Charger (thermal sudah dikembalikan oleh tk_restore_all di atas)
  downscale_revert 2>/dev/null                              # WAJIB: jangan sampai layar tertinggal di resolusi/density downscale setelah uninstall
  wm size reset >/dev/null 2>&1; wm density reset >/dev/null 2>&1   # jaring pengaman tambahan - paksa reset walau marker sesi sudah tidak ada
  nb_restore 2>/dev/null   # kembalikan sysctl Network Booster ke nilai asal
  [ -f "$RUNDIR/nb_wifi_ll" ] && { cmd wifi force-low-latency-mode disabled >/dev/null 2>&1; rm -f "$RUNDIR/nb_wifi_ll"; }   # sisa versi lama
  wl_off >/dev/null 2>&1   # WiFi Low Latency: kembalikan setting Wi-Fi ke nilai asal
  wt_off >/dev/null 2>&1   # WiFi Tweak: kembalikan cmd wifi ke default
  pl_restore_node 2>/dev/null   # Perf Lite: kembalikan swappiness/vfs/governor/prop/settings ke nilai asal
  perf_touch_revert 2>/dev/null  # WAJIB: fpsgo_enable/GED touch boost jangan tersisa aktif setelah uninstall
fi
# v2 tidak memakai resetprop -p (persisten). Hapus sisa v1 kalau ada.
for p in sys.thermal.engine.disable persist.sys.ui.hw persist.sys.input.boost vendor.mtk.fpsgo.enable \
         dalvik.vm.heapgrowthlimit debug.hwui.renderer; do
  case "$p" in
    persist.*|dalvik.*|debug.hwui.*) : ;;   # jangan hapus prop sistem asli secara membabi buta
    *) resetprop --delete "$p" 2>/dev/null ;;
  esac
done
rm -rf "$DATADIR"
exit 0
