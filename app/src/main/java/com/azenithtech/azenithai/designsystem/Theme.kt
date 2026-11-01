package com.azenithtech.azenithai.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AzColors {
    val Accent = Color(0xFF4EA1FF)
    val Good = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFC145)
    val Bad = Color(0xFFFF5C5C)
    val Muted = Color(0xFF9AA4B2)
}

@Composable
fun AzTheme(content: @Composable () -> Unit) {
    val ui = LocalUi.current
    val bg = if (ui.amoled) Color.Black else Color(0xFF0E1116)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AzColors.Accent,
            background = bg,
            surface = if (ui.amoled) Color(0xFF0B0B0B) else Color(0xFF171B22),
            onBackground = Color(0xFFE6EAF0),
            onSurface = Color(0xFFE6EAF0),
            error = AzColors.Bad,
        ),
        content = content,
    )
}
