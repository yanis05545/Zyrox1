package com.azenithtech.azenithai.core.util

import java.util.Locale

/** "key=value" per baris -> Map (toleran: baris lain diabaikan). */
fun String.kv(): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    for (l in lineSequence()) {
        val i = l.indexOf('=')
        if (i > 0) m[l.substring(0, i).trim()] = l.substring(i + 1).trim()
    }
    return m
}

/** Parser azenith-ai.conf: abaikan komentar, buang komentar di belakang nilai. */
fun String.confKv(): Map<String, String> {
    val m = LinkedHashMap<String, String>()
    for (raw in lineSequence()) {
        val l = raw.trim()
        if (l.isEmpty() || l.startsWith("#")) continue
        val i = l.indexOf('=')
        if (i <= 0) continue
        val v = l.substring(i + 1).split(Regex("\\s+#"), limit = 2)[0].trim().trim('"')
        m[l.substring(0, i).trim()] = v
    }
    return m
}

fun Map<String, String>.long(k: String): Long? = this[k]?.trim()?.toLongOrNull()
fun Map<String, String>.dbl(k: String): Double? = this[k]?.trim()?.toDoubleOrNull()

fun fmt(d: Double, n: Int = 1): String = String.format(Locale.US, "%.${n}f", d)

fun fmtKb(kb: Long): String = when {
    kb >= 1_048_576 -> fmt(kb / 1_048_576.0, 2) + " GB"
    kb >= 1024 -> fmt(kb / 1024.0, 1) + " MB"
    else -> "$kb KB"
}

fun fmtHz(hz: Long): String = when {
    hz >= 1_000_000_000 -> fmt(hz / 1e9, 2) + " GHz"
    hz >= 1_000_000 -> (hz / 1_000_000).toString() + " MHz"
    else -> "$hz Hz"
}

fun fmtKhz(khz: Long): String = if (khz >= 1_000_000) fmt(khz / 1e6, 2) + " GHz" else (khz / 1000).toString() + " MHz"

/** Suhu mentah (°C, 1/10 °C, atau m°C) -> teks °C. */
fun tempC(raw: String?): String {
    val v = raw?.trim()?.toDoubleOrNull() ?: return "-"
    val c = when {
        v >= 1000 -> v / 1000.0
        v >= 150 -> v / 10.0
        else -> v
    }
    return fmt(c, 1) + " °C"
}
