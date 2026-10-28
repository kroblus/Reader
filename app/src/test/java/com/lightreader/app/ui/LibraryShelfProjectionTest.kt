package com.lightreader.app.ui

import com.lightreader.app.core.model.Book
import com.lightreader.app.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LibraryShelfProjectionTest {
    @Test
    fun importingAnUnreadBookDoesNotReplaceContinueReading() {
        val reading = shelfBook("reading", title = "Reading", addedAt = 1, lastReadAt = 20)
        val imported = shelfBook("new", title = "New", addedAt = 30)

        val shelf = projectLibraryShelf(listOf(imported, reading), "", LibrarySortOrder.RECENT)

        assertEquals("reading", shelf.recentBook?.book?.id)
        assertEquals(listOf("new", "reading"), shelf.books.map { it.book.id })
    }

    @Test
    fun unreadShelfHasNoContinueReadingCard() {
        val shelf = projectLibraryShelf(listOf(shelfBook("new")), "", LibrarySortOrder.RECENT)
        assertNull(shelf.recentBook)
        assertEquals(1, shelf.books.size)
    }

    @Test
    fun recentBookUsesLatestReadingTimeEvenWhenSortedByTitle() {
        val earlier = shelfBook("a", title = "Alpha", lastReadAt = 10)
        val later = shelfBook("b", title = "Beta", lastReadAt = 20)
        val shelf = projectLibraryShelf(listOf(later, earlier), "", LibrarySortOrder.TITLE, Locale.ENGLISH)

        assertEquals(listOf("a", "b"), shelf.books.map { it.book.id })
        assertEquals("b", shelf.recentBook?.book?.id)
    }

    @Test
    fun searchMatchesTitleAndAuthorAcrossWhitespaceAndCase() {
        val wanted = shelfBook("match", title = "The Long Road", author = "Ada Lin", lastReadAt = 20)
        val wrongAuthor = shelfBook("other", title = "The Long Road", author = "Other")

        val shelf = projectLibraryShelf(listOf(wanted, wrongAuthor), "  ROAD\tada  ", LibrarySortOrder.RECENT)

        assertEquals(listOf("match"), shelf.books.map { it.book.id })
        assertNull(shelf.recentBook)
    }

    @Test
    fun noMatchIsAnEmptyProjectionWithoutChangingSourceBooks() {
        val books = listOf(shelfBook("a", author = null), shelfBook("b", title = "长夜"))
        assertEquals(emptyList<ShelfBookUi>(), projectLibraryShelf(books, "missing", LibrarySortOrder.RECENT).books)
        assertEquals(2, projectLibraryShelf(books, " \n ", LibrarySortOrder.RECENT).books.size)
        assertEquals(listOf("b"), projectLibraryShelf(books, "长夜", LibrarySortOrder.RECENT).books.map { it.book.id })
    }

    @Test
    fun recentActivityOrderingIsStableAcrossDatabaseRowOrder() {
        val first = shelfBook("a", addedAt = 10)
        val second = shelfBook("b", addedAt = 10)
        val recent = shelfBook("recent", addedAt = 1, lastReadAt = 20)
        val books = listOf(second, first, recent)

        val forward = projectLibraryShelf(books, "", LibrarySortOrder.RECENT).books.map { it.book.id }
        val reversed = projectLibraryShelf(books.reversed(), "", LibrarySortOrder.RECENT).books.map { it.book.id }

        assertEquals(listOf("recent", "a", "b"), forward)
        assertEquals(forward, reversed)
    }

    private fun shelfBook(
        id: String,
        title: String = id,
        author: String? = null,
        addedAt: Long = 1,
        lastReadAt: Long? = null,
    ) = ShelfBookUi(
        book = Book(id, title, author, BookFormat.TXT, 3, 1000, addedAt, lastReadAt, null),
        currentChapterTitle = lastReadAt?.let { "Chapter 1" },
        chapterIndex = lastReadAt?.let { 0 },
        progressPercent = if (lastReadAt == null) 0 else 10,
        started = lastReadAt != null,
        updatedAt = lastReadAt,
    )
}
