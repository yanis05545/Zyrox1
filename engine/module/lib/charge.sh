#!/system/bin/sh
# =====================================================================
#  Azenith-AI - lib/charge.sh : Bypass Charging (versi simpel, 2 command)
#
#   ON  (bypass aktif, baterai tidak menerima daya) : cmd battery set status 4
#   OFF (normal, baterai menerima daya lagi)        : cmd battery reset
#
#  Module berjalan sebagai root, jadi "su -c" tidak perlu lagi - command
#  dipanggil langsung. Status 4 = BATTERY_STATUS_NOT_CHARGING.
#  chargeloop.sh hanya menulis ulang status 4 berkala, karena override
#  ini bisa tertimpa saat charger dicolok/dicabut atau layar nyala/mati.
# =====================================================================

CHG_PID="$RUNDIR/chg.pid"

chg_setconf() { # chg_setconf KEY VAL
  if grep -q "^$1=" "$CONF" 2>/dev/null; then sed -i "s/^$1=[^ #]*/$1=$2/" "$CONF"
  else echo "$1=$2" >> "$CONF"; fi
}

# ---- 2 command inti ----
chg_cmd_on()  { cmd battery set status 4 >/dev/null 2>&1; }
chg_cmd_off() { cmd battery reset        >/dev/null 2>&1; }

# ---- helper baca baterai (dipakai azenith-ai status) ----
chg_bat_dir() {
  for cb_d in /sys/class/power_supply/battery /sys/class/power_supply/Battery; do
    [ -d "$cb_d" ] && { echo "$cb_d"; return 0; }
  done
  return 1
}
chg_cur_status() { cs_bd=$(chg_bat_dir) 2>/dev/null; cat "$cs_bd/status" 2>/dev/null; }
chg_cur_level()  { cl_bd=$(chg_bat_dir) 2>/dev/null; cat "$cl_bd/capacity" 2>/dev/null; }
chg_plugged() {
  for cp_d in /sys/class/power_supply/*; do
    case "$(cat "$cp_d/type" 2>/dev/null)" in
      USB|Mains|Wireless|DC|usb|mains|wireless|dc)
        [ "$(cat "$cp_d/online" 2>/dev/null)" = 1 ] && return 0 ;;
    esac
  done
  return 1
}

# ---- on / off / status ----
chg_on() {
  load_conf
  chg_cmd_on
  chg_setconf CHARGE_BYPASS 1
  chg_loop_start
  log_msg "CHARGE: Bypass Charging AKTIF (cmd battery set status 4)"
  echo "Bypass Charging: AKTIF (cmd battery set status 4)"
  sleep 1
  chg_v=$(chg_cur_status)
  case "$chg_v" in
    "Not charging"|"Discharging") ;;
    *) echo "PERINGATAN: status baterai nyata masih '${chg_v:-kosong}', bukan 'Not charging' - 'cmd battery' mungkin tidak didukung ROM/device ini" ;;
  esac
  chg_status
}

chg_off() {
  load_conf
  chg_loop_stop
  chg_cmd_off
  chg_setconf CHARGE_BYPASS 0
  log_msg "CHARGE: Bypass Charging MATI (cmd battery reset)"
  echo "Bypass Charging: MATI (cmd battery reset, baterai menerima daya normal lagi)"
  chg_status
}

# dipanggil uninstall.sh: pastikan override dilepas
chg_restore_node() { chg_cmd_off; }

chg_diag() { chg_status; echo "Mode: cmd battery set status 4 (on) / cmd battery reset (off)"; }

# ---------------------------------------------------------------------
# chg_test - nyalakan Bypass Charging, baca BALIK status baterai NYATA dari
# sysfs (bukan cuma asumsi command sukses), lalu matikan lagi & baca ulang -
# supaya ketahuan statusnya beneran pindah ke "Not charging" di kernel atau
# cuma gimik di WebUI yang tidak benar2 menghentikan arus charge.
# ---------------------------------------------------------------------
chg_test() {
  testlog_begin bypass_charging
  testlog "--- SEBELUM ---"
  testlog "status=$(chg_cur_status)  level=$(chg_cur_level)%  charger_terpasang=$(chg_plugged && echo ya || echo tidak)"
  testlog ""
  testlog "--- MENYALAKAN Bypass Charging (cmd battery set status 4) ---"
  chg_on >/dev/null 2>&1
  sleep 1
  ct_st=$(chg_cur_status)
  testlog "status NYATA sesudah ON=$ct_st  (diharapkan: Not charging)"
  if [ "$ct_st" = "Not charging" ]; then
    testlog "[NYATA-BEKERJA] status baterai benar2 berubah di sysfs, bukan kosmetik WebUI"
  else
    testlog "[PERLU DICEK] status tidak seperti yang diharapkan - cek apakah 'cmd battery' didukung di ROM/device ini (su -c \"cmd battery get status\")"
  fi
  testlog ""
  testlog "--- MEMATIKAN Bypass Charging (cmd battery reset) ---"
  chg_off >/dev/null 2>&1
  sleep 1
  testlog "status NYATA sesudah OFF=$(chg_cur_status)  level=$(chg_cur_level)%  (diharapkan kembali normal/Charging kalau charger terpasang)"
  testlog_end
}

chg_status() {
  load_conf
  echo "CHARGE_BYPASS=$CHARGE_BYPASS  mode=cmd  loop=$(chg_loop_alive && echo jalan || echo mati)"
  echo "baterai: level=$(chg_cur_level)%  status=$(chg_cur_status)  charger_terpasang=$(chg_plugged && echo ya || echo tidak)"
}

# ---- watchdog ----
chg_loop_alive() { cla_p=$(cat "$CHG_PID" 2>/dev/null); [ -n "$cla_p" ] && kill -0 "$cla_p" 2>/dev/null; }
chg_loop_start() {
  chg_loop_alive && return 0
  nohup sh "$MODDIR/lib/chargeloop.sh" >/dev/null 2>&1 &
}
chg_loop_stop() {
  cls_p=$(cat "$CHG_PID" 2>/dev/null)
  [ -n "$cls_p" ] && kill "$cls_p" 2>/dev/null
  rm -f "$CHG_PID"
}

# =====================================================================
#  Fast Charger (v1.0.15) - HANYA mematikan thermal. TIDAK menyentuh node
#  arus charge sama sekali (constant_charge_current dkk sudah dihapus).
#
#   ON  = Disable Thermal agresif (tk_on) + BLOCK respawn service thermal
#   OFF = kembalikan semuanya (tk_off) + TK_BLOCK ke nilai semula
#
#  Penjaganya adalah tkloop.sh milik Disable Thermal (tahan reboot lewat
#  THERMAL_KILL=1 di service.sh) - tidak ada lagi fcloop.sh / cutoff suhu
#  arus, karena yang dikontrol sekarang cuma thermal.
# =====================================================================
FC_PREV_TK="$DATADIR/fc_prev_tk"        # 1 = THERMAL_KILL sudah ON sebelum Fast Charger dinyalakan
FC_PREV_BLOCK="$DATADIR/fc_prev_block"  # nilai TK_BLOCK sebelum Fast Charger dinyalakan

# FC_MODE_LOCK - selama Fast Charger AKTIF, tombol mode balance & saver tidak
# direspon (as_reset_all milik kedua mode itu memanggil tk_off, yaitu diam-diam
# menyalakan thermal lagi). Dilepas begitu Fast Charger dimatikan.
FC_MODE_LOCK="$RUNDIR/fc_mode_lock"

fc_setconf() { chg_setconf "$1" "$2"; }

# ---------------------------------------------------------------------
# Kunci suhu zona thermal (diport dari disable_thermal.zip: chmod 000 pada
# temp & uevent). Efek: app/sistem NON-root tidak bisa membaca suhu zona
# sehingga tidak ikut men-throttle. Beda dari file aslinya:
#   - mode asli di-BACKUP dan dikembalikan persis oleh fc_temp_unlock
#   - zona baterai/charger/BMS/USB/gauge TIDAK disentuh (suhu baterai tetap terbaca)
#   - yang diubah hanya zona thermal, bukan semua node bernama "temp" di /sys/devices
# Modul ini sendiri berjalan sebagai root -> tetap bisa membaca semua suhu.
# ---------------------------------------------------------------------
FC_TEMP_BK="$DATADIR/fc_temp_modes"   # path|mode_asli

fc_temp_skip_zone() { # zona yang TIDAK boleh dikunci (terkait baterai/pengisian)
  fz_t=$(cat "$1/type" 2>/dev/null | tr 'A-Z' 'a-z')
  case "$fz_t" in
    *batt*|*bat_*|bat|*bms*|*charg*|*chg*|*usb*|*wireless*|*gauge*|*fuel*|*pmic_bat*) return 0 ;;
  esac
  return 1
}

fc_temp_lock() {
  [ -f "$FC_TEMP_BK" ] || : > "$FC_TEMP_BK"
  fl_n=0
  for fl_z in "$R"/sys/class/thermal/thermal_zone[0-9]*; do
    [ -d "$fl_z" ] || continue
    fc_temp_skip_zone "$fl_z" && continue
    for fl_f in "$fl_z/temp" "$fl_z/uevent"; do
      [ -e "$fl_f" ] || continue
      if ! grep -qF "$fl_f|" "$FC_TEMP_BK" 2>/dev/null; then
        fl_m=$(stat -c %a "$fl_f" 2>/dev/null)
        case "$fl_m" in ''|*[!0-7]*) case "$fl_f" in */temp) fl_m=444 ;; *) fl_m=644 ;; esac ;; esac
        echo "$fl_f|$fl_m" >> "$FC_TEMP_BK"
      fi
      chmod 000 "$fl_f" 2>/dev/null && fl_n=$((fl_n + 1))
    done
  done
  log_msg "FASTCHARGE: kunci suhu zona thermal -> $fl_n node dikunci (zona baterai/charger dilewati)"
  echo "Kunci suhu zona thermal: $fl_n node (zona baterai/charger/BMS/USB tidak disentuh)"
}

fc_temp_unlock() {
  fu_n=0
  if [ -s "$FC_TEMP_BK" ]; then
    while IFS='|' read -r fu_f fu_m; do
      [ -n "$fu_f" ] && [ -e "$fu_f" ] && chmod "${fu_m:-444}" "$fu_f" 2>/dev/null && fu_n=$((fu_n + 1))
    done < "$FC_TEMP_BK"
  fi
  : > "$FC_TEMP_BK"
  echo "Kunci suhu zona thermal: dilepas ($fu_n node dikembalikan ke mode asli)"
}

# suhu BATERAI (derajat C bulat), dibaca root dari power_supply - tidak pernah dikunci
chg_batt_temp() {
  cbt_bd=$(chg_bat_dir) 2>/dev/null || return 1
  norm_temp "$(cat "$cbt_bd/temp" 2>/dev/null)"
}

# dipanggil daemon (lib/daemon.sh). Fast Charger menyembunyikan suhu zona -> kalau baterai
# terlalu panas, matikan otomatis. FC_BATT_MAX_C=0 menonaktifkan.
fc_watch() {
  grep -q '^FAST_CHARGE=1' "$CONF" 2>/dev/null || return 0
  fw_max=$(sed -n 's/^FC_BATT_MAX_C=\([0-9]*\).*/\1/p' "$CONF" 2>/dev/null | tail -n 1); fw_max=${fw_max:-50}
  [ "$fw_max" -gt 0 ] 2>/dev/null || return 0
  fw_t=$(chg_batt_temp) || return 0
  [ -n "$fw_t" ] && [ "$fw_t" -ge "$fw_max" ] 2>/dev/null || return 0
  log_msg "FASTCHARGE: baterai ${fw_t}C >= ${fw_max}C -> Fast Charger dimatikan otomatis"
  flog FASTCHARGE BUG "baterai ${fw_t}C >= batas ${fw_max}C -> Fast Charger dimatikan otomatis (pengaman)"
  load_conf; fc_off >/dev/null 2>&1
}

fc_on() {
  load_conf
  # Perf Lite AKTIF -> Fast Charger tidak boleh menyala (lib/perflite.sh, 2 fitur bentrok)
  if [ "$PERFLITE" = 1 ] || [ -f "$RUNDIR/pl_mode_lock" ]; then
    echo "Fast Charger DITOLAK: Perf Lite sedang AKTIF (Fast Charger otomatis mati selama Perf Lite aktif)."
    echo "Matikan Perf Lite dulu (azenith-ai perflite off / toggle WebUI) baru Fast Charger bisa dinyalakan."
    flog FASTCHARGE INFO "Fast Charger ditolak - Perf Lite aktif"
    return 1
  fi
  # catat keadaan awal HANYA kalau belum pernah dicatat (on dua kali jangan menimpa)
  if [ ! -f "$FC_PREV_TK" ]; then
    echo "${THERMAL_KILL:-0}" > "$FC_PREV_TK"
    echo "${TK_BLOCK:-0}" > "$FC_PREV_BLOCK"
  fi
  fc_setconf FAST_CHARGE 1
  # BLOCK wajib: tanpa ini service HAL thermal dinyalakan ulang hwservicemanager
  # (ctl.interface_start) dan status "running" muncul lagi. Aman untuk combo-HAL
  # karena tk_stop_one melewati BLOCK/kill -9 untuk binary yang dipakai bareng.
  fc_setconf TK_BLOCK 1
  fc_setconf TK_BLOCK_AFTER 1
  load_conf
  : > "$FC_MODE_LOCK"
  log_msg "FASTCHARGE: AKTIF (khusus matikan thermal, node arus TIDAK disentuh)"
  flog FASTCHARGE INFO "Fast Charger ON -> Disable Thermal + BLOCK"
  echo "Fast Charger: AKTIF (mematikan thermal saja, arus charge tidak diubah)"
  tk_on quick >/dev/null
  sleep 3
  [ -n "$(tk_list_running)" ] && { tk_enforce force; sleep 2; tk_enforce; }
  echo "Disable Thermal: AKTIF"
  fc_temp_lock
  echo "Pengaman: Fast Charger otomatis mati kalau suhu baterai >= ${FC_BATT_MAX_C:-50}C (FC_BATT_MAX_C, 0 = nonaktif)"
  echo "catatan: mode balance & saver tidak direspon selama Fast Charger aktif - matikan Fast Charger dulu untuk ganti mode"
  fc_status
}

fc_off() {
  load_conf
  fc_setconf FAST_CHARGE 0
  rm -f "$FC_MODE_LOCK"
  # kalau Disable Thermal sudah ON sebelum Fast Charger dinyalakan (mis. dari mode
  # ZAI-Turbo), biarkan tetap ON - Fast Charger cuma mencabut bagiannya sendiri.
  fc_was=$(cat "$FC_PREV_TK" 2>/dev/null)
  fc_wb=$(cat "$FC_PREV_BLOCK" 2>/dev/null)
  if [ "$fc_was" = 1 ]; then
    echo "Disable Thermal sudah ON sebelum Fast Charger - dibiarkan tetap ON"
  else
    tk_off
  fi
  fc_temp_unlock
  fc_setconf TK_BLOCK "${fc_wb:-0}"
  rm -f "$FC_PREV_TK" "$FC_PREV_BLOCK"
  log_msg "FASTCHARGE: MATI (thermal dikembalikan)"
  flog FASTCHARGE INFO "Fast Charger OFF"
  echo "Fast Charger: MATI (mode balance & saver kembali direspon normal)"
  fc_status
}

fc_status() {
  load_conf
  fc_n=$(tk_list_running | wc -l)
  echo "suhu_zona_dikunci=$(grep -c . "$FC_TEMP_BK" 2>/dev/null || echo 0) node  batas_suhu_baterai=${FC_BATT_MAX_C:-50}C  suhu_baterai_sekarang=$(chg_batt_temp 2>/dev/null)C"
  echo "FAST_CHARGE=$FAST_CHARGE  thermal_kill=$THERMAL_KILL  penjaga=$(tk_loop_alive && echo jalan || echo mati)  block=$(grep -c . "$TK_MNT" 2>/dev/null || echo 0)  service_thermal_hidup=${fc_n:-0}"
  if [ "${fc_n:-0}" -gt 0 ] 2>/dev/null; then
    echo "masih running/restarting:"
    tk_list_running | while read -r fc_s; do echo "  $fc_s"; done
  fi
  [ -s "$DATADIR/tk_lasterr" ] && { echo "error terakhir:"; tail -n 2 "$DATADIR/tk_lasterr"; }
}

fc_diag() { fc_status; tk_status; }

# dipanggil uninstall.sh - thermal dikembalikan lewat tk_restore_all di sana
fc_restore_node() { fc_temp_unlock >/dev/null 2>&1; rm -f "$FC_MODE_LOCK" "$FC_PREV_TK" "$FC_PREV_BLOCK"; }
fc_loop_stop() { :; }
