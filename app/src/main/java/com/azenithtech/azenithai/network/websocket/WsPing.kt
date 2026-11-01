package com.azenithtech.azenithai.network.websocket

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Cara A: ping-pong ke server echo WebSocket (server bisa diatur di kartu Latensi). Return ms atau -1. */
object WsPing {
    private val client = OkHttpClient()

    suspend fun measure(url: String): Int = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext -1
        val done = CompletableDeferred<Int>()
        var t0 = 0L
        val ws = try {
            client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    t0 = System.nanoTime(); webSocket.send("azenith-ping")
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    done.complete(((System.nanoTime() - t0) / 1_000_000).toInt()); webSocket.close(1000, null)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.complete(-1) }
            })
        } catch (e: Exception) { return@withContext -1 }
        val r = withTimeoutOrNull(5000) { done.await() } ?: -1
        ws.cancel()
        r
    }
}
