package com.lightreader.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.lightreader.app.core.data.BookRepository
import com.lightreader.app.core.data.ReaderDatabase
import com.lightreader.app.core.formats.EpubBookFormatPlugin
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.reader.StaticLayoutPaginationEngine
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
        val pages = StaticLayoutPaginationEngine().paginate(text, 720, 1080, 3f, ReaderPreferences())
        assertTrue(pages.size > 1)
        assertEquals(0, pages.first().start)
        assertEquals(text.length, pages.last().endExclusive)
        pages.zipWithNext().forEach { (left, right) -> assertEquals(left.endExclusive, right.start) }
    }

    @Test
    fun largeChapterPaginationCompletesWithContinuousBoundaries() {
        val text = buildString(256_000) {
            while (length < 256_000) append("山中修行，自此开始。天地玄黄，宇宙洪荒。\n")
        }
        lateinit var pages: List<com.lightreader.app.core.model.PageSlice>
        val elapsed = measureTimeMillis {
            pages = StaticLayoutPaginationEngine().paginate(text, 720, 1080, 3f, ReaderPreferences())
        }
        assertTrue("分页耗时 ${elapsed}ms", elapsed < 10_000)
        assertEquals(0, pages.first().start)
        assertEquals(text.length, pages.last().endExclusive)
        pages.zipWithNext().forEach { (left, right) -> assertEquals(left.endExclusive, right.start) }
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
            assertTrue(repository.search(book.id, "第一章").isNotEmpty())
            assertEquals(book.totalChars, database.readerDao().indexedCharacterCount(book.id))
        } finally {
            database.close()
            source.delete()
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
