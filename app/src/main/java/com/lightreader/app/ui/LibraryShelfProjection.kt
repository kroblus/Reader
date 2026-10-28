package com.lightreader.app.ui

import java.text.Collator
import java.util.Locale

internal enum class LibrarySortOrder { RECENT, TITLE }

/** Shelf presentation rules stay independent of Compose and the database's row order. */
internal data class LibraryShelfProjection(
    val books: List<ShelfBookUi>,
    val recentBook: ShelfBookUi?,
)

internal fun projectLibraryShelf(
    books: List<ShelfBookUi>,
    query: String,
    sortOrder: LibrarySortOrder,
    locale: Locale = Locale.getDefault(),
): LibraryShelfProjection {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    val filtered = books.filter { shelfBook ->
        terms.all { term ->
            shelfBook.book.title.contains(term, ignoreCase = true) ||
                shelfBook.book.author.orEmpty().contains(term, ignoreCase = true)
        }
    }
    val comparator = when (sortOrder) {
        LibrarySortOrder.RECENT -> compareByDescending<ShelfBookUi> { it.updatedAt ?: it.book.addedAt }
            .thenByDescending { it.book.addedAt }
            .thenBy { it.book.id }
        LibrarySortOrder.TITLE -> {
            val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
            Comparator<ShelfBookUi> { first, second -> collator.compare(first.book.title, second.book.title) }
                .thenBy { it.book.id }
        }
    }
    return LibraryShelfProjection(
        books = filtered.sortedWith(comparator),
        recentBook = if (terms.isEmpty()) {
            books.asSequence().filter { it.started }
                .maxWithOrNull(compareBy<ShelfBookUi> { it.updatedAt ?: 0L }.thenBy { it.book.id })
        } else null,
    )
}
