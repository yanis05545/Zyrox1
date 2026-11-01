package com.azenithtech.azenithai.features.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.core.util.*
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import com.azenithtech.azenithai.network.client.HttpPing
import com.azenithtech.azenithai.network.websocket.WsPing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun latClass(ms: Int): Int = when { ms < 0 -> -1; ms < 30 -> 0; ms <= 80 -> 1; ms <= 150 -> 2; else -> 3 }

@Composable
fun MonitorScreen(active: Boolean) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var net by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var gpu by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val cpuHist = remember { mutableStateListOf<Float>() }
    val latHist = remember { mutableStateListOf<Float>() }
    var lat by remember { mutableStateOf(-1) }
    var latMethod by remember { mutableStateOf("-") }
    var jitter by remember { mutableStateOf("-") }
    var loss by remember { mutableStateOf("-") }
    var wsMs by remember { mutableStateOf(-1) }
    var critical by remember { mutableStateOf(false) }
    var inCrit by remember { mutableStateOf(false) }
    var wsUrl by remember { mutableStateOf(ui.wsUrl) }

    // Throttling WebUI: latensi tinggi -> kurangi frekuensi polling
    val interval by rememberUpdatedState(when (latClass(lat)) { 2 -> 4000L; 3 -> 6000L; else -> 2000L })

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (gpu.isEmpty()) gpu = Az.gpuinfo()
        while (true) {
            m = Az.monitor()
            m["cpu_load_all"]?.toFloatOrNull()?.let { cpuHist.add(it); if (cpuHist.size > 40) cpuHist.removeAt(0) }
            delay(interval)
        }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) { net = Az.netinfo(); delay(10_000) }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            val p = Az.ping()
            val icmp = p["ms"]?.toDoubleOrNull()?.toInt() ?: -1
            if (icmp >= 0) { lat = icmp; latMethod = "ICMP (root) → ${p["host"] ?: "-"}"; jitter = p["jitter"] ?: "-"; loss = p["loss"] ?: "-" }
            else {
                val h = HttpPing.measure()
                lat = h; latMethod = if (h >= 0) "HTTP (fallback)" else "tidak terdeteksi"; jitter = "-"; loss = "-"
            }
            if (lat >= 0) { latHist.add(lat.toFloat()); if (latHist.size > 40) latHist.removeAt(0) }
            val now = lat > 150
            if (now && !inCrit) critical = true
            inCrit = now
            delay(5_000)
        }
    }

    Page("Monitor") {
        // CPU
        AzCard("CPU") {
            val all = m["cpu_load_all"]?.toDoubleOrNull()
            Kv("Beban CPU", all?.let { fmt(it, 0) + " %" })
            Meter(((all ?: 0.0) / 100).toFloat())
            Spark(cpuHist, 100f)
            val cur = m["cpu_cur_khz"]?.split(",")?.map { it.trim().toLongOrNull() }.orEmpty()
            val max = m["cpu_max_khz"]?.split(",")?.map { it.trim().toLongOrNull() }.orEmpty()
            Text("Per cluster", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            cur.forEachIndexed { i, c ->
                Kv("Cluster $i", c?.let { fmtKhz(it) + (max.getOrNull(i)?.let { mx -> " / " + fmtKhz(mx) } ?: "") })
            }
            Text("Per inti", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            val cores = m.keys.filter { it.startsWith("cpu_load_") && it != "cpu_load_all" }
                .sortedBy { it.removePrefix("cpu_load_").toIntOrNull() ?: 99 }
            cores.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Column(Modifier.weight(1f)) {
                            Muted("core ${k.removePrefix("cpu_load_")}")
                            Text((m[k] ?: "-") + "%", fontSize = 13.sp)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // GPU
        AzCard("GPU") {
            val cur = m.long("gpu_cur_hz"); val mx = m.long("gpu_max_hz")
            Kv("GPU", listOfNotNull(gpu["vendor"], gpu["model"]).joinToString(" "))
            Kv("Clock", cur?.let { fmtHz(it) + (mx?.let { x -> " / " + fmtHz(x) } ?: "") })
            Kv("Beban GPU", m["gpu_load_pct"]?.let { "$it %" })
            if (cur != null && mx != null && mx > 0) Meter(cur.toFloat() / mx)
            Kv("Suhu GPU (SoC)", tempC(m["t_soc"]))
        }
        // RAM
        AzCard("RAM") {
            val t = m.long("ram_total_kb"); val a = m.long("ram_avail_kb")
            Kv("RAM", t?.let { fmtKb(it) })
            Kv("Tersedia", a?.let { fmtKb(it) })
            Kv("Terpakai", if (t != null && a != null) fmtKb(t - a) + " (${m["ram_used_pct"] ?: "-"}%)" else null)
            if (t != null && a != null && t > 0) Meter((t - a).toFloat() / t)
        }
        // Jaringan
        AzCard("Jaringan") {
            val wifi = net["net_wifi_ssid"].orEmpty().isNotBlank()
            Kv("Aktivitas", m["net_conn"] ?: net["net_conn"])
            Kv("↓ Unduh", m["net_rx_kBps"]?.let { "$it kB/s" })
            Kv("↑ Unggah", m["net_tx_kBps"]?.let { "$it kB/s" })
            if (wifi) {
                Kv("SSID", net["net_wifi_ssid"]); Kv("RSSI", net["net_wifi_rssi_dbm"]?.let { "$it dBm" })
                Kv("Link speed", net["net_wifi_linkspeed_mbps"]?.let { "$it Mbps" })
            } else {
                Kv("Operator", net["net_mobile_operator"]); Kv("Jenis", listOfNotNull(net["net_mobile_gen"], net["net_mobile_rat"]).joinToString(" "))
                Kv("Sinyal", net["net_mobile_dbm"]?.let { "$it dBm" })
            }
        }
        // Latensi
        AzCard("Latensi (WiFi Low Latency)") {
            val c = latClass(lat)
            Text(if (lat >= 0) "$lat ms" else "-- ms", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = when (c) { 0, 1 -> AzColors.Good; 2 -> AzColors.Warn; 3 -> AzColors.Bad; else -> AzColors.Muted })
            Kv("Metode", latMethod); Kv("Jitter", jitter); Kv("Loss", loss)
            Kv("WebSocket (Cara A)", if (wsMs >= 0) "$wsMs ms" else "-")
            Spark(latHist, (latHist.maxOrNull() ?: 100f).coerceAtLeast(100f), AzColors.Warn)
            HorizontalDivider()
            listOf(
                "< 30 ms" to "Sangat Baik · semua real-time aktif",
                "30 - 80 ms" to "Normal · jalan seperti biasa",
                "80 - 150 ms" to "Latensi Tinggi (Lag) · indikator kuning, polling diturunkan",
                "> 150 ms" to "Kritis / Buruk · peringatan, animasi berat dimatikan",
            ).forEachIndexed { i, (a, b) ->
                Text("$a — $b", fontSize = 12.sp, color = if (i == c) AzColors.Accent else AzColors.Muted,
                    fontWeight = if (i == c) FontWeight.Bold else FontWeight.Normal)
            }
            HorizontalDivider()
            Text("Pengaturan server WebSocket (Cara A)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(wsUrl, { wsUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("wss://…") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Simpan", { ui.wsUrl = wsUrl; ui.save(); scope.launch { wsMs = WsPing.measure(wsUrl) } })
                AzButton("Reset", { wsUrl = "wss://ws.postman-echo.com/raw"; ui.wsUrl = wsUrl; ui.save() }, tonal = true)
            }
            Muted("Acuan utama: ICMP asli via root (WiFi & data seluler). Cara A hanya pembanding.")
        }
        // Suhu
        AzCard("Suhu") {
            Kv("CPU / SoC", tempC(m["t_soc"]))
            Kv("GPU", tempC(m["t_soc"]))
            Kv("Baterai", tempC(m["t_batt"]))
            Kv("Skin", tempC(m["t_skin"]))
        }
    }

    if (critical && active) {
        AlertDialog(
            onDismissRequest = { critical = false },
            title = { Text("Koneksi Kritis") },
            text = { Text("Latensi $lat ms (> 150 ms). Jaringan sangat buruk; animasi berat dimatikan dan polling diperlambat.") },
            confirmButton = { TextButton({ critical = false }) { Text("Mengerti") } },
        )
    }
}
