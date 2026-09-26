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
import app.catatuang.engine.pendingClosing
import app.catatuang.feature.closing.ClosingScreen
import app.catatuang.feature.settings.SettingsScreen
import app.catatuang.feature.common.LedgerViewModel
import app.catatuang.feature.detail.DailyDetailScreen
import app.catatuang.feature.detail.StockDetailScreen
import app.catatuang.feature.history.EditTxSheet
import app.catatuang.feature.history.HistoryScreen
import app.catatuang.feature.home.HomeScreen
import app.catatuang.feature.home.buildHomeUi
import app.catatuang.feature.fixed.FixedDue
import app.catatuang.feature.fixed.FixedPayContent
import app.catatuang.feature.income.IncomeContent
import app.catatuang.feature.input.InputContent
import app.catatuang.feature.salary.SalaryScreen
import app.catatuang.feature.savings.SavingsScreen
import app.catatuang.ui.components.SecondaryButton
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
fun MainScaffold(vm: LedgerViewModel, deepLink: kotlinx.coroutines.flow.MutableStateFlow<String?> = remember { kotlinx.coroutines.flow.MutableStateFlow(null) }) {
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
    var inputDate by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
    var editTx by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNotices by rememberSaveable { mutableStateOf(false) }
    var showIncome by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var payFixed by remember { mutableStateOf<FixedDue?>(null) }
    val checklist by vm.transferChecklist.collectAsStateWithLifecycle()
    val cadanganDismissed by vm.cadanganBannerDismissed.collectAsStateWithLifecycle()
    fun homeUi() = buildHomeUi(ready.settings.nickname, ready.input, ready.ledger, ready.today, checklist.first, cadanganDismissed)
    var feedback by remember { mutableStateOf<Pair<String, Tone>?>(null) }
    var feedbackCategory by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            if (e.feedback != null) {
                feedback = e.feedback to e.tone
                feedbackCategory = e.detailCategoryId
                launch { delay(3_000); feedback = null }
            }
            val message = e.snackbar ?: return@collect
            val result = snackbar.showSnackbar(message, actionLabel = e.undo?.let { "Urungkan" }, duration = SnackbarDuration.Indefinite, withDismissAction = false)
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

    // 6.10: Tutup Buku terbuka otomatis sekali saat app dibuka setelah bulannya berakhir.
    val autoOpened by vm.closingAutoOpened.collectAsStateWithLifecycle()
    val pending = pendingClosing(ready.ledger)
    LaunchedEffect(pending?.month, autoOpened, current) {
        val p = pending ?: return@LaunchedEffect
        if (current == Tab.Beranda.route && autoOpened != LedgerViewModel.LOADING && autoOpened != p.month.toString()) {
            vm.markClosingAutoOpened(p.month)
            nav.navigate("closing/${p.month}")
        }
    }

    // Rute dari notifikasi (bab 9). Hanya diproses di sini, jadi kunci PIN sudah dilewati (8.14).
    val link by deepLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        val route = link ?: return@LaunchedEffect
        deepLink.value = null
        fun home() = nav.navigate(Tab.Beranda.route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
        when {
            route.startsWith("input/") -> { home(); inputFor = route.removePrefix("input/").toLongOrNull() }
            route == "picker" -> { home(); showPicker = true }
            route == "salary" -> nav.navigate("salary")
            route == "closing" -> pendingClosing(ready.ledger)?.let { nav.navigate("closing/${it.month}") } ?: home()
            route.startsWith("pay/") -> {
                home()
                val id = route.removePrefix("pay/").toLongOrNull()
                payFixed = app.catatuang.feature.fixed.unpaidFixed(ready.input.categories, ready.ledger, ready.today).firstOrNull { it.categoryId == id }
            }
            route == "report" -> nav.navigate(Tab.Laporan.route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            else -> home()
        }
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
                val ui = homeUi()
                HomeScreen(
                    ui = ui,
                    feedback = feedback,
                    onFeedbackClick = { feedbackCategory?.let(::openDetail) },
                    onTile = { inputFor = it },
                    onTileLong = ::openDetail,
                    onNotifications = { showNotices = true },
                    onNotYet = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                    onSalary = { nav.navigate("salary") },
                    onIncome = { showIncome = true },
                    onSavings = { nav.navigate("savings") },
                    onPayFixed = { payFixed = it },
                    onDismissCadangan = { vm.dismissCadanganBanner(ready.ledger.currentMonth) },
                    onClosing = { pendingClosing(ready.ledger)?.let { nav.navigate("closing/${it.month}") } },
                )
            }
            composable(Tab.Riwayat.route) { HistoryScreen(ready, onEdit = { editTx = it }) }
            composable(Tab.Laporan.route) { app.catatuang.feature.report.ReportScreen(ready, vm, onExport = { showExport = true }) }
            composable("salary?target={target}") { entry ->
                val target = entry.arguments?.getString("target")?.let(java.time.YearMonth::parse)
                SalaryScreen(ready, vm, onDone = { nav.popBackStack() }, initialTarget = target)
            }
            composable("closing/{month}") { entry ->
                val month = entry.arguments?.getString("month")?.let(java.time.YearMonth::parse) ?: return@composable
                ClosingScreen(
                    ready, vm, month,
                    onRecordLastDay = { d -> inputDate = d; showPicker = true },
                    onSalary = { m -> nav.navigate("salary?target=$m") },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Tab.Pengaturan.route) {
                SettingsScreen(ready, vm, onCategories = { nav.navigate("settings/pos") })
            }
            composable("settings/pos") { app.catatuang.feature.settings.CategoriesScreen(ready, vm, onBack = { nav.popBackStack() }) }
            composable("savings") { SavingsScreen(ready, vm, onBack = { nav.popBackStack() }) }
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
        ModalBottomSheet(onDismissRequest = { showPicker = false; inputDate = null }, shape = CatatShapes.sheet, containerColor = colors.surface) {
            CategoryPicker(ready) { id -> showPicker = false; inputFor = id }
        }
    }

    inputFor?.let { id ->
        val cat = ready.input.categories.firstOrNull { it.id == id }
        if (cat != null) {
            ModalBottomSheet(
                onDismissRequest = { inputFor = null; inputDate = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = CatatShapes.sheet,
                containerColor = colors.surface,
            ) {
                InputContent(cat, ready, vm, initialDate = inputDate, onSaved = {
                    inputFor = null
                    inputDate = null
                    if (isTab && current != Tab.Beranda.route) nav.navigate(Tab.Beranda.route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
                })
            }
        }
    }

    editTx?.let { id ->
        val tx = ready.input.transactions.firstOrNull { it.id == id }
        if (tx == null) editTx = null else EditTxSheet(tx, ready, vm, onDismiss = { editTx = null })
    }

    if (showIncome) {
        ModalBottomSheet(
            onDismissRequest = { showIncome = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = CatatShapes.sheet,
            containerColor = colors.surface,
        ) {
            IncomeContent(ready, vm, onSaved = {
                showIncome = false
                if (isTab && current != Tab.Beranda.route) nav.navigate(Tab.Beranda.route) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            })
        }
    }

    if (showExport) {
        ModalBottomSheet(
            onDismissRequest = { showExport = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = CatatShapes.sheet,
            containerColor = colors.surface,
        ) { app.catatuang.feature.report.ExportContent(ready, onDone = { showExport = false }) }
    }

    payFixed?.let { due ->
        ModalBottomSheet(
            onDismissRequest = { payFixed = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = CatatShapes.sheet,
            containerColor = colors.surface,
        ) { FixedPayContent(due, ready, vm, onDone = { payFixed = null }) }
    }

    if (showNotices) {
        val ui = homeUi()
        ModalBottomSheet(onDismissRequest = { showNotices = false }, shape = CatatShapes.sheet, containerColor = colors.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pengingat aktif", style = CatatType.cardTitle, color = colors.textPrimary)
                if (ui.notices.isEmpty() && ui.checklist == null) Text("Tidak ada. Semua aman.", style = CatatType.bodySmall, color = colors.textSecondary)
                ui.checklist?.let { text ->
                    NoticeBox(text, Tone.SAVINGS)
                    SecondaryButton("Sudah transfer · tandai selesai", onClick = { vm.setTransferChecklist(null) })
                }
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
                .background(colors.successFill)
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
