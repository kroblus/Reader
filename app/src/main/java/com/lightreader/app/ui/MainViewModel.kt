package com.lightreader.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.Bookmark
import com.lightreader.app.core.model.Chapter
import com.lightreader.app.core.model.PageSlice
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.SearchResult
import com.lightreader.app.core.model.WebBookPreview
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
    val pages: List<PageSlice> = emptyList(),
    val pageIndex: Int = 0,
    val toolbarVisible: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val bookmarks: List<Bookmark> = emptyList(),
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
    private var viewport = Viewport(0, 0, 1f)
    private var chapterText = ""
    private var requestedOffset = 0
    private var paginationJob: Job? = null
    private var bookmarkJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.preferences.collect {
                if (readerState.value.book != null && viewport.width > 0) repaginate(it)
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
        screen.value = when (screen.value) {
            is AppScreen.Search -> AppScreen.Reader((screen.value as AppScreen.Search).bookId)
            else -> AppScreen.Library
        }
    }

    fun openBook(bookId: String) {
        screen.value = AppScreen.Reader(bookId)
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
                ?.takeIf { it >= 0 } ?: 0
            requestedOffset = progress?.charOffset ?: 0
            readerState.value = ReaderUiState(book = book, chapters = chapters, chapterIndex = chapterIndex, loading = true)
            observeBookmarks(bookId)
            loadChapter(chapterIndex)
        }
    }

    fun setViewport(width: Int, height: Int, scaledDensity: Float) {
        val next = Viewport(width, height, scaledDensity)
        if (next == viewport) return
        viewport = next
        if (chapterText.isNotEmpty()) repaginate(uiState.value.preferences)
    }

    fun selectChapter(index: Int) {
        if (index !in readerState.value.chapters.indices) return
        requestedOffset = 0
        loadChapter(index)
    }

    private fun loadChapter(index: Int) {
        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            val chapter = readerState.value.chapters.getOrNull(index) ?: return@launch
            readerState.value = readerState.value.copy(chapterIndex = index, loading = true, error = null, pages = emptyList(), pageIndex = 0)
            runCatching { container.bookRepository.readChapter(chapter) }
                .onSuccess {
                    chapterText = it
                    repaginate(uiState.value.preferences)
                }
                .onFailure { readerState.value = readerState.value.copy(loading = false, error = it.message) }
        }
    }

    private fun repaginate(preferences: ReaderPreferences) {
        if (viewport.width <= 0 || viewport.height <= 0 || chapterText.isEmpty()) return
        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            readerState.value = readerState.value.copy(loading = true)
            val pages = withContext(Dispatchers.Default) {
                container.paginationEngine.paginate(
                    chapterText,
                    viewport.width,
                    viewport.height,
                    viewport.scaledDensity,
                    preferences,
                )
            }
            val index = pages.indexOfFirst { requestedOffset in it.start until it.endExclusive }
                .takeIf { it >= 0 } ?: 0
            readerState.value = readerState.value.copy(pages = pages, pageIndex = index, loading = false)
            requestedOffset = 0
        }
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

    private fun saveCurrentProgress() {
        val state = readerState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val offset = state.pages.getOrNull(state.pageIndex)?.start ?: 0
        viewModelScope.launch { container.bookRepository.saveProgress(book.id, chapter.id, offset) }
    }

    fun addBookmark() {
        val state = readerState.value
        val book = state.book ?: return
        val chapter = state.chapters.getOrNull(state.chapterIndex) ?: return
        val page = state.pages.getOrNull(state.pageIndex) ?: return
        viewModelScope.launch {
            container.bookRepository.addBookmark(book.id, chapter.id, page.start, page.text.trim())
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

    private data class Viewport(val width: Int, val height: Int, val scaledDensity: Float)
}
