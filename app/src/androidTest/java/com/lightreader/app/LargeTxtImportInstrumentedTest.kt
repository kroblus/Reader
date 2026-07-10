package com.lightreader.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LargeTxtImportInstrumentedTest {
    @Test
    fun importsOneHundredMegabyteTxtWithBoundedChapterFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "large-100mb.txt")
        val block = ("a".repeat(4094) + "\n\n").toByteArray()
        source.outputStream().buffered().use { output ->
            repeat((100 * 1024 * 1024) / block.size) { output.write(block) }
        }
        val target = File(context.cacheDir, "large-import").apply { deleteRecursively(); mkdirs() }
        try {
            val result = TxtBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
            assertTrue(result.chapters.size > 300)
            assertTrue(result.chapters.all { it.charCount <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS + block.size })
            assertTrue(result.chapters.sumOf { it.charCount.toLong() } >= 99L * 1024 * 1024)
        } finally {
            source.delete()
            target.deleteRecursively()
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
}
