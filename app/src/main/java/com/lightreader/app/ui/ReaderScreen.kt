package com.lightreader.app.ui

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.FontFamilyOption
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.model.PageAnimation
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(preferences: ReaderPreferences, viewModel: MainViewModel) {
    val state by viewModel.readerState.collectAsState()
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val colors = readerColors(preferences)
    ApplyWindowPreferences(preferences)
    ApplyReaderStatusBar(state.toolbarVisible)

    LaunchedEffect(state.toolbarVisible, showToc, showBookmarks, showSettings) {
        if (state.toolbarVisible && !showToc && !showBookmarks && !showSettings) {
            delay(3_000)
            viewModel.hideToolbar()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.first)) {
        val density = LocalDensity.current
        val widthPx = with(density) { (maxWidth - preferences.horizontalPaddingDp.dp * 2).roundToPx() }
        val heightPx = with(density) { (maxHeight - preferences.verticalPaddingDp.dp * 2 - 36.dp).roundToPx() }
        LaunchedEffect(widthPx, heightPx, density.density, density.fontScale) {
            viewModel.setViewport(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), density.density * density.fontScale)
        }

        when {
            state.loading && state.pages.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.second)
            state.error != null -> Text(state.error.orEmpty(), Modifier.align(Alignment.Center), color = colors.second)
            state.pages.isNotEmpty() -> {
                androidx.compose.runtime.key(state.chapters.getOrNull(state.chapterIndex)?.id, state.pages.size) {
                    val pagerState = rememberPagerState(initialPage = state.pageIndex.coerceIn(state.pages.indices)) { state.pages.size }
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect(viewModel::pageSelected)
                    }
                    LaunchedEffect(state.pageIndex) {
                        if (state.pageIndex in state.pages.indices && state.pageIndex != pagerState.currentPage) {
                            pagerState.scrollToPage(state.pageIndex)
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = preferences.pageAnimation != PageAnimation.NONE,
                    ) { pageIndex ->
                        val page = state.pages[pageIndex]
                        Text(
                            text = styledPage(page.text, preferences),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = preferences.horizontalPaddingDp.dp,
                                    vertical = preferences.verticalPaddingDp.dp,
                                )
                                .graphicsLayer {
                                    if (preferences.pageAnimation == PageAnimation.COVER) {
                                        val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                        alpha = (1f - kotlin.math.abs(pageOffset) * .18f).coerceIn(.75f, 1f)
                                        scaleX = (1f - kotlin.math.abs(pageOffset) * .025f).coerceAtLeast(.97f)
                                        scaleY = scaleX
                                        shadowElevation = if (kotlin.math.abs(pageOffset) < 1f) 12f else 0f
                                    }
                                }
                                .pointerInput(pageIndex) {
                                    detectTapGestures { point ->
                                        when {
                                            point.x < size.width / 3f -> viewModel.previousPage()
                                            point.x > size.width * 2f / 3f -> viewModel.nextPage()
                                            else -> viewModel.toggleToolbar()
                                        }
                                    }
                                },
                            color = colors.second,
                            fontSize = preferences.fontSizeSp.sp,
                            fontWeight = if (preferences.fontWeight >= 500) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = when (preferences.fontFamily) {
                                FontFamilyOption.SANS -> FontFamily.SansSerif
                                FontFamilyOption.SERIF -> FontFamily.Serif
                                FontFamilyOption.MONOSPACE -> FontFamily.Monospace
                            },
                            lineHeight = (preferences.fontSizeSp * preferences.lineSpacingMultiplier).sp,
                            textAlign = if (preferences.justified) TextAlign.Justify else TextAlign.Start,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(state.toolbarVisible, Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(colors.first.copy(alpha = .96f)).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::goBack) { Icon(Icons.Outlined.ArrowBack, "返回", tint = colors.second) }
                Text(
                    state.chapters.getOrNull(state.chapterIndex)?.title ?: state.book?.title.orEmpty(),
                    Modifier.weight(1f),
                    maxLines = 1,
                    color = colors.second,
                )
                IconButton(onClick = { showToc = true }, enabled = state.chapters.isNotEmpty() && !state.loading) {
                    Icon(Icons.Outlined.FormatListBulleted, "目录", tint = colors.second)
                }
                IconButton(
                    onClick = { state.book?.let { viewModel.navigate(AppScreen.Search(it.id)) } },
                    enabled = state.book != null && !state.loading,
                ) { Icon(Icons.Outlined.Search, "搜索", tint = colors.second) }
                IconButton(onClick = viewModel::addBookmark, enabled = state.pages.isNotEmpty() && !state.loading) {
                    Icon(Icons.Outlined.BookmarkAdd, "添加书签", tint = colors.second)
                }
            }
        }

        AnimatedVisibility(state.toolbarVisible, Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(colors.first.copy(alpha = .96f)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showBookmarks = true }, enabled = state.book != null && !state.loading) {
                    Icon(Icons.Outlined.Bookmarks, "书签", tint = colors.second)
                }
                if (preferences.showStatus) {
                    Text(
                        "${state.chapterIndex + 1}/${state.chapters.size} · ${state.pageIndex + 1}/${state.pages.size}",
                        color = colors.second,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.TextFields, "阅读设置", tint = colors.second) }
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
                            modifier = Modifier.clickable {
                                viewModel.selectChapter(chapter.orderIndex)
                                showToc = false
                            },
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
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettingsPanel(preferences, viewModel::savePreferences, onClose = { showSettings = false })
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun ApplyReaderStatusBar(visible: Boolean) {
    val activity = LocalActivity.current ?: return
    val controller = remember(activity) { WindowCompat.getInsetsController(activity.window, activity.window.decorView) }
    DisposableEffect(controller) {
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = previousBehavior
        }
    }
    LaunchedEffect(controller, visible) {
        if (visible) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
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

private fun readerColors(preferences: ReaderPreferences): Pair<Color, Color> = when (preferences.theme) {
    ReaderTheme.DAY -> Color(0xFFFAF9F6) to Color(0xFF242421)
    ReaderTheme.SEPIA -> Color(0xFFF3E8CE) to Color(0xFF3C3125)
    ReaderTheme.NIGHT -> Color(0xFF121412) to Color(0xFFD4D7D1)
    ReaderTheme.CUSTOM -> Color(preferences.customBackground) to Color(preferences.customForeground)
}

private fun styledPage(text: String, preferences: ReaderPreferences): AnnotatedString {
    if (!preferences.firstLineIndent) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    var start = 0
    text.forEachIndexed { index, character ->
        if (character == '\n') {
            if (index > start) builder.addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = (preferences.fontSizeSp * 2).sp)), start, index)
            start = index + 1
        }
    }
    if (start < text.length) builder.addStyle(ParagraphStyle(textIndent = TextIndent(firstLine = (preferences.fontSizeSp * 2).sp)), start, text.length)
    return builder.toAnnotatedString()
}

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
