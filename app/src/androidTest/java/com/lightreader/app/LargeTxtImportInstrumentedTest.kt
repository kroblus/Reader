package com.lightreader.app

import android.net.Uri
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.core.formats.TxtBookFormatPlugin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "large-100mb.txt")
        val block = ("a".repeat(4094) + "\n\n").toByteArray()
        source.outputStream().buffered().use { output ->
            repeat((100 * 1024 * 1024) / block.size) { output.write(block) }
        }
        val target = File(context.cacheDir, "large-import").apply { deleteRecursively(); mkdirs() }
        try {
            var result: com.lightreader.app.core.formats.ImportResult? = null
            val beforePssKb = Debug.getPss()
            val elapsedMs = measureTimeMillis {
                result = TxtBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
            }
            val imported = requireNotNull(result)
            writePerformanceEvidence("100mb", elapsedMs, beforePssKb, Debug.getPss(), imported.chapters.size)
            assertTrue(imported.chapters.size > 300)
            assertTrue(imported.chapters.all { it.charCount <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS + block.size })
            assertTrue(imported.chapters.sumOf { it.charCount.toLong() } >= 99L * 1024 * 1024)
        } finally {
            source.delete()
            target.deleteRecursively()
        }
    }

    @Test
    fun importsTenMegabyteTxtAndRecordsPerformanceEvidence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "large-10mb.txt")
        val block = ("第1章 性能测试\n" + "山".repeat(4070) + "\n\n").toByteArray()
        source.outputStream().buffered().use { output ->
            repeat((10 * 1024 * 1024) / block.size) { output.write(block) }
        }
        val target = File(context.cacheDir, "large-10mb-import").apply { deleteRecursively(); mkdirs() }
        try {
            var result: com.lightreader.app.core.formats.ImportResult? = null
            val beforePssKb = Debug.getPss()
            val elapsedMs = measureTimeMillis {
                result = TxtBookFormatPlugin().import(context, Uri.fromFile(source), source.name, target)
            }
            val imported = requireNotNull(result)
            writePerformanceEvidence("10mb", elapsedMs, beforePssKb, Debug.getPss(), imported.chapters.size)
            assertTrue(imported.chapters.isNotEmpty())
            assertTrue(imported.chapters.all { it.charCount <= TxtBookFormatPlugin.MAX_CHAPTER_CHARS + block.size })
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

    private fun writePerformanceEvidence(
        corpus: String,
        elapsedMs: Long,
        beforePssKb: Long,
        afterPssKb: Long,
        chapterCount: Int,
    ) {
        val json = JSONObject()
            .put("corpus", corpus)
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
