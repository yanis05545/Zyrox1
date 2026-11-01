package com.azenithtech.azenithai.core

/** Semua path modul di satu tempat (lihat blueprint bagian 3). */
object Paths {
    const val MODDIR = "/data/adb/modules/azenith-ai"
    const val DATADIR = "/data/adb/azenith-ai"
    const val CLI = "sh $MODDIR/system/bin/azenith-ai"
    const val THERMAL = "sh $MODDIR/system/bin/azenith-thermal"
    const val LICENSE = "sh $MODDIR/lib/license.sh"
    const val CONF = "$DATADIR/azenith-ai.conf"
    const val CONF_DEFAULT = "$MODDIR/azenith-ai.conf.default"
    const val TMP = "/data/local/tmp"
}
