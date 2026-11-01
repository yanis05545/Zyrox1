package com.azenithtech.azenithai.features.onboarding

import androidx.compose.runtime.Composable
import com.azenithtech.azenithai.designsystem.*

@Composable
fun OnboardingScreen(noRoot: Boolean, onRetry: () -> Unit) {
    Page(if (noRoot) "Izin root diperlukan" else "Modul belum terpasang") {
        AzCard {
            if (noRoot) {
                Muted("Aplikasi ini mengontrol kernel lewat root. Izinkan akses root untuk Azenith-AI di Magisk / KernelSU / APatch, lalu coba lagi.")
            } else {
                Muted("Modul Azenith-AI belum ditemukan di /data/adb/modules/azenith-ai. Flash zip modul (yang sudah berisi aplikasi ini) di root manager, reboot, lalu buka lagi.")
            }
            AzButton("Coba lagi", onRetry)
        }
    }
}
