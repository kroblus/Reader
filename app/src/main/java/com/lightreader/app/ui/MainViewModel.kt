package com.lightreader.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookParagraph
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.ReaderPage
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.model.SearchResult
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.core.settings.AiConfiguration
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
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
    data class Search(val bookId: String) : AppScreen
    data object WebImport : AppScreen
    data object ApiSettings : AppScreen
}

data class MainUiState(
    val books: List<Book> = emptyList(),
    val preferences: ReaderPreferences = ReaderPreferences(),
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val screen: AppScreen = AppScreen.Library,
    val busy: Boolean = false,
    val message: String? = null,
)

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val pages: List<ReaderPage> = emptyList(),
    val pageIndex: Int = 0,
    val toolbarVisible: Boolean = true,
    val settingsVisible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val layoutVersion: Int = 0,
    val layoutPreferences: ReaderPreferences? = null,
)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val searching: Boolean = false,
)

data class WebImportUiState(
    val url: String = "",
    val loading: Boolean = false,
    val preview: WebBookPreview? = null,
    val error: String? = null,
    val hasApiKey: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ReaderApplication).container
    private val screen = MutableStateFlow<AppScreen>(AppScreen.Library)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val contentState = combine(
        container.bookRepository.books,
        container.settingsRepository.preferences,
        container.downloadRepository.tasks,
    ) { books, preferences, tasks -> Triple(books, preferences, tasks) }
    private val chromeState = combine(
        screen,
        busy,
        message,
    ) { currentScreen, isBusy, currentMessage -> Triple(currentScreen, isBusy, currentMessage) }
    val uiState: StateFlow<MainUiState> = combine(contentState, chromeState) { content, chrome ->
        MainUiState(content.first, content.second, content.third, chrome.first, chrome.second, chrome.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val readerState = MutableStateFlow(ReaderUiState())
    val searchState = MutableStateFlow(SearchUiState())
    val webState = MutableStateFlow(WebImportUiState(hasApiKey = container.keyStore.hasKey()))
    private val normalizer = BookTextNormalizer()
    private var viewport = ReaderViewport(0, 0, 1f, 1f)
    private var chapterText = ""
    private var chapterParagraphs: List<BookParagraph> = emptyList()
    private var requestedOffset = 0
    private var currentLayoutFingerprint = 0
    private var paginationJob: Job? = null
    private var prefetchJob: Job? = null
    private var bookmarkJob: Job? = null
    private val pageCache = object : LinkedHashMap<LayoutCacheKey, List<ReaderPage>>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutCacheKey, List<ReaderPage>>): Boolean = size > 3
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
            busy.value = true
            runCatching { container.bookRepository.import(uri) }
                .onSuccess { message.value = "已导入《${it.title}》" }
                .onFailure { message.value = it.message ?: "导入失败" }
            busy.value = false
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            container.bookRepository.deleteBook(id)
            message.value = "已删除"
        }
    }

    fun clearMessage() { message.value = null }

    fun navigate(target: AppScreen) { screen.value = target }

    fun goBack() {
        if (screen.value is AppScreen.Reader) saveCurrentProgress()
        screen.value = when (screen.value) {
            is AppScreen.Search -> AppScreen.Reader((screen.value as AppScreen.Search).bookId)
            else -> AppScreen.Library
        }
    }

    fun openBook(bookId: String) {
        screen.value = AppScreen.Reader(bookId)
        paginationJob?.cancel()
        prefetchJob?.cancel()
        chapterText = ""
        chapterParagraphs = emptyList()
        currentLayoutFingerprint = 0
        viewModelScope.launch {
            readerState.value = ReaderUiState(loading = true)
            val book = container.bookRepository.book(bookId) ?: run {
                readerState.value = ReaderUiState(error = "书籍不存在")
                return@launch
            }
            val chapters = container.bookRepository.chapters(bookId)
            if (chapters.isEmpty()) {
                readerState.value = ReaderUiState(book = book, error = "书籍没有章节")
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
        if (chapterText.isNotEmpty() || chapterParagraphs.isNotEmpty()) {
            requestedOffset = currentPageOffset()
            repaginate(uiState.value.preferences)
        }
    }

    fun selectChapter(index: Int) {
        if (index !in readerState.value.chapters.indices) return
        if (index != readerState.value.chapterIndex) saveCurrentProgress()
        requestedOffset = 0
        loadChapter(index)
    }

    private fun loadChapter(index: Int) {
        paginationJob?.cancel()
        prefetchJob?.cancel()
        paginationJob = viewModelScope.launch {
            val chapter = readerState.value.chapters.getOrNull(index) ?: return@launch
            readerState.value = readerState.value.copy(chapterIndex = index, loading = true, error = null, pages = emptyList(), pageIndex = 0)
            val rawText = runCatching { container.bookRepository.readChapter(chapter) }
                .getOrElse {
                    readerState.value = readerState.value.copy(loading = false, error = it.message ?: "章节读取失败")
                    return@launch
                }
            chapterText = rawText
            chapterParagraphs = withContext(Dispatchers.Default) { normalizer.normalize(rawText) }
            paginateAndApply(index, chapter, chapterParagraphs, uiState.value.preferences)
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
        val style = preferences.toReaderStyle()
        val key = LayoutCacheKey(chapter.bookId, chapter.id, style.layoutFingerprint(), viewport)
        val cached = pageCache[key]
        val pages = cached ?: withContext(Dispatchers.Default) {
            container.paginationEngine.paginate(chapterIndex, chapter.title, paragraphs, viewport, style).pages
        }.also { pageCache[key] = it }
        if (readerState.value.chapters.getOrNull(chapterIndex)?.id != chapter.id) return
        val pageIndex = pageForOffset(pages, requestedOffset)
        currentLayoutFingerprint = style.layoutFingerprint()
        readerState.value = readerState.value.copy(
            chapterIndex = chapterIndex,
            pages = pages,
            pageIndex = pageIndex,
            loading = false,
            error = null,
            layoutVersion = readerState.value.layoutVersion + if (cached == null) 1 else 0,
            layoutPreferences = preferences,
        )
        requestedOffset = 0
        prefetchAdjacent(chapterIndex, preferences)
    }

    private fun prefetchAdjacent(chapterIndex: Int, preferences: ReaderPreferences) {
        val chapters = readerState.value.chapters
        val style = preferences.toReaderStyle()
        val targetViewport = viewport
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            listOf(chapterIndex - 1, chapterIndex + 1).filter { it in chapters.indices }.forEach { index ->
                val chapter = chapters[index]
                val key = LayoutCacheKey(chapter.bookId, chapter.id, style.layoutFingerprint(), targetViewport)
                if (pageCache[key] == null) {
                    val rawText = runCatching { container.bookRepository.readChapter(chapter) }.getOrNull() ?: return@forEach
                    val pages = withContext(Dispatchers.Default) {
                        val paragraphs = normalizer.normalize(rawText)
                        container.paginationEngine.paginate(index, chapter.title, paragraphs, targetViewport, style).pages
                    }
                    pageCache[key] = pages
                }
            }
        }
    }

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

    fun nextPage() {
        val state = readerState.value
        when {
            state.pageIndex < state.pages.lastIndex -> pageSelected(state.pageIndex + 1)
            state.chapterIndex < state.chapters.lastIndex -> selectChapter(state.chapterIndex + 1)
        }
    }

    fun previousPage() {
        val state = readerState.value
        when {
            state.pageIndex > 0 -> pageSelected(state.pageIndex - 1)
            state.chapterIndex > 0 -> {
                val previous = state.chapters[state.chapterIndex - 1]
                requestedOffset = (previous.charCount - 1).coerceAtLeast(0)
                loadChapter(state.chapterIndex - 1)
            }
        }
    }

    fun toggleToolbar() {
        setToolbarVisible(!readerState.value.toolbarVisible)
    }

    fun setToolbarVisible(visible: Boolean) {
        if (readerState.value.toolbarVisible != visible) {
            readerState.value = readerState.value.copy(toolbarVisible = visible)
        }
    }

    fun hideToolbar() = setToolbarVisible(false)

    fun showSettings() { readerState.value = readerState.value.copy(settingsVisible = true, toolbarVisible = true) }
    fun hideSettings() { readerState.value = readerState.value.copy(settingsVisible = false) }

    fun jumpToProgress(progress: Float) {
        val state = readerState.value
        if (state.chapters.isEmpty()) return
        val total = state.chapters.sumOf { it.charCount.toLong() }.coerceAtLeast(1L)
        var target = (progress.coerceIn(0f, 1f) * total).toLong()
        val index = state.chapters.indexOfFirst { chapter ->
            if (target < chapter.charCount) true else { target -= chapter.charCount; false }
        }.takeIf { it >= 0 } ?: state.chapters.lastIndex
        saveCurrentProgress()
        requestedOffset = target.toInt().coerceAtLeast(0)
        loadChapter(index)
    }

    private fun saveCurrentProgress() {
        val state = readerState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val page = state.pages.getOrNull(state.pageIndex) ?: return
        viewModelScope.launch {
            container.bookRepository.saveProgress(
                book.id, chapter.id, page.startOffset, state.chapterIndex,
                state.pageIndex, chapter.title, currentLayoutFingerprint,
            )
        }
    }

    fun onReaderStopped() = saveCurrentProgress()

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
            message.value = "已添加书签"
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

    fun updateSearchQuery(value: String) { searchState.value = searchState.value.copy(query = value) }

    fun search() {
        val target = screen.value as? AppScreen.Search ?: return
        val query = searchState.value.query
        viewModelScope.launch {
            searchState.value = searchState.value.copy(searching = true)
            val results = runCatching { container.bookRepository.search(target.bookId, query) }
                .getOrElse { message.value = it.message; emptyList() }
            searchState.value = searchState.value.copy(results = results, searching = false)
        }
    }

    fun jumpToSearchResult(result: SearchResult) {
        screen.value = AppScreen.Reader((screen.value as AppScreen.Search).bookId)
        val index = readerState.value.chapters.indexOfFirst { it.id == result.chapterId }
        if (index >= 0) {
            requestedOffset = 0
            loadChapter(index)
        }
    }

    fun savePreferences(preferences: ReaderPreferences) {
        viewModelScope.launch { container.settingsRepository.save(preferences) }
    }

    fun updateWebUrl(value: String) { webState.value = webState.value.copy(url = value, preview = null, error = null) }

    fun previewWebBook() {
        val url = webState.value.url.trim()
        viewModelScope.launch {
            webState.value = webState.value.copy(loading = true, error = null, preview = null)
            runCatching { container.webSourceParser.preview(url) }
                .onSuccess { webState.value = webState.value.copy(loading = false, preview = it) }
                .onFailure { webState.value = webState.value.copy(loading = false, error = it.message ?: "解析失败") }
        }
    }

    fun startWebDownload() {
        val preview = webState.value.preview ?: return
        viewModelScope.launch {
            container.downloadRepository.start(preview)
            webState.value = webState.value.copy(preview = null, url = "")
            message.value = "下载任务已创建"
        }
    }

    fun pauseDownload(id: String) { viewModelScope.launch { container.downloadRepository.pause(id) } }
    fun resumeDownload(id: String) { viewModelScope.launch { container.downloadRepository.resume(id) } }

    fun saveApiKey(key: String) {
        runCatching { container.keyStore.save(key) }
            .onSuccess {
                webState.value = webState.value.copy(hasApiKey = true)
                message.value = "API Key 已加密保存"
            }
            .onFailure { message.value = it.message }
    }

    fun deleteApiKey() {
        container.keyStore.clear()
        webState.value = webState.value.copy(hasApiKey = false)
        message.value = "API Key 已删除"
    }

    fun testApiKey() {
        viewModelScope.launch {
            busy.value = true
            message.value = if (container.aiProvider.testConnection()) "DeepSeek 连接成功" else "DeepSeek 连接失败"
            busy.value = false
        }
    }

    fun aiConfiguration(): AiConfiguration = container.aiConfigurationStore.get()

    fun saveAiConfiguration(value: AiConfiguration) {
        runCatching { container.aiConfigurationStore.save(value) }
            .onSuccess { message.value = "AI 高级设置已保存" }
            .onFailure { message.value = it.message }
    }

    fun onVolumeKey(next: Boolean): Boolean {
        val state = uiState.value
        if (state.screen !is AppScreen.Reader || !state.preferences.volumeKeys) return false
        if (next) nextPage() else previousPage()
        return true
    }

    private data class LayoutCacheKey(
        val bookId: String,
        val chapterId: Long,
        val styleHash: Int,
        val viewport: ReaderViewport,
    )
}
