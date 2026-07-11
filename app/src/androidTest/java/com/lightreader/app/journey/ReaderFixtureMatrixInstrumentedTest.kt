package com.lightreader.app.journey

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.BuildConfig
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.formats.EpubBookFormatPlugin
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderFixtureMatrixInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: ReaderApplication = context as ReaderApplication

    @Test
    fun importsLegacyAndMixedTextFixturesAsReadableBoundedChapters() = runBlocking {
        val fixtures = listOf(
            FixtureId.UTF8_TXT,
            FixtureId.UTF16_TXT,
            FixtureId.GB18030_TXT,
            FixtureId.BIG5_TXT,
            FixtureId.MIXED_LANGUAGE_TXT,
            FixtureId.NO_CHAPTER_TXT,
        )
        fixtures.forEach { fixtureId ->
            val source = FixtureCatalog.materialize(context, fixtureId)
            val target = File(context.cacheDir, "matrix-${fixtureId.name}").apply { deleteRecursively(); mkdirs() }
            try {
                val result = TxtBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
                assertTrue("$fixtureId should create readable chapters", result.chapters.isNotEmpty())
                assertTrue(result.chapters.all { it.charCount in 1..TxtBookFormatPlugin.MAX_CHAPTER_CHARS })
                val text = result.chapters.joinToString("\n") { it.file.readText() }
                assertTrue("$fixtureId should not decode mostly as replacement characters", text.count { it == '\uFFFD' } < 3)
            } finally {
                target.deleteRecursively()
            }
        }
    }

    @Test
    fun epubFixtureMatrixAcceptsReadableBooksAndRejectsUnsafePackages() = runBlocking {
        listOf(FixtureId.EPUB_BASIC, FixtureId.EPUB_NESTED_TOC).forEach { fixtureId ->
            val source = FixtureCatalog.materialize(context, fixtureId)
            val target = File(context.cacheDir, "matrix-${fixtureId.name}").apply { deleteRecursively(); mkdirs() }
            try {
                val result = EpubBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
                assertTrue(result.chapters.isNotEmpty())
                assertTrue(result.chapters.first().file.readText().contains("山中修行"))
            } finally {
                target.deleteRecursively()
            }
        }

        listOf(FixtureId.EPUB_EMPTY, FixtureId.EPUB_ENCRYPTED, FixtureId.EPUB_ZIP_SLIP, FixtureId.EPUB_CORRUPT).forEach { fixtureId ->
            val source = FixtureCatalog.materialize(context, fixtureId)
            val target = File(context.cacheDir, "matrix-reject-${fixtureId.name}").apply { deleteRecursively(); mkdirs() }
            try {
                assertTrue("$fixtureId must be rejected", runCatching {
                    EpubBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
                }.isFailure)
                assertFalse(File(target.parentFile, "escaped.txt").exists())
            } finally {
                target.deleteRecursively()
            }
        }
    }

    @Test
    fun deletingBookRemovesProgressBookmarksSearchIndexAndPrivateFiles() = runBlocking {
        application.container.database.clearAllTables()
        val source = FixtureCatalog.materialize(context, FixtureId.UTF8_TXT)
        val book = application.container.bookRepository.import(Uri.fromFile(source))
        val chapter = application.container.bookRepository.chapters(book.id).first()
        application.container.bookRepository.saveProgress(
            bookId = book.id,
            chapterId = chapter.id,
            charOffset = 12,
            chapterIndex = chapter.orderIndex,
            pageIndex = 0,
            chapterTitle = chapter.title,
            styleHash = 0,
        )
        application.container.bookRepository.addBookmark(book.id, chapter.id, 12, "QA bookmark")
        application.container.bookRepository.search(book.id, "山中")
        val root = File(requireNotNull(application.container.database.readerDao().book(book.id)).rootPath)
        assertTrue(root.exists())

        application.container.bookRepository.deleteBook(book.id)

        assertNull(application.container.database.readerDao().book(book.id))
        assertNull(application.container.database.readerDao().progress(book.id))
        assertTrue(application.container.bookRepository.bookmarks(book.id).first().isEmpty())
        assertEquals(0L, application.container.database.readerDao().indexedCharacterCount(book.id))
        assertFalse(root.exists())
    }

    @Test
    fun qaManifestKeepsBackupAndCleartextDisabledAndKeyOutOfPlaintextFiles() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".qa"))
        val info = context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)

        val secret = "qa-secret-that-must-not-appear-in-plaintext"
        application.container.keyStore.save(secret)
        val files = File(context.applicationInfo.dataDir).walkTopDown()
            .filter { it.isFile && it.length() < 1024 * 1024 }
            .toList()
        assertFalse(files.any { file -> runCatching { file.readText().contains(secret) }.getOrDefault(false) })
        application.container.keyStore.clear()
    }
}
