package com.azenithtech.azenithai.features.baterai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.core.util.*
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import com.azenithtech.azenithai.domain.Specs
import com.azenithtech.azenithai.features.common.ConfStore
import com.azenithtech.azenithai.features.common.ToggleCard
import kotlinx.coroutines.launch

@Composable
fun BateraiRamScreen(store: ConfStore) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var chargeStatus by remember { mutableStateOf("") }
    var clean by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var cleanBusy by remember { mutableStateOf(false) }
    var cleanMsg by remember { mutableStateOf("Tekan Pindai untuk menghitung ukuran.") }
    var z by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var zAlgo by remember { mutableStateOf("") }
    var zMb by remember { mutableStateOf(2048f) }
    var zSwap by remember { mutableStateOf(60f) }
    var zBusy by remember { mutableStateOf(false) }
    var zMsg by remember { mutableStateOf("") }

    suspend fun loadZram() {
        z = Az.run("zram status").out.kv()
        if (zAlgo.isBlank()) zAlgo = z["algo"] ?: ""
        z["disksize_mb"]?.toFloatOrNull()?.takeIf { it > 0 }?.let { zMb = it }
        z["swappiness"]?.toFloatOrNull()?.let { zSwap = it }
    }
    LaunchedEffect(Unit) { store.refresh(); chargeStatus = Az.run("charge status").text; loadZram() }

    Page("Battery & RAM") {
        ToggleCard(Specs.bypassCharge, store, action = { v -> Az.run(if (v) "charge on" else "charge off", 60_000).also { chargeStatus = Az.run("charge status").text } })
        if (chargeStatus.isNotBlank()) Muted("Status: " + chargeStatus.lines().lastOrNull { it.isNotBlank() }.orEmpty())
        AzButton("Diagnosa node charger", { scope.launch { info = "Diagnosa charger" to Az.run("charge diag", 60_000).out } }, tonal = true)

        ToggleCard(
            Specs.fastCharger, store,
            lockedReason = if (store.perfLiteOn) "Perf Lite aktif: Fast Charger dikunci (otomatis mati)" else null,
        )
        AzButton("Diagnosa thermal", { scope.launch { info = "Diagnosa thermal" to Az.run("fastcharge diag", 60_000).out } }, tonal = true)

        // Pembersih Sistem
        AzCard("Pembersih Sistem") {
            val rows = listOf(
                "Cache app (internal)" to "cache_app_kb", "Cache app (penyimpanan)" to "cache_ext_kb",
                "Cache sistem (package_cache)" to "cache_syscache_kb", "Dalvik-cache/OAT + /cache" to "cache_dalvik_kb",
                "Log crash (tombstones/ANR/dropbox)" to "crash_log_kb", "Sisa OTA/update" to "ota_kb",
                "File sementara (tmp/LOST.DIR)" to "tmp_kb", "Sampah app terhapus" to "trash_kb",
            )
            rows.forEach { (l, k) -> Kv(l, clean.long(k)?.let { fmtKb(it) }) }
            Kv("Total bisa dibersihkan", if (clean.isEmpty()) null else fmtKb(rows.sumOf { clean.long(it.second) ?: 0L }))
            Muted(cleanMsg)
            if (cleanBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Pindai", { scope.launch { cleanBusy = true; clean = Az.run("cleaner scan", 120_000).out.kv(); cleanMsg = "Pindai selesai."; cleanBusy = false } }, enabled = !cleanBusy)
                AzButton("Bersihkan (Soft)", { scope.launch { cleanBusy = true; cleanMsg = Az.run("cleaner clean soft", 300_000).text.lines().lastOrNull().orEmpty(); clean = Az.run("cleaner scan", 120_000).out.kv(); cleanBusy = false } }, enabled = !cleanBusy, tonal = true)
            }
            AzButton("Bersihkan (Agresif)", { scope.launch { cleanBusy = true; cleanMsg = Az.run("cleaner clean agresif", 600_000).text.lines().lastOrNull().orEmpty(); clean = Az.run("cleaner scan", 120_000).out.kv(); cleanBusy = false } }, enabled = !cleanBusy, tonal = true, modifier = Modifier.fillMaxWidth())
            Muted("Soft: cache app + sampah app terhapus. Agresif: semua kategori termasuk Dalvik/OAT dan /cache. Bukan antivirus.")
        }

        // zRAM
        AzCard("zRAM") {
            val exists = z["exists"] == "1"
            if (!exists) Muted(z["info"] ?: "zram0 belum ada. Tombol Terapkan akan mencoba memuat modul.")
            Kv("Status", if (z["active"] == "1") "Aktif" else "Mati")
            Kv("Algoritma aktif", z["algo"]); Kv("Ukuran (disksize)", z["disksize_mb"]?.let { "$it MB" })
            Kv("Data asli → terkompresi", if (z["orig_mb"] != null) "${z["orig_mb"]} MB → ${z["compr_mb"]} MB (rasio ${z["ratio"]})" else null)
            Kv("Memori fisik terpakai zRAM", z["used_mb"]?.let { "$it MB" })
            Kv("Swap terpakai / total", if (z["swap_total_mb"] != null) "${z["swap_used_mb"]} / ${z["swap_total_mb"]} MB" else null)
            Kv("Swappiness sekarang", z["swappiness"])
            HorizontalDivider()
            Text("Algoritma kompresi", fontSize = 13.sp)
            ChipRow { (z["algos"].orEmpty().replace("[", "").replace("]", "").split(" ").filter { it.isNotBlank() }).forEach { a -> AzChip(a, zAlgo == a, { zAlgo = a }) } }
            val maxMb = (z["max_mb"]?.toFloatOrNull() ?: 8192f).coerceAtLeast(512f)
            Text("Ukuran zRAM: ${zMb.toInt()} MB", fontSize = 13.sp)
            Slider(zMb, { zMb = (it / 128).toInt() * 128f }, valueRange = 128f..maxMb)
            Text("Swappiness: ${zSwap.toInt()}", fontSize = 13.sp)
            Slider(zSwap, { zSwap = it }, valueRange = 0f..100f)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Terapkan otomatis saat boot", Modifier.weight(1f), fontSize = 13.sp)
                Switch(z["boot"] == "1", { v -> scope.launch { Az.run("zram boot ${if (v) "on" else "off"}"); loadZram() } })
            }
            if (zBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (zMsg.isNotBlank()) Muted(zMsg)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Terapkan", { scope.launch { zBusy = true; zMsg = Az.run("zram apply $zAlgo ${zMb.toInt()} ${zSwap.toInt()}", 180_000).text.lines().lastOrNull().orEmpty(); loadZram(); zBusy = false } }, enabled = !zBusy && zAlgo.isNotBlank())
                AzButton("Rekomendasi", { scope.launch { zBusy = true; zMsg = Az.run("zram auto", 180_000).text.lines().lastOrNull().orEmpty(); loadZram(); zBusy = false } }, enabled = !zBusy, tonal = true)
            }
            AzButton("Matikan zRAM", { scope.launch { zBusy = true; zMsg = Az.run("zram off", 120_000).text.lines().lastOrNull().orEmpty(); loadZram(); zBusy = false } }, enabled = !zBusy, tonal = true, modifier = Modifier.fillMaxWidth())
        }
    }
    info?.let { OutputDialog(it.first, it.second) { info = null } }
}
