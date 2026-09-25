package app.catatuang.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Beranda("beranda", "Beranda", LucideIcons.Home),
    Riwayat("riwayat", "Riwayat", LucideIcons.History),
    Laporan("laporan", "Laporan", LucideIcons.Report),
    Pengaturan("pengaturan", "Pengaturan", LucideIcons.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatatNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var showPicker by remember { mutableStateOf(false) }
    val colors = CatatTheme.colors

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            BottomBar(
                current = current,
                onTab = { tab ->
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = { showPicker = true },
            )
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Beranda.route,
            modifier = Modifier.padding(padding),
        ) {
            Tab.entries.forEach { tab ->
                composable(tab.route) { PlaceholderScreen(tab.label) }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPicker = false },
            shape = CatatShapes.sheet,
            containerColor = colors.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(CatatShapes.screenPadding)) {
                Text("Pilih kategori", style = CatatType.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text("Segera hadir di Fase 2.", style = CatatType.bodySmall, color = colors.textSecondary)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    val colors = CatatTheme.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(CatatShapes.screenPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = CatatType.screenTitle, color = colors.textPrimary)
        Text("Layar ini dibuat di fase berikutnya.", style = CatatType.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun BottomBar(current: String?, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    val colors = CatatTheme.colors
    // Tombol [+] berada di Box luar (tidak di-clip bentuk Surface) supaya lingkarannya utuh.
    Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.padding(top = 22.dp),
            color = colors.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(Tab.Beranda, current, onTab, Modifier.weight(1f))
                NavItem(Tab.Riwayat, current, onTab, Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                NavItem(Tab.Laporan, current, onTab, Modifier.weight(1f))
                NavItem(Tab.Pengaturan, current, onTab, Modifier.weight(1f))
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(62.dp)
                .clip(CircleShape)
                .background(colors.background)
                .padding(4.dp)
                .clip(CircleShape)
                .background(colors.success)
                .clickable(role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = "Catat cepat" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LucideIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun NavItem(tab: Tab, current: String?, onTab: (Tab) -> Unit, modifier: Modifier) {
    val colors = CatatTheme.colors
    val selected = current == tab.route
    val tint = if (selected) colors.primary else colors.textSecondary
    Column(
        modifier
            .height(CatatShapes.minTouch + 12.dp)
            .clip(CatatShapes.chip)
            .clickable(role = Role.Tab) { onTab(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(tab.label, style = CatatType.captionSmall, color = tint)
    }
}
