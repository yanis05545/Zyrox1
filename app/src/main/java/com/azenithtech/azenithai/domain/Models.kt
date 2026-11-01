package com.azenithtech.azenithai.domain

/** Satu toggle yang dibaca dari azenith-ai.conf dan diubah lewat CLI. */
data class ToggleSpec(
    val title: String,
    val desc: String,
    val key: String,
    val on: String,
    val off: String,
    val warn: String? = null,
)

enum class ThermalMode(val id: String, val label: String) {
    BALANCE("balance", "Balanced"),
    SAVER("saver", "PowerSaver"),
    TURBO("extreme", "ZAI-Turbo"),
}

object Specs {
    val kontrol = listOf(
        ToggleSpec(
            "Perf Lite", "Profil ringan & hemat daya (VM/RAM, governor, grafis/UI, I/O+FSTrim, log). " +
                "Bisa bareng Balanced; PowerSaver & ZAI-Turbo dikunci, Fast Charger otomatis mati.",
            "PERFLITE", "perflite on", "perflite off"
        ),
        ToggleSpec(
            "Network Booster", "NSS/optimasi TCP-IP + fix latency. Aktif: bbr3, mati: cubic. Tahan reboot.",
            "NETBOOST", "netboost on", "netboost off"
        ),
        ToggleSpec(
            "WiFi Low Latency", "Kurangi delay (ping) Wi-Fi: low-latency mode Android, matikan hemat daya radio & throttle scan. " +
                "Tidak menambah kecepatan, hanya mempercepat respons.",
            "WIFILL", "wifill on", "wifill off"
        ),
        ToggleSpec(
            "WiFi Tweak", "Hi-perf + low-latency, matikan PNO scan/watchdog/throttling (15 perintah cmd wifi, ada cek dukungan ROM + revert).",
            "WIFITWEAK", "wifitweak on", "wifitweak off"
        ),
    )

    val lanjutan = listOf(
        ToggleSpec(
            "Disable Thermal", "Mematikan service HAL thermal secara agresif (thermalkill).",
            "THERMAL_KILL", "thermalkill on", "thermalkill off",
            warn = "Berisiko: perangkat bisa terlalu panas. Pengaman suhu tetap jalan, tapi pantau suhu sendiri. Lanjutkan?"
        ),
        ToggleSpec(
            "Thermal Lock Daily", "Thermal lock non-stop (harian & gaming), auto-revert bila suhu melewati ambang.",
            "THERMAL_DAILY", "thermal daily on", "thermal daily off"
        ),
    )

    val touch = ToggleSpec(
        "K · Touch Boost", "Node touch boost vendor + fpsgo_enable/stop_boost/perfserv_ta (fallback GED). Meningkatkan respons layar sentuh.",
        "FEAT_TOUCH", "touch on", "touch off"
    )
    val aks = ToggleSpec(
        "AKS · Abaikan Keamanan Kernel", "Kunci CPU min=max ke plafon tertinggi, HANYA selama game aktif.",
        "AKS_ON", "killsec on", "killsec off",
        warn = "Mode ini mengabaikan limiter software kernel selama game. Lanjutkan?"
    )
    val dnd = ToggleSpec(
        "DND In-Game", "Senyapkan semua notifikasi dari luar game (DND resmi Android). Lepas otomatis setelah game berhenti.",
        "FEAT_DND_INGAME", "set FEAT_DND_INGAME 1", "set FEAT_DND_INGAME 0"
    )
    val freeze = ToggleSpec(
        "Freeze In-Game", "Bekukan app pihak ketiga yang tidak dipakai selama game (reversibel, otomatis dikembalikan).",
        "FEAT_FREEZE_INGAME", "set FEAT_FREEZE_INGAME 1", "set FEAT_FREEZE_INGAME 0"
    )
    val freezeSys = ToggleSpec(
        "Ikut bekukan app sistem (whitelist)", "Hanya paket yang sudah kamu tambahkan ke whitelist sistem. App inti selalu ditolak.",
        "FREEZE_INCLUDE_SYSTEM", "set FREEZE_INCLUDE_SYSTEM 1", "set FREEZE_INCLUDE_SYSTEM 0"
    )

    val bypassCharge = ToggleSpec(
        "Bypass Charging", "ON = baterai tidak menerima daya, OFF = normal. Diverifikasi dari status baterai & current_now. Mati otomatis bila baterai rendah.",
        "CHARGE_BYPASS", "charge on", "charge off"
    )
    val fastCharger = ToggleSpec(
        "Fast Charger", "Matikan thermal + kunci suhu zona (arus charge tidak diubah). Mati otomatis kalau baterai terlalu panas.",
        "FAST_CHARGE", "fastcharge on", "fastcharge off"
    )

    /** Paket "Tweaks Performance" = A · D · F/G · F · J · L · AKS (sama dengan WebUI). */
    val perfSuite = listOf("FEAT_THERMAL", "FEAT_SCHED", "FEAT_FPS_ENGINE", "FEAT_FPSGO_TUNE", "FEAT_LOG_REDUCE", "FEAT_IO", "AKS_ON")

    /** Semua flag fitur mesin (A–Q). FEAT_TEMP_MONITOR sengaja tidak boleh dimatikan. */
    val featFlags = listOf(
        "FEAT_THERMAL" to "A · Thermal lock/inject berbasis scan",
        "FEAT_TEMP_MONITOR" to "B · Monitor suhu asli + safety (jangan dimatikan)",
        "FEAT_FREQ" to "C · Lantai frekuensi CPU/GPU",
        "FEAT_SCHED" to "D · Scheduler (rate limit, sched_boost)",
        "FEAT_PRIORITY" to "E · nice/ionice proses game",
        "FEAT_FPS_ENGINE" to "F/G · FPS sustain engine (basis FPSGO)",
        "FEAT_FPSGO_TUNE" to "F · Tuning node FPSGO (MediaTek)",
        "FEAT_SURFACEFLINGER" to "I · Prop SurfaceFlinger saat boot",
        "FEAT_LOG_REDUCE" to "J · Kurangi overhead log/trace",
        "FEAT_IO" to "L · Read-ahead, UFS clock",
        "FEAT_REFRESH" to "M · Kunci refresh rate tertinggi saat game",
        "FEAT_NETWORK" to "N · Tuning jaringan",
        "FEAT_RAM" to "O · RAM manager",
        "FEAT_DOWNSCALE" to "P · Downscale resolusi per game",
        "FEAT_COOLING" to "Q · Cooling SoC per game",
    )

    val thermalModes = listOf("off", "soft", "auto", "hard")
    val presets = listOf("auto", "cool", "balanced", "extreme")
    val downscaleSteps = listOf(100, 90, 80, 70, 60, 50, 40, 30, 25, 20, 10)
    val coolingLabels = listOf("Mati", "Ringan", "Sedang", "Kuat", "Maksimal")
}
