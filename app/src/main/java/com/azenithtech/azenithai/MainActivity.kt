package com.azenithtech.azenithai

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.azenithtech.azenithai.core.root.RootBridge
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import com.azenithtech.azenithai.features.alat.AlatScreen
import com.azenithtech.azenithai.features.baterai.BateraiRamScreen
import com.azenithtech.azenithai.features.common.ConfStore
import com.azenithtech.azenithai.features.fitur.FiturScreen
import com.azenithtech.azenithai.features.game.GameScreen
import com.azenithtech.azenithai.features.kontrol.KontrolScreen
import com.azenithtech.azenithai.features.lisensi.LisensiScreen
import com.azenithtech.azenithai.features.monitor.MonitorScreen
import com.azenithtech.azenithai.features.onboarding.OnboardingScreen
import com.azenithtech.azenithai.features.perangkat.PerangkatScreen
import com.azenithtech.azenithai.features.tampilan.TampilanScreen
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ui = UiSettings(applicationContext)
        setContent {
            CompositionLocalProvider(LocalUi provides ui) {
                AzTheme { AzRoot() }
            }
        }
    }
}

private enum class Gate { Checking, NoRoot, NoModule, License, Ready }

private val TABS = listOf(
    "📱" to "Perangkat", "📈" to "Monitor", "🎛" to "Kontrol", "⚡" to "Fitur",
    "🎮" to "Game", "🔋" to "Battery & RAM", "🛠" to "Alat", "🎨" to "Tampilan",
)

@Composable
private fun AzRoot() {
    val scope = rememberCoroutineScope()
    var gate by remember { mutableStateOf(Gate.Checking) }

    fun check() {
        gate = Gate.Checking
        scope.launch {
            gate = when {
                !RootBridge.hasRoot() -> Gate.NoRoot
                !RootBridge.moduleInstalled() -> Gate.NoModule
                !Az.licenseOk() -> Gate.License
                else -> Gate.Ready
            }
        }
    }
    LaunchedEffect(Unit) { check() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BackgroundLayer()
        when (gate) {
            Gate.Checking -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            Gate.NoRoot -> OnboardingScreen(noRoot = true) { check() }
            Gate.NoModule -> OnboardingScreen(noRoot = false) { check() }
            Gate.License -> LisensiScreen { check() }
            Gate.Ready -> MainPager()
        }
    }
}

@Composable
private fun BackgroundLayer() {
    val ui = LocalUi.current
    val path = ui.bgPath ?: return
    val bmp = remember(path) {
        try { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() } catch (e: Exception) { null }
    } ?: return
    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ui.overlay)))
}

@Composable
private fun MainPager() {
    val scope = rememberCoroutineScope()
    val store = remember { ConfStore() }
    val pager = rememberPagerState { TABS.size }
    LaunchedEffect(Unit) { store.refresh() }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            when (i) {
                0 -> PerangkatScreen()
                1 -> MonitorScreen(active = pager.currentPage == 1)
                2 -> KontrolScreen(store)
                3 -> FiturScreen(store)
                4 -> GameScreen(store)
                5 -> BateraiRamScreen(store)
                6 -> AlatScreen()
                else -> TampilanScreen()
            }
        }
        FloatingTabBar(TABS, pager.currentPage, { scope.launch { pager.animateScrollToPage(it) } },
            Modifier.align(Alignment.BottomCenter))
    }
}
