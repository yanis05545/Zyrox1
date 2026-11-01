package com.azenithtech.azenithai.core.root

import com.azenithtech.azenithai.core.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ExecResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
    val text: String get() = out.trim()
}

/** Satu-satunya jembatan ke root. Semua fitur lewat sini (sama seperti ksu.exec di WebUI). */
object RootBridge {
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    suspend fun exec(cmd: String, timeoutMs: Long = 20_000): ExecResult = withContext(Dispatchers.IO) {
        var p: Process? = null
        try {
            val proc = ProcessBuilder("su").redirectErrorStream(true).start()
            p = proc
            proc.outputStream.bufferedWriter().use { w ->
                w.write(cmd)
                w.write("\nexit\n")
                w.flush()
            }
            val reader = async { proc.inputStream.bufferedReader().readText() }
            val out = withTimeoutOrNull(timeoutMs) { reader.await() }
            if (out == null) {
                proc.destroy()
                return@withContext ExecResult(124, "timeout setelah ${timeoutMs / 1000} dtk")
            }
            ExecResult(proc.waitFor(), out)
        } catch (e: Exception) {
            p?.destroy()
            ExecResult(127, e.message ?: "gagal menjalankan su")
        }
    }

    suspend fun cli(args: String, timeoutMs: Long = 20_000): ExecResult =
        exec("${Paths.CLI} $args", timeoutMs)

    suspend fun hasRoot(): Boolean {
        val r = exec("id -u", 10_000)
        return r.ok && r.text.endsWith("0")
    }

    suspend fun moduleInstalled(): Boolean =
        exec("[ -f ${Paths.MODDIR}/system/bin/azenith-ai ] && echo yes", 10_000).text.contains("yes")
}
