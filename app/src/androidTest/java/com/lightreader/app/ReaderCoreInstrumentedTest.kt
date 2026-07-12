package com.lightreader.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.lightreader.app.core.data.BookRepository
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.data.DownloadRepository
import com.lightreader.app.core.data.DuplicateBookImportException
import com.lightreader.app.core.data.ReaderDatabase
import com.lightreader.app.core.formats.EpubBookFormatPlugin
import com.lightreader.app.core.formats.BookImportException
import com.lightreader.app.core.formats.BookImportFailure
import com.lightreader.app.core.formats.ImportedChapter
import com.lightreader.app.core.formats.ImportStage
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.WebChapter
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.PaintReaderLayoutEngine
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.core.security.EncryptedApiKeyStore
import com.lightreader.app.core.web.WebSourceParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.Charset
import kotlin.system.measureTimeMillis
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderCoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun encryptedApiKeyRoundTripAndDelete() {
        val store = EncryptedApiKeyStore(context)
        store.clear()
        store.save("test-secret-key")
        assertEquals("test-secret-key", store.get())
        store.clear()
        assertEquals(null, store.get())
    }

    @Test
    fun paginationProducesContinuousBoundaries() {
        val text = (1..500).joinToString("\n") { "第 $it 行，这是一段用于分页验证的文字。" }
        val paragraphs = BookTextNormalizer().normalize(text)
        val pages = PaintReaderLayoutEngine().paginate(
            0, "正文", paragraphs, ReaderViewport(720, 1080, 3f, 3f), ReaderPreferences().toReaderStyle(),
        ).pages
        assertTrue(pages.size > 1)
        val bodyLines = pages.flatMap { it.lines }.filterNot { it.isChapterTitle }
        assertEquals(paragraphs.joinToString("") { it.text }, bodyLines.joinToString("") { it.text })
        assertTrue(pages.flatMap { it.lines }.all { it.widthPx <= it.availableWidthPx + .5f })
        assertTrue(bodyLines.filter { it.isFirstLineOfParagraph }.all { it.xOffsetPx > 84f })
    }

    @Test
    fun defaultPaginationUsesUcLikeContentBounds() {
        val text = (1..24).joinToString("\n") {
            "This paragraph is long enough to wrap across multiple measured reader lines for layout bounds verification."
        }
        val paragraphs = BookTextNormalizer().normalize(text)
        val viewport = ReaderViewport(576, 1280, 1f, 1f, 0, 0)
        val engine = PaintReaderLayoutEngine()
        val compactPages = engine.paginate(
            0,
            "正文",
            paragraphs,
            viewport,
            ReaderPreferences(firstLineIndent = false).toReaderStyle(),
        ).pages
        val titleLine = compactPages.first().lines.first { it.isChapterTitle }
        val compactFirstLine = compactPages.first().lines.first { !it.isChapterTitle }

        assertEquals(30f, titleLine.xOffsetPx, .5f)
        assertEquals(30f, compactFirstLine.xOffsetPx, .5f)
        assertTrue(titleLine.baselinePx < compactFirstLine.baselinePx)
        assertTrue(compactPages.flatMap { it.lines }.all { it.baselinePx < viewport.heightPx - 54f })
    }

    @Test
    fun chapterTitleAppearsOnlyOnFirstPageAndDoesNotBreakTextContinuity() {
        val text = (1..80).joinToString("\n") { "正文段落 $it，会跨越多页以验证章节标题只出现在第一页。" }
        val paragraphs = BookTextNormalizer().normalize(text)
        val pages = PaintReaderLayoutEngine().paginate(
            7,
            "第二百三十三章 借力打力",
            paragraphs,
            ReaderViewport(576, 1280, 1f, 1f),
            ReaderPreferences(firstLineIndent = false).toReaderStyle(),
        ).pages

        assertTrue(pages.size > 1)
        assertTrue(pages.first().lines.any { it.isChapterTitle && it.text.contains("第二百三十三章") })
        assertEquals(0, pages.drop(1).flatMap { it.lines }.count { it.isChapterTitle })
        assertEquals(paragraphs.joinToString("") { it.text }, pages.flatMap { it.lines }.filterNot { it.isChapterTitle }.joinToString("") { it.text })
    }

    @Test
    fun existingChapterTitleParagraphIsRenderedOnce() {
        val text = "第二百三十三章 借力打力\n正文第一段。\n正文第二段。"
        val paragraphs = BookTextNormalizer().normalize(text)
        val pages = PaintReaderLayoutEngine().paginate(
            0,
            "第二百三十三章 借力打力",
            paragraphs,
            ReaderViewport(576, 1280, 1f, 1f),
            ReaderPreferences(firstLineIndent = false).toReaderStyle(),
        ).pages
        val titleLines = pages.flatMap { it.lines }.filter { it.isChapterTitle }

        assertEquals(1, titleLines.size)
        assertEquals("第二百三十三章 借力打力", titleLines.single().text)
        assertEquals("正文第一段。正文第二段。", pages.flatMap { it.lines }.filterNot { it.isChapterTitle }.joinToString("") { it.text })
    }

    @Test
    fun largeChapterPaginationCompletesWithContinuousBoundaries() {
        val text = buildString(256_000) {
            while (length < 256_000) append("山中修行，自此开始。天地玄黄，宇宙洪荒。\n")
        }
        lateinit var pages: List<com.lightreader.app.core.model.ReaderPage>
        val elapsed = measureTimeMillis {
            val paragraphs = BookTextNormalizer().normalize(text)
            pages = PaintReaderLayoutEngine().paginate(
                0, "正文", paragraphs, ReaderViewport(720, 1080, 3f, 3f), ReaderPreferences().toReaderStyle(),
            ).pages
        }
        assertTrue("分页耗时 ${elapsed}ms", elapsed < 10_000)
        val normalizedText = BookTextNormalizer().normalize(text).joinToString("") { it.text }
        assertEquals(normalizedText, pages.flatMap { it.lines }.filterNot { it.isChapterTitle }.joinToString("") { it.text })
    }

    @Test
    fun txtImportSplitsChapters() = kotlinx.coroutines.runBlocking {
        val source = File(context.cacheDir, "sample.txt").apply {
            writeText("前言内容\n第一章 入山\n正文一\n第二章 筑基\n正文二")
        }
        val target = File(context.cacheDir, "txt-import").apply { deleteRecursively(); mkdirs() }
        val result = TxtBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
        assertEquals(3, result.chapters.size)
        assertEquals("第一章 入山", result.chapters[1].title)
    }

    @Test
    fun gb18030ImportIsNormalizedAndSearchIndexIsBuiltLazily() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val source = File(context.cacheDir, "legacy-gb18030.txt").apply {
            writeBytes("第一章 入山\n山中修行，自此开始。".toByteArray(Charset.forName("GB18030")))
        }
        try {
            val book = repository.import(Uri.fromFile(source))
            assertEquals(0L, database.readerDao().indexedCharacterCount(book.id))
            val chapter = repository.chapters(book.id).single()
            assertTrue(repository.readChapter(chapter).contains("山中修行"))
            val searchResult = repository.search(book.id, "山中").first()
            assertEquals(repository.readChapter(chapter).indexOf("山中"), searchResult.charOffset)
            assertEquals(book.totalChars, database.readerDao().indexedCharacterCount(book.id))
        } finally {
            database.close()
            source.delete()
        }
    }

    @Test
    fun localImportProgressIsMonotonicAndCancellationLeavesNoBookOrDirectory() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val source = File(context.cacheDir, "cancel-import-${System.nanoTime()}.txt").apply {
            bufferedWriter().use { writer ->
                repeat(60_000) { writer.append("第${it}章 测试\n正文内容用于取消回归。\n") }
            }
        }
        val booksRoot = File(context.filesDir, "books")
        val beforeDirectories = booksRoot.listFiles()?.map { it.name }?.toSet().orEmpty()
        val fractions = mutableListOf<Float>()
        try {
            val candidate = repository.inspectImport(Uri.fromFile(source)) { fractions += it.normalizedFraction }
            val failure = runCatching {
                repository.import(candidate) { progress ->
                    fractions += progress.normalizedFraction
                    if (progress.stage == ImportStage.READING && progress.processed > 256 * 1024) {
                        throw CancellationException("qa cancellation")
                    }
                }
            }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertTrue(fractions.zipWithNext().all { (left, right) -> right >= left })
            assertTrue(database.readerDao().bookRootPaths().isEmpty())
            assertEquals(beforeDirectories, booksRoot.listFiles()?.map { it.name }?.toSet().orEmpty())
        } finally {
            database.close()
            source.delete()
        }
    }

    @Test
    fun duplicateTxtImportIsDetectedByContentFingerprint() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val source = File(context.cacheDir, "duplicate-${System.nanoTime()}.txt").apply {
            writeText("第一章 入山\n山中修行，自此开始。")
        }
        try {
            val imported = repository.import(Uri.fromFile(source))
            val candidate = repository.inspectImport(Uri.fromFile(source))
            assertEquals(imported.id, candidate.existingBook?.id)

            // Passing a candidate is the explicit "keep another copy" path after UI confirmation.
            // Re-importing the URI must instead refuse the duplicate automatically.
            val duplicate = runCatching { repository.import(Uri.fromFile(source)) }.exceptionOrNull() as? DuplicateBookImportException
            assertEquals(imported.id, duplicate?.existingBook?.id)
        } finally {
            database.close()
            source.delete()
        }
    }

    @Test
    fun txtImportHandlesNoChapterEmptyAndCorruptedInputsExplicitly() = kotlinx.coroutines.runBlocking {
        val plugin = TxtBookFormatPlugin()
        val root = File(context.cacheDir, "txt-edge-${System.nanoTime()}").apply { mkdirs() }
        val noChapter = File(root, "no-chapter.txt").apply { writeText("没有章节标题的散文正文。\n仍然可以继续阅读。") }
        val empty = File(root, "empty.txt").apply { writeText("\uFEFF  \n\n") }
        val corrupted = File(root, "corrupted.txt").apply {
            writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + ByteArray(128) { 0xFF.toByte() })
        }
        try {
            val noChapterResult = plugin.import(context, Uri.fromFile(noChapter), noChapter.name, File(root, "no-chapter"))
            assertEquals(1, noChapterResult.chapters.size)
            assertEquals("正文", noChapterResult.chapters.single().title)

            val emptyFailure = runCatching {
                plugin.import(context, Uri.fromFile(empty), empty.name, File(root, "empty"))
            }.exceptionOrNull() as? BookImportException
            assertEquals(BookImportFailure.EMPTY_CONTENT, emptyFailure?.failure)

            val corruptFailure = runCatching {
                plugin.import(context, Uri.fromFile(corrupted), corrupted.name, File(root, "corrupted"))
            }.exceptionOrNull() as? BookImportException
            assertEquals(BookImportFailure.ENCODING_QUALITY, corruptFailure?.failure)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun webImportPersistsSourceUrlsAndAppendRefreshChapters() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val root = File(context.cacheDir, "web-refresh-${System.nanoTime()}").apply { deleteRecursively(); mkdirs() }
        try {
            val first = File(root, "00000.txt").apply { writeText("first web chapter") }
            val second = File(root, "00001.txt").apply { writeText("second web chapter") }
            val third = File(root, "00002.txt").apply { writeText("third web chapter") }

            val book = repository.importDownloadedWebBook(
                id = "web-book",
                title = "Web Book",
                author = "Author",
                sourceUrl = "https://example.test/book",
                rootDirectory = root,
                chapters = listOf(
                    ImportedChapter("Chapter 1", first, first.readText().length, "https://example.test/1"),
                    ImportedChapter("Chapter 2", second, second.readText().length, "https://example.test/2"),
                ),
            )
            assertEquals(BookFormat.WEB, book.format)
            assertEquals(listOf("https://example.test/1", "https://example.test/2"), repository.chapters(book.id).map { it.sourceUrl })

            val added = repository.appendDownloadedWebChapters(
                bookId = book.id,
                chapters = listOf(ImportedChapter("Chapter 3", third, third.readText().length, "https://example.test/3")),
            )

            val updated = repository.book(book.id)!!
            assertEquals(1, added)
            assertEquals(3, updated.chapterCount)
            assertEquals(
                listOf("https://example.test/1", "https://example.test/2", "https://example.test/3"),
                repository.chapters(book.id).map { it.sourceUrl },
            )

            val replayed = repository.appendDownloadedWebChapters(
                bookId = book.id,
                chapters = listOf(ImportedChapter("Chapter 3", third, third.readText().length, "https://example.test/3")),
            )
            assertEquals(0, replayed)
            assertEquals(3, repository.chapters(book.id).size)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun refreshBookQueuesOnlyNewWebChapters() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val parser = object : WebSourceParser {
            override suspend fun preview(url: String): WebBookPreview = WebBookPreview(
                title = "Web Book",
                author = "Author",
                description = null,
                sourceUrl = url,
                finalUrl = url,
                chapters = listOf(
                    WebChapter("Chapter 1", "https://example.test/1"),
                    WebChapter("Chapter 2", "https://example.test/2"),
                    WebChapter("Chapter 3", "https://example.test/3"),
                    WebChapter("Chapter 4", "https://example.test/4"),
                ),
                sample = "sample",
                extractionPlan = ExtractionPlan(contentSelector = "#content"),
            )

            override suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String): String = "body"
        }
        val downloadRepository = DownloadRepository(
            context = context,
            dao = database.readerDao(),
            json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            webSourceParser = parser,
            enqueueWork = false,
        )
        val root = File(context.cacheDir, "web-refresh-task-${System.nanoTime()}").apply { deleteRecursively(); mkdirs() }
        try {
            val first = File(root, "00000.txt").apply { writeText("first") }
            val second = File(root, "00001.txt").apply { writeText("second") }
            val book = repository.importDownloadedWebBook(
                id = "web-refresh-book",
                title = "Web Book",
                author = "Author",
                sourceUrl = "https://example.test/book",
                rootDirectory = root,
                chapters = listOf(
                    ImportedChapter("Chapter 1", first, first.readText().length, "https://example.test/1"),
                    ImportedChapter("Chapter 2", second, second.readText().length, "https://example.test/2"),
                ),
            )

            val count = downloadRepository.refreshBook(book.id)

            val task = database.readerDao().observeDownloadTasks().first().single()
            val queued = database.readerDao().downloadChapters(task.id)
            assertEquals(2, count)
            assertEquals(book.id, task.importedBookId)
            assertEquals(listOf("Chapter 3", "Chapter 4"), queued.map { it.title })
            assertEquals(listOf(2, 3), queued.map { it.orderIndex })
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun readingProgressRoundTripsStableOffsetAndDiagnostics() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val now = System.currentTimeMillis()
        try {
            val ids = database.readerDao().insertBookWithChapters(
                BookEntity("progress-book", "进度测试", null, BookFormat.TXT.name, context.cacheDir.path, now, null, 2000, 1, null),
                listOf(
                    ChapterEntity(
                        bookId = "progress-book",
                        orderIndex = 0,
                        title = "第一章",
                        contentPath = "unused",
                        charCount = 2000,
                        sourceUrl = null,
                    ),
                ),
            )
            repository.saveProgress("progress-book", ids.single(), 1234, 0, 4, "第一章", 42)
            val progress = repository.progress("progress-book")!!
            assertEquals(1234, progress.charOffset)
            assertEquals(4, progress.pageIndex)
            assertEquals("第一章", progress.chapterTitle)
            assertEquals(42, progress.styleHash)
        } finally {
            database.close()
        }
    }

    @Test
    fun importsMinimalEpub() = kotlinx.coroutines.runBlocking {
        val source = File(context.cacheDir, "sample.epub")
        ZipOutputStream(source.outputStream()).use { zip ->
            fun entry(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            entry("META-INF/container.xml", "<container><rootfiles><rootfile full-path='OEBPS/content.opf'/></rootfiles></container>")
            entry("OEBPS/content.opf", "<package><metadata><dc:title>仙途</dc:title></metadata><manifest><item id='c1' href='c1.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='c1'/></spine></package>")
            entry("OEBPS/c1.xhtml", "<html><body><h1>第一章</h1><p>山中修行，自此开始。</p></body></html>")
        }
        val target = File(context.cacheDir, "epub-import").apply { deleteRecursively(); mkdirs() }
        val result = EpubBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
        assertEquals("仙途", result.title)
        assertEquals(1, result.chapters.size)
        assertTrue(result.chapters.first().file.readText().contains("山中修行"))
    }
}
