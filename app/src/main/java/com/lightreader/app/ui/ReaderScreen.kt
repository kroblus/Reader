package com.lightreader.app.ui

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lightreader.app.R
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.PageTurnMode
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.reader.palette
import java.text.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(preferences: ReaderPreferences, viewModel: MainViewModel) {
    val state by viewModel.readerState.collectAsState()
    var tocAscending by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(currentTime()) }
    val palette = preferences.palette()
    val background = Color(palette.background)
    val foreground = Color(palette.foreground)
    val overlayVisible = state.overlay != ReaderOverlay.NONE

    ApplyWindowPreferences(preferences)
    ApplyReaderSystemBars(preferences, state.toolbarVisible)
    DisposableEffect(viewModel) { onDispose(viewModel::onReaderStopped) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = currentTime()
            delay(30_000)
        }
    }
    LaunchedEffect(state.toolbarVisible, state.overlay, state.settingsVisible) {
        if (state.toolbarVisible && !overlayVisible && !state.settingsVisible) {
            delay(5_000)
            viewModel.hideToolbar()
        }
    }
    LaunchedEffect(
        state.autoReading,
        state.pageIndex,
        state.chapterIndex,
        state.toolbarVisible,
        state.overlay,
        state.settingsVisible,
        preferences.autoReadIntervalSeconds,
    ) {
        if (state.autoReading && !state.toolbarVisible && !overlayVisible && !state.settingsVisible) {
            delay(preferences.autoReadIntervalSeconds.coerceIn(3, 60) * 1_000L)
            viewModel.nextPage()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background)
            .readerPageTapNavigation(
                toolbarVisible = state.toolbarVisible,
                settingsVisible = state.settingsVisible,
                fullScreenTapNext = preferences.fullScreenTapNext,
                onPrevious = viewModel::previousPage,
                onNext = viewModel::nextPage,
                onCenter = viewModel::toggleToolbar,
            ),
    ) {
        val density = LocalDensity.current
        val viewportHeight = maxHeight
        val settingsDockHeight = (viewportHeight * 4f / 9f - 124.dp).coerceAtLeast(viewportHeight * .24f)
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
            state.error != null -> state.error?.let { error ->
                Text(error.asString(), Modifier.align(Alignment.Center), color = foreground)
            }
            state.pages.isNotEmpty() -> {
                val includeAdjacentPreviews = preferences.pageTurnMode != PageTurnMode.NONE && preferences.pageTurnMode != PageTurnMode.VERTICAL
                val displayedPages = remember(state.pages, state.previousPreview, state.nextPreview, includeAdjacentPreviews) {
                    readerPagerPages(state, includeAdjacentPreviews)
                }
                val previousOffset = if (displayedPages.firstOrNull()?.slot == ReaderPagerSlot.PREVIOUS_PREVIEW) 1 else 0
                val targetPagerPage = (state.pageIndex + previousOffset).coerceIn(displayedPages.indices)
                key(
                    state.chapters.getOrNull(state.chapterIndex)?.id,
                    state.layoutVersion,
                    state.pages.size,
                    state.previousPreview?.chapterIndex,
                    state.nextPreview?.chapterIndex,
                    state.boundaryTurnRequest?.nonce,
                    previousOffset,
                ) {
                    val pagerState = rememberPagerState(targetPagerPage) { displayedPages.size }
                    LaunchedEffect(pagerState, displayedPages, state.boundaryTurnRequest) {
                        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }.distinctUntilChanged().collect { (pagerPage, scrolling) ->
                            if (scrolling) return@collect
                            when (val displayed = displayedPages.getOrNull(pagerPage)) {
                                null -> Unit
                                else -> when (displayed.slot) {
                                    ReaderPagerSlot.PREVIOUS_PREVIEW -> {
                                        val request = state.boundaryTurnRequest
                                        viewModel.commitAdjacentPreview(
                                            next = false,
                                            nonce = request?.takeIf {
                                                !it.next && it.targetChapterIndex == displayed.page.chapterIndex
                                            }?.nonce,
                                            sourceChapterIndex = state.chapterIndex,
                                            targetChapterIndex = displayed.page.chapterIndex,
                                        )
                                    }
                                    ReaderPagerSlot.NEXT_PREVIEW -> {
                                        val request = state.boundaryTurnRequest
                                        viewModel.commitAdjacentPreview(
                                            next = true,
                                            nonce = request?.takeIf {
                                                it.next && it.targetChapterIndex == displayed.page.chapterIndex
                                            }?.nonce,
                                            sourceChapterIndex = state.chapterIndex,
                                            targetChapterIndex = displayed.page.chapterIndex,
                                        )
                                    }
                                    ReaderPagerSlot.CURRENT -> displayed.currentPageIndex?.let(viewModel::pageSelected)
                                }
                            }
                        }
                    }
                    LaunchedEffect(state.boundaryTurnRequest, displayedPages) {
                        val request = state.boundaryTurnRequest ?: return@LaunchedEffect
                        val target = displayedPages.indexOfFirst {
                            it.slot == (if (request.next) ReaderPagerSlot.NEXT_PREVIEW else ReaderPagerSlot.PREVIOUS_PREVIEW) &&
                                it.page.chapterIndex == request.targetChapterIndex
                        }
                        if (target >= 0) {
                            try {
                                pagerState.animateScrollToPage(target)
                            } finally {
                                if (pagerState.currentPage == target) {
                                    viewModel.commitAdjacentPreview(request.next, request.nonce)
                                }
                            }
                        } else {
                            viewModel.consumeBoundaryTurnRequest(request.nonce)
                        }
                    }
                    LaunchedEffect(
                        state.boundaryTurnPhase,
                        state.chapterIndex,
                        state.pageIndex,
                        targetPagerPage,
                        displayedPages.size,
                    ) {
                        if (state.boundaryTurnPhase == BoundaryTurnPhase.SETTLING_CHAPTER &&
                            targetPagerPage in 0 until displayedPages.size
                        ) {
                            if (pagerState.currentPage != targetPagerPage) {
                                pagerState.scrollToPage(targetPagerPage)
                            }
                            viewModel.boundaryChapterSettled(state.chapterIndex)
                        }
                    }
                    LaunchedEffect(targetPagerPage, preferences.pageTurnMode, displayedPages.size) {
                        if (state.boundaryTurnRequest == null &&
                            state.boundaryTurnPhase != BoundaryTurnPhase.SETTLING_CHAPTER &&
                            targetPagerPage in 0 until displayedPages.size &&
                            targetPagerPage != pagerState.currentPage
                        ) {
                            if (preferences.pageTurnMode != PageTurnMode.NONE && abs(targetPagerPage - pagerState.currentPage) == 1) {
                                pagerState.animateScrollToPage(targetPagerPage)
                            } else {
                                pagerState.scrollToPage(targetPagerPage)
                            }
                        }
                    }
                    val pageContent: @Composable (Int) -> Unit = { pagerPageIndex ->
                        val displayed = displayedPages[pagerPageIndex]
                        val page = displayed.page
                        var dragDistance by remember(pagerPageIndex, page.chapterIndex, page.pageIndex) { mutableFloatStateOf(0f) }
                        val manualSwipe = if (preferences.pageTurnMode == PageTurnMode.NONE) {
                            Modifier.pointerInput(pagerPageIndex) {
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
                        val pageEffect = Modifier.graphicsLayer {
                            val pageOffset = (pagerState.currentPage - pagerPageIndex) + pagerState.currentPageOffsetFraction
                            when (preferences.pageTurnMode) {
                                PageTurnMode.SIMULATION -> {
                                    rotationY = (-pageOffset * 22f).coerceIn(-22f, 22f)
                                    transformOrigin = TransformOrigin(if (pageOffset < 0f) 0f else 1f, .5f)
                                    cameraDistance = 14f * density.density
                                    shadowElevation = if (abs(pageOffset) < 1f) 18f else 0f
                                    alpha = (1f - abs(pageOffset) * .12f).coerceIn(.82f, 1f)
                                }
                                PageTurnMode.SLIDE -> alpha = (1f - abs(pageOffset) * .08f).coerceIn(.9f, 1f)
                                else -> Unit
                            }
                        }
                        val boundarySwipe = if (preferences.pageTurnMode == PageTurnMode.VERTICAL) {
                            Modifier.pointerInput(
                                pagerPageIndex,
                                state.chapterIndex,
                                preferences.pageTurnMode,
                            ) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    var lastPosition = down.position
                                    var pointerPressed = true
                                    while (pointerPressed) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        lastPosition = change.position
                                        pointerPressed = change.pressed
                                    }
                                    val delta = lastPosition - down.position
                                    val threshold = 72.dp.toPx()
                                    if (preferences.pageTurnMode == PageTurnMode.VERTICAL) {
                                        when {
                                            displayed.currentPageIndex == state.pages.lastIndex && delta.y < -threshold -> viewModel.nextPage()
                                            displayed.currentPageIndex == 0 && delta.y > threshold -> viewModel.previousPage()
                                        }
                                    } else {
                                        when {
                                            displayed.currentPageIndex == state.pages.lastIndex && delta.x < -threshold -> viewModel.nextPage()
                                            displayed.currentPageIndex == 0 && delta.x > threshold -> viewModel.previousPage()
                                        }
                                    }
                                }
                            }
                        } else Modifier
                        ReaderPageCanvas(
                            page = page,
                            bookTitle = state.book?.title.orEmpty(),
                            pageCount = displayed.pageCount,
                            layoutPreferences = state.layoutPreferences ?: preferences,
                            displayPreferences = preferences,
                            overallProgress = overallProgress(state, page),
                            currentTime = currentTime,
                            safeTopPx = safeTopPx,
                            safeBottomPx = safeBottomPx,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pageEffect)
                                .then(boundarySwipe)
                                .then(manualSwipe)
                        )
                    }
                    if (preferences.pageTurnMode == PageTurnMode.VERTICAL) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = true,
                        ) { pageContent(it) }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = preferences.pageTurnMode != PageTurnMode.NONE,
                        ) { pageContent(it) }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.toolbarVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(animationSpec = tween(140)) { -it } + fadeIn(tween(120)),
            exit = slideOutVertically(animationSpec = tween(120)) { -it } + fadeOut(tween(100)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(palette.overlay).copy(alpha = 1f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::goBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), tint = foreground)
                }
                Text(
                    state.chapters.getOrNull(state.chapterIndex)?.title ?: state.book?.title.orEmpty(),
                    Modifier.weight(1f),
                    maxLines = 1,
                    color = foreground,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                )
                IconButton(
                    onClick = { state.book?.let { viewModel.navigate(AppScreen.Search(it.id)) } },
                    enabled = state.book != null && !state.loading,
                    modifier = Modifier.size(40.dp),
                ) { Icon(Icons.Outlined.Search, stringResource(R.string.action_search), tint = foreground) }
                val currentPage = state.pages.getOrNull(state.pageIndex)
                val isBookmarked = state.currentPageBookmarked()
                val bookmarkStateDescription = stringResource(
                    if (isBookmarked) R.string.reader_bookmarked_state else R.string.reader_unbookmarked_state,
                )
                val bookmarkContentDescription = stringResource(
                    if (isBookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add,
                )
                IconButton(
                    onClick = viewModel::toggleCurrentPageBookmark,
                    enabled = currentPage != null && !state.loading,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            stateDescription = bookmarkStateDescription
                        },
                ) {
                    Icon(
                        if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkAdd,
                        bookmarkContentDescription,
                        tint = foreground,
                    )
                }
                val book = state.book
                if (book?.format == BookFormat.WEB && !book.sourceUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = viewModel::refreshCurrentWebBook,
                        enabled = !state.loading,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.reader_refresh), tint = foreground)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentHeight()) {
            AnimatedVisibility(
                visible = state.toolbarVisible && state.settingsVisible && !overlayVisible,
                enter = slideInVertically(animationSpec = tween(150)) { it / 2 } + fadeIn(tween(120)),
                exit = slideOutVertically(animationSpec = tween(130)) { it / 2 } + fadeOut(tween(100)),
            ) {
                ReaderSettingsDock(
                    preferences = preferences,
                    settingsHeight = settingsDockHeight,
                    autoReading = state.autoReading,
                    onChange = viewModel::savePreferences,
                    onToggleAutoReading = viewModel::toggleAutoReading,
                    onOpenMoreSettings = viewModel::openReaderSettingsDetail,
                )
            }

            AnimatedVisibility(
                visible = state.toolbarVisible && !overlayVisible,
                enter = slideInVertically(animationSpec = tween(140)) { it } + fadeIn(tween(120)),
                exit = slideOutVertically(animationSpec = tween(120)) { it } + fadeOut(tween(100)),
            ) {
                ReaderBottomControls(
                    state = state,
                    preferences = preferences,
                    viewModel = viewModel,
                    attachedToSettings = state.settingsVisible,
                    onShowToc = viewModel::showTableOfContents,
                    onShowBookmarks = viewModel::showBookmarksOverlay,
                )
            }
        }

        AnimatedVisibility(
            visible = state.autoReading && !state.toolbarVisible && !overlayVisible && !state.settingsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 18.dp),
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
        ) {
            Surface(
                color = Color(palette.overlay).copy(alpha = .96f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 2.dp,
            ) {
                TextButton(onClick = viewModel::toggleAutoReading) {
                    Icon(Icons.Outlined.Pause, stringResource(R.string.reader_pause_auto_reading), tint = foreground, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reader_pause_auto), color = foreground, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        ChapterListOverlay(
            visible = state.overlay == ReaderOverlay.TOC,
            state = state,
            preferences = preferences,
            ascending = tocAscending,
            onToggleSort = { tocAscending = !tocAscending },
            onDismiss = viewModel::hideReaderOverlay,
            onSelectChapter = { chapter ->
                viewModel.selectChapter(chapter.orderIndex)
                viewModel.hideReaderOverlay()
            },
        )
        BookmarksOverlay(
            visible = state.overlay == ReaderOverlay.BOOKMARKS,
            state = state,
            preferences = preferences,
            onDismiss = viewModel::hideReaderOverlay,
            onJump = { viewModel.jumpToBookmark(it); viewModel.hideReaderOverlay() },
            onDelete = viewModel::deleteBookmark,
        )
    }
}

@Composable
private fun ReaderSettingsDock(
    preferences: ReaderPreferences,
    settingsHeight: Dp,
    autoReading: Boolean,
    onChange: (ReaderPreferences) -> Unit,
    onToggleAutoReading: () -> Unit,
    onOpenMoreSettings: () -> Unit,
) {
    val palette = preferences.palette()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(settingsHeight)
            .testTag("reader_settings_dock"),
        color = Color(palette.overlay).copy(alpha = .99f),
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.fillMaxWidth().wrapContentHeight()) {
            ReaderSettingsPanel(
                value = preferences,
                onChange = onChange,
                autoReading = autoReading,
                onToggleAutoReading = onToggleAutoReading,
                onOpenMoreSettings = onOpenMoreSettings,
                bottomPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun ReaderBottomControls(
    state: ReaderUiState,
    preferences: ReaderPreferences,
    viewModel: MainViewModel,
    attachedToSettings: Boolean,
    onShowToc: () -> Unit,
    onShowBookmarks: () -> Unit,
) {
    val palette = preferences.palette()
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)
    val current = state.pages.getOrNull(state.pageIndex)?.let { overallProgress(state, it) } ?: 0f
    var sliderValue by remember(state.chapterIndex, state.pageIndex) { mutableFloatStateOf(current) }
    val progressLabel = progressPreview(state, sliderValue)
    val progressPercent = (sliderValue * 100).coerceIn(0f, 100f).toInt()
    val progressStateDescription = stringResource(R.string.reader_progress_state, progressPercent, progressLabel)
    val isBookmarked = state.currentPageBookmarked()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(palette.overlay).copy(alpha = 1f),
        shape = if (attachedToSettings) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 3.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::previousChapter,
                    enabled = state.chapterIndex > 0 && !state.loading,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.reader_prev_chapter),
                        color = if (state.chapterIndex > 0) foreground else secondary.copy(alpha = .45f),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { viewModel.jumpToProgress(sliderValue) },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .semantics {
                            stateDescription = progressStateDescription
                        },
                    enabled = state.pages.isNotEmpty() && !state.loading,
                    colors = SliderDefaults.colors(
                        thumbColor = secondary,
                        activeTrackColor = secondary,
                        inactiveTrackColor = secondary.copy(alpha = .2f),
                    ),
                )
                TextButton(
                    onClick = viewModel::nextChapter,
                    enabled = state.chapterIndex < state.chapters.lastIndex && !state.loading,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.reader_next_chapter),
                        color = if (state.chapterIndex < state.chapters.lastIndex) foreground else secondary.copy(alpha = .45f),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
                    )
                }
            }
            Text(
                stringResource(R.string.reader_progress, progressPercent, progressLabel),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("reader_bottom_progress_text"),
                color = secondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                ),
            )
            Spacer(Modifier.height(5.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderAction(Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(R.string.reader_toc), foreground, onShowToc)
                ReaderAction(
                    Icons.Outlined.DarkMode,
                    stringResource(R.string.reader_night),
                    foreground,
                    viewModel::toggleNightMode,
                    selected = preferences.theme == ReaderTheme.NIGHT,
                )
                ReaderAction(
                    Icons.Outlined.TextFields,
                    stringResource(R.string.reader_settings),
                    foreground,
                    viewModel::toggleSettings,
                    stringResource(R.string.reader_settings_content_description),
                    selected = attachedToSettings,
                )
                ReaderAction(
                    if (state.autoReading) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (state.autoReading) stringResource(R.string.reader_pause) else stringResource(R.string.reader_auto),
                    foreground,
                    viewModel::toggleAutoReading,
                    selected = state.autoReading,
                )
                ReaderAction(
                    if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.Bookmarks,
                    stringResource(R.string.reader_bookmarks),
                    foreground,
                    onShowBookmarks,
                    selected = isBookmarked,
                )
            }
        }
    }
}

@Composable
private fun ReaderAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    contentDescription: String = label,
    selected: Boolean = false,
) {
    val stateText = stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)
    val itemColor = if (selected) tint else tint.copy(alpha = .86f)
    Column(
        modifier = Modifier.width(54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) tint.copy(alpha = .18f) else Color.Transparent)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = stateText
                },
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = itemColor)
        }
        Text(
            label,
            color = itemColor,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                lineHeight = 13.sp,
            ),
        )
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

private enum class ReaderPagerSlot { PREVIOUS_PREVIEW, CURRENT, NEXT_PREVIEW }

private data class DisplayedReaderPage(
    val page: ReaderPage,
    val pageCount: Int,
    val slot: ReaderPagerSlot,
    val currentPageIndex: Int?,
)

private fun readerPagerPages(
    state: ReaderUiState,
    includeAdjacentPreviews: Boolean,
): List<DisplayedReaderPage> = buildList {
    if (includeAdjacentPreviews) {
        state.previousPreview?.let { preview ->
            add(
                DisplayedReaderPage(
                    page = preview.page,
                    pageCount = preview.pageCount,
                    slot = ReaderPagerSlot.PREVIOUS_PREVIEW,
                    currentPageIndex = null,
                ),
            )
        }
    }
    state.pages.forEach { page ->
        add(
            DisplayedReaderPage(
                page = page,
                pageCount = state.pages.size,
                slot = ReaderPagerSlot.CURRENT,
                currentPageIndex = page.pageIndex,
            ),
        )
    }
    if (includeAdjacentPreviews) {
        state.nextPreview?.let { preview ->
            add(
                DisplayedReaderPage(
                    page = preview.page,
                    pageCount = preview.pageCount,
                    slot = ReaderPagerSlot.NEXT_PREVIEW,
                    currentPageIndex = null,
                ),
            )
        }
    }
}

private fun overallProgress(state: ReaderUiState, page: ReaderPage): Float {
    val total = state.chapters.sumOf { it.charCount.toLong() }.coerceAtLeast(1L)
    val chapterIndex = page.chapterIndex.coerceIn(0, (state.chapters.size - 1).coerceAtLeast(0))
    val before = state.chapters.take(chapterIndex).sumOf { it.charCount.toLong() }
    val currentLength = state.chapters.getOrNull(chapterIndex)?.charCount ?: 0
    return ((before + currentLength * page.progressInChapter) / total.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun ReaderUiState.currentPageBookmarked(): Boolean {
    val page = pages.getOrNull(pageIndex) ?: return false
    val chapter = chapters.getOrNull(chapterIndex) ?: return false
    return bookmarks.any {
        it.chapterId == chapter.id &&
            it.charOffset >= page.startOffset &&
            it.charOffset < page.endOffset.coerceAtLeast(page.startOffset + 1)
    }
}

@Composable
private fun progressPreview(state: ReaderUiState, progress: Float): String {
    if (state.chapters.isEmpty()) return stringResource(R.string.reader_no_chapters)
    val total = state.chapters.sumOf { it.charCount.toLong() }.coerceAtLeast(1L)
    var target = (progress.coerceIn(0f, 1f) * total).toLong()
    val index = state.chapters.indexOfFirst { chapter ->
        if (target < chapter.charCount) true else {
            target -= chapter.charCount
            false
        }
    }.takeIf { it >= 0 } ?: state.chapters.lastIndex
    val chapter = state.chapters[index]
    val chapterPercent = if (chapter.charCount > 0) {
        ((target.toFloat() / chapter.charCount) * 100).toInt().coerceIn(0, 100)
    } else {
        0
    }
    return stringResource(R.string.reader_progress_chapter, index + 1, chapterPercent)
}

private fun currentTime(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

private fun Modifier.readerPageTapNavigation(
    toolbarVisible: Boolean,
    settingsVisible: Boolean,
    fullScreenTapNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCenter: () -> Unit,
): Modifier = pointerInput(toolbarVisible, settingsVisible, fullScreenTapNext, onPrevious, onNext, onCenter) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = down.position
        var maxDistance = 0f
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - start
            maxDistance = max(maxDistance, max(abs(delta.x), abs(delta.y)))
            pressed = change.pressed
        }
        if (maxDistance <= 18.dp.toPx()) {
            val xFraction = if (size.width > 0) start.x / size.width else .5f
            val yFraction = if (size.height > 0) start.y / size.height else .5f
            when (readerTapAction(xFraction, yFraction, toolbarVisible, settingsVisible, fullScreenTapNext)) {
                ReaderTapAction.PREVIOUS -> onPrevious()
                ReaderTapAction.NEXT -> onNext()
                ReaderTapAction.MENU -> onCenter()
                ReaderTapAction.NONE -> Unit
            }
        }
    }
}

internal enum class ReaderTapAction { PREVIOUS, NEXT, MENU, NONE }

internal fun readerTapAction(
    xFraction: Float,
    yFraction: Float,
    toolbarVisible: Boolean,
    settingsVisible: Boolean,
    fullScreenTapNext: Boolean,
): ReaderTapAction {
    val x = xFraction.coerceIn(0f, 1f)
    val y = yFraction.coerceIn(0f, 1f)
    val menuStart = if (fullScreenTapNext) .42f else .3f
    val menuEnd = if (fullScreenTapNext) .58f else .7f
    if (toolbarVisible) {
        if (settingsVisible) {
            return if (y in 0.1f..0.56f) ReaderTapAction.MENU else ReaderTapAction.NONE
        }
        val menuBottom = .78f
        return if (x in menuStart..menuEnd && y in 0.14f..menuBottom) ReaderTapAction.MENU else ReaderTapAction.NONE
    }
    return when {
        x in menuStart..menuEnd -> ReaderTapAction.MENU
        fullScreenTapNext -> ReaderTapAction.NEXT
        x < menuStart -> ReaderTapAction.PREVIOUS
        else -> ReaderTapAction.NEXT
    }
}

@Composable
private fun ChapterListOverlay(
    visible: Boolean,
    state: ReaderUiState,
    preferences: ReaderPreferences,
    ascending: Boolean,
    onToggleSort: () -> Unit,
    onDismiss: () -> Unit,
    onSelectChapter: (Chapter) -> Unit,
) {
    val chapters = remember(state.chapters, ascending) {
        if (ascending) state.chapters else state.chapters.asReversed()
    }
    val currentIndex = chapters.indexOfFirst { it.orderIndex == state.chapterIndex }.coerceAtLeast(0)
    val centeredFirstVisible = (currentIndex - 7).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = centeredFirstVisible)
    LaunchedEffect(visible, currentIndex, chapters.size) {
        if (visible && chapters.isNotEmpty()) {
            listState.scrollToItem(centeredFirstVisible)
        }
    }
    val palette = preferences.palette()
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)
    val selectedBackground = Color(palette.overlay).copy(alpha = .72f)
    val readPercent = ((state.pages.getOrNull(state.pageIndex)?.progressInChapter ?: 0f) * 100)
        .toInt()
        .coerceIn(0, 100)

    ReaderListOverlay(
        visible = visible,
        title = stringResource(R.string.reader_toc),
        preferences = preferences,
        heightFraction = if (chapters.size <= 6) .36f else .92f,
        onDismiss = onDismiss,
        action = {
            TextButton(
                onClick = onToggleSort,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    stringResource(if (ascending) R.string.reader_toc_ascending else R.string.reader_toc_descending),
                    color = foreground,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(chapters, key = { it.id }) { chapter ->
                val selected = chapter.orderIndex == state.chapterIndex
                ChapterOverlayRow(
                    chapter = chapter,
                    selected = selected,
                    foreground = foreground,
                    secondary = secondary,
                    selectedBackground = selectedBackground,
                    readPercentText = stringResource(R.string.reader_read_percent, readPercent),
                    onClick = { onSelectChapter(chapter) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 58.dp, end = 24.dp),
                    color = secondary.copy(alpha = .14f),
                )
            }
        }
    }
}

@Composable
private fun ChapterOverlayRow(
    chapter: Chapter,
    selected: Boolean,
    readPercentText: String,
    foreground: Color,
    secondary: Color,
    selectedBackground: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) selectedBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(6.dp)
                .clip(CircleShape)
                .background(if (selected) foreground else Color.Transparent),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            chapter.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) foreground else secondary.copy(alpha = .92f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
        )
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Text(
                readPercentText,
                color = foreground.copy(alpha = .84f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
        }
    }
}

@Composable
private fun BookmarksOverlay(
    visible: Boolean,
    state: ReaderUiState,
    preferences: ReaderPreferences,
    onDismiss: () -> Unit,
    onJump: (Bookmark) -> Unit,
    onDelete: (String) -> Unit,
) {
    val palette = preferences.palette()
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)

    ReaderListOverlay(
        visible = visible,
        title = stringResource(R.string.reader_bookmarks),
        preferences = preferences,
        heightFraction = if (state.bookmarks.size <= 2) .28f else if (state.bookmarks.size <= 4) .42f else .92f,
        onDismiss = onDismiss,
    ) {
        if (state.bookmarks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.reader_no_bookmarks),
                    color = foreground,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.reader_no_bookmarks_hint),
                    color = secondary.copy(alpha = .82f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.bookmarks, key = { it.id }) { bookmark ->
                    val chapter = state.chapters.firstOrNull { it.id == bookmark.chapterId }
                    val chapterTitle = chapter?.title
                        ?: stringResource(R.string.reader_bookmark_position)
                    val chapterPercent = chapter?.takeIf { it.charCount > 0 }?.let {
                        ((bookmark.charOffset.toFloat() / it.charCount) * 100).toInt().coerceIn(0, 100)
                    } ?: 0
                    val createdAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(bookmark.createdAt))
                    BookmarkOverlayRow(
                        bookmark = bookmark,
                        chapterTitle = chapterTitle,
                        meta = stringResource(R.string.reader_bookmark_meta, chapterPercent, createdAt),
                        foreground = foreground,
                        secondary = secondary,
                        onJump = { onJump(bookmark) },
                        onDelete = { onDelete(bookmark.id) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 30.dp, end = 28.dp),
                        color = secondary.copy(alpha = .08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkOverlayRow(
    bookmark: Bookmark,
    chapterTitle: String,
    meta: String,
    foreground: Color,
    secondary: Color,
    onJump: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onJump).padding(start = 28.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                bookmark.excerpt,
                color = foreground.copy(alpha = .86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                chapterTitle,
                color = secondary.copy(alpha = .9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                meta,
                color = secondary.copy(alpha = .74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.reader_delete_bookmark),
                modifier = Modifier.size(20.dp),
                tint = secondary.copy(alpha = .82f),
            )
        }
    }
}

@Composable
private fun ReaderListOverlay(
    visible: Boolean,
    title: String,
    preferences: ReaderPreferences,
    heightFraction: Float,
    onDismiss: () -> Unit,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = preferences.palette()
    val foreground = Color(palette.foreground)
    val secondary = Color(palette.secondary)
    val panelBackground = Color(palette.overlay)
    val outsideInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val closeOverlayBackground = stringResource(R.string.reader_close_overlay_background)
    val closeOverlay = stringResource(R.string.reader_close_overlay)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = if (maxWidth * .86f < 360.dp) maxWidth * .86f else 360.dp
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(80)),
            exit = fadeOut(animationSpec = tween(70)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = ReaderUiTokens.overlayScrimAlpha))
                    .semantics { contentDescription = closeOverlayBackground }
                    .clickable(
                        interactionSource = outsideInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(animationSpec = tween(80)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(70)) { -it },
        ) {
            Surface(
                modifier = Modifier
                    .padding(start = 8.dp, top = 14.dp, bottom = 14.dp)
                    .width(panelWidth)
                    .fillMaxHeight(heightFraction.coerceIn(.28f, .92f))
                    .clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = {},
                    ),
                color = panelBackground,
                shape = RoundedCornerShape(ReaderUiTokens.dialogRadius),
                shadowElevation = 5.dp,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 18.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            color = foreground,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                lineHeight = 29.sp,
                            ),
                        )
                        action()
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Close, closeOverlay, tint = foreground)
                        }
                    }
                    HorizontalDivider(color = secondary.copy(alpha = .12f))
                    Box(Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
    }
}
