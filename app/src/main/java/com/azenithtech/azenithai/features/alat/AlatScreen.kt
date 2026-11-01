package com.azenithtech.azenithai.features.alat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.core.Paths
import com.azenithtech.azenithai.core.root.RootBridge
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AlatScreen() {
    Page("Alat") {
        TerminalCard()
        LogCard()
        KernelCard()
    }
}

// ---------------------------------------------------------------- Terminal
@Composable
private fun TerminalCard() {
    val scope = rememberCoroutineScope()
    var cwd by remember { mutableStateOf("/") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf("Azenith-AI Terminal - root shell (su). Ketik 'help' untuk bantuan.") }

    fun run() {
        val c = input.trim(); if (c.isEmpty()) return
        input = ""
        when (c) {
            "clear" -> lines.clear()
            "help" -> lines.add("Perintah apa saja (root). Pintasan: 'az <sub>' = azenith-ai <sub>. Contoh: az status raw · az doctor · cd /sys · clear")
            else -> scope.launch {
                busy = true
                lines.add("$ $c")
                val cmd = if (c.startsWith("az ")) "${Paths.CLI} ${c.drop(3)}" else c
                val r = RootBridge.exec("cd ${RootBridge.q(cwd)} 2>/dev/null; $cmd\necho; echo __PWD__\$(pwd)", 120_000)
                val idx = r.out.lastIndexOf("__PWD__")
                if (idx >= 0) {
                    cwd = r.out.substring(idx + 7).trim().ifBlank { cwd }
                    lines.add(r.out.substring(0, idx).trimEnd())
                } else lines.add(r.out.trimEnd())
                busy = false
            }
        }
    }

    AzCard("Terminal") {
        Surface(color = androidx.compose.ui.graphics.Color.Black.copy(0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp).verticalScroll(rememberScrollState(), reverseScrolling = true).padding(10.dp)) {
                lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
        ChipRow {
            listOf("az status raw", "az doctor", "az selftest", "az scan", "az report").forEach { AzChip(it, false, { input = it }) }
        }
        OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(cwd) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton("Jalankan", { run() }, enabled = !busy)
            AzButton("Hapus layar", { lines.clear() }, tonal = true)
        }
    }
}

// ---------------------------------------------------------------- Log Fitur
@Composable
private fun LogCard() {
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var fitur by remember { mutableStateOf("all") }
    var level by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var out by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        names = Az.run("flog list").out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.contains(' ') }.toList()
    }
    AzCard("Log Fitur") {
        ChipRow { (listOf("all", "summary") + names).forEach { AzChip(it, fitur == it, { fitur = it }) } }
        ChipRow { listOf("" to "Semua level", "ok" to "OK", "err" to "ERR", "bug" to "BUG", "info" to "INFO").forEach { (v, l) -> AzChip(l, level == v, { level = v }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton("Muat log", { scope.launch { out = Az.run("flog $fitur $level 200".trim(), 40_000).out } })
            AzButton("Salin", { clip.setText(AnnotatedString(out)) }, tonal = true)
            AzButton("Hapus", { scope.launch { Az.run("flog clear ${if (fitur == "summary") "all" else fitur}"); out = "" } }, tonal = true)
        }
        if (out.isNotBlank()) Surface(color = androidx.compose.ui.graphics.Color.Black.copy(0.4f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(out, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Scan Kernel
private suspend fun stageFile(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val tmp = File(ctx.cacheDir, "upload.bin")
        val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val dest = "${Paths.TMP}/az_upload.bin"
        val r = RootBridge.exec("cp ${RootBridge.q(tmp.absolutePath)} $dest && chmod 644 $dest", 180_000)
        tmp.delete()
        if (r.ok) dest else null
    } catch (e: Exception) { null }
}

@Composable
private fun KernelCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var scanOut by remember { mutableStateOf("") }
    var features by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var matchOut by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("scan") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val path = stageFile(ctx, uri)
            if (path == null) {
                if (pending == "scan") scanOut = "Gagal menyalin berkas." else matchOut = "Gagal menyalin berkas."
            } else if (pending == "scan") {
                scanOut = Az.run("kernel scan ${RootBridge.q(path)}", 300_000).out
            } else {
                matchOut = Az.run("kernel match ${RootBridge.q(path)}", 300_000).out
            }
            busy = false
        }
    }

    AzCard("Scan Kernel") {
        Muted("Scan LIVE dari perangkat: apa/basis/lock/frekuensi CPU-GPU, tanpa perlu upload.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton("Scan Sekarang", { scope.launch { busy = true; scanOut = Az.run("kernel scan", 300_000).out; busy = false } }, enabled = !busy)
            AzButton("Upload & Scan", { pending = "scan"; picker.launch(arrayOf("*/*")) }, enabled = !busy, tonal = true)
        }
        AzButton("Cek Fitur & Batas Kernel", {
            scope.launch {
                busy = true
                features = Az.run("kernel features", 120_000).out.lines().map { it.split("|") }.filter { it.size >= 3 }
                busy = false
            }
        }, enabled = !busy, tonal = true, modifier = Modifier.fillMaxWidth())
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (features.isNotEmpty()) {
            features.forEach { f ->
                val col = when (f[0].trim().uppercase()) { "BISA" -> AzColors.Good; "SEBAGIAN" -> AzColors.Warn; else -> AzColors.Bad }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f[0].trim(), color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
                    Column {
                        Text(f.getOrNull(2)?.trim().orEmpty() + (f.getOrNull(1)?.let { "  [${it.trim()}]" } ?: ""), fontSize = 12.sp)
                        f.getOrNull(3)?.let { Muted(it.trim()) }
                    }
                }
            }
        }
        if (scanOut.isNotBlank()) Surface(color = androidx.compose.ui.graphics.Color.Black.copy(0.4f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(scanOut, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }

    AzCard("Deteksi driver dari berkas (opsional, lanjutan)") {
        Muted("Cocokkan katalog node dengan boot Image / vendor_dlkm.img / .ko nyata.")
        AzButton("Upload & cocokkan", { pending = "match"; picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AzButton("Terapkan hasil (node cocok saja)", { scope.launch { busy = true; matchOut = Az.run("kernel apply", 120_000).out; busy = false } }, enabled = !busy, tonal = true)
        }
        AzButton("Batalkan ke profil sebelumnya", { scope.launch { busy = true; matchOut = Az.run("kernel revert", 120_000).out; busy = false } }, enabled = !busy, tonal = true, modifier = Modifier.fillMaxWidth())
        if (matchOut.isNotBlank()) Surface(color = androidx.compose.ui.graphics.Color.Black.copy(0.4f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(matchOut, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}
