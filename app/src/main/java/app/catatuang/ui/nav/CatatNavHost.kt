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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.catatuang.data.AppState
import app.catatuang.engine.CategoryKind
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.detail.DailyDetailScreen
import app.catatuang.feature.detail.StockDetailScreen
import app.catatuang.feature.history.EditTxSheet
import app.catatuang.feature.history.HistoryScreen
import app.catatuang.feature.home.HomeScreen
import app.catatuang.feature.home.buildHomeUi
import app.catatuang.feature.input.InputContent
import app.catatuang.ui.components.CategoryIcon
import app.catatuang.ui.components.NoticeBox
import app.catatuang.ui.components.Tone
import app.catatuang.ui.icons.LucideIcons
import app.catatuang.ui.theme.CatatShapes
import app.catatuang.ui.theme.CatatTheme
import app.catatuang.ui.theme.CatatType
import app.catatuang.ui.theme.CatatUangTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Beranda("beranda", "Beranda", LucideIcons.Home),
    Riwayat("riwayat", "Riwayat", LucideIcons.History),
    Laporan("laporan", "Laporan", LucideIcons.Report),
    Pengaturan("pengaturan", "Pengaturan", LucideIcons.Settings),
}

/** Layar utama setelah onboarding & kunci: tab, detail, sheet input/edit, feedback & Urungkan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(vm: LedgerViewModel) {
    val appState by vm.state.collectAsStateWithLifecycle()
    val ready = appState as? AppState.Ready ?: return
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val colors = CatatTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var inputFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var editTx by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNotices by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var feedbackCategory by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (e.feedback != null) {
                feedback = e.feedback to e.tone
                feedbackCategory = e.detailCategoryId
                launch { delay(3_000); feedback = null }
            }
            val result = snackbar.showSnackbar(e.snackbar, actionLabel = e.undo?.let { "Urungkan" }, duration = SnackbarDuration.Indefinite, withDismissAction = false)
                .let { it }
            if (result == SnackbarResult.ActionPerformed) {
                feedback = null
                e.undo?.invoke()
            }
        }
    }
    // Snackbar Urungkan tampil 5 detik (R-62).
    LaunchedEffect(snackbar.currentSnackbarData) {
        val data = snackbar.currentSnackbarData ?: return@LaunchedEffect
        delay(5_000)
        data.dismiss()
    }

    fun openDetail(id: Long) {
        val cat = ready.input.categories.firstOrNull { it.id == id } ?: return
        nav.navigate(if (cat.kind == CategoryKind.DAILY) "detail/daily/$id" else "detail/stock/$id")
    }

    val isTab = Tab.entries.any { it.route == current } || current == null
    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTab) {
                BottomBar(
                    current = current ?: Tab.Beranda.route,
                    onTab = { tab ->
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAdd = { showPicker = true },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Beranda.route, modifier = Modifier.padding(bottom = if (isTab) padding.calculateBottomPadding() else 0.dp)) {
            composable(Tab.Beranda.route) {
                val ui = buildHomeUi(ready.settings.nickname, ready.input, ready.ledger, ready.today)
                HomeScreen(
                    ui = ui,
                    feedback = feedback,
                    onFeedbackClick = { feedbackCategory?.let(::openDetail) },
                    onTile = { inputFor = it },
                    onTileLong = ::openDetail,
                    onNotifications = { showNotices = true },
                    onNotYet = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                )
            }
            composable(Tab.Riwayat.route) { HistoryScreen(ready, onEdit = { editTx = it }) }
            composable(Tab.Laporan.route) { Placeholder("Laporan", "Laporan mingguan & bulanan dibuat di Fase 6.") }
            composable(Tab.Pengaturan.route) { Placeholder("Pengaturan", "Pengaturan dibuat di fase berikutnya.") }
            composable("detail/daily/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                val cat = ready.input.categories.firstOrNull { it.id == id }
                if (cat != null) {
                    CatatUangTheme(dark = true) {
                        DailyDetailScreen(cat, ready, vm, onBack = { nav.popBackStack() }, onEdit = { editTx = it })
                    }
                }
            }
            composable("detail/stock/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                val cat = ready.input.categories.firstOrNull { it.id == id }
                if (cat != null) StockDetailScreen(cat, ready, onBack = { nav.popBackStack() }, onEdit = { editTx = it })
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }, shape = CatatShapes.sheet, containerColor = colors.surface) {
            CategoryPicker(ready) { id -> showPicker = false; inputFor = id }
        }
    }

    inputFor?.let { id ->
        val cat = ready.input.categories.firstOrNull { it.id == id }
        if (cat != null) {
            ModalBottomSheet(
                onDismissRequest = { inputFor = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = CatatShapes.sheet,
                containerColor = colors.surface,
            ) {
                InputContent(cat, ready, vm, onSaved = {
                    inputFor = null
                    if (current != Tab.Beranda.route) nav.navigate(Tab.Beranda.route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
                })
            }
        }
    }

    editTx?.let { id ->
        val tx = ready.input.transactions.firstOrNull { it.id == id }
        if (tx == null) editTx = null else EditTxSheet(tx, ready, vm, onDismiss = { editTx = null })
    }

    if (showNotices) {
        val ui = buildHomeUi(ready.settings.nickname, ready.input, ready.ledger, ready.today)
        ModalBottomSheet(onDismissRequest = { showNotices = false }, shape = CatatShapes.sheet, containerColor = colors.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pengingat aktif", style = CatatType.cardTitle, color = colors.textPrimary)
                if (ui.notices.isEmpty()) Text("Tidak ada. Semua aman.", style = CatatType.bodySmall, color = colors.textSecondary)
                ui.notices.forEach { (text, tone) -> NoticeBox(text, tone) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CategoryPicker(ready: AppState.Ready, onPick: (Long) -> Unit) {
    val c = CatatTheme.colors
    val cats = ready.input.categories.filter { it.isActiveIn(ready.ledger.currentMonth) && (it.kind == CategoryKind.DAILY || it.kind == CategoryKind.STOCK) }
        .sortedBy { it.sortOrder }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Catat pengeluaran", style = CatatType.cardTitle, color = c.textPrimary)
        cats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cat ->
                    Row(
                        Modifier.weight(1f).clip(CatatShapes.card).background(c.background).clickable(role = Role.Button) { onPick(cat.id) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CategoryIcon(cat.key, 36)
                        Text(cat.name, style = CatatType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.textPrimary)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Placeholder(title: String, text: String) {
    val colors = CatatTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(CatatShapes.screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = CatatType.screenTitle, color = colors.textPrimary)
        Text(text, style = CatatType.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun BottomBar(current: String, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
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
private fun NavItem(tab: Tab, current: String, onTab: (Tab) -> Unit, modifier: Modifier) {
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
