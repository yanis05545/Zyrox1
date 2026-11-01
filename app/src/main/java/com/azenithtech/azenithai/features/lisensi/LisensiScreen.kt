package com.azenithtech.azenithai.features.lisensi

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun LisensiScreen(onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var serial by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { serial = Az.licenseSerial() }
    Page("Lisensi Diperlukan") {
        AzCard("Aktivasi") {
            Muted("Kirim kode perangkat ini ke penjual untuk mendapatkan key. Key hanya berlaku untuk perangkat ini.")
            Kv("Kode perangkat", serial)
            AzButton("Salin kode", { clip.setText(AnnotatedString(serial)) }, tonal = true)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Key lisensi") })
            AzButton("Aktifkan", {
                scope.launch {
                    val r = Az.licenseActivate(key)
                    msg = r.text.lines().lastOrNull().orEmpty()
                    if (Az.licenseOk()) onUnlocked()
                }
            }, enabled = key.isNotBlank())
            if (msg.isNotBlank()) Muted(msg)
        }
    }
}
