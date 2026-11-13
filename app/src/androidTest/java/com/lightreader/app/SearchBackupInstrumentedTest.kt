package com.lightreader.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.backup.*
import com.lightreader.app.core.data.*
import com.lightreader.app.core.settings.ReaderSettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SearchBackupInstrumentedTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var database: ReaderDatabase
    private lateinit var backup: ReaderBackupRepository
    private val dao get() = database.readerDao()

    @Before fun setup() {
        check(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "search-backup-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(base, ReaderDatabase::class.java).build()
        backup = ReaderBackupRepository(context, database, ReaderSettingsRepository(base))
    }
    @After fun cleanup() { database.close(); root.deleteRecursively() }

    @Test fun exactSearchContinuesPast100WithCjkEnglishAndUtf16Anchors() = runBlocking {
        val search = BookSearchRepository(dao)
        for (count in listOf(99, 100, 101, 237)) {
            val id = "matches-$count"
            val source = "😀前缀\n" + "山中 needle\n".repeat(count)
            seed(id, "TXT", listOf(source, "tail needle 山中"))
            for (query in listOf("山中", "needle")) {
                val hits = mutableListOf<com.lightreader.app.core.model.SearchResult>()
                var cursor: SearchCursor? = null
                do {
                    val page = search.page(id, query, cursor, 100)
                    assertEquals(!page.hasMore, page.complete)
                    hits += page.results
                    cursor = page.cursor
                } while (cursor != null)
                assertEquals(count + 1, hits.size)
                assertEquals(hits.size, hits.distinctBy { it.chapterId to it.charOffset }.size)
                hits.forEach { hit ->
                    val text = File(dao.chapter(hit.chapterId)!!.contentPath).readText()
                    assertTrue(text.substring(hit.charOffset).startsWith(query))
                }
            }
        }
    }

    @Test fun ftsBatchesAndExactFallbackDoNotMissCrossChunkOrSubstringMatches() = runBlocking {
        val text = "x".repeat(3998) + "crossboundary" + " needle ".repeat(140) + "unneedleable"
        seed("fts", "TXT", listOf(text, "needle after candidate limit"))
        val repository = BookRepository(context, dao)
        assertEquals(142, repository.search("fts", "needle").size)
        assertEquals(1, repository.search("fts", "crossboundary").size)
        val candidates = mutableListOf<SearchRow>()
        var rowId = 0
        do {
            val batch = dao.searchCandidates("fts", "\"needle\"*", rowId, 1)
            candidates += batch
            rowId = batch.lastOrNull()?.rowId ?: rowId
        } while (batch.isNotEmpty())
        assertTrue(candidates.isNotEmpty())
        assertEquals(candidates.size, candidates.distinctBy { it.rowId }.size)
        val search = BookSearchRepository(dao)
        val first = search.page("fts", "needle", limit = 10)
        assertTrue(first.hasMore)
        val chapter = dao.chapters("fts").first()
        File(chapter.contentPath).appendText(" modified")
        try { search.page("fts", "needle", first.cursor); fail("Old content cursor accepted") }
        catch (_: SearchCursorExpiredException) { }
        try { search.page("fts", "different", first.cursor); fail("Old query cursor accepted") }
        catch (_: SearchCursorExpiredException) { }
    }

    @Test fun freshRestorePreservesTxtEpubWebTextIdsBookmarksAndProgressAndIsIdempotent() = runBlocking {
        for (format in listOf("TXT", "EPUB", "WEB")) seed("backup-$format", format, listOf("标题\n😀正文\n原始  空格", "next\n尾声"))
        val before = dao.observeBooks().first()
        val originalChapters = before.associate { it.id to dao.chapters(it.id) }
        val archive = File(root, "backup.zip")
        assertEquals(3, backup.exportTo(archive))
        val entries = unzip(archive.readBytes())
        val manifestText = entries.getValue("manifest.json").toString(Charsets.UTF_8)
        assertFalse(manifestText.contains(context.filesDir.absolutePath.replace("\\", "\\\\")))
        assertFalse(manifestText.contains("apiKey", ignoreCase = true))
        assertFalse(entries.keys.any { it.contains("downloads") || it.contains("keystore") })
        database.clearAllTables()
        originalChapters.values.flatten().forEach { File(it.contentPath).delete() }
        val preview = backup.preview(archive.inputStream())
        assertEquals(3, preview.restorableBooks.size)
        assertTrue(preview.invalidBooks.isEmpty())
        assertEquals(3, backup.restore(preview).restored)
        before.forEach { book ->
            val chapters = dao.chapters(book.id)
            assertEquals(originalChapters.getValue(book.id).map { it.id }, chapters.map { it.id })
            assertEquals("标题\n😀正文\n原始  空格", File(chapters.first().contentPath).readText())
            assertEquals(5, dao.progress(book.id)!!.charOffset)
            assertEquals("mark-${book.id}", dao.bookmark("mark-${book.id}")!!.id)
            assertEquals(5, dao.bookmark("mark-${book.id}")!!.charOffset)
        }
        val repeat = backup.preview(archive.inputStream())
        assertEquals(3, repeat.duplicateBookIds.size)
        assertEquals(0, backup.restore(repeat).restored)
        assertEquals(3, dao.observeBooks().first().size)
    }

    @Test fun invalidPathsMissingResourcesCorruptionAndSpaceCannotDamageLibrary() = runBlocking {
        seed("keep", "TXT", listOf("protected 😀 text"))
        val archive = File(root, "valid.zip")
        backup.exportTo(archive)
        val entries = unzip(archive.readBytes())
        suspend fun reject(values: Map<String, ByteArray>) {
            try { backup.preview(ByteArrayInputStream(zip(values))); fail("Invalid archive accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, dao.chapters("keep").size)
            assertEquals("protected 😀 text", File(dao.chapters("keep").single().contentPath).readText())
        }
        reject(entries + ("../escape" to byteArrayOf(1)))
        reject(entries.filterKeys { it == "manifest.json" })
        val contentPath = entries.keys.first { it != "manifest.json" }
        val corrupt = entries + (contentPath to "corrupted".toByteArray())
        val preview = backup.preview(ByteArrayInputStream(zip(corrupt)))
        assertEquals(1, preview.invalidBooks.size)
        assertEquals(0, preview.restorableBooks.size)
        backup.discard(preview)
        val lowSpace = ReaderBackupRepository(context, database, ReaderSettingsRepository(context), availableBytes = { 0L })
        try { lowSpace.preview(archive.inputStream()); fail("Low space accepted") }
        catch (_: IllegalArgumentException) { }
        assertNotNull(dao.book("keep"))
    }

    @Test fun interruptedFilePreparationIsRemovedButCommittedRestoreSurvivesRecovery() = runBlocking {
        seed("recover", "TXT", listOf("正文😀恢复"))
        val archive = File(root, "recover.zip")
        backup.exportTo(archive)
        database.clearAllTables()
        backup.restore(backup.preview(archive.inputStream()))
        val restoredPath = dao.chapters("recover").single().contentPath
        val orphan = File(context.filesDir, "restored-books/abandoned").apply { mkdirs() }
        File(orphan, "partial.txt").writeText("partial")
        backup.recoverInterruptedRestores()
        assertFalse(orphan.exists())
        assertTrue(File(restoredPath).exists())
        assertEquals(5, dao.progress("recover")!!.charOffset)
    }

    @Test fun sameArchiveFingerprintDuplicatesAreSkippedAndFailedTransactionRollsBackAllBooks() = runBlocking {
        seed("first", "TXT", listOf("相同原文😀content"))
        seed("second", "TXT", listOf("相同原文😀content"))
        database.openHelper.writableDatabase.execSQL("UPDATE books SET contentFingerprint = 'same-file'")
        val duplicateArchive = File(root, "duplicates.zip")
        backup.exportTo(duplicateArchive)
        database.clearAllTables()
        val duplicatePreview = backup.preview(duplicateArchive.inputStream())
        assertEquals(1, duplicatePreview.restorableBooks.size)
        assertEquals(1, duplicatePreview.duplicateBookIds.size)
        val result = backup.restore(duplicatePreview)
        assertEquals(1, result.restored)
        assertEquals(1, result.skipped)

        database.clearAllTables()
        seed("a", "TXT", listOf("first book body")); seed("b", "TXT", listOf("second book body"))
        val failingArchive = File(root, "transaction.zip")
        backup.exportTo(failingArchive)
        database.clearAllTables()
        seed("keep", "TXT", listOf("keep existing book"))
        val preview = backup.preview(failingArchive.inputStream())
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON books WHEN NEW.id = 'b' BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try { backup.restore(preview); fail("Injected failure did not roll back") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(listOf("keep"), dao.observeBooks().first().map { it.id })
        assertEquals("keep existing book", File(dao.chapters("keep").single().contentPath).readText())
    }

    @Test fun cancelDuringArchiveReadRemovesStageAndKeepsExistingLibrary() = runBlocking {
        seed("keep", "TXT", listOf("existing library body"))
        val archive = File(root, "cancel.zip")
        backup.exportTo(archive)
        val bytes = archive.readBytes()
        val operation = launch {
            val job = currentCoroutineContext()[Job]!!
            val input = object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = super.read(buffer, offset, length)
                    job.cancel()
                    return count
                }
            }
            backup.preview(input)
        }
        operation.join()
        assertTrue(operation.isCancelled)
        assertNotNull(dao.book("keep"))
        assertEquals(0, File(context.cacheDir, "manual-backup").listFiles()?.count { it.isDirectory } ?: 0)
    }

    private suspend fun seed(id: String, format: String, texts: List<String>): List<Long> {
        val directory = File(context.filesDir, "source-$id").apply { mkdirs() }
        val chapters = texts.mapIndexed { i, text ->
            val file = File(directory, "$i.txt").apply { writeText(text) }
            ChapterEntity(bookId = id, orderIndex = i, title = "Chapter $i", contentPath = file.absolutePath,
                charCount = text.length, sourceUrl = if (format == "WEB") "https://example.org/$id/$i" else null)
        }
        val ids = dao.insertBookWithChapters(BookEntity(id, id, "Author", format, directory.absolutePath, 1, 2,
            texts.sumOf { it.length.toLong() }, texts.size, if (format == "WEB") "https://example.org/$id" else null), chapters)
        dao.saveProgress(ReadingProgressEntity(id, ids.first(), 5, 0, 0, "Chapter 0", 0, 42))
        dao.insertBookmark(BookmarkEntity("mark-$id", id, ids.first(), 5, "excerpt", 7))
        return ids
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()); zip.closeEntry() }
        }
    }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() } }
    }.toByteArray()
}
