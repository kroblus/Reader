package com.lightreader.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.lightreader.app.core.data.BookRepository
import com.lightreader.app.core.data.BookEntity
import com.lightreader.app.core.data.ChapterEntity
import com.lightreader.app.core.data.ReaderDatabase
import com.lightreader.app.core.formats.EpubBookFormatPlugin
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderViewport
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.PaintReaderLayoutEngine
import com.lightreader.app.core.reader.toReaderStyle
import com.lightreader.app.core.security.EncryptedApiKeyStore
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
        assertEquals(paragraphs.joinToString("") { it.text }, pages.flatMap { it.lines }.joinToString("") { it.text })
        assertTrue(pages.flatMap { it.lines }.all { it.widthPx <= it.availableWidthPx + .5f })
        assertTrue(pages.flatMap { it.lines }.filter { it.isFirstLineOfParagraph }.all { it.xOffsetPx > 84f })
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
        assertEquals(normalizedText, pages.flatMap { it.lines }.joinToString("") { it.text })
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
    fun readingProgressRoundTripsStableOffsetAndDiagnostics() = kotlinx.coroutines.runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val repository = BookRepository(context, database.readerDao())
        val now = System.currentTimeMillis()
        try {
            val ids = database.readerDao().insertBookWithChapters(
                BookEntity("progress-book", "进度测试", null, BookFormat.TXT.name, context.cacheDir.path, now, null, 2000, 1, null),
                listOf(ChapterEntity(bookId = "progress-book", orderIndex = 0, title = "第一章", contentPath = "unused", charCount = 2000)),
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
