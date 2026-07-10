package com.lightreader.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.app.R
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.data.BookImportCandidate
import com.lightreader.app.core.data.ShelfBookProgress
import com.lightreader.app.core.formats.BookImportException
import com.lightreader.app.core.formats.BookImportFailure
import com.lightreader.app.core.formats.BookImportOptions
import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.ReaderPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Owns shelf data and import/edit workflows; navigation stays in the app shell. */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val readerApplication = application as ReaderApplication
    private val container = readerApplication.container
    private val pendingDuplicateImport = MutableStateFlow<PendingDuplicateImport?>(null)
    private val eventsChannel = Channel<LibraryEvent>(Channel.BUFFERED)

    val events = eventsChannel.receiveAsFlow()
    val busy = MutableStateFlow(BusyState())
    val state: StateFlow<LibraryUiState> = combine(
        container.bookRepository.books,
        container.bookRepository.shelfProgress,
        container.settingsRepository.preferences,
        pendingDuplicateImport,
    ) { books, progress, preferences, duplicate ->
        LibraryUiState(
            shelfBooks = buildShelfBooks(books, progress),
            preferences = preferences,
            libraryTaglineIndex = preferences.libraryTaglineIndex,
            pendingDuplicateImport = duplicate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            val preferences = container.settingsRepository.preferences.first()
            val taglineCount = application.resources.getStringArray(R.array.brand_taglines).size
            val nextIndex = nextTaglineIndex(preferences.libraryTaglineIndex, taglineCount)
            if (nextIndex != preferences.libraryTaglineIndex) {
                container.settingsRepository.saveLibraryTaglineIndex(nextIndex)
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            busy.value = BusyState(active = true, message = uiText(R.string.busy_importing_book))
            val candidate = runCatching { container.bookRepository.inspectImport(uri) }
                .getOrElse { error ->
                    postMessage(importFailureText(error))
                    busy.value = BusyState()
                    return@launch
                }
            if (candidate.existingBook != null) {
                pendingDuplicateImport.value = PendingDuplicateImport(candidate)
                busy.value = BusyState()
            } else {
                importCandidate(candidate)
            }
        }
    }

    fun dismissDuplicateImport() {
        pendingDuplicateImport.value = null
    }

    fun openExistingDuplicate() {
        val existing = pendingDuplicateImport.value?.candidate?.existingBook ?: return
        pendingDuplicateImport.value = null
        eventsChannel.trySend(LibraryEvent.OpenBook(existing.id))
    }

    fun importDuplicateCopy() {
        val candidate = pendingDuplicateImport.value?.candidate ?: return
        pendingDuplicateImport.value = null
        viewModelScope.launch {
            busy.value = BusyState(active = true, message = uiText(R.string.busy_importing_book))
            importCandidate(candidate)
        }
    }

    fun openBook(bookId: String) {
        eventsChannel.trySend(LibraryEvent.OpenBook(bookId))
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            container.bookRepository.deleteBook(id)
            postMessage(uiText(R.string.message_deleted))
        }
    }

    fun updateBookMetadata(bookId: String, title: String, author: String?) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            postMessage(uiText(R.string.library_edit_title_required))
            return
        }
        viewModelScope.launch {
            runCatching { container.bookRepository.updateBookMetadata(bookId, normalizedTitle, author) }
                .onSuccess { updated ->
                    eventsChannel.trySend(LibraryEvent.BookMetadataUpdated(updated))
                    postMessage(uiText(R.string.message_book_info_saved))
                }
                .onFailure { postMessage(uiText(R.string.message_book_info_save_failed)) }
        }
    }

    private suspend fun importCandidate(candidate: BookImportCandidate) {
        runCatching {
            container.bookRepository.import(
                candidate,
                BookImportOptions(cleanTxtNoise = state.value.preferences.cleanTxtNoise),
            )
        }.onSuccess { book ->
            postMessage(uiText(R.string.message_imported_book, book.title))
        }.onFailure { error ->
            postMessage(importFailureText(error))
        }
        busy.value = BusyState()
    }

    private fun postMessage(message: UiText) {
        eventsChannel.trySend(LibraryEvent.Message(message))
    }
}

data class LibraryUiState(
    val shelfBooks: List<ShelfBookUi> = emptyList(),
    val preferences: ReaderPreferences = ReaderPreferences(),
    val libraryTaglineIndex: Int = 0,
    val pendingDuplicateImport: PendingDuplicateImport? = null,
)

data class PendingDuplicateImport(val candidate: BookImportCandidate)

data class ShelfBookUi(
    val book: Book,
    val currentChapterTitle: String?,
    val chapterIndex: Int?,
    val progressPercent: Int,
    val started: Boolean,
    val updatedAt: Long?,
)

sealed interface LibraryEvent {
    data class Message(val value: UiText) : LibraryEvent
    data class OpenBook(val bookId: String) : LibraryEvent
    data class BookMetadataUpdated(val book: Book) : LibraryEvent
}

private fun buildShelfBooks(
    books: List<Book>,
    progress: List<ShelfBookProgress>,
): List<ShelfBookUi> {
    val progressByBook = progress.associateBy { it.bookId }
    return books.map { book ->
        val current = progressByBook[book.id]
        val progressPercent = current?.let {
            if (book.totalChars > 0L) {
                ((it.readChars.toDouble() / book.totalChars.toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(0, 100)
            } else {
                0
            }
        } ?: 0
        ShelfBookUi(
            book = book,
            currentChapterTitle = current?.chapterTitle,
            chapterIndex = current?.chapterIndex,
            progressPercent = progressPercent,
            started = current != null,
            updatedAt = current?.updatedAt ?: book.lastReadAt,
        )
    }
}

private fun importFailureText(error: Throwable): UiText = when ((error as? BookImportException)?.failure) {
    BookImportFailure.FILE_UNREADABLE -> uiText(R.string.message_import_file_unreadable)
    BookImportFailure.EMPTY_CONTENT -> uiText(R.string.message_import_empty)
    BookImportFailure.ENCODING_QUALITY -> uiText(R.string.message_import_encoding)
    BookImportFailure.UNSUPPORTED_FORMAT -> uiText(R.string.message_import_unsupported)
    null -> uiText(R.string.message_import_failed)
}
