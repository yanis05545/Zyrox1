package com.azenithtech.azenithai.features.game

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import com.azenithtech.azenithai.domain.Specs
import com.azenithtech.azenithai.features.common.ConfStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppRow(val pkg: String, val label: String)

private suspend fun installedApps(ctx: Context): List<AppRow> = withContext(Dispatchers.IO) {
    val pm = ctx.packageManager
    pm.getInstalledApplications(0)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

private fun labelOf(ctx: Context, pkg: String): String = try {
    ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) { pkg }

@Composable
fun GameScreen(store: ConfStore) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<String>>(emptyList()) }
    var user by remember { mutableStateOf<List<String>>(emptyList()) }
    var picker by remember { mutableStateOf(false) }
    var gfx by remember { mutableStateOf("") }
    var gfxBusy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }

    suspend fun reload() { all = Az.games(); user = Az.userGames(); gfx = Az.run("gfx universal status").text }
    LaunchedEffect(Unit) { store.refresh(); reload() }

    Page("Game") {
        AzCard("Daftar game") {
            AzButton("+ Tambah dari aplikasi terpasang", { picker = true }, modifier = Modifier.fillMaxWidth())
            if (all.isEmpty()) Muted("Belum ada game terdeteksi.")
            all.forEach { pkg ->
                val isUser = pkg in user
                Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == pkg) null else pkg }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(labelOf(ctx, pkg), fontWeight = FontWeight.SemiBold)
                            Muted(pkg + if (isUser) "  · ditambah user" else "  · otomatis")
                        }
                        Text(if (expanded == pkg) "▲" else "▼")
                    }
                    if (expanded == pkg) GameSettings(pkg, isUser, onRemove = { scope.launch { Az.gameDel(pkg); expanded = null; reload() } })
                }
                HorizontalDivider()
            }
        }

        AzCard("Graphics API Universal") {
            Muted("Berlaku SELURUH sistem (semua app), tahan reboot. Kalau backend gagal diverifikasi, hasilnya GAGAL apa adanya.")
            ChipRow {
                listOf("vulkan" to "Vulkan", "skiavk" to "SkiaVk", "skiagl" to "SkiaGl").forEach { (id, lbl) ->
                    AzChip(lbl, gfx.contains(id, ignoreCase = true) && !gfx.contains("off", true), {
                        scope.launch { gfxBusy = true; gfx = Az.run("gfx universal set $id", 90_000).text; gfxBusy = false; store.refresh() }
                    }, enabled = !gfxBusy)
                }
                AzChip("Matikan", false, { scope.launch { gfxBusy = true; gfx = Az.run("gfx universal off", 90_000).text; gfxBusy = false; store.refresh() } }, enabled = !gfxBusy)
            }
            if (gfxBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (gfx.isNotBlank()) Muted("Status: " + gfx.lines().lastOrNull { it.isNotBlank() }.orEmpty())
        }
    }

    if (picker) AppPicker(ctx, onDismiss = { picker = false }) { chosen ->
        picker = false
        scope.launch { chosen.forEach { Az.gameAdd(it) }; reload() }
    }
}

@Composable
private fun GameSettings(pkg: String, isUser: Boolean, onRemove: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ds by remember { mutableStateOf(100) }
    var cool by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(pkg) { ds = Az.downscaleGet(pkg); cool = Az.coolingGet(pkg) }
    val dsIdx = Specs.downscaleSteps.indexOf(ds).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text("Downscale resolusi: $ds%", fontSize = 13.sp)
        Slider(dsIdx.toFloat(), { ds = Specs.downscaleSteps[it.toInt().coerceIn(0, Specs.downscaleSteps.lastIndex)] },
            valueRange = 0f..(Specs.downscaleSteps.size - 1).toFloat(), steps = Specs.downscaleSteps.size - 2)
        Text("Cooling SoC: ${Specs.coolingLabels[cool]}", fontSize = 13.sp)
        Slider(cool.toFloat(), { cool = it.toInt().coerceIn(0, 4) }, valueRange = 0f..4f, steps = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton("Terapkan", {
                scope.launch {
                    busy = true
                    val a = Az.downscaleSet(pkg, ds).text.lines().lastOrNull { it.isNotBlank() }.orEmpty()
                    val b = Az.coolingSet(pkg, cool).text.lines().lastOrNull { it.isNotBlank() }.orEmpty()
                    msg = listOf(a, b).filter { it.isNotBlank() }.joinToString("\n"); busy = false
                }
            }, enabled = !busy)
            AzButton("Batal", { scope.launch { ds = Az.downscaleGet(pkg); cool = Az.coolingGet(pkg) } }, tonal = true)
            if (isUser) AzButton("Hapus", onRemove, tonal = true)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (msg.isNotBlank()) Muted(msg)
    }
}

@Composable
private fun AppPicker(ctx: Context, onDismiss: () -> Unit, onAdd: (Set<String>) -> Unit) {
    var apps by remember { mutableStateOf<List<AppRow>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    val sel = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { apps = installedApps(ctx) }
    val shown = apps.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih aplikasi/game") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cari") })
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    shown.forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { if (a.pkg in sel) sel.remove(a.pkg) else sel.add(a.pkg) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(a.pkg in sel, { if (it) sel.add(a.pkg) else sel.remove(a.pkg) })
                            Column { Text(a.label, fontSize = 14.sp); Muted(a.pkg) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onAdd(sel.toSet()) }, enabled = sel.isNotEmpty()) { Text("Tambah (${sel.size})") } },
        dismissButton = { TextButton(onDismiss) { Text("Tutup") } },
    )
}
