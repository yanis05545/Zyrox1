package com.azenithtech.azenithai.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Halaman satu tab: judul + isi yang bisa di-scroll (ruang bawah untuk tab bar melayang). */
@Composable
fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
fun AzCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    val shape = RoundedCornerShape(20.dp)
    val color = if (ui.glass) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface
    val border = if (ui.liquid && ui.glass)
        BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.35f), Color.White.copy(0.04f))))
    else null
    Surface(modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
fun Kv(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AzColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (value.isNullOrBlank()) "-" else value, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun Muted(text: String) = Text(text, color = AzColors.Muted, fontSize = 12.sp)

@Composable
fun AzButton(text: String, onClick: () -> Unit, enabled: Boolean = true, tonal: Boolean = false, modifier: Modifier = Modifier) {
    if (tonal) FilledTonalButton(onClick, modifier, enabled = enabled) { Text(text) }
    else Button(onClick, modifier, enabled = enabled) { Text(text) }
}

/** Chip sederhana (tanpa API eksperimental). */
@Composable
fun AzChip(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val bg = if (selected) AzColors.Accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f)
    Surface(
        shape = RoundedCornerShape(50), color = bg,
        border = if (selected) BorderStroke(1.dp, AzColors.Accent) else null,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp,
            color = if (enabled) Color.Unspecified else AzColors.Muted)
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun OutputDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Tutup") } },
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(text.ifBlank { "(kosong)" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        },
    )
}

@Composable
fun Spark(values: List<Float>, max: Float, color: Color = AzColors.Accent, modifier: Modifier = Modifier.fillMaxWidth().height(44.dp)) {
    Canvas(modifier) {
        if (values.size < 2 || max <= 0f) return@Canvas
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max).coerceIn(0f, 1f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f))
    }
}

@Composable
fun Meter(fraction: Float, color: Color = AzColors.Accent) {
    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp), color = color,
        trackColor = Color.White.copy(0.08f))
}

/** Tab bar kapsul melayang: bisa digeser dengan tangan (scroll horizontal) dan diketuk. */
@Composable
fun FloatingTabBar(items: List<Pair<String, String>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 14.dp),
        shape = RoundedCornerShape(30.dp),
        color = if (ui.glass) Color(0xFF1B2029).copy(alpha = 0.86f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.35f), Color.White.copy(0.05f)))),
        shadowElevation = 12.dp,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = i == selected
                Column(
                    Modifier
                        .clip22(sel)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(icon, fontSize = 20.sp)
                    Text(label, fontSize = 11.sp, color = if (sel) AzColors.Accent else AzColors.Muted, maxLines = 1)
                }
            }
        }
    }
}

private fun Modifier.clip22(selected: Boolean): Modifier =
    this.background(if (selected) AzColors.Accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(22.dp))
