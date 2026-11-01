package com.azenithtech.azenithai.features.fitur

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azenithtech.azenithai.core.root.RootBridge
import com.azenithtech.azenithai.data.Az
import com.azenithtech.azenithai.designsystem.*
import com.azenithtech.azenithai.domain.Specs
import com.azenithtech.azenithai.domain.ToggleSpec
import com.azenithtech.azenithai.features.common.ConfStore
import com.azenithtech.azenithai.features.common.ToggleCard
import kotlinx.coroutines.launch

@Composable
fun FiturScreen(store: ConfStore) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pkg by remember { mutableStateOf("") }
    var showFlags by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.refresh() }

    Page("Fitur") {
        // Paket Tweaks Performance
        val suiteOn = Specs.perfSuite.all { store.on(it) }
        ToggleCard(
            ToggleSpec("Tweaks Performance", "Aktifkan paket performa (A · D · F/G · F · J · L · AKS)", "", "", ""),
            store, checkedOverride = suiteOn,
            action = { v ->
                for (k in Specs.perfSuite) {
                    if (k == "AKS_ON") Az.run(if (v) "killsec on" else "killsec off")
                    else Az.set(k, if (v) "1" else "0")
                }
                Az.run("status", 20_000)
            },
        )
        ToggleCard(Specs.touch, store)
        ToggleCard(Specs.aks, store)

        AzCard("Preset boost") {
            ChipRow {
                Specs.presets.forEach { p -> AzChip(p, store["PRESET"] == p, { scope.launch { Az.run("preset $p"); store.refresh() } }) }
            }
            Muted("auto = ikut tier perangkat · cool · balanced · extreme")
        }

        Text("Otomatisasi In-Game", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        ToggleCard(Specs.dnd, store)
        ToggleCard(Specs.freeze, store)
        ToggleCard(Specs.freezeSys, store)
        AzCard("Whitelist & pengecualian Freeze") {
            OutlinedTextField(pkg, { pkg = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("nama paket") })
            ChipRow {
                AzChip("+ sistem", false, { if (pkg.isNotBlank()) scope.launch { info = "addsys" to Az.run("freeze addsys ${RootBridge.q(pkg)}").out } })
                AzChip("− sistem", false, { if (pkg.isNotBlank()) scope.launch { info = "delsys" to Az.run("freeze delsys ${RootBridge.q(pkg)}").out } })
                AzChip("keep", false, { if (pkg.isNotBlank()) scope.launch { info = "keep" to Az.run("freeze keep ${RootBridge.q(pkg)}").out } })
                AzChip("unkeep", false, { if (pkg.isNotBlank()) scope.launch { info = "unkeep" to Az.run("freeze unkeep ${RootBridge.q(pkg)}").out } })
            }
            ChipRow {
                AzChip("Status", false, { scope.launch { info = "Freeze status" to Az.run("freeze status").out } })
                AzChip("List sistem", false, { scope.launch { info = "Whitelist sistem" to Az.run("freeze listsys").out } })
                AzChip("List keep", false, { scope.launch { info = "Pengecualian user" to Az.run("freeze listkeep").out } })
            }
        }

        AzCard("Fitur mesin (A–Q, lanjutan)") {
            TextButton({ showFlags = !showFlags }) { Text(if (showFlags) "Sembunyikan" else "Tampilkan semua flag") }
            if (showFlags) Specs.featFlags.forEach { (k, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(label, fontSize = 13.sp); Muted(k) }
                    Switch(store.on(k), { v -> scope.launch { Az.set(k, if (v) "1" else "0"); store.refresh() } },
                        enabled = k != "FEAT_TEMP_MONITOR")
                }
            }
        }
    }
    info?.let { OutputDialog(it.first, it.second) { info = null } }
}
