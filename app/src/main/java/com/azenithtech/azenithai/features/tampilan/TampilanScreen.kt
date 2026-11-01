package com.azenithtech.azenithai.features.tampilan

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun saveBackground(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val f = File(ctx.filesDir, "bg_custom")
        val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f.absolutePath
    } catch (e: Exception) { null }
}

@Composable
private fun SwitchRow(label: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label); if (desc != null) Muted(desc) }
        Switch(checked, onChange)
    }
}

@Composable
fun TampilanScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var st by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) { st = Az.status() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { saveBackground(ctx, uri)?.let { ui.bgPath = it; ui.save() } }
    }

    Page("Tampilan") {
        AzCard("Mode layar") {
            ChipRow {
                AzChip("Biasa", !ui.amoled, { ui.amoled = false; ui.save() })
                AzChip("AMOLED", ui.amoled, { ui.amoled = true; ui.save() })
            }
            SwitchRow("Background transparan (kaca buram)", null, ui.glass) { ui.glass = it; ui.save() }
            SwitchRow("Liquid Glass (tab, tombol, kartu & zoom)", null, ui.liquid) { ui.liquid = it; ui.save() }
        }
        AzCard("Background custom") {
            Text("Kegelapan overlay ${(ui.overlay * 100).toInt()}%", fontSize = 13.sp)
            Slider(ui.overlay, { ui.overlay = it }, onValueChangeFinished = { ui.save() }, valueRange = 0f..0.9f)
            Muted(if (ui.bgPath == null) "Belum ada background custom" else "Background custom terpasang")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Pasang", { pick.launch(arrayOf("image/*")) })
                AzButton("Hapus", { ui.bgPath?.let { File(it).delete() }; ui.bgPath = null; ui.save() }, tonal = true, enabled = ui.bgPath != null)
            }
        }
        AzCard("Tentang") {
            Kv("Aplikasi", "Azenith-AI native 2.0.0")
            Kv("Modul", st["version"])
            Kv("Daemon", st["daemon"])
            Kv("Mode", st["mode"])
            Kv("Heartbeat (dtk)", st["heartbeat_age"])
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Doctor", { scope.launch { info = "Doctor" to Az.run("doctor", 120_000).out } }, tonal = true)
                AzButton("Self-test", { scope.launch { info = "Self-test" to Az.run("selftest", 120_000).out } }, tonal = true)
            }
            Muted("Tombol WebUI di manager module (cadangan sampai semua fitur terverifikasi):")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AzButton("Sembunyikan", { scope.launch { info = "WebUI" to Az.run("webui hide").out } }, tonal = true)
                AzButton("Tampilkan", { scope.launch { info = "WebUI" to Az.run("webui show").out } }, tonal = true)
            }
        }
    }
    info?.let { OutputDialog(it.first, it.second) { info = null } }
}
