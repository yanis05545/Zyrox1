package com.azenithtech.azenithai.network.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Fallback latensi (Cara B): HTTP 204 ringan. Dipakai bila ICMP via root tidak tersedia. Return ms atau -1. */
object HttpPing {
    suspend fun measure(url: String = "https://www.gstatic.com/generate_204"): Int = withContext(Dispatchers.IO) {
        try {
            val t0 = System.nanoTime()
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000; c.requestMethod = "GET"; c.useCaches = false
            c.responseCode
            c.disconnect()
            ((System.nanoTime() - t0) / 1_000_000).toInt()
        } catch (e: Exception) { -1 }
    }
}
