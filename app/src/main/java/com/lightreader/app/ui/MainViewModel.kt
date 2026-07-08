package com.lightreader.app.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookParagraph
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.model.SearchResult
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.core.settings.AiConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
    data object Library : AppScreen
    data class Reader(val bookId: String) : AppScreen
    data class ReaderSettingsDetail(val bookId: String) : AppScreen
    data class Search(val bookId: String) : AppScreen
    data object WebImport : AppScreen
    data object WebDomBridge : AppScreen
    data object ApiSettings : AppScreen
}

data class MainUiState(
    val books: List<Book> = emptyList(),
    val preferences: ReaderPreferences = ReaderPreferences(),
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val screen: AppScreen = AppScreen.Library,
    val navigation: NavigationState = NavigationState(),
    val busy: BusyState = BusyState(),
    val message: String? = null,
)

data class NavigationState(
    val current: AppScreen = AppScreen.Library,
    val backStack: List<AppScreen> = emptyList(),
)

data class BusyState(
    val active: Boolean = false,
    val message: String = "",
)

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val pages: List<ReaderPage> = emptyList(),
    val previousPreview: AdjacentChapterPreview? = null,
    val nextPreview: AdjacentChapterPreview? = null,
    val boundaryTurnRequest: BoundaryTurnRequest? = null,
    val boundaryTurnPhase: BoundaryTurnPhase = BoundaryTurnPhase.IDLE,
    val pageIndex: Int = 0,
    val toolbarVisible: Boolean = true,
    val settingsVisible: Boolean = false,
    val overlay: ReaderOverlay = ReaderOverlay.NONE,
    val autoReading: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val layoutVersion: Int = 0,
    val layoutPreferences: ReaderPreferences? = null,
)

data class AdjacentChapterPreview(
    val chapterIndex: Int,
    val page: ReaderPage,
    val pageCount: Int,
)

data class BoundaryTurnRequest(
    val next: Boolean,
    val nonce: Long,
    val sourceChapterIndex: Int,
    val targetChapterIndex: Int,
)

enum class BoundaryTurnPhase { IDLE, ANIMATING_PREVIEW, COMMITTING_CHAPTER, SETTLING_CHAPTER }
enum class ReaderOverlay { NONE, TOC, BOOKMARKS }

internal class PendingPageTurnQueue(private val maxSize: Int = 3) {
    var pendingDelta: Int = 0
        private set

    fun enqueue(next: Boolean) {
        val delta = if (next) 1 else -1
        pendingDelta = (pendingDelta + delta).coerceIn(-maxSize, maxSize)
    }

    fun poll(): Boolean? = when {
        pendingDelta > 0 -> {
            pendingDelta -= 1
            true
        }
        pendingDelta < 0 -> {
            pendingDelta += 1
            false
        }
        else -> null
    }

    fun clear() {
        pendingDelta = 0
    }
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
)

data class WebImportUiState(
    val url: String = "",
    val loading: Boolean = false,
    val preview: WebBookPreview? = null,
    val error: String? = null,
    val hasApiKey: Boolean = false,
)

data class ApiSettingsUiState(
    val keyDraft: String = "",
    val showKey: Boolean = false,
    val advancedExpanded: Boolean = false,
    val configuration: AiConfiguration = AiConfiguration(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ReaderApplication).container
    private val navigation = MutableStateFlow(NavigationState())
    private val busy = MutableStateFlow(BusyState())
    private val message = MutableStateFlow<String?>(null)
    private val contentState = combine(
        container.bookRepository.books,
        container.settingsRepository.preferences,
        container.downloadRepository.tasks,
    ) { books, preferences, tasks -> Triple(books, preferences, tasks) }
    private val chromeState = combine(
        navigation,
        busy,
        message,
    ) { currentNavigation, isBusy, currentMessage -> Triple(currentNavigation, isBusy, currentMessage) }
    val uiState: StateFlow<MainUiState> = combine(contentState, chromeState) { content, chrome ->
        MainUiState(
            books = content.first,
            preferences = content.second,
            tasks = content.third,
            screen = chrome.first.current,
            navigation = chrome.first,
            busy = chrome.second,
            message = chrome.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val readerState = MutableStateFlow(ReaderUiState())
    val searchState = MutableStateFlow(SearchUiState())
    val webState = MutableStateFlow(WebImportUiState(hasApiKey = container.keyStore.hasKey()))
    val apiSettingsState = MutableStateFlow(ApiSettingsUiState(configuration = container.aiConfigurationStore.get()))
    private val normalizer = BookTextNormalizer()
    private var viewport = ReaderViewport(0, 0, 1f, 1f)
    private var chapterText = ""
    private var chapterParagraphs: List<BookParagraph> = emptyList()
    private var loadedChapterId: Long? = null
    private var requestedOffset = 0
    private var currentLayoutFingerprint = 0
    private var boundaryTurnNonce = 0L
    private var boundaryCommitSourceChapterIndex: Int? = null
    private var drainingQueuedTurns = false
    private val pendingPageTurns = PendingPageTurnQueue()
    private var paginationJob: Job? = null
    private var prefetchJob: Job? = null
    private var bookmarkJob: Job? = null
    private var progressSaveJob: Job? = null
    private val chapterContentCache = object : LinkedHashMap<ChapterContentCacheKey, ChapterContent>(6, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChapterContentCacheKey, ChapterContent>): Boolean = size > 6
    }
    private val pageCache = object : LinkedHashMap<LayoutCacheKey, List<ReaderPage>>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutCacheKey, List<ReaderPage>>): Boolean = size > 8
    }

    init {
        viewModelScope.launch {
            container.settingsRepository.preferences.collect { preferences ->
                val fingerprint = preferences.toReaderStyle().layoutFingerprint()
                if (readerState.value.book != null && viewport.widthPx > 0 && fingerprint != currentLayoutFingerprint) {
                    requestedOffset = currentPageOffset()
                    repaginate(preferences)
                }
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            showBusy(R.string.busy_importing_book)
            runCatching { container.bookRepository.import(uri) }
                .onSuccess { message.value = text(R.string.message_imported_book, it.title) }
                .onFailure { message.value = text(R.string.message_import_failed) }
            hideBusy()
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            container.bookRepository.deleteBook(id)
            message.value = text(R.string.message_deleted)
        }
    }

    fun clearMessage() { message.value = null }

    fun navigate(target: AppScreen) {
        val state = navigation.value
        if (state.current == target) return
        navigation.value = state.copy(
            current = target,
            backStack = (state.backStack + state.current).takeLast(8),
        )
    }

    private fun replaceCurrent(target: AppScreen) {
        navigation.value = navigation.value.copy(current = target)
    }

    private fun popBackStack(): AppScreen {
        val state = navigation.value
        val previous = state.backStack.lastOrNull() ?: AppScreen.Library
        navigation.value = NavigationState(
            current = previous,
            backStack = state.backStack.dropLast(1),
        )
        return previous
    }

    fun goBack() {
        val current = navigation.value.current
        if (current is AppScreen.Reader) {
            val state = readerState.value
            if (state.overlay != ReaderOverlay.NONE || state.settingsVisible) {
                readerState.value = state.copy(
                    overlay = ReaderOverlay.NONE,
                    settingsVisible = false,
                    toolbarVisible = true,
                )
                return
            }
            saveCurrentProgress(immediate = true)
        }
        val next = popBackStack()
        if (current is AppScreen.Reader && next !is AppScreen.Reader) setAutoReading(false)
        if (current !is AppScreen.Reader && next is AppScreen.Reader) {
            readerState.value = readerState.value.copy(toolbarVisible = true)
        }
    }

    fun openReaderSettingsDetail() {
        val bookId = readerState.value.book?.id ?: return
        saveCurrentProgress(immediate = true)
        val state = readerState.value
        readerState.value = state.copy(
            settingsVisible = false,
            overlay = ReaderOverlay.NONE,
            toolbarVisible = if (state.autoReading) false else state.toolbarVisible,
        )
        navigate(AppScreen.ReaderSettingsDetail(bookId))
    }

    fun openBook(bookId: String) {
        navigate(AppScreen.Reader(bookId))
        paginationJob?.cancel()
        prefetchJob?.cancel()
        chapterText = ""
        chapterParagraphs = emptyList()
        loadedChapterId = null
        currentLayoutFingerprint = 0
        boundaryCommitSourceChapterIndex = null
        pendingPageTurns.clear()
        setAutoReading(false)
        viewModelScope.launch {
            readerState.value = ReaderUiState(loading = true)
            val book = container.bookRepository.book(bookId) ?: run {
                readerState.value = ReaderUiState(error = text(R.string.message_book_missing))
                return@launch
            }
            val chapters = container.bookRepository.chapters(bookId)
            if (chapters.isEmpty()) {
                readerState.value = ReaderUiState(book = book, error = text(R.string.message_book_has_no_chapters))
                return@launch
            }
            val progress = container.bookRepository.progress(bookId)
            val chapterIndex = progress?.let { saved -> chapters.indexOfFirst { it.id == saved.chapterId } }
                ?.takeIf { it >= 0 }
                ?: progress?.chapterIndex?.coerceIn(chapters.indices)
                ?: 0
            requestedOffset = progress?.charOffset ?: 0
            readerState.value = ReaderUiState(book = book, chapters = chapters, chapterIndex = chapterIndex, loading = true)
            observeBookmarks(bookId)
            loadChapter(chapterIndex)
        }
    }

    fun setViewport(
        width: Int,
        height: Int,
        density: Float,
        scaledDensity: Float,
        safeTopPx: Int,
        safeBottomPx: Int,
    ) {
        val next = ReaderViewport(width, height, density, scaledDensity, safeTopPx, safeBottomPx)
        if (next == viewport) return
        viewport = next
        if (loadedChapterId != null) {
            requestedOffset = currentPageOffset()
            repaginate(uiState.value.preferences)
        }
    }

    fun selectChapter(index: Int) {
        if (index !in readerState.value.chapters.indices) return
        if (index != readerState.value.chapterIndex) saveCurrentProgress(immediate = true)
        requestedOffset = 0
        loadChapter(index)
    }

    private fun loadChapter(index: Int) {
        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            val chapter = readerState.value.chapters.getOrNull(index) ?: return@launch
            val preferences = uiState.value.preferences
            val key = layoutCacheKey(chapter, preferences, viewport)
            val cachedPages = pageCache[key]
            val cachedContent = chapterContentCache[ChapterContentCacheKey(chapter.bookId, chapter.id)]
            if (cachedPages != null) {
                cachedContent?.let(::setLoadedChapterContent)
                loadedChapterId = chapter.id
                applyPages(index, chapter, cachedPages, preferences, fromCache = true)
                prefetchAdjacent(index, preferences)
                return@launch
            }

            readerState.value = readerState.value.copy(loading = true, error = null)
            val content = runCatching { loadChapterContent(chapter) }
                .getOrElse {
                    readerState.value = readerState.value.copy(loading = false, error = text(R.string.message_read_chapter_failed))
                    return@launch
                }
            setLoadedChapterContent(content)
            loadedChapterId = chapter.id
            paginateAndApply(index, chapter, content.paragraphs, preferences)
        }
    }

    private fun repaginate(preferences: ReaderPreferences) {
        if (viewport.widthPx <= 0 || viewport.heightPx <= 0) return
        val state = readerState.value
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            readerState.value = readerState.value.copy(loading = true)
            paginateAndApply(state.chapterIndex, chapter, chapterParagraphs, preferences)
        }
    }

    private suspend fun paginateAndApply(
        chapterIndex: Int,
        chapter: Chapter,
        paragraphs: List<BookParagraph>,
        preferences: ReaderPreferences,
    ) {
        if (viewport.widthPx <= 0 || viewport.heightPx <= 0) return
        val key = layoutCacheKey(chapter, preferences, viewport)
        val cached = pageCache[key]
        val pages = cached ?: withContext(Dispatchers.Default) {
            container.paginationEngine.paginate(chapterIndex, chapter.title, paragraphs, viewport, preferences.toReaderStyle()).pages
        }.also { pageCache[key] = it }
        applyPages(chapterIndex, chapter, pages, preferences, fromCache = cached != null)
        prefetchAdjacent(chapterIndex, preferences)
    }

    private fun prefetchAdjacent(chapterIndex: Int, preferences: ReaderPreferences) {
        val chapters = readerState.value.chapters
        val targetViewport = viewport
        prefetchJob = viewModelScope.launch {
            listOf(chapterIndex + 1, chapterIndex - 1, chapterIndex + 2).filter { it in chapters.indices }.distinct().forEach { index ->
                val chapter = chapters[index]
                val key = layoutCacheKey(chapter, preferences, targetViewport)
                if (pageCache[key] == null) {
                    val content = runCatching { loadChapterContent(chapter) }.getOrNull() ?: return@forEach
                    val pages = withContext(Dispatchers.Default) {
                        container.paginationEngine.paginate(index, chapter.title, content.paragraphs, targetViewport, preferences.toReaderStyle()).pages
                    }
                    pageCache[key] = pages
                }
                refreshAdjacentPreviews(preferences, targetViewport)
            }
        }
    }

    private suspend fun loadChapterContent(chapter: Chapter): ChapterContent {
        val key = ChapterContentCacheKey(chapter.bookId, chapter.id)
        chapterContentCache[key]?.let { return it }
        val rawText = container.bookRepository.readChapter(chapter)
        val paragraphs = withContext(Dispatchers.Default) { normalizer.normalize(rawText) }
        return ChapterContent(rawText, paragraphs).also { chapterContentCache[key] = it }
    }

    private fun setLoadedChapterContent(content: ChapterContent) {
        chapterText = content.rawText
        chapterParagraphs = content.paragraphs
    }

    private fun applyPages(
        chapterIndex: Int,
        chapter: Chapter,
        pages: List<ReaderPage>,
        preferences: ReaderPreferences,
        fromCache: Boolean,
    ) {
        val beforeApply = readerState.value
        if (beforeApply.chapters.getOrNull(chapterIndex)?.id != chapter.id) return
        val pageIndex = pageForOffset(pages, requestedOffset)
        val fingerprint = preferences.toReaderStyle().layoutFingerprint()
        val settlingAfterBoundary = beforeApply.boundaryTurnPhase == BoundaryTurnPhase.COMMITTING_CHAPTER ||
            boundaryCommitSourceChapterIndex != null
        currentLayoutFingerprint = fingerprint
        readerState.value = beforeApply.copy(
            chapterIndex = chapterIndex,
            pages = pages,
            previousPreview = null,
            nextPreview = null,
            boundaryTurnRequest = null,
            boundaryTurnPhase = if (settlingAfterBoundary) BoundaryTurnPhase.SETTLING_CHAPTER else BoundaryTurnPhase.IDLE,
            pageIndex = pageIndex,
            loading = false,
            error = null,
            layoutVersion = beforeApply.layoutVersion + if (fromCache) 0 else 1,
            layoutPreferences = preferences,
        )
        refreshAdjacentPreviews(preferences)
        requestedOffset = 0
        saveCurrentProgress()
        if (settlingAfterBoundary) {
            scheduleBoundarySettleFallback(chapterIndex)
        } else {
            drainQueuedPageTurns()
        }
    }

    private fun refreshAdjacentPreviews(
        preferences: ReaderPreferences,
        targetViewport: ReaderViewport = viewport,
    ) {
        if (targetViewport.widthPx <= 0 || targetViewport.heightPx <= 0) return
        val state = readerState.value
        val currentPreferences = state.layoutPreferences ?: uiState.value.preferences
        if (targetViewport != viewport || currentPreferences.toReaderStyle().layoutFingerprint() != preferences.toReaderStyle().layoutFingerprint()) return
        val previous = adjacentPreview(state, state.chapterIndex - 1, preferences, targetViewport, useLastPage = true)
        val next = adjacentPreview(state, state.chapterIndex + 1, preferences, targetViewport, useLastPage = false)
        if (state.previousPreview != previous || state.nextPreview != next) {
            readerState.value = state.copy(previousPreview = previous, nextPreview = next)
        }
    }

    private fun adjacentPreview(
        state: ReaderUiState,
        index: Int,
        preferences: ReaderPreferences,
        targetViewport: ReaderViewport,
        useLastPage: Boolean,
    ): AdjacentChapterPreview? {
        val chapter = state.chapters.getOrNull(index) ?: return null
        val pages = pageCache[layoutCacheKey(chapter, preferences, targetViewport)] ?: return null
        val page = if (useLastPage) pages.lastOrNull() else pages.firstOrNull()
        return page?.let { AdjacentChapterPreview(index, it, pages.size) }
    }

    private fun layoutCacheKey(
        chapter: Chapter,
        preferences: ReaderPreferences,
        targetViewport: ReaderViewport,
    ) = LayoutCacheKey(chapter.bookId, chapter.id, preferences.toReaderStyle().layoutFingerprint(), targetViewport)

    private fun pageForOffset(pages: List<ReaderPage>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        val exact = pages.indexOfFirst { offset >= it.startOffset && offset < it.endOffset }
        if (exact >= 0) return exact
        return pages.indexOfLast { it.startOffset <= offset }.coerceAtLeast(0)
    }

    fun pageSelected(index: Int) {
        val state = readerState.value
        if (index !in state.pages.indices || index == state.pageIndex) return
        readerState.value = state.copy(pageIndex = index)
        saveCurrentProgress()
    }

    fun consumeBoundaryTurnRequest(nonce: Long) {
        val state = readerState.value
        if (state.boundaryTurnRequest?.nonce == nonce) {
            readerState.value = state.copy(
                boundaryTurnRequest = null,
                boundaryTurnPhase = BoundaryTurnPhase.IDLE,
            )
            drainQueuedPageTurns()
        }
    }

    fun boundaryChapterSettled(chapterIndex: Int) {
        val state = readerState.value
        if (state.boundaryTurnPhase != BoundaryTurnPhase.SETTLING_CHAPTER || state.chapterIndex != chapterIndex) return
        boundaryCommitSourceChapterIndex = null
        readerState.value = state.copy(boundaryTurnPhase = BoundaryTurnPhase.IDLE)
        drainQueuedPageTurns()
    }

    private fun scheduleBoundarySettleFallback(chapterIndex: Int) {
        viewModelScope.launch {
            delay(180)
            val state = readerState.value
            if (state.boundaryTurnPhase == BoundaryTurnPhase.SETTLING_CHAPTER && state.chapterIndex == chapterIndex) {
                boundaryChapterSettled(chapterIndex)
            }
        }
    }

    fun commitAdjacentPreview(
        next: Boolean,
        nonce: Long? = null,
        sourceChapterIndex: Int? = null,
        targetChapterIndex: Int? = null,
    ) {
        val state = readerState.value
        val request = state.boundaryTurnRequest
        if (nonce != null && request?.nonce != nonce) return
        if (request != null && request.sourceChapterIndex != state.chapterIndex) return
        if (nonce == null && (request != null || state.boundaryTurnPhase != BoundaryTurnPhase.IDLE || boundaryCommitSourceChapterIndex == state.chapterIndex)) return
        if (nonce == null && sourceChapterIndex != null && sourceChapterIndex != state.chapterIndex) return
        val targetNext = request?.next ?: next
        val preview = if (targetNext) state.nextPreview else state.previousPreview
        if (preview == null || preview.chapterIndex !in state.chapters.indices) return
        if (request != null && request.targetChapterIndex != preview.chapterIndex) return
        if (nonce == null && targetChapterIndex != null && targetChapterIndex != preview.chapterIndex) return
        saveCurrentProgress(immediate = true)
        requestedOffset = preview.page.startOffset
        boundaryCommitSourceChapterIndex = state.chapterIndex
        readerState.value = state.copy(
            boundaryTurnRequest = null,
            boundaryTurnPhase = BoundaryTurnPhase.COMMITTING_CHAPTER,
        )
        loadChapter(preview.chapterIndex)
    }

    fun nextPage() {
        turnPage(next = true)
    }

    fun previousPage() {
        turnPage(next = false)
    }

    private fun turnPage(next: Boolean) {
        val state = readerState.value
        if (state.boundaryTurnPhase != BoundaryTurnPhase.IDLE || state.boundaryTurnRequest != null || boundaryCommitSourceChapterIndex != null) {
            pendingPageTurns.enqueue(next)
            return
        }
        if (!performPageTurn(next)) pendingPageTurns.clear()
    }

    private fun performPageTurn(next: Boolean): Boolean {
        val state = readerState.value
        return if (next) {
            performNextPageTurn(state)
        } else {
            performPreviousPageTurn(state)
        }
    }

    private fun performNextPageTurn(state: ReaderUiState): Boolean {
        when {
            state.pageIndex < state.pages.lastIndex -> {
                pageSelected(state.pageIndex + 1)
                return true
            }
            state.chapterIndex < state.chapters.lastIndex -> {
                if (!requestBoundaryTurn(next = true)) selectChapter(state.chapterIndex + 1)
                return true
            }
            else -> {
                setAutoReading(false)
                return false
            }
        }
    }

    private fun performPreviousPageTurn(state: ReaderUiState): Boolean {
        when {
            state.pageIndex > 0 -> {
                pageSelected(state.pageIndex - 1)
                return true
            }
            state.chapterIndex > 0 -> {
                if (!requestBoundaryTurn(next = false)) {
                    val previous = state.chapters[state.chapterIndex - 1]
                    requestedOffset = (previous.charCount - 1).coerceAtLeast(0)
                    loadChapter(state.chapterIndex - 1)
                }
                return true
            }
            else -> return false
        }
    }

    private fun requestBoundaryTurn(next: Boolean): Boolean {
        val state = readerState.value
        val preferences = uiState.value.preferences
        if (preferences.pageTurnMode == com.lightreader.app.core.model.PageTurnMode.NONE ||
            preferences.pageTurnMode == com.lightreader.app.core.model.PageTurnMode.VERTICAL
        ) {
            return false
        }
        val preview = if (next) state.nextPreview else state.previousPreview
        if (preview == null) return false
        if (state.boundaryTurnRequest != null) return true
        boundaryTurnNonce += 1
        readerState.value = state.copy(
            boundaryTurnRequest = BoundaryTurnRequest(next, boundaryTurnNonce, state.chapterIndex, preview.chapterIndex),
            boundaryTurnPhase = BoundaryTurnPhase.ANIMATING_PREVIEW,
        )
        scheduleBoundaryAnimationFallback(boundaryTurnNonce)
        return true
    }

    private fun scheduleBoundaryAnimationFallback(nonce: Long) {
        viewModelScope.launch {
            delay(700)
            val request = readerState.value.boundaryTurnRequest
            if (request?.nonce == nonce && readerState.value.boundaryTurnPhase == BoundaryTurnPhase.ANIMATING_PREVIEW) {
                commitAdjacentPreview(request.next, nonce = nonce)
            }
        }
    }

    private fun drainQueuedPageTurns() {
        if (drainingQueuedTurns) return
        drainingQueuedTurns = true
        try {
            var consumed = 0
            while (consumed < 3) {
                val next = pendingPageTurns.poll() ?: break
                if (readerState.value.boundaryTurnPhase != BoundaryTurnPhase.IDLE ||
                    readerState.value.boundaryTurnRequest != null ||
                    boundaryCommitSourceChapterIndex != null
                ) {
                    pendingPageTurns.enqueue(next)
                    break
                }
                if (!performPageTurn(next)) {
                    pendingPageTurns.clear()
                    break
                }
                consumed += 1
                if (readerState.value.boundaryTurnPhase != BoundaryTurnPhase.IDLE ||
                    readerState.value.boundaryTurnRequest != null ||
                    boundaryCommitSourceChapterIndex != null
                ) break
            }
        } finally {
            drainingQueuedTurns = false
        }
    }

    fun nextChapter() {
        val state = readerState.value
        if (state.chapterIndex < state.chapters.lastIndex) {
            saveCurrentProgress(immediate = true)
            requestedOffset = 0
            loadChapter(state.chapterIndex + 1)
        }
    }

    fun previousChapter() {
        val state = readerState.value
        if (state.chapterIndex > 0) {
            saveCurrentProgress(immediate = true)
            requestedOffset = 0
            loadChapter(state.chapterIndex - 1)
        }
    }

    fun toggleToolbar() {
        setToolbarVisible(!readerState.value.toolbarVisible)
    }

    fun setToolbarVisible(visible: Boolean) {
        val state = readerState.value
        val nextSettingsVisible = if (visible) state.settingsVisible else false
        if (state.toolbarVisible != visible || state.settingsVisible != nextSettingsVisible) {
            readerState.value = state.copy(toolbarVisible = visible, settingsVisible = nextSettingsVisible)
        }
    }

    fun hideToolbar() = setToolbarVisible(false)

    fun showSettings() { readerState.value = readerState.value.copy(settingsVisible = true, overlay = ReaderOverlay.NONE, toolbarVisible = true) }
    fun hideSettings() { readerState.value = readerState.value.copy(settingsVisible = false) }
    fun toggleSettings() {
        val state = readerState.value
        readerState.value = state.copy(
            settingsVisible = !state.settingsVisible,
            overlay = ReaderOverlay.NONE,
            toolbarVisible = true,
        )
    }

    fun showTableOfContents() {
        readerState.value = readerState.value.copy(
            overlay = ReaderOverlay.TOC,
            settingsVisible = false,
            toolbarVisible = false,
        )
    }

    fun showBookmarksOverlay() {
        readerState.value = readerState.value.copy(
            overlay = ReaderOverlay.BOOKMARKS,
            settingsVisible = false,
            toolbarVisible = false,
        )
    }

    fun hideReaderOverlay() {
        val state = readerState.value
        if (state.overlay != ReaderOverlay.NONE) {
            readerState.value = state.copy(overlay = ReaderOverlay.NONE, toolbarVisible = true)
        }
    }

    fun toggleAutoReading() = setAutoReading(!readerState.value.autoReading)

    fun setAutoReading(enabled: Boolean) {
        if (readerState.value.autoReading != enabled) {
            val state = readerState.value
            readerState.value = state.copy(
                autoReading = enabled,
                toolbarVisible = if (enabled) false else state.toolbarVisible,
                settingsVisible = if (enabled) false else state.settingsVisible,
                overlay = if (enabled) ReaderOverlay.NONE else state.overlay,
            )
        }
    }

    fun jumpToProgress(progress: Float) {
        val state = readerState.value
        if (state.chapters.isEmpty()) return
        val total = state.chapters.sumOf { it.charCount.toLong() }.coerceAtLeast(1L)
        var target = (progress.coerceIn(0f, 1f) * total).toLong()
        val index = state.chapters.indexOfFirst { chapter ->
            if (target < chapter.charCount) true else { target -= chapter.charCount; false }
        }.takeIf { it >= 0 } ?: state.chapters.lastIndex
        saveCurrentProgress(immediate = true)
        requestedOffset = target.toInt().coerceAtLeast(0)
        loadChapter(index)
    }

    private fun saveCurrentProgress(immediate: Boolean = false) {
        val state = readerState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val page = state.pages.getOrNull(state.pageIndex) ?: return
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            if (!immediate) delay(250)
            container.bookRepository.saveProgress(
                book.id, chapter.id, page.startOffset, state.chapterIndex,
                state.pageIndex, chapter.title, currentLayoutFingerprint,
            )
        }
    }

    fun onReaderStopped() = saveCurrentProgress(immediate = true)

    private fun currentPageOffset(): Int = readerState.value.let { state ->
        state.pages.getOrNull(state.pageIndex)?.startOffset ?: requestedOffset
    }

    fun addBookmark() {
        val state = readerState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val page = state.pages.getOrNull(state.pageIndex) ?: return
        viewModelScope.launch {
            container.bookRepository.addBookmark(book.id, chapter.id, page.startOffset, page.text.trim())
            message.value = text(R.string.message_bookmark_added)
        }
    }

    fun toggleCurrentPageBookmark() {
        val state = readerState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val page = state.pages.getOrNull(state.pageIndex) ?: return
        val existing = state.bookmarks.firstOrNull {
            it.chapterId == chapter.id && it.charOffset >= page.startOffset && it.charOffset < page.endOffset.coerceAtLeast(page.startOffset + 1)
        }
        if (existing != null) {
            viewModelScope.launch {
                container.bookRepository.deleteBookmark(existing.id)
                message.value = text(R.string.message_bookmark_removed)
            }
        } else {
            viewModelScope.launch {
                container.bookRepository.addBookmark(book.id, chapter.id, page.startOffset, page.text.trim())
                message.value = text(R.string.message_bookmark_added)
            }
        }
    }

    fun deleteBookmark(id: String) { viewModelScope.launch { container.bookRepository.deleteBookmark(id) } }

    fun jumpToBookmark(bookmark: Bookmark) {
        val index = readerState.value.chapters.indexOfFirst { it.id == bookmark.chapterId }
        if (index >= 0) {
            requestedOffset = bookmark.charOffset
            loadChapter(index)
        }
    }

    private fun observeBookmarks(bookId: String) {
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            container.bookRepository.bookmarks(bookId).collect { bookmarks ->
                readerState.value = readerState.value.copy(bookmarks = bookmarks)
            }
        }
    }

    fun updateSearchQuery(value: String) {
        searchState.value = searchState.value.copy(query = value, hasSearched = false)
    }

    fun clearSearchQuery() {
        searchState.value = SearchUiState()
    }

    fun search() {
        val target = navigation.value.current as? AppScreen.Search ?: return
        val query = searchState.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            searchState.value = searchState.value.copy(query = query, searching = true, hasSearched = true)
            val results = runCatching { container.bookRepository.search(target.bookId, query) }
                .getOrElse { message.value = text(R.string.message_search_failed); emptyList() }
            searchState.value = searchState.value.copy(results = results, searching = false)
        }
    }

    fun jumpToSearchResult(result: SearchResult) {
        val target = navigation.value.current as? AppScreen.Search ?: return
        val stack = navigation.value.backStack
        val trimmedStack = if (stack.lastOrNull() is AppScreen.Reader) stack.dropLast(1) else stack
        navigation.value = NavigationState(
            current = AppScreen.Reader(target.bookId),
            backStack = trimmedStack,
        )
        val index = readerState.value.chapters.indexOfFirst { it.id == result.chapterId }
        if (index >= 0) {
            requestedOffset = result.charOffset
            loadChapter(index)
            message.value = text(R.string.message_jumped_to_search_result)
        }
    }

    fun savePreferences(preferences: ReaderPreferences) {
        val normalized = if (preferences.theme != ReaderTheme.NIGHT) {
            preferences.copy(lastNonNightTheme = preferences.theme)
        } else preferences
        viewModelScope.launch { container.settingsRepository.save(normalized) }
    }

    fun toggleNightMode() {
        val preferences = uiState.value.preferences
        val updated = if (preferences.theme == ReaderTheme.NIGHT) {
            preferences.copy(theme = preferences.lastNonNightTheme.takeUnless { it == ReaderTheme.NIGHT } ?: ReaderTheme.EYE_CARE)
        } else {
            preferences.copy(lastNonNightTheme = preferences.theme, theme = ReaderTheme.NIGHT)
        }
        savePreferences(updated)
    }

    fun updateWebUrl(value: String) { webState.value = webState.value.copy(url = value, preview = null, error = null) }

    fun previewWebBook() {
        val url = webState.value.url.trim()
        viewModelScope.launch {
            webState.value = webState.value.copy(loading = true, error = null, preview = null)
            runCatching { container.webSourceParser.preview(url) }
                .onSuccess { webState.value = webState.value.copy(loading = false, preview = it) }
                .onFailure { webState.value = webState.value.copy(loading = false, error = text(R.string.message_parse_failed)) }
        }
    }

    fun startWebDownload() {
        val preview = webState.value.preview ?: return
        viewModelScope.launch {
            container.downloadRepository.start(preview)
            webState.value = webState.value.copy(preview = null, url = "")
            message.value = text(R.string.message_download_task_created)
        }
    }

    fun pauseDownload(id: String) { viewModelScope.launch { container.downloadRepository.pause(id) } }
    fun resumeDownload(id: String) { viewModelScope.launch { container.downloadRepository.resume(id) } }
    fun cancelDownload(id: String) { viewModelScope.launch { container.downloadRepository.cancel(id) } }
    fun deleteDownload(id: String) { viewModelScope.launch { container.downloadRepository.delete(id) } }
    fun openDownloadedBook(id: String) {
        viewModelScope.launch {
            if (container.bookRepository.book(id) != null) {
                openBook(id)
            } else {
                message.value = text(R.string.message_book_not_imported)
            }
        }
    }

    fun refreshCurrentWebBook() {
        val book = readerState.value.book ?: return
        if (book.format != BookFormat.WEB || book.sourceUrl.isNullOrBlank()) {
            message.value = text(R.string.message_refresh_web_only)
            return
        }
        viewModelScope.launch {
            showBusy(R.string.busy_checking_updates)
            message.value = text(R.string.busy_checking_updates)
            runCatching { container.downloadRepository.refreshBook(book.id) }
                .onSuccess { count ->
                    message.value = if (count == 0) text(R.string.message_no_new_chapters) else text(R.string.message_added_chapters_to_refresh, count)
                }
                .onFailure { message.value = text(R.string.message_refresh_failed) }
            hideBusy()
        }
    }

    fun updateApiKeyDraft(value: String) {
        apiSettingsState.value = apiSettingsState.value.copy(keyDraft = value)
    }

    fun toggleApiKeyVisibility() {
        apiSettingsState.value = apiSettingsState.value.copy(showKey = !apiSettingsState.value.showKey)
    }

    fun toggleAiAdvancedSettings() {
        apiSettingsState.value = apiSettingsState.value.copy(advancedExpanded = !apiSettingsState.value.advancedExpanded)
    }

    fun updateAiConfigurationDraft(value: AiConfiguration) {
        apiSettingsState.value = apiSettingsState.value.copy(configuration = value)
    }

    fun saveApiKeyDraft() {
        val key = apiSettingsState.value.keyDraft.trim()
        if (key.isBlank()) return
        runCatching { container.keyStore.save(key) }
            .onSuccess {
                webState.value = webState.value.copy(hasApiKey = true)
                apiSettingsState.value = apiSettingsState.value.copy(keyDraft = "", showKey = false)
                message.value = text(R.string.message_api_key_saved)
            }
            .onFailure { message.value = text(R.string.message_api_key_save_failed) }
    }

    fun deleteApiKey() {
        container.keyStore.clear()
        webState.value = webState.value.copy(hasApiKey = false)
        message.value = text(R.string.message_api_key_deleted)
    }

    fun testApiKey() {
        viewModelScope.launch {
            showBusy(R.string.busy_testing_connection)
            message.value = if (container.aiProvider.testConnection()) text(R.string.message_deepseek_connected) else text(R.string.message_deepseek_failed)
            hideBusy()
        }
    }

    fun aiConfiguration(): AiConfiguration = container.aiConfigurationStore.get()

    fun saveAiConfiguration() {
        saveAiConfiguration(apiSettingsState.value.configuration)
    }

    fun saveAiConfiguration(value: AiConfiguration) {
        runCatching { container.aiConfigurationStore.save(value) }
            .onSuccess {
                apiSettingsState.value = apiSettingsState.value.copy(configuration = container.aiConfigurationStore.get())
                message.value = text(R.string.message_ai_advanced_saved)
            }
            .onFailure { message.value = text(R.string.message_ai_advanced_save_failed) }
    }

    fun onVolumeKey(next: Boolean): Boolean {
        val state = uiState.value
        if (state.screen !is AppScreen.Reader || !state.preferences.volumeKeys) return false
        if (next) nextPage() else previousPage()
        return true
    }

    private fun showBusy(@StringRes labelRes: Int) {
        busy.value = BusyState(active = true, message = text(labelRes))
    }

    private fun hideBusy() {
        busy.value = BusyState()
    }

    private fun text(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) getApplication<Application>().getString(id) else getApplication<Application>().getString(id, *args)

    private data class LayoutCacheKey(
        val bookId: String,
        val chapterId: Long,
        val styleHash: Int,
        val viewport: ReaderViewport,
    )

    private data class ChapterContentCacheKey(val bookId: String, val chapterId: Long)

    private data class ChapterContent(
        val rawText: String,
        val paragraphs: List<BookParagraph>,
    )
}
