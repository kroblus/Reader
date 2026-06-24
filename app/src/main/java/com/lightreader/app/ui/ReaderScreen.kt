package com.lightreader.app.ui

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.reader.palette
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(preferences: ReaderPreferences, viewModel: MainViewModel) {
    val state by viewModel.readerState.collectAsState()
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(currentTime()) }
    val palette = preferences.palette()
    val background = Color(palette.background)
    val foreground = Color(palette.foreground)

    ApplyWindowPreferences(preferences)
    ApplyReaderSystemBars(preferences, state.toolbarVisible)
    DisposableEffect(viewModel) { onDispose(viewModel::onReaderStopped) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = currentTime()
            delay(30_000)
        }
    }
    LaunchedEffect(state.toolbarVisible, showToc, showBookmarks, state.settingsVisible) {
        if (state.toolbarVisible && !showToc && !showBookmarks && !state.settingsVisible) {
            delay(3_000)
            viewModel.hideToolbar()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val density = LocalDensity.current
        val safeTopPx = WindowInsets.systemBarsIgnoringVisibility.getTop(density)
        val safeBottomPx = WindowInsets.systemBarsIgnoringVisibility.getBottom(density)
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        LaunchedEffect(widthPx, heightPx, density.density, density.fontScale, safeTopPx, safeBottomPx) {
            viewModel.setViewport(
                widthPx.coerceAtLeast(1),
                heightPx.coerceAtLeast(1),
                density.density,
                density.density * density.fontScale,
                safeTopPx,
                safeBottomPx,
            )
        }

        when {
            state.loading && state.pages.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = foreground)
            state.error != null -> Text(state.error.orEmpty(), Modifier.align(Alignment.Center), color = foreground)
            state.pages.isNotEmpty() -> {
                key(state.chapters.getOrNull(state.chapterIndex)?.id, state.layoutVersion, state.pages.size) {
                    val pagerState = rememberPagerState(state.pageIndex.coerceIn(state.pages.indices)) { state.pages.size }
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(viewModel::pageSelected)
                    }
                    LaunchedEffect(state.pageIndex, preferences.pageTurnMode) {
                        if (state.pageIndex in state.pages.indices && state.pageIndex != pagerState.currentPage) {
                            if (preferences.pageTurnMode == PageTurnMode.SLIDE) pagerState.animateScrollToPage(state.pageIndex)
                            else pagerState.scrollToPage(state.pageIndex)
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = preferences.pageTurnMode == PageTurnMode.HORIZONTAL ||
                            preferences.pageTurnMode == PageTurnMode.SLIDE,
                    ) { pageIndex ->
                        val page = state.pages[pageIndex]
                        var dragDistance by remember(pageIndex) { mutableFloatStateOf(0f) }
                        val manualSwipe = if (preferences.pageTurnMode == PageTurnMode.NONE) {
                            Modifier.pointerInput(pageIndex) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragDistance = 0f },
                                    onHorizontalDrag = { change, amount -> change.consume(); dragDistance += amount },
                                    onDragEnd = {
                                        when {
                                            dragDistance < -64.dp.toPx() -> viewModel.nextPage()
                                            dragDistance > 64.dp.toPx() -> viewModel.previousPage()
                                        }
                                    },
                                )
                            }
                        } else Modifier
                        ReaderPageCanvas(
                            page = page,
                            pageCount = state.pages.size,
                            layoutPreferences = state.layoutPreferences ?: preferences,
                            displayPreferences = preferences,
                            overallProgress = overallProgress(state, page.progressInChapter),
                            currentTime = currentTime,
                            safeTopPx = safeTopPx,
                            safeBottomPx = safeBottomPx,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(manualSwipe)
                                .pointerInput(pageIndex, state.toolbarVisible) {
                                    detectTapGestures { point ->
                                        if (!state.toolbarVisible) {
                                            when {
                                                point.x < size.width * .3f -> viewModel.previousPage()
                                                point.x > size.width * .7f -> viewModel.nextPage()
                                                else -> viewModel.toggleToolbar()
                                            }
                                        } else if (point.x in size.width * .3f..size.width * .7f) {
                                            viewModel.toggleToolbar()
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(state.toolbarVisible, Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(palette.overlay))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::goBack) { Icon(Icons.Outlined.ArrowBack, "返回", tint = foreground) }
                Text(
                    state.chapters.getOrNull(state.chapterIndex)?.title ?: state.book?.title.orEmpty(),
                    Modifier.weight(1f),
                    maxLines = 1,
                    color = foreground,
                )
                IconButton(onClick = { showToc = true }, enabled = state.chapters.isNotEmpty() && !state.loading) {
                    Icon(Icons.Outlined.FormatListBulleted, "目录", tint = foreground)
                }
                IconButton(
                    onClick = { state.book?.let { viewModel.navigate(AppScreen.Search(it.id)) } },
                    enabled = state.book != null && !state.loading,
                ) { Icon(Icons.Outlined.Search, "搜索", tint = foreground) }
                IconButton(onClick = viewModel::addBookmark, enabled = state.pages.isNotEmpty() && !state.loading) {
                    Icon(Icons.Outlined.BookmarkAdd, "添加书签", tint = foreground)
                }
            }
        }

        AnimatedVisibility(state.toolbarVisible, Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(palette.overlay))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showBookmarks = true }, enabled = state.book != null && !state.loading) {
                    Icon(Icons.Outlined.Bookmarks, "书签", tint = foreground)
                }
                Text(
                    "${state.chapterIndex + 1}/${state.chapters.size} · ${state.pageIndex + 1}/${state.pages.size}",
                    color = foreground,
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = viewModel::showSettings) {
                    Icon(Icons.Outlined.TextFields, "阅读设置", tint = foreground)
                }
            }
        }
    }

    if (showToc) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("目录") },
            text = {
                LazyColumn {
                    items(state.chapters, key = { it.id }) { chapter ->
                        ListItem(
                            headlineContent = { Text(chapter.title, maxLines = 2) },
                            supportingContent = { Text("${chapter.charCount} 字") },
                            modifier = Modifier.clickable { viewModel.selectChapter(chapter.orderIndex); showToc = false },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showToc = false }) { Text("关闭") } },
        )
    }
    if (showBookmarks) {
        BookmarksDialog(
            state.bookmarks,
            onDismiss = { showBookmarks = false },
            onJump = { viewModel.jumpToBookmark(it); showBookmarks = false },
            onDelete = viewModel::deleteBookmark,
        )
    }
    if (state.settingsVisible) {
        ModalBottomSheet(
            onDismissRequest = viewModel::hideSettings,
            containerColor = Color(palette.overlay),
            scrimColor = Color.Black.copy(alpha = .24f),
        ) {
            ReaderSettingsPanel(preferences, viewModel::savePreferences, viewModel::hideSettings)
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun ApplyReaderSystemBars(preferences: ReaderPreferences, visible: Boolean) {
    val activity = LocalActivity.current ?: return
    val palette = preferences.palette()
    val controller = remember(activity) { WindowCompat.getInsetsController(activity.window, activity.window.decorView) }
    DisposableEffect(activity, controller) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
    }
    LaunchedEffect(controller, visible, palette.background) {
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = palette.background.toInt()
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = palette.background.toInt()
        controller.isAppearanceLightStatusBars = preferences.theme != com.lightreader.app.core.model.ReaderTheme.NIGHT
        controller.isAppearanceLightNavigationBars = controller.isAppearanceLightStatusBars
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun ApplyWindowPreferences(preferences: ReaderPreferences) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(preferences.brightness, preferences.keepScreenOn, preferences.lockPortrait) {
        val oldBrightness = activity.window.attributes.screenBrightness
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = preferences.brightness.takeIf { it >= 0f } ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (preferences.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.requestedOrientation = if (preferences.lockPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = oldBrightness }
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private fun overallProgress(state: ReaderUiState, chapterProgress: Float): Float {
    val total = state.chapters.sumOf { it.charCount.toLong() }.coerceAtLeast(1L)
    val before = state.chapters.take(state.chapterIndex).sumOf { it.charCount.toLong() }
    val currentLength = state.chapters.getOrNull(state.chapterIndex)?.charCount ?: 0
    return ((before + currentLength * chapterProgress) / total.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun currentTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun BookmarksDialog(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onJump: (Bookmark) -> Unit,
    onDelete: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("书签") },
        text = {
            if (bookmarks.isEmpty()) Text("还没有书签") else LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    ListItem(
                        headlineContent = { Text(bookmark.excerpt, maxLines = 2) },
                        trailingContent = { TextButton(onClick = { onDelete(bookmark.id) }) { Text("删除") } },
                        modifier = Modifier.clickable { onJump(bookmark) },
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
