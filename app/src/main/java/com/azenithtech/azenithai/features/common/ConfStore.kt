package com.azenithtech.azenithai.features.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azenithtech.azenithai.data.Az

/** Cermin azenith-ai.conf + mode thermal. Semua layar membaca dari sini dan memanggil refresh() setelah menulis. */
class ConfStore {
    var conf by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var mode by mutableStateOf("")
        private set

    suspend fun refresh() {
        conf = Az.conf()
        mode = Az.mode()
    }

    fun on(key: String) = conf[key] == "1"
    operator fun get(key: String): String = conf[key].orEmpty()

    // Aturan bentrok (blueprint bagian 8)
    val perfLiteOn get() = on("PERFLITE")
    val fastChargeOn get() = on("FAST_CHARGE")
}
