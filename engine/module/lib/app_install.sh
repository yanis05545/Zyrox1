#!/system/bin/sh
# Azenith-AI - lib/app_install.sh : pasang / upgrade Companion App dari dalam module.
# Dipanggil oleh service.sh (tiap boot) dan action.sh. Aman dijalankan berulang:
# hanya memasang kalau app belum ada atau APK di module berubah (checksum beda).
MODDIR=${MODDIR:-${0%/lib/*}}
APK="$MODDIR/app/AzenithAI-Companion.apk"
PKG="com.azenithtech.azenithai"
DATADIR=/data/adb/azenith-ai
SUMF="$DATADIR/app_apk.sum"
LOGF="$DATADIR/app_install.log"
mkdir -p "$DATADIR"

# Best-effort: beri izin root otomatis ke app (Magisk saja, lewat policies db). KernelSU/APatch tetap
# menampilkan dialog izin di Manager; app sendiri juga terkunci sampai root diizinkan.
grant_root() {
  command -v magisk >/dev/null 2>&1 || return 0
  u=$(pm list packages -U "$PKG" 2>/dev/null | sed -n 's/.*uid:\([0-9][0-9]*\).*/\1/p' | head -n1)
  [ -n "$u" ] || return 0
  magisk --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($u,2,0,1,1)" >/dev/null 2>&1
}

[ -f "$APK" ] || { echo "APK tidak ada di $APK"; exit 1; }
SUM=$(cksum "$APK" 2>/dev/null | cut -d' ' -f1,2)
OLD=$(cat "$SUMF" 2>/dev/null)

if pm path "$PKG" >/dev/null 2>&1 && [ "$SUM" = "$OLD" ]; then
  echo "app sudah terpasang & terbaru"; exit 0
fi

# pm tidak selalu boleh membaca /data/adb (SELinux) -> salin dulu ke /data/local/tmp
TMP=/data/local/tmp/azenithai-install.apk
cp -f "$APK" "$TMP" && chmod 644 "$TMP" || { echo "gagal menyalin APK ke $TMP"; exit 1; }
OUT=$(pm install -r -g "$TMP" 2>&1)
rm -f "$TMP"
echo "$(date '+%Y-%m-%d %H:%M:%S') pm install: $OUT" >> "$LOGF"
case "$OUT" in
  *Success*) echo "$SUM" > "$SUMF"; grant_root; echo "app terpasang" ;;
  *SIGNATURE*|*signatures*|*INCONSISTENT*)
    # APK baru ditandatangani key lain dari yang terpasang: copot dulu lalu pasang ulang
    pm uninstall "$PKG" >/dev/null 2>&1
    cp -f "$APK" "$TMP" && chmod 644 "$TMP"
    OUT=$(pm install -r -g "$TMP" 2>&1); rm -f "$TMP"
    echo "$(date '+%Y-%m-%d %H:%M:%S') reinstall: $OUT" >> "$LOGF"
    case "$OUT" in *Success*) echo "$SUM" > "$SUMF"; grant_root; echo "app terpasang (setelah copot versi lama)" ;; *) echo "gagal: $OUT"; exit 1 ;; esac ;;
  *) echo "gagal: $OUT"; exit 1 ;;
esac
exit 0
