package com.azenithtech.azenithai.features.kontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import com.azenithtech.azenithai.domain.Specs
import com.azenithtech.azenithai.domain.ThermalMode
import com.azenithtech.azenithai.features.common.ConfStore
import com.azenithtech.azenithai.features.common.ToggleCard
import kotlinx.coroutines.launch

@Composable
fun KontrolScreen(store: ConfStore) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { store.refresh() }

    val cur = when (store.mode) { "gaming", "extreme" -> "extreme"; else -> store.mode }

    Page("Kontrol") {
        AzCard("Mode Thermal (Azenith)") {
            ChipRow {
                ThermalMode.values().forEach { m ->
                    val locked = when {
                        store.perfLiteOn && m != ThermalMode.BALANCE -> "Perf Lite aktif: hanya Balanced"
                        store.fastChargeOn && m != ThermalMode.TURBO -> "Fast Charger aktif: Balanced & PowerSaver tidak direspon"
                        else -> null
                    }
                    AzChip(m.label, cur == m.id, {
                        scope.launch { busy = true; msg = Az.setMode(m.id).text.lines().lastOrNull { it.isNotBlank() }.orEmpty(); store.refresh(); busy = false }
                    }, enabled = !busy && locked == null)
                }
            }
            Muted("Status: ${store.mode.ifBlank { "-" }}")
            if (store.perfLiteOn) Text("Perf Lite aktif → PowerSaver & ZAI-Turbo dikunci.", color = AzColors.Warn, fontSize = 12.sp)
            if (store.fastChargeOn) Text("Fast Charger aktif → mode Balanced & PowerSaver tidak direspon.", color = AzColors.Warn, fontSize = 12.sp)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (msg.isNotBlank()) Muted(msg)
        }

        Specs.kontrol.forEach { spec -> ToggleCard(spec, store) }

        AzCard("Apa itu WiFi Low Latency?") {
            Muted("Latensi adalah waktu respons Wi-Fi saat paket pulang-pergi perangkat ↔ server. Kalau bandwidth itu lebar jalan tol, latensi adalah kecepatan mobil menempuhnya. " +
                "Toggle ini memakai mode low-latency Wi-Fi Android, mematikan hemat daya radio & throttle scan, dan power_save driver (kalau ada). Ia tidak menambah kecepatan unduh.")
        }

        Text("Lanjutan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Specs.lanjutan.forEach { ToggleCard(it, store) }
        AzCard("THERMAL_MODE (mesin)") {
            ChipRow {
                Specs.thermalModes.forEach { t ->
                    AzChip(t, store["THERMAL_MODE"] == t, { scope.launch { Az.set("THERMAL_MODE", t); store.refresh() } })
                }
            }
            Muted("off · soft · auto · hard (hard mematikan lebih banyak node throttle, pengaman lebih ketat).")
        }
        AzCard("Bypass Thermal (jalur halus)") {
            Muted("Beda dengan Disable Thermal: tidak mematikan service HAL.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Aktifkan", { scope.launch { val r = Az.run("bypass on", 60_000); info = "Bypass Thermal" to r.out } })
                AzButton("Status", { scope.launch { val r = Az.run("bypass status", 30_000); info = "Bypass Thermal" to r.out } }, tonal = true)
            }
        }
    }
    info?.let { OutputDialog(it.first, it.second) { info = null } }
}
