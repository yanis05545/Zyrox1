package com.azenithtech.azenithai.data

import com.azenithtech.azenithai.core.Paths
import com.azenithtech.azenithai.core.root.ExecResult
import com.azenithtech.azenithai.core.root.RootBridge
import com.azenithtech.azenithai.core.util.confKv
import com.azenithtech.azenithai.core.util.kv

/** Pembungkus semua subcommand azenith-ai (peta lengkap: docs/AZENITH-APP-BLUEPRINT.md bagian 6). */
object Az {
    suspend fun run(args: String, timeoutMs: Long = 30_000): ExecResult = RootBridge.cli(args, timeoutMs)

    suspend fun conf(): Map<String, String> =
        RootBridge.exec("[ -f ${Paths.CONF} ] && cat ${Paths.CONF} || cat ${Paths.CONF_DEFAULT}", 10_000).out.confKv()

    suspend fun set(key: String, value: String) = run("set $key $value")

    suspend fun mode(): String =
        RootBridge.exec("cat ${Paths.DATADIR}/mode_state 2>/dev/null", 8_000).text

    suspend fun setMode(id: String): ExecResult = RootBridge.exec("${Paths.THERMAL} $id", 60_000)

    suspend fun devinfo() = run("devinfo", 40_000).out.kv()
    suspend fun monitor() = run("monitor", 15_000).out.kv()
    suspend fun netinfo() = run("netinfo", 15_000).out.kv()
    suspend fun ping() = run("ping", 25_000).out.kv()
    suspend fun gpuinfo() = run("gpuinfo", 15_000).out.kv()
    suspend fun status() = run("status raw", 15_000).out.kv()

    // --- Lisensi ---
    suspend fun licenseOk(): Boolean {
        val r = RootBridge.exec(
            "[ -f ${Paths.MODDIR}/lib/license.sh ] || { echo OK:none; exit 0; }; ${Paths.LICENSE} status", 15_000
        )
        return r.text.lineSequence().any { it.startsWith("OK") }
    }
    suspend fun licenseSerial(): String = RootBridge.exec("${Paths.LICENSE} code", 15_000).text
    suspend fun licenseActivate(key: String): ExecResult =
        RootBridge.exec("${Paths.LICENSE} activate ${RootBridge.q(key)}", 20_000)

    // --- Game ---
    suspend fun games(): List<String> = lines(run("game list").out)
    suspend fun userGames(): List<String> = lines(run("game listuser").out)
    suspend fun gameAdd(pkg: String) = run("game add ${RootBridge.q(pkg)}")
    suspend fun gameDel(pkg: String) = run("game del ${RootBridge.q(pkg)}")
    suspend fun downscaleGet(pkg: String): Int = run("game downscale get ${RootBridge.q(pkg)}").text.toIntOrNull() ?: 100
    suspend fun downscaleSet(pkg: String, pct: Int) =
        if (pct >= 100) run("game downscale off ${RootBridge.q(pkg)}", 60_000)
        else run("game downscale set ${RootBridge.q(pkg)} $pct", 60_000)
    suspend fun coolingGet(pkg: String): Int = run("game cooling get ${RootBridge.q(pkg)}").text.toIntOrNull() ?: 0
    suspend fun coolingSet(pkg: String, lv: Int) = run("game cooling set ${RootBridge.q(pkg)} $lv")

    private fun lines(s: String) = s.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("(") }.toList()
}
