#!/system/bin/sh
# Tombol Action di Manager: buka Companion App (dipasang dulu kalau belum ada).
MODDIR=${0%/*}
. "$MODDIR/lib/license.sh"
if ! lic_check; then
  echo "Lisensi terkunci untuk perangkat ini: $(lic_status 2>&1)"
  echo "Buka WebUI dari Manager untuk memasukkan key, atau hubungi penjual."
  exit 0
fi
PKG="com.azenithtech.azenithai"
if ! pm path "$PKG" >/dev/null 2>&1; then
  if [ -f "$MODDIR/app/AzenithAI-Companion.apk" ]; then
    echo "Memasang Companion App..."
    sh "$MODDIR/lib/app_install.sh"
  else
    echo "Companion App tidak ada di module ini. Buka WebUI dari Manager sebagai gantinya."
    exit 0
  fi
fi
am start -n "$PKG/$PKG.MainActivity" >/dev/null 2>&1 \
  && echo "Companion App dibuka." \
  || echo "Gagal membuka app. Cek log: /data/adb/azenith-ai/app_install.log"
