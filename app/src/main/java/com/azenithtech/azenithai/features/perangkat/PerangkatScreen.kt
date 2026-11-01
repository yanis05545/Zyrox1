package com.azenithtech.azenithai.features.perangkat

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.azenithtech.azenithai.core.util.*
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*

@Composable
fun PerangkatScreen() {
    var d by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var gpu by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { d = Az.devinfo(); gpu = Az.gpuinfo() }

    val name = d["marketname"].takeUnless { it.isNullOrBlank() } ?: d["model"]
    val soc = listOfNotNull(d["soc_mfr"], d["soc_model"]).joinToString(" ").ifBlank { d["hardware"] ?: "-" }

    Page("Perangkat") {
        AzCard("Perangkat Saya") {
            Kv("Nama Perangkat", listOfNotNull(d["brand"], name).joinToString(" "))
            Kv("Kernel", d["kernel_rel"])
            Kv("SOC", soc)
            Kv("Status Root", listOfNotNull(d["root"], d["root_ver"]).joinToString(" "))
        }
        AzCard("Ringkasan Perangkat") {
            Kv("SOC", soc)
            Kv("RAM", d.long("mem_total_kb")?.let { fmtKb(it) })
            Kv("Storage", d.long("data_total_kb")?.let { t -> "${fmtKb(d.long("data_used_kb") ?: 0)} / ${fmtKb(t)}" })
            Kv("Baterai", d["bat_level"]?.let { "$it% · ${tempC(d["bat_temp"])} · ${d["bat_status"] ?: ""}" })
            Kv("Suhu perangkat", "SoC ${tempC(d["t_soc"])} · Baterai ${tempC(d["t_batt"])} · Skin ${tempC(d["t_skin"])}")
            Kv("Frekuensi SOC", d["cpu_max_khz"]?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.maxOrNull()?.let { fmtKhz(it) })
        }
        AzCard("Status Perangkat") {
            Kv("ROM", listOfNotNull(d["rom_name"], d["rom_ver"]).joinToString(" "))
            Kv("Tipe ROM", d["rom_type"])
            Kv("Android", d["android"]?.let { "$it (SDK ${d["sdk"] ?: "?"})" })
            Kv("Kernel (lengkap)", d["kernel_ver"])
            Kv("Arsitektur / ABI", listOfNotNull(d["kernel_arch"], d["abi"]).joinToString(" / "))
            Kv("Jumlah core", d["cores"])
            Kv("GPU", listOfNotNull(gpu["vendor"], gpu["model"] ?: d["gpu_name"]).joinToString(" "))
            Kv("Renderer", d["gl_renderer"])
            Kv("Layar", listOfNotNull(d["wm_size"], d["wm_density"]?.let { "${it}dpi" }).joinToString(" · "))
            Kv("Tipe storage", d["storage_type"])
            Kv("SELinux", d["selinux"])
            Kv("Bootloader terkunci", d["bl_locked"])
            Kv("Treble / Slot", listOfNotNull(d["treble"], d["slot"]).joinToString(" / "))
            Kv("Uptime", d.long("uptime_s")?.let { "${it / 3600} jam ${(it % 3600) / 60} mnt" })
        }
    }
}
