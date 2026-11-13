package com.lightreader.app

import android.net.Uri
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.lightreader.app.core.formats.ImportedChapter
import com.lightreader.app.journey.FixtureCatalog
import com.lightreader.app.journey.FixtureId
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONObject
import com.lightreader.app.journey.TestEvidenceOutput
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class LargeTxtImportInstrumentedTest {
    @Test
    fun importsOneHundredMegabyteTxtWithBoundedChapterFiles() = runBlocking {
        val context = qaApplication()
        val source = File(context.cacheDir, "large-100mb.txt")
        val block = ("a".repeat(4094) + "\n\n").toByteArray()
        source.outputStream().buffered().use { output ->
            repeat((100 * 1024 * 1024) / block.size) { output.write(block) }
        }
        try {
            importToLibraryAndVerify(context, source, "100mb") { chapters ->
                verifyBody(chapters, 'a', ((100 * 1024 * 1024) / block.size).toLong() * 4094)
                assertTrue(chapters.size > 300)
                assertTrue(chapters.all { it.charCount <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS + block.size })
                assertTrue(chapters.sumOf { it.charCount.toLong() } >= 99L * 1024 * 1024)
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun importsTenMegabyteTxtAndRecordsPerformanceEvidence() = runBlocking {
        val context = qaApplication()
        val source = File(context.cacheDir, "large-10mb.txt")
        val block = ("第1章 性能测试\n" + "山".repeat(4070) + "\n\n").toByteArray()
        source.outputStream().buffered().use { output ->
            repeat((10 * 1024 * 1024) / block.size) { output.write(block) }
        }
        try {
            importToLibraryAndVerify(context, source, "10mb") { chapters ->
                verifyBody(chapters, '山', ((10 * 1024 * 1024) / block.size).toLong() * 4070)
                assertTrue(chapters.isNotEmpty())
                assertTrue(chapters.all { it.charCount <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS + block.size })
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun importsHugeSingleParagraphWithoutCreatingAnOversizedChapter() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "huge-single-paragraph.txt")
        source.writer().buffered().use { output ->
            repeat(2 * 1024) { output.write("山".repeat(1024)) }
        }
        val target = File(context.cacheDir, "huge-single-paragraph-import").apply { deleteRecursively(); mkdirs() }
        try {
            val result = TxtBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
            assertTrue(result.chapters.size > 1)
            assertTrue(result.chapters.all { it.charCount <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS })
            assertTrue(result.chapters.sumOf { it.charCount.toLong() } >= 2L * 1024 * 1024)
        } finally {
            source.delete()
            target.deleteRecursively()
        }
    }

    @Test
    fun importsRepresentativeNestedEpubWithPeakMemoryEvidence() = runBlocking {
        val context = qaApplication()
        val source = FixtureCatalog.materialize(context, FixtureId.EPUB_NESTED_TOC)
        try {
            importToLibraryAndVerify(context, source, "nested-epub") { chapters ->
                assertEquals(1, chapters.size)
                assertEquals("山中修行，自此开始。", chapters.single().file.readText())
                chapters.forEach { assertEquals(it.charCount, it.file.readText().length) }
            }
        } finally { source.delete() }
    }

    private fun qaApplication(): ReaderApplication =
        ApplicationProvider.getApplicationContext<ReaderApplication>().also {
            assertEquals("Library import tests must use the isolated QA package", "com.lightreader.app.qa", it.packageName)
        }

    private suspend fun importToLibraryAndVerify(
        application: ReaderApplication,
        source: File,
        corpus: String,
        verifyContent: (List<ImportedChapter>) -> Unit,
    ) {
        val repository = application.container.bookRepository
        application.container.backupRepository.awaitReady()
        var importedBook: Book? = null
        try {
            val beforePssKb = Debug.getPss()
            val peak = PeakMemorySampler()
            // Include source inspection/fingerprinting, private files, parsing, and the Room commit.
            // Verification and fixture cleanup are deliberately outside this measured interval.
            val elapsedMs = try { measureTimeMillis {
                importedBook = repository.import(Uri.fromFile(source))
            } } finally { peak.close() }
            val afterPssKb = Debug.getPss()
            val book = requireNotNull(importedBook)
            val dao = application.container.database.readerDao()
            val persisted = requireNotNull(dao.book(book.id)) { "Import returned before its book row was committed" }
            assertEquals(book, repository.book(book.id))
            assertTrue("Source fingerprint must be committed", !persisted.contentFingerprint.isNullOrBlank())
            val chapters = repository.chapters(book.id)
            assertTrue("Chapter rows must be committed", chapters.isNotEmpty())
            assertEquals(book.chapterCount, chapters.size)
            assertEquals(persisted.chapterCount, chapters.size)
            assertEquals(book.totalChars, chapters.sumOf { it.charCount.toLong() })
            assertEquals(persisted.totalChars, book.totalChars)
            val root = File(persisted.rootPath).canonicalFile
            assertEquals(File(application.filesDir, "books/${book.id}").canonicalFile, root)
            assertTrue("Imported private directory must exist", root.isDirectory)
            val importedChapters = chapters.mapIndexed { index, chapter ->
                assertTrue("Chapter primary key must be persisted", chapter.id > 0L)
                assertEquals(book.id, chapter.bookId)
                assertEquals(index, chapter.orderIndex)
                val file = File(chapter.contentPath).canonicalFile
                assertTrue("Chapter file must be inside the committed book directory", file.path.startsWith(root.path + File.separator))
                assertTrue("Committed chapter file must exist", file.isFile)
                ImportedChapter(chapter.title, file, chapter.charCount, chapter.sourceUrl)
            }
            verifyContent(importedChapters)
            writePerformanceEvidence(corpus, elapsedMs, beforePssKb, afterPssKb, chapters.size, peak.peak.get(), source)
        } finally {
            importedBook?.let { repository.deleteBook(it.id) }
        }
    }

    private fun verifyBody(chapters: List<ImportedChapter>, expected: Char, expectedCount: Long) {
        var count = 0L
        chapters.forEach { chapter ->
            var characters = 0
            chapter.file.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                while (true) {
                    val length = reader.read(buffer)
                    if (length < 0) break
                    characters += length
                    for (index in 0 until length) {
                        val value = buffer[index]
                        assertTrue("Unexpected or corrupted body character", value == expected || value == '\n')
                        if (value == expected) count++
                    }
                }
            }
            assertEquals(chapter.charCount, characters)
        }
        assertEquals("Import must preserve every source body character", expectedCount, count)
    }

    private class PeakMemorySampler : AutoCloseable {
        val peak = AtomicLong(Debug.getPss())
        private val active = AtomicBoolean(true)
        private val thread = Thread {
            while (active.get()) {
                peak.accumulateAndGet(Debug.getPss(), ::maxOf)
                try { Thread.sleep(50) } catch (_: InterruptedException) { break }
            }
        }.apply { name = "qa-peak-pss"; isDaemon = true; start() }
        override fun close() { active.set(false); thread.interrupt(); thread.join(2000); peak.accumulateAndGet(Debug.getPss(), ::maxOf) }
    }

    private fun writePerformanceEvidence(
        corpus: String,
        elapsedMs: Long,
        beforePssKb: Long,
        afterPssKb: Long,
        chapterCount: Int,
        peakPssKb: Long,
        source: File,
    ) {
        val json = JSONObject()
            .put("corpus", corpus)
            .put("measurement_scope", "library_import")
            .put("pss_sampling_interval_ms", 50)
            .put("sample_count", 1)
            .put("source_bytes", source.length())
            .put("peakPssKb", peakPssKb)
            .put("device", android.os.Build.FINGERPRINT)
            .put("corpus_sha256", source.inputStream().use { input ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            })
            .put("elapsedMs", elapsedMs)
            .put("beforePssKb", beforePssKb)
            .put("afterPssKb", afterPssKb)
            .put("pssDeltaKb", afterPssKb - beforePssKb)
            .put("chapterCount", chapterCount)
        TestEvidenceOutput.writeText(
            "qa-evidence/performance/txt-import-$corpus.json",
            json.toString(2),
        )
    }
}
