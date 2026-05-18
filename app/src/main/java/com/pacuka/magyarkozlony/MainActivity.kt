package com.pacuka.magyarkozlony

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.pacuka.magyarkozlony.models.Kozlony
import com.pacuka.magyarkozlony.ui.navigation.Screen
import com.pacuka.magyarkozlony.ui.screens.PdfViewerScreen
import com.pacuka.magyarkozlony.ui.theme.MagyarKozlonyTheme
import com.pacuka.magyarkozlony.utils.Downloader
import com.pacuka.magyarkozlony.viewmodels.AppTheme
import com.pacuka.magyarkozlony.viewmodels.KozlonyViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: KozlonyViewModel = viewModel()
            val appTheme by viewModel.appTheme.collectAsState()
            
            val isDark = when(appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.NAVY -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            
            val isNavy = appTheme == AppTheme.NAVY

            MagyarKozlonyTheme(darkTheme = isDark, isNavy = isNavy) {
                val navController = rememberNavController()
                MainApp(navController, viewModel)
            }
        }
    }
}

@Composable
fun MainApp(navController: NavHostController, viewModel: KozlonyViewModel) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == Screen.Home.route || currentRoute == Screen.Settings.route) {
                BottomBar(navController)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) {
                    HomeScreen(viewModel = viewModel, onIssueClick = { url, title ->
                        navController.navigate(Screen.PdfViewer.createRoute(url, title))
                    })
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = viewModel, onAboutClick = {
                        navController.navigate("about")
                    })
                }
                composable("about") {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.PdfViewer.route) { backStackEntry ->
                    val pdfUrl = backStackEntry.arguments?.getString("pdfUrl") ?: ""
                    val title = backStackEntry.arguments?.getString("title") ?: ""
                    PdfViewerScreen(pdfUrl = pdfUrl, title = title, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
fun BottomBar(navController: NavHostController) {
    val items = listOf(Screen.Home, Screen.Settings)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: KozlonyViewModel, onIssueClick: (String, String) -> Unit) {
    val issues by viewModel.displayIssues.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var showSearchHelp by remember { mutableStateOf(false) }
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val showScrollButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 5 } }

    if (showSearchHelp) {
        AlertDialog(
            onDismissRequest = { showSearchHelp = false },
            title = { Text("Keresési tippek") },
            text = {
                Column {
                    Text("• Használj csillagot (*) szótöredékekhez (pl. 'körjegyz*').")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Kereshetsz évszámra (pl. '2023').")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• A keresés a teljes szöveges archívumban történik.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchHelp = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TricolorBar()
                TopAppBar(
                    title = { 
                        Text("MAGYAR KÖZLÖNY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Frissítés")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showScrollButton, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Keresés az archívumban...") },
                    leadingIcon = { 
                        IconButton(onClick = { showSearchHelp = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { 
                        viewModel.performSearch()
                        focusManager.clearFocus() 
                    })
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (issues.isEmpty() && !isLoading) {
                    EmptyState(isSearching = searchQuery.isNotEmpty())
                } else {
                    KozlonyList(
                        items = issues,
                        onIssueClick = onIssueClick,
                        onLoadMore = { viewModel.loadNextPage() },
                        isLoadingMore = isLoading,
                        state = listState
                    )
                }
            }
        }
    }
}

@Composable
fun TricolorBar() {
    Row(modifier = Modifier.fillMaxWidth().height(4.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFC8102E)))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White))
        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF43B02A)))
    }
}

@Composable
fun EmptyState(isSearching: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.animateContentSize()) {
            Icon(
                imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            )
            Text(
                text = if (isSearching) "Nincs találat" else "Húzd lefelé a frissítéshez",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: KozlonyViewModel, onAboutClick: () -> Unit) {
    val currentTheme by viewModel.appTheme.collectAsState()
    val notifications by viewModel.notificationsEnabled.collectAsState()
    val externalBrowser by viewModel.externalBrowserEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BEÁLLÍTÁSOK", fontWeight = FontWeight.Black) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection("MEGJELENÉS") {
                ThemeOption(AppTheme.SYSTEM, "Rendszer alapértelmezett", currentTheme) { viewModel.setTheme(it) }
                ThemeOption(AppTheme.LIGHT, "Világos", currentTheme) { viewModel.setTheme(it) }
                ThemeOption(AppTheme.DARK, "Sötét", currentTheme) { viewModel.setTheme(it) }
                ThemeOption(AppTheme.NAVY, "Elnöki Sötétkék", currentTheme) { viewModel.setTheme(it) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection("ÁLTALÁNOS") {
                SettingsToggle("Értesítések", notifications) { viewModel.toggleNotifications() }
                SettingsToggle("Külső PDF néző", externalBrowser) { viewModel.toggleExternalBrowser() }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                onClick = onAboutClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Névjegy és Jogi nyilatkozat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                "Magyar Közlöny Unofficial v0.1.0",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NÉVJEGY") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "MAGYAR KÖZLÖNY UNOFFICIAL",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Egy modern, független projekt a hivatalos állami közlönyök kényelmes mobil eléréséhez. Az alkalmazás célja a transzparencia és a gyors tájékozódás elősegítése.", style = MaterialTheme.typography.bodyLarge)
            
            Spacer(modifier = Modifier.height(32.dp))
            AboutTextSection("JOGI NYILATKOZAT", "Az alkalmazás NEM hivatalos forrás. Az adatok a magyarkozlony.hu weboldalról származnak valós idejű feldolgozással. A fejlesztő nem vállal felelősséget az adatok hitelességéért, esetleges pontatlanságáért vagy az alkalmazás használatából eredő károkért. Hivatalos ügyintézéshez kizárólag a hitelesített dokumentumokat használja!")
            
            Spacer(modifier = Modifier.height(24.dp))
            AboutTextSection("TECHNIKAI HÁTTÉR", "Az applikáció modern technológiákra épül: Kotlin, Jetpack Compose, Material 3, és Jsoup. Teljesen nyílt forráskódú és mentes mindenféle követőkódtól vagy analitikától.")
            
            Spacer(modifier = Modifier.height(24.dp))
            AboutTextSection("HIVATALOS FORRÁS", "Kiadó: Igazságügyi Minisztérium\nOperátor: MKIFK Magyar Közlönykiadó Zrt.\nCím: 1055 Budapest, Nádor u. 22.")
            
            Spacer(modifier = Modifier.height(64.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Készítette: pacuka\n2024 - Minden jog fenntartva", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AboutTextSection(title: String, content: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(12.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ThemeOption(theme: AppTheme, label: String, currentTheme: AppTheme, onClick: (AppTheme) -> Unit) {
    Surface(
        onClick = { onClick(theme) },
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            RadioButton(selected = currentTheme == theme, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun KozlonyList(
    items: List<Kozlony>,
    onIssueClick: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    state: androidx.compose.foundation.lazy.LazyListState
) {
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filter { it != null && it >= items.size - 3 && items.isNotEmpty() }
            .distinctUntilChanged()
            .collect {
                onLoadMore()
            }
    }

    LazyColumn(
        state = state, 
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.Top
    ) {
        itemsIndexed(items, key = { _, item -> item.pdfUrl }) { index, item ->
            KozlonyItem(item, onIssueClick)
            if (index < items.size - 1) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            }
        }
        if (isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun KozlonyItem(item: Kozlony, onClick: (String, String) -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = { onClick(item.pdfUrl, item.title) },
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { Downloader.downloadPdf(context, item.pdfUrl, item.title) }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            // Only show distinct documents (skip if url is the same as main pdfUrl)
            val additionalDocs = item.documents.filter { it.url != item.pdfUrl && it.label != "Közlöny" && it.label != "Letöltés" }
            
            if (additionalDocs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    additionalDocs.forEach { doc ->
                        OutlinedButton(
                            onClick = { onClick(doc.url, "${item.title} - ${doc.label}") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(doc.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
