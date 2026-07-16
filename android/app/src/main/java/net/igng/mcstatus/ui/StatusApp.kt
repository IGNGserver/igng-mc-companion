@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package net.igng.mcstatus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import kotlin.math.max
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.launch
import net.igng.mcstatus.data.AppSettings
import net.igng.mcstatus.data.DetailSection
import net.igng.mcstatus.data.LatencyRecord
import net.igng.mcstatus.data.PerformanceSample
import net.igng.mcstatus.data.RangePreset
import net.igng.mcstatus.data.ServerCardState
import net.igng.mcstatus.data.StatusRepository
import net.igng.mcstatus.data.ThemeAccent
import net.igng.mcstatus.data.TicketRepository
import net.igng.mcstatus.data.SavedAccount
import net.igng.mcstatus.data.toDisplayTime

@Composable
fun StatusApp(
    repository: StatusRepository,
    ticketRepository: TicketRepository,
    settings: AppSettings,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetUseSystemAccent: (Boolean) -> Unit,
    onSetAccent: (ThemeAccent) -> Unit,
    onLogin: (String, String, String, net.igng.mcstatus.data.MathCaptcha, String) -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
    loginState: LoginUiState,
) {
    val navController = rememberNavController()
    val haptic = LocalHapticFeedback.current
    val destination by navController.currentBackStackEntryAsState()
    val route = destination?.destination?.route
    Scaffold(bottomBar = {
        if (route == "overview" || route == "tickets") NavigationBar {
            NavigationBarItem(selected = route == "overview", onClick = { performAppHaptic(haptic, settings, AppHapticType.Tap); navController.navigate("overview") { launchSingleTop = true } }, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("状态") })
            NavigationBarItem(selected = route == "tickets", onClick = { performAppHaptic(haptic, settings, AppHapticType.Tap); navController.navigate("tickets") { launchSingleTop = true } }, icon = { Icon(Icons.Rounded.Article, null) }, label = { Text("工单") })
        }
    }) { outerPadding -> NavHost(
        navController = navController,
        startDestination = "overview",
        modifier = Modifier.padding(outerPadding),
    ) {
        composable("overview") {
            val viewModel: OverviewViewModel = viewModel(
                factory = remember(repository) { OverviewViewModelFactory(repository) }
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            OverviewScreen(
                uiState = uiState,
                onRetry = viewModel::refresh,
                settings = settings,
                onOpenSettings = { navController.navigate("settings") },
                onOpenServer = { serverId, section ->
                    navController.navigate("detail/$serverId?section=${section.id}")
                }
            )
        }

        composable(
            route = "detail/{serverId}?section={section}",
            arguments = listOf(
                navArgument("serverId") { type = NavType.IntType },
                navArgument("section") {
                    type = NavType.StringType
                    defaultValue = DetailSection.OVERVIEW.id
                }
            )
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getInt("serverId") ?: return@composable
            val initialSection = DetailSection.fromId(backStackEntry.arguments?.getString("section"))
            val viewModel: DetailViewModel = viewModel(
                key = "detail-$serverId",
                factory = remember(repository, serverId) {
                    DetailViewModelFactory(repository, serverId)
                }
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            DetailScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSelectRange = viewModel::selectRange,
                initialSection = initialSection,
                settings = settings,
                onRetry = viewModel::refresh,
            )
        }

        composable("settings") {
            SettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onSetVibrationEnabled = onSetVibrationEnabled,
                onSetUseSystemAccent = onSetUseSystemAccent,
                onSetAccent = onSetAccent,
                loginState = loginState,
                onLogin = onLogin,
                onLogout = onLogout,
                onSwitchAccount = onSwitchAccount,
            )
        }
        composable("tickets") { TicketsScreen(ticketRepository, settings) }
    }
    }
}

@Composable
private fun OverviewScreen(
    uiState: OverviewUiState,
    onRetry: () -> Unit,
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenServer: (Int, DetailSection) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("IGNGmc")
                        Text(
                            text = "服务器状态总览",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            performAppHaptic(haptic, settings)
                            onRetry()
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                    IconButton(
                        onClick = {
                            performAppHaptic(haptic, settings)
                            onOpenSettings()
                        }
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(innerPadding)
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage,
                innerPadding = innerPadding,
                settings = settings,
                onRetry = onRetry
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        OverviewSummary(uiState = uiState)
                    }
                    items(uiState.cards, key = { it.server.server_id }) { card ->
                        ServerOverviewCard(
                            card = card,
                            settings = settings,
                            onClick = { onOpenServer(card.server.server_id, DetailSection.OVERVIEW) },
                            onOpenSection = { section -> onOpenServer(card.server.server_id, section) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onSelectRange: (RangePreset) -> Unit,
    initialSection: DetailSection,
    settings: AppSettings,
    onRetry: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val performanceListState = rememberLazyListState()
    val networkListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var initialJumpHandled by rememberSaveable(uiState.detail?.server?.server_id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.detail?.server?.server_name ?: "服务器详情")
                        Text(
                            text = uiState.detail?.server?.address ?: "性能历史与趋势",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            performAppHaptic(haptic, settings)
                            onRetry()
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.detail == null && uiState.isLoading -> LoadingState(innerPadding)
            uiState.detail == null && uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage,
                innerPadding = innerPadding,
                settings = settings,
                onRetry = onRetry
            )
            uiState.detail == null -> ErrorState(
                message = "没有可用的服务器详情数据",
                innerPadding = innerPadding,
                settings = settings,
                onRetry = onRetry
            )
            else -> {
                val perfLogs = uiState.detail.perfLogs
                val latencies = uiState.detail.latencies
                val availableNodeIds = remember(latencies) {
                    latencies
                        .groupBy { it.node_id }
                        .mapNotNull { (nodeId, samples) ->
                            val name = samples.lastOrNull()?.node_name ?: "节点 $nodeId"
                            if (samples.isEmpty()) null else nodeId to name
                        }
                        .sortedBy { it.first }
                }
                val initialPage = remember(initialSection) { detailSectionToPage(initialSection) }
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
                var selectedNodeIds by remember(uiState.detail.server.server_id) { mutableStateOf(emptySet<Int>()) }

                LaunchedEffect(availableNodeIds) {
                    val nextIds = availableNodeIds.map { it.first }.toSet()
                    selectedNodeIds = when {
                        nextIds.isEmpty() -> emptySet()
                        selectedNodeIds.isEmpty() -> nextIds
                        else -> selectedNodeIds.intersect(nextIds).ifEmpty { nextIds }
                    }
                }

                LaunchedEffect(uiState.detail?.server?.server_id, initialSection, initialJumpHandled) {
                    if (!initialJumpHandled) {
                        val targetPage = detailSectionToPage(initialSection)
                        if (pagerState.currentPage != targetPage) {
                            pagerState.scrollToPage(targetPage)
                        }
                        val targetIndex = detailSectionToPageIndex(initialSection)
                        val targetList = if (targetPage == DetailPage.PERFORMANCE) {
                            performanceListState
                        } else {
                            networkListState
                        }
                        if (targetIndex > 0) {
                            targetList.scrollToItem(targetIndex)
                        }
                        initialJumpHandled = true
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailPageSelector(
                                selectedPage = pagerState.currentPage,
                                onSelectPage = { page ->
                                    performAppHaptic(haptic, settings)
                                    coroutineScope.launch { pagerState.animateScrollToPage(page) }
                                }
                            )
                            RangeSelector(
                                selected = uiState.selectedRange,
                                onSelect = {
                                    performAppHaptic(haptic, settings)
                                    onSelectRange(it)
                                }
                            )
                            if (pagerState.currentPage == DetailPage.NETWORK && availableNodeIds.isNotEmpty()) {
                                NodeMultiSelector(
                                    nodes = availableNodeIds,
                                    selectedNodeIds = selectedNodeIds,
                                    onToggleNode = { nodeId ->
                                        performAppHaptic(haptic, settings, AppHapticType.Tap)
                                        selectedNodeIds = selectedNodeIds.toggleNode(nodeId, availableNodeIds.size)
                                    }
                                )
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            DetailPage.PERFORMANCE -> PerformanceDetailPage(
                                perfLogs = perfLogs,
                                isLoading = uiState.isLoading,
                                settings = settings,
                                listState = performanceListState
                            )
                            else -> NetworkDetailPage(
                                latencies = latencies,
                                selectedNodeIds = selectedNodeIds,
                                isLoading = uiState.isLoading,
                                settings = settings,
                                listState = networkListState
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(
    selected: RangePreset,
    onSelect: (RangePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RangePreset.entries.forEach { preset ->
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                label = { Text(preset.label) }
            )
        }
    }
}

private object DetailPage {
    const val PERFORMANCE = 0
    const val NETWORK = 1
}

@Composable
private fun DetailPageSelector(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val trackPadding = 4.dp
        val segmentWidth = (maxWidth - trackPadding * 2) / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedPage == DetailPage.PERFORMANCE) {
                trackPadding
            } else {
                trackPadding + segmentWidth
            },
            label = "detail-page-indicator"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(999.dp))
                    .padding(trackPadding)
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorOffset)
                        .width(segmentWidth)
                        .height(40.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {}

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailPageButton(
                        label = "性能",
                        selected = selectedPage == DetailPage.PERFORMANCE,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPage(DetailPage.PERFORMANCE) }
                    )
                    DetailPageButton(
                        label = "网络",
                        selected = selectedPage == DetailPage.NETWORK,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectPage(DetailPage.NETWORK) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPageButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun NodeMultiSelector(
    nodes: List<Pair<Int, String>>,
    selectedNodeIds: Set<Int>,
    onToggleNode: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "显示节点",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            nodes.forEach { (nodeId, nodeName) ->
                val chipInteractionSource = remember(nodeId) { MutableInteractionSource() }
                val selected = selectedNodeIds.contains(nodeId)
                val containerColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "node-chip-bg-$nodeId"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "node-chip-label-$nodeId"
                )
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.97f,
                    label = "node-chip-scale-$nodeId"
                )

                Surface(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(
                            interactionSource = chipInteractionSource,
                            indication = null,
                            onClick = { onToggleNode(nodeId) }
                        ),
                    shape = RoundedCornerShape(999.dp),
                    color = containerColor
                ) {
                    Text(
                        text = nodeName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = labelColor
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceDetailPage(
    perfLogs: List<PerformanceSample>,
    isLoading: Boolean,
    settings: AppSettings,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailSummary(perfLogs = perfLogs)
        }
        item {
            MetricLineChart(
                title = "TPS",
                unit = "",
                color = Color(0xFF3384FF),
                records = perfLogs,
                valueSelector = { it.avg_tps ?: 0.0 },
                displayedValueFormatter = { value -> "%.1f".format(value) },
                isLoading = isLoading,
                settings = settings,
                maxOverride = 20.0
            )
        }
        item {
            MetricLineChart(
                title = "MSPT",
                unit = "ms",
                color = Color(0xFFFF8C42),
                records = perfLogs,
                valueSelector = { it.avg_mspt ?: 0.0 },
                displayedValueFormatter = { value -> "%.1f ms".format(value) },
                isLoading = isLoading,
                settings = settings
            )
        }
        item {
            MetricLineChart(
                title = "内存",
                unit = "MB",
                color = Color(0xFFC13484),
                records = perfLogs,
                valueSelector = { it.memory_usage_mb ?: 0.0 },
                displayedValueFormatter = { value -> "%.0f MB".format(value) },
                isLoading = isLoading,
                settings = settings
            )
        }
        item {
            MetricLineChart(
                title = "CPU",
                unit = "%",
                color = Color(0xFF5BA832),
                records = perfLogs,
                valueSelector = { it.cpu_usage ?: 0.0 },
                displayedValueFormatter = { value -> "%.1f%%".format(value) },
                isLoading = isLoading,
                settings = settings,
                maxOverride = 100.0
            )
        }
        item {
            MetricLineChart(
                title = "在线人数",
                unit = "人",
                color = Color(0xFF6B82FF),
                records = perfLogs,
                valueSelector = { (it.online_players ?: 0).toDouble() },
                displayedValueFormatter = { value -> "${value.toInt()} 人" },
                isLoading = isLoading,
                settings = settings
            )
        }
    }
}

@Composable
private fun NetworkDetailPage(
    latencies: List<LatencyRecord>,
    selectedNodeIds: Set<Int>,
    isLoading: Boolean,
    settings: AppSettings,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            NetworkSummary(latencies = latencies, selectedNodeIds = selectedNodeIds)
        }
        item {
            LatencyMultiLineChart(
                title = "节点延迟",
                unit = "ms",
                colorSeed = Color(0xFF33A1FD),
                records = latencies,
                selectedNodeIds = selectedNodeIds,
                valueSelector = { it.avg_latency_ms ?: 0.0 },
                displayedValueFormatter = { value -> if (value <= 0.0) "超时" else "%.1f ms".format(value) },
                isLoading = isLoading,
                settings = settings
            )
        }
        item {
            LatencyMultiLineChart(
                title = "节点最小延迟",
                unit = "ms",
                colorSeed = Color(0xFF00A896),
                records = latencies,
                selectedNodeIds = selectedNodeIds,
                valueSelector = { it.min_latency_ms ?: 0.0 },
                displayedValueFormatter = { value -> if (value <= 0.0) "超时" else "%.1f ms".format(value) },
                isLoading = isLoading,
                settings = settings
            )
        }
        item {
            LatencyMultiLineChart(
                title = "节点最大延迟",
                unit = "ms",
                colorSeed = Color(0xFFFF9F1C),
                records = latencies,
                selectedNodeIds = selectedNodeIds,
                valueSelector = { it.max_latency_ms ?: 0.0 },
                displayedValueFormatter = { value -> if (value <= 0.0) "超时" else "%.1f ms".format(value) },
                isLoading = isLoading,
                settings = settings
            )
        }
        item {
            LatencyMultiLineChart(
                title = "节点丢包",
                unit = "%",
                colorSeed = Color(0xFFE71D36),
                records = latencies,
                selectedNodeIds = selectedNodeIds,
                valueSelector = { it.packet_loss_pct ?: 0.0 },
                displayedValueFormatter = { value -> "%.2f%%".format(value) },
                isLoading = isLoading,
                settings = settings,
                maxOverride = 100.0
            )
        }
    }
}

@Composable
private fun OverviewSummary(uiState: OverviewUiState) {
    val metrics = uiState.metrics
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "状态总览",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "服务器",
                value = metrics.serverCount.toString(),
                icon = Icons.Rounded.Memory
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "在线中",
                value = metrics.onlineCount.toString(),
                icon = Icons.Rounded.NetworkCheck
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "在线人数",
                value = metrics.totalPlayers.toString(),
                icon = Icons.Rounded.Groups
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "平均 TPS",
                value = if (metrics.avgTps == 0.0) "0.0" else "%.1f".format(metrics.avgTps),
                icon = Icons.Rounded.Speed
            )
        }
    }
}

@Composable
private fun SummaryTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AutoResizeValueText(value = value, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ServerOverviewCard(
    card: ServerCardState,
    settings: AppSettings,
    onClick: () -> Unit,
    onOpenSection: (DetailSection) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val perf = card.latestPerf
    val latency = card.bestLatency

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                performAppHaptic(haptic, settings)
                onClick()
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.server.server_name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = card.server.address ?: "未提供地址",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "更新于 ${perf?.recorded_at?.toDisplayTime() ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onClick()
                    },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = if (card.isOnline) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        labelColor = if (card.isOnline) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    ),
                    label = { Text(if (card.isOnline) "在线" else "离线") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (card.isOnline) Color(0xFF20C997) else Color(0xFFE63946))
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip(
                    label = "TPS",
                    value = perf?.avg_tps?.let { "%.1f".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.TPS)
                    }
                )
                MetricChip(
                    label = "MSPT",
                    value = perf?.avg_mspt?.let { "%.1f".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.MSPT)
                    }
                )
                MetricChip(
                    label = "延迟",
                    value = latency?.avg_latency_ms?.takeIf { it > 0 }?.let { "%.1fms".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.LATENCY)
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip(
                    label = "CPU",
                    value = perf?.cpu_usage?.let { "%.1f%%".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.CPU)
                    }
                )
                MetricChip(
                    label = "内存",
                    value = perf?.memory_usage_mb?.let { "%.0fMB".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.MEMORY)
                    }
                )
                MetricChip(
                    label = "丢包",
                    value = latency?.packet_loss_pct?.let { "%.2f%%".format(it) } ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.PACKET_LOSS)
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip(
                    label = "人数",
                    value = perf?.online_players?.toString() ?: "--",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.PLAYERS)
                    }
                )
                MetricChip(
                    label = "节点",
                    value = latency?.node_name ?: "暂无",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onOpenSection(DetailSection.LATENCY)
                    }
                )
                MetricChip(
                    label = "状态",
                    value = if (card.isOnline) "运行中" else "暂无数据",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .height(76.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            AutoResizeMetricText(value = value, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AutoResizeValueText(
    value: String,
    modifier: Modifier = Modifier,
) {
    val candidateSizes = listOf(24.sp, 22.sp, 20.sp, 18.sp, 16.sp, 14.sp, 12.sp)
    var sizeIndex by remember(value) { mutableIntStateOf(0) }

    Text(
        text = value,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        fontSize = candidateSizes[sizeIndex.coerceAtMost(candidateSizes.lastIndex)],
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && sizeIndex < candidateSizes.lastIndex) {
                sizeIndex += 1
            }
        }
    )
}

private fun resolveChartIndex(
    x: Float,
    width: Float,
    count: Int,
): Int {
    if (count <= 1 || width <= 0f) return 0
    val stepX = width / (count - 1)
    return (x / stepX).toInt().coerceIn(0, count - 1)
}

private fun detailSectionToPage(section: DetailSection): Int = when (section) {
    DetailSection.OVERVIEW,
    DetailSection.TPS,
    DetailSection.MSPT,
    DetailSection.MEMORY,
    DetailSection.CPU,
    DetailSection.PLAYERS -> DetailPage.PERFORMANCE
    DetailSection.LATENCY,
    DetailSection.LATENCY_MIN,
    DetailSection.LATENCY_MAX,
    DetailSection.PACKET_LOSS -> DetailPage.NETWORK
}

private fun detailSectionToPageIndex(section: DetailSection): Int = when (section) {
    DetailSection.OVERVIEW -> 0
    DetailSection.TPS -> 1
    DetailSection.MSPT -> 2
    DetailSection.MEMORY -> 3
    DetailSection.CPU -> 4
    DetailSection.PLAYERS -> 5
    DetailSection.LATENCY -> 1
    DetailSection.LATENCY_MIN -> 2
    DetailSection.LATENCY_MAX -> 3
    DetailSection.PACKET_LOSS -> 4
}

private fun Set<Int>.toggleNode(
    nodeId: Int,
    totalNodes: Int,
): Set<Int> = if (contains(nodeId)) {
    if (size == 1 && totalNodes > 0) this else this - nodeId
} else {
    this + nodeId
}

private fun chartPalette(
    seed: Color,
    count: Int,
): List<Color> = List(count.coerceAtLeast(1)) { index ->
    seed.copy(
        red = (seed.red + index * 0.07f).coerceAtMost(1f),
        green = (seed.green + index * 0.05f).coerceAtMost(1f),
        blue = (seed.blue + index * 0.06f).coerceAtMost(1f),
        alpha = 1f
    )
}

@Composable
private fun AutoResizeMetricText(
    value: String,
    modifier: Modifier = Modifier,
) {
    val sizes = listOf(20.sp, 18.sp, 16.sp, 14.sp, 12.sp, 11.sp, 10.sp, 9.sp)
    var index by remember(value) { mutableIntStateOf(0) }

    Text(
        text = value,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        fontSize = sizes[index],
        fontWeight = FontWeight.Bold,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && index < sizes.lastIndex) {
                index += 1
            }
        }
    )
}

@Composable
private fun DetailSummary(perfLogs: List<PerformanceSample>) {
    val latest = perfLogs.lastOrNull()
    val avgTps = perfLogs.mapNotNull { it.avg_tps }.average().takeIf { !it.isNaN() } ?: 0.0
    val peakPlayers = perfLogs.maxOfOrNull { it.online_players ?: 0 } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "样本数",
                value = perfLogs.size.toString(),
                icon = Icons.Rounded.Memory
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "当前人数",
                value = (latest?.online_players ?: 0).toString(),
                icon = Icons.Rounded.Groups
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "平均 TPS",
                value = "%.1f".format(avgTps),
                icon = Icons.Rounded.Speed
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "峰值人数",
                value = peakPlayers.toString(),
                icon = Icons.Rounded.NetworkCheck
            )
        }
    }
}

@Composable
private fun NetworkSummary(
    latencies: List<LatencyRecord>,
    selectedNodeIds: Set<Int>,
) {
    val latestPerNode = latencies
        .groupBy { it.node_id }
        .mapValues { (_, records) -> records.maxByOrNull { it.timestamp_utc } }
        .values
        .filterNotNull()
    val visibleNodes = latestPerNode.filter { selectedNodeIds.contains(it.node_id) }
    val bestNode = latestPerNode
        .filter { (it.avg_latency_ms ?: 0.0) > 0.0 }
        .minByOrNull { it.avg_latency_ms ?: Double.MAX_VALUE }
    val avgLoss = visibleNodes.mapNotNull { it.packet_loss_pct }.average().takeIf { !it.isNaN() } ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "网络概览",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "显示节点",
                value = visibleNodes.size.toString(),
                icon = Icons.Rounded.NetworkCheck
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "最低延迟",
                value = bestNode?.avg_latency_ms?.let { "%.1f ms".format(it) } ?: "--",
                icon = Icons.Rounded.Speed
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "最佳节点",
                value = bestNode?.node_name ?: "暂无",
                icon = Icons.Rounded.Memory
            )
            SummaryTile(
                modifier = Modifier.weight(1f),
                label = "平均丢包",
                value = "%.2f%%".format(avgLoss),
                icon = Icons.Rounded.Groups
            )
        }
    }
}

private data class LatencySeries(
    val nodeId: Int,
    val nodeName: String,
    val records: List<LatencyRecord>,
)

@Composable
private fun LatencyMultiLineChart(
    title: String,
    unit: String,
    colorSeed: Color,
    records: List<LatencyRecord>,
    selectedNodeIds: Set<Int>,
    valueSelector: (LatencyRecord) -> Double,
    displayedValueFormatter: (Double) -> String,
    isLoading: Boolean,
    settings: AppSettings,
    maxOverride: Double? = null,
) {
    val allSeries = remember(records) {
        records
            .groupBy { it.node_id }
            .mapNotNull { (nodeId, samples) ->
                val sorted = samples.sortedBy { it.timestamp_utc }
                val name = sorted.lastOrNull()?.node_name ?: "节点 $nodeId"
                if (sorted.isEmpty()) null else LatencySeries(nodeId, name, sorted)
            }
            .sortedBy { it.nodeId }
    }
    val series = remember(allSeries, selectedNodeIds) {
        allSeries.filter { selectedNodeIds.contains(it.nodeId) }
    }
    var selectedNodeIndex by remember(series) { mutableIntStateOf(0) }
    var selectedPointIndex by remember(series) { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(series) {
        if (series.isNotEmpty()) {
            selectedNodeIndex = selectedNodeIndex.coerceIn(0, series.lastIndex)
            selectedPointIndex = selectedPointIndex.coerceAtLeast(0)
        }
    }

    fun updateSelection(nextNodeIndex: Int, nextPointIndex: Int) {
        val safeNodeIndex = nextNodeIndex.coerceIn(0, series.lastIndex.coerceAtLeast(0))
        val nodeSeries = series.getOrNull(safeNodeIndex) ?: return
        val safePointIndex = nextPointIndex.coerceIn(0, nodeSeries.records.lastIndex.coerceAtLeast(0))
        if (safeNodeIndex != selectedNodeIndex || safePointIndex != selectedPointIndex) {
            selectedNodeIndex = safeNodeIndex
            selectedPointIndex = safePointIndex
            performAppHaptic(haptic, settings, AppHapticType.Tick)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .alpha(if (isLoading) 0.4f else 1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )

                if (series.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("当前时间范围内暂无网络样本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }

                val activeSeries = series[selectedNodeIndex.coerceIn(0, series.lastIndex)]
                val values = series.flatMap { node -> node.records.map(valueSelector) }
                val maxValue = maxOverride ?: max(values.maxOrNull() ?: 1.0, 1.0)
                val palette = chartPalette(colorSeed, series.size)
                val selectedRecord = activeSeries.records[selectedPointIndex.coerceIn(0, activeSeries.records.lastIndex)]
                val selectedValue = valueSelector(selectedRecord)
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                val markerOuterColor = MaterialTheme.colorScheme.surface

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已显示 ${series.size} 个节点",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AssistChip(
                        onClick = {
                            val nextIndex = (selectedNodeIndex + 1) % series.size
                            updateSelection(nextIndex, series[nextIndex].records.lastIndex)
                        },
                        label = { Text("焦点: ${activeSeries.nodeName}") }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        selectedRecord.timestamp_utc.toDisplayTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${activeSeries.nodeName} ${displayedValueFormatter(selectedValue)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .pointerInput(series, selectedNodeIndex) {
                            detectTapGestures { offset ->
                                val nodeSeries = series.getOrNull(selectedNodeIndex) ?: return@detectTapGestures
                                updateSelection(
                                    selectedNodeIndex,
                                    resolveChartIndex(offset.x, size.width.toFloat(), nodeSeries.records.size)
                                )
                            }
                        }
                        .pointerInput(series, selectedNodeIndex) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val nodeSeries = series.getOrNull(selectedNodeIndex) ?: return@detectDragGestures
                                    updateSelection(
                                        selectedNodeIndex,
                                        resolveChartIndex(offset.x, size.width.toFloat(), nodeSeries.records.size)
                                    )
                                },
                                onDrag = { change, _ ->
                                    val nodeSeries = series.getOrNull(selectedNodeIndex) ?: return@detectDragGestures
                                    updateSelection(
                                        selectedNodeIndex,
                                        resolveChartIndex(change.position.x, size.width.toFloat(), nodeSeries.records.size)
                                    )
                                    change.consume()
                                }
                            )
                        }
                ) {
                    val yFor: (Double) -> Float = { value ->
                        size.height - ((value / maxValue).toFloat() * size.height)
                    }

                    repeat(4) { idx ->
                        val y = size.height * idx / 3f
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }

                    series.forEachIndexed { index, nodeSeries ->
                        val stepX = if (nodeSeries.records.size <= 1) 0f else size.width / (nodeSeries.records.size - 1)
                        val path = Path()
                        nodeSeries.records.forEachIndexed { pointIndex, record ->
                            val x = if (nodeSeries.records.size == 1) size.width / 2f else pointIndex * stepX
                            val y = yFor(valueSelector(record))
                            if (pointIndex == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = palette[index],
                            style = Stroke(width = if (index == selectedNodeIndex) 4f else 2.5f, cap = StrokeCap.Round)
                        )
                    }

                    val activeStepX = if (activeSeries.records.size <= 1) 0f else size.width / (activeSeries.records.size - 1)
                    val selectedX = if (activeSeries.records.size == 1) size.width / 2f else selectedPointIndex.coerceIn(0, activeSeries.records.lastIndex) * activeStepX
                    val selectedY = yFor(selectedValue)

                    drawLine(
                        color = palette[selectedNodeIndex].copy(alpha = 0.35f),
                        start = Offset(selectedX, 0f),
                        end = Offset(selectedX, size.height),
                        strokeWidth = 2f
                    )
                    drawCircle(
                        color = markerOuterColor,
                        radius = 10f,
                        center = Offset(selectedX, selectedY)
                    )
                    drawCircle(
                        color = palette[selectedNodeIndex],
                        radius = 6f,
                        center = Offset(selectedX, selectedY)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChartAxisLabel("${formatAxisValue(maxValue, unit)} / 顶部")
                    ChartAxisLabel("${formatAxisValue(0.0, unit)} / 底部")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        activeSeries.records.first().timestamp_utc.toDisplayTime(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        activeSeries.records.last().timestamp_utc.toDisplayTime(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun MetricLineChart(
    title: String,
    unit: String,
    color: Color,
    records: List<PerformanceSample>,
    valueSelector: (PerformanceSample) -> Double,
    displayedValueFormatter: (Double) -> String,
    isLoading: Boolean,
    settings: AppSettings,
    maxOverride: Double? = null,
) {
    var selectedIndex by remember(records) { mutableIntStateOf(records.lastIndex.coerceAtLeast(0)) }
    val haptic = LocalHapticFeedback.current

    fun updateSelectedIndex(
        nextIndex: Int,
        vibrate: Boolean = true,
        feedbackType: AppHapticType = AppHapticType.Tick,
    ) {
        if (nextIndex != selectedIndex) {
            selectedIndex = nextIndex
            if (vibrate) {
                performAppHaptic(haptic, settings, feedbackType)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .alpha(if (isLoading) 0.4f else 1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (records.isEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("当前时间范围内暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }

                val values = records.map(valueSelector)
                val maxValue = maxOverride ?: max(values.maxOrNull() ?: 1.0, 1.0)
                val selected = records[selectedIndex.coerceIn(0, records.lastIndex)]
                val selectedValue = valueSelector(selected)
                val lineColor = color
                val fillColor = color.copy(alpha = 0.16f)
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                val markerOuterColor = MaterialTheme.colorScheme.surface

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        selected.recorded_at.toDisplayTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$title ${displayedValueFormatter(selectedValue)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .pointerInput(records) {
                            detectTapGestures { offset ->
                                if (records.isEmpty()) return@detectTapGestures
                                updateSelectedIndex(
                                    resolveChartIndex(offset.x, size.width.toFloat(), records.size),
                                    feedbackType = AppHapticType.Tap
                                )
                            }
                        }
                        .pointerInput(records) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (records.isEmpty()) return@detectDragGestures
                                    updateSelectedIndex(
                                        resolveChartIndex(offset.x, size.width.toFloat(), records.size),
                                        feedbackType = AppHapticType.Tick
                                    )
                                },
                                onDrag = { change, _ ->
                                    updateSelectedIndex(
                                        resolveChartIndex(change.position.x, size.width.toFloat(), records.size),
                                        feedbackType = AppHapticType.Tick
                                    )
                                    change.consume()
                                }
                            )
                        }
                ) {
                    val stepX = if (records.size == 1) 0f else size.width / (records.size - 1)
                    val yFor: (Double) -> Float = { value ->
                        size.height - ((value / maxValue).toFloat() * size.height)
                    }

                    repeat(4) { idx ->
                        val y = size.height * idx / 3f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }

                    val path = Path()
                    val fillPath = Path()
                    records.forEachIndexed { index, record ->
                        val x = index * stepX
                        val y = yFor(valueSelector(record))
                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(size.width, size.height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent))
                    )
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )

                    val selectedX = if (records.size == 1) size.width / 2f else selectedIndex.coerceIn(0, records.lastIndex) * stepX
                    val selectedY = yFor(selectedValue)
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = Offset(selectedX, 0f),
                        end = Offset(selectedX, size.height),
                        strokeWidth = 2f
                    )
                    drawCircle(color = markerOuterColor, radius = 10f, center = Offset(selectedX, selectedY))
                    drawCircle(color = lineColor, radius = 6f, center = Offset(selectedX, selectedY))
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChartAxisLabel("${formatAxisValue(maxValue, unit)} / 顶部")
                    ChartAxisLabel("${formatAxisValue(0.0, unit)} / 底部")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(records.first().recorded_at.toDisplayTime(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(records.last().recorded_at.toDisplayTime(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ChartAxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetUseSystemAccent: (Boolean) -> Unit,
    onSetAccent: (ThemeAccent) -> Unit,
    loginState: LoginUiState,
    onLogin: (String, String, String, net.igng.mcstatus.data.MathCaptcha, String) -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: (SavedAccount) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AccountSettings(settings, loginState, onLogin, onLogout, onSwitchAccount) }
            item {
                SettingsSection(title = "交互") {
                    SettingSwitchRow(
                        title = "启用震动反馈",
                        subtitle = "按钮和切换时跟随系统震感强度反馈",
                        checked = settings.vibrationEnabled,
                        onCheckedChange = {
                            if (it) {
                                performAppHaptic(haptic, settings.copy(vibrationEnabled = true))
                            }
                            onSetVibrationEnabled(it)
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "外观") {
                    SettingSwitchRow(
                        title = "使用系统主题色",
                        subtitle = "开启后优先使用 Android 系统动态主题色",
                        checked = settings.useSystemAccent,
                        onCheckedChange = {
                            performAppHaptic(haptic, settings)
                            onSetUseSystemAccent(it)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "应用主题色",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeAccent.entries.forEach { accent ->
                            FilterChip(
                                selected = settings.accent == accent,
                                onClick = {
                                    performAppHaptic(haptic, settings)
                                    onSetAccent(accent)
                                },
                                enabled = !settings.useSystemAccent,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                label = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(accent.label)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSettings(settings: AppSettings, loginState: LoginUiState, onLogin: (String, String, String, net.igng.mcstatus.data.MathCaptcha, String) -> Unit, onLogout: () -> Unit, onSwitchAccount: (SavedAccount) -> Unit) {
    var identifier by rememberSaveable { mutableStateOf("") }; var password by rememberSaveable { mutableStateOf("") }; var answer by rememberSaveable { mutableStateOf("") }; var duration by rememberSaveable { mutableStateOf("7d") }; var showLoginForm by rememberSaveable { mutableStateOf(settings.accounts.isEmpty()) }
    SettingsSection("IGNG 账户") {
        if (settings.accounts.isNotEmpty()) {
            Text("当前账户：${settings.accountName ?: "未选择"}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { settings.accounts.forEach { account -> FilterChip(selected = settings.sessionToken == account.token, onClick = { onSwitchAccount(account); showLoginForm = false }, label = { Text(account.displayName) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showLoginForm = !showLoginForm }) { Text(if (showLoginForm) "取消添加" else "添加账户") }; OutlinedButton(onClick = onLogout) { Text("退出当前账户") } }
        }
        if (showLoginForm || settings.accounts.isEmpty()) {
            Text(if (settings.accounts.isEmpty()) "登录 IGNG 账户" else "添加 IGNG 账户", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(identifier, { identifier = it }, label = { Text("用户名或邮箱") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(mask = '\u2022'), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text("登录时长", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("30m" to "30 分钟", "1d" to "1 天", "7d" to "7 天", "1m" to "1 个月", "1y" to "1 年").forEach { (value, label) -> FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text(label) }) } }
            val captcha = loginState.captcha
            if (captcha != null) OutlinedTextField(answer, { answer = it }, label = { Text("人机验证：${captcha.a} + ${captcha.b} = ?") }, modifier = Modifier.fillMaxWidth())
            loginState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { if (captcha != null) onLogin(identifier, password, duration, captcha, answer) }, enabled = captcha != null && !loginState.loading && identifier.isNotBlank() && password.isNotBlank() && answer.isNotBlank()) { Text(if (loginState.loading) "登录中..." else "登录") }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatAxisValue(value: Double, unit: String): String {
    val formatted = when {
        unit == "人" -> value.toInt().toString()
        value >= 100 -> "%.0f".format(value)
        else -> "%.1f".format(value)
    }
    return if (unit.isBlank()) formatted else "$formatted$unit"
}

@Composable
private fun LoadingState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String,
    innerPadding: PaddingValues,
    settings: AppSettings,
    onRetry: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("加载失败", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(message, textAlign = TextAlign.Center)
                AssistChip(
                    onClick = {
                        performAppHaptic(haptic, settings)
                        onRetry()
                    },
                    label = { Text("重试") }
                )
            }
        }
    }
}

private fun performAppHaptic(
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    settings: AppSettings,
    type: AppHapticType = AppHapticType.Action,
) {
    if (settings.vibrationEnabled) {
        haptic.performHapticFeedback(
            when (type) {
                AppHapticType.Action -> HapticFeedbackType.LongPress
                // MIX 7 ignores TextHandleMove (constant 9), while LongPress maps to a supported touch effect.
                AppHapticType.Tap -> HapticFeedbackType.LongPress
                AppHapticType.Tick -> HapticFeedbackType.LongPress
            }
        )
    }
}

private enum class AppHapticType {
    Action,
    Tap,
    Tick,
}
