package com.azenithtech.azenithai.designsystem

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Preferensi tampilan (tab Tampilan). Disimpan di SharedPreferences. */
class UiSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("azenith_ui", Context.MODE_PRIVATE)

    var amoled by mutableStateOf(sp.getBoolean("amoled", false))
    var glass by mutableStateOf(sp.getBoolean("glass", true))
    var liquid by mutableStateOf(sp.getBoolean("liquid", true))
    var overlay by mutableStateOf(sp.getFloat("overlay", 0.30f))
    var bgPath by mutableStateOf(sp.getString("bgPath", null))
    var wsUrl by mutableStateOf(sp.getString("wsUrl", "wss://ws.postman-echo.com/raw") ?: "")

    fun save() {
        sp.edit().putBoolean("amoled", amoled).putBoolean("glass", glass).putBoolean("liquid", liquid)
            .putFloat("overlay", overlay).putString("bgPath", bgPath).putString("wsUrl", wsUrl).apply()
    }
}

val LocalUi = staticCompositionLocalOf<UiSettings> { error("UiSettings belum disediakan") }
