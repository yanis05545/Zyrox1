package com.azenithtech.azenithai.features.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.core.root.ExecResult
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.AzCard
import com.azenithtech.azenithai.designsystem.AzColors
import com.azenithtech.azenithai.designsystem.Muted
import com.azenithtech.azenithai.domain.ToggleSpec
import kotlinx.coroutines.launch

/**
 * Kartu toggle generik: status dibaca dari conf, aksi lewat CLI, lalu status DIBACA ULANG
 * (kalau kernel menolak, saklar kembali ke nilai nyata dan pesan modul ditampilkan apa adanya).
 */
@Composable
fun ToggleCard(
    spec: ToggleSpec,
    store: ConfStore,
    lockedReason: String? = null,
    checkedOverride: Boolean? = null,
    action: (suspend (Boolean) -> ExecResult)? = null,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val checked = checkedOverride ?: store.on(spec.key)

    fun go(v: Boolean) {
        scope.launch {
            busy = true
            val r = if (action != null) action(v) else Az.run(if (v) spec.on else spec.off, 90_000)
            msg = r.text.lines().lastOrNull { it.isNotBlank() }.orEmpty()
            store.refresh()
            busy = false
        }
    }

    AzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spec.title, fontWeight = FontWeight.SemiBold)
                Muted(spec.desc)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = { if (spec.warn != null && it) confirm = true else go(it) },
                enabled = !busy && lockedReason == null,
            )
        }
        if (lockedReason != null) Text(lockedReason, color = AzColors.Warn, fontSize = 12.sp)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (msg.isNotBlank()) Muted(msg)
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(spec.title) },
            text = { Text(spec.warn.orEmpty()) },
            confirmButton = { TextButton({ confirm = false; go(true) }) { Text("Lanjutkan") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Batal") } },
        )
    }
}
