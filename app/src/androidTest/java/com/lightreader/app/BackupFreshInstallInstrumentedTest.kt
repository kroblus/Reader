package com.lightreader.app

import android.net.Uri
import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lightreader.app.core.backup.BackupManifest
import com.lightreader.app.core.data.BookmarkEntity
import com.lightreader.app.core.data.ReadingProgressEntity
import com.lightreader.app.core.formats.ImportedChapter
import com.lightreader.app.core.model.AppLanguage
import com.lightreader.app.core.model.AppSkin
import com.lightreader.app.core.model.ReaderLayoutPreset
import com.lightreader.app.core.model.ReaderPreferences
import com.lightreader.app.core.model.ReaderTheme
import com.lightreader.app.core.settings.AiConfiguration
import com.lightreader.app.ui.ReaderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Separate invocations with an external QA uninstall/install between them.
 * Exclude this class from generic suites; scripts/run-backup-fresh-install.py owns both phases.
 * Only synthetic books and a deliberately fake key are created. No network request is made. */
@RunWith(AndroidJUnit4::class)
class BackupFreshInstallInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as ReaderApplication
    private val container get() = app.container
    private val dao get() = container.database.readerDao()
    private val json = Json { encodeDefaults = true }
    private val runId: String get() = requireNotNull(
        InstrumentationRegistry.getArguments().getString("freshInstallRunId"),
    ) { "This class requires the dedicated fresh-install script, not a generic suite." }
        .also { require(it.matches(Regex("[A-Za-z0-9_-]{8,80}"))) }
    private val evidence get() = File(app.filesDir, "backup-fresh-install/$runId").apply { mkdirs() }
    private val sentinel get() = File(app.filesDir, "fresh-install-private-sentinel.txt")

    @Test fun prepareArchiveForFreshInstall() = runBlocking {
        requireQa()
        container.backupRepository.awaitReady()
        // This fixture owns the isolated QA package only. Never run against the production package.
        container.database.clearAllTables()
        container.keyStore.clear()
        val source = File(evidence, "sources").apply { mkdirs() }
        val txt = File(source, "Fresh TXT.txt").apply {
            writeText("第一章 起点\n" + paragraph("TXT", 1).repeat(45) + "\n第二章 终点\n" + paragraph("TXT", 2).repeat(45))
        }
        val epub = File(source, "Fresh EPUB.epub").also(::writeEpub)
        val importedTxt = container.bookRepository.import(Uri.fromFile(txt))
        val importedEpub = container.bookRepository.import(Uri.fromFile(epub))
        val webRoot = File(app.filesDir, "books/fresh-web-$runId").apply { mkdirs() }
        val webChapters = (1..2).map { index ->
            val body = "网页章节 $index\n" + paragraph("WEB", index).repeat(45)
            ImportedChapter("Web chapter $index", File(webRoot, "$index.txt").apply { writeText(body) }, body.length,
                "https://example.invalid/Chapter/$index?Case=Upper&keep=1")
        }
        val importedWeb = container.bookRepository.importDownloadedWebBook("fresh-web-$runId", "Fresh WEB", "QA author",
            "https://example.invalid/catalog", webRoot, webChapters)
        val imported = listOf(importedTxt, importedEpub, importedWeb)
        assertEquals(setOf("TXT", "EPUB", "WEB"), imported.map { it.format.name }.toSet())
        imported.forEach { book ->
            val chapter = dao.chapters(book.id).last()
            val text = File(chapter.contentPath).readText()
            val emoji = text.indexOf("😀")
            assertTrue("Every source contains a surrogate-pair anchor", emoji >= 0)
            val offset = emoji + "😀".length
            assertEquals(2, "😀".length)
            assertTrue(Character.isHighSurrogate(text[offset - 2]))
            assertTrue(Character.isLowSurrogate(text[offset - 1]))
            dao.saveProgress(ReadingProgressEntity(book.id, chapter.id, offset, chapter.orderIndex, 0, chapter.title, 77, 123456789))
            dao.updateLastReadAt(book.id, 123456789)
            dao.insertBookmark(BookmarkEntity("fresh-mark-${book.id}", book.id, chapter.id, offset, "😀 UTF-16 bookmark", 123456700))
        }
        container.settingsRepository.save(ReaderPreferences(layoutPreset = ReaderLayoutPreset.CUSTOM,
            fontSizeSp = 23f, lineSpacingMultiplier = 1.65f, theme = ReaderTheme.SEPIA,
            appLanguage = AppLanguage.EN, appSkin = AppSkin.OCEAN))
        container.keyStore.save(FAKE_KEY)
        container.aiConfigurationStore.save(AiConfiguration(baseUrl = "https://example.invalid", model = "qa-no-network", enabled = true))
        assertTrue(container.keyStore.hasKey())
        assertTrue(container.aiConfigurationStore.get().enabled)
        sentinel.writeText("This private file must disappear on uninstall: $runId")
        val archive = File(evidence, ARCHIVE)
        assertEquals(3, container.backupRepository.exportTo(archive))
        val manifestText = ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            assertTrue(entries.all { it.name == "manifest.json" || it.name.startsWith("books/") })
            zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() }
        }
        assertFalse(manifestText.contains(FAKE_KEY))
        assertFalse(manifestText.contains("ciphertext", ignoreCase = true))
        assertFalse(manifestText.contains("keystore", ignoreCase = true))
        assertFalse(manifestText.contains(app.filesDir.absolutePath))
        val manifest = json.decodeFromString<BackupManifest>(manifestText)
        assertEquals(1, manifest.archiveVersion)
        val bodies = JSONObject()
        val oldPaths = JSONObject()
        manifest.books.forEach { book -> dao.chapters(book.id).forEach { chapter ->
            bodies.put(chapter.id.toString(), File(chapter.contentPath).readText())
            oldPaths.put(chapter.id.toString(), chapter.contentPath)
        } }
        File(evidence, EXPECTED).writeText(JSONObject().put("runId", runId).put("preparePid", Process.myPid())
            .put("archiveSha256", sha256(archive)).put("manifest", JSONObject(manifestText))
            .put("chapterBodies", bodies).put("oldPaths", oldPaths).toString())
        File(evidence, "prepare-result.json").writeText(JSONObject().put("runId", runId).put("books", 3)
            .put("formats", "TXT,EPUB,WEB").put("fakeKeyConfigured", true).put("archiveSha256", sha256(archive))
            .put("phase", "prepare").put("passed", true).toString())
    }

    @Test fun verifyRestoreInFreshInstall() {
        requireQa()
        val expected = JSONObject(File(evidence, EXPECTED).readText())
        val manifest = json.decodeFromString<BackupManifest>(expected.getJSONObject("manifest").toString())
        assertEquals(runId, expected.getString("runId"))
        assertFalse("The old private installation marker must be gone", sentinel.exists())
        val archive = File(evidence, ARCHIVE)
        assertEquals(expected.getString("archiveSha256"), sha256(archive))
        runBlocking {
            container.backupRepository.awaitReady()
            assertTrue("Restore must start from an empty installed database", dao.observeBooks().first().isEmpty())
            assertTrue(dao.downloadTasks().isEmpty())
            assertFalse("A fresh installation has no API key", container.keyStore.hasKey())
            assertFalse(container.aiConfigurationStore.get().enabled)
            // A fresh DataStore uses the repository's existing 18sp default, not
            // ReaderPreferences' 17sp constructor fallback or the archived 23sp.
            assertEquals(18f, container.settingsRepository.preferences.first().fontSizeSp, 0f)
            val preview = container.backupRepository.preview(Uri.fromFile(archive))
            assertEquals(3, preview.restorableBooks.size)
            assertTrue(preview.duplicateBookIds.isEmpty())
            assertTrue(preview.invalidBooks.isEmpty())
            val result = container.backupRepository.restore(preview, restoreSettings = true)
            assertEquals(3, result.restored)
            assertEquals(0, result.skipped)
            assertEquals(0, result.invalid)
            assertTrue(result.settingsRestored)
            assertNull(result.settingsError)
            assertEquals(manifest.settings, container.settingsRepository.preferences.first())
            manifest.books.forEach { book ->
                val restored = requireNotNull(dao.book(book.id))
                assertEquals(book.title, restored.title)
                assertEquals(book.author, restored.author)
                assertEquals(book.format, restored.format)
                assertEquals(book.sourceUrl, restored.sourceUrl)
                assertEquals(book.fingerprint, restored.contentFingerprint)
                assertEquals(book.chapters.size, restored.chapterCount)
                assertEquals(book.chapters.sumOf { it.charCount.toLong() }, restored.totalChars)
                val chapters = dao.chapters(book.id)
                assertEquals(book.chapters.map { it.id }, chapters.map { it.id })
                chapters.forEach { chapter ->
                    assertNotEquals(expected.getJSONObject("oldPaths").getString(chapter.id.toString()), chapter.contentPath)
                    assertTrue(File(chapter.contentPath).canonicalPath.startsWith(File(app.filesDir, "restored-books").canonicalPath + File.separator))
                    val actual = container.bookRepository.readChapter(requireNotNull(container.bookRepository.chapter(chapter.id)))
                    assertEquals(expected.getJSONObject("chapterBodies").getString(chapter.id.toString()), actual)
                    assertEquals(chapter.charCount, actual.length)
                    assertEquals(book.chapters.single { it.id == chapter.id }.sourceUrl, chapter.sourceUrl)
                }
                val progress = requireNotNull(dao.progress(book.id))
                val original = requireNotNull(book.progress)
                assertEquals(original.chapterId, progress.chapterId)
                assertEquals(original.charOffset, progress.charOffset)
                assertEquals(original.updatedAt, progress.updatedAt)
                val body = File(dao.chapter(progress.chapterId)!!.contentPath).readText()
                assertEquals("😀", body.substring(progress.charOffset - 2, progress.charOffset))
                book.bookmarks.forEach { mark ->
                    val restoredMark = requireNotNull(dao.bookmark(mark.id))
                    assertEquals(book.id, restoredMark.bookId)
                    assertEquals(mark.chapterId, restoredMark.chapterId)
                    assertEquals(mark.charOffset, restoredMark.charOffset)
                    assertEquals(mark.createdAt, restoredMark.createdAt)
                    assertEquals(mark.excerpt, restoredMark.excerpt)
                }
            }
            val repeatedPreview = container.backupRepository.preview(Uri.fromFile(archive))
            assertEquals(3, repeatedPreview.duplicateBookIds.size)
            val repeated = container.backupRepository.restore(repeatedPreview, restoreSettings = true)
            assertEquals(0, repeated.restored)
            assertEquals(3, repeated.skipped)
            assertEquals(3, dao.observeBooks().first().size)
            manifest.books.forEach { book -> assertEquals(book.progress!!.charOffset, dao.progress(book.id)!!.charOffset) }
            assertFalse("Backup must never restore the fake credential", container.keyStore.hasKey())
            assertFalse(container.aiConfigurationStore.get().enabled)
        }
        val vm = ViewModelProvider(compose.activity)[ReaderViewModel::class.java]
        manifest.books.forEach { book ->
            compose.runOnIdle { vm.openBook(book.id) }
            compose.waitUntil(20_000) {
                vm.readerState.value.book?.id == book.id && !vm.readerState.value.loading && vm.readerState.value.pages.isNotEmpty()
            }
            val state = vm.readerState.value
            assertNull(state.error)
            val progress = requireNotNull(book.progress)
            assertEquals(progress.chapterId, state.chapters[state.chapterIndex].id)
            val page = state.pages[state.pageIndex]
            assertTrue("Restored UTF-16 anchor must be inside its readable page",
                progress.charOffset >= page.startOffset && progress.charOffset <= page.endOffset)
        }
        File(evidence, "verify-result.json").writeText(JSONObject().put("runId", runId).put("books", 3)
            .put("formats", "TXT,EPUB,WEB").put("readerBooksOpened", 3).put("duplicateRestoreCount", 0)
            .put("aiConfigured", false).put("archiveSha256", sha256(archive)).put("phase", "verify")
            .put("passed", true).put("pid", Process.myPid()).toString())
    }

    private fun requireQa() {
        check(BuildConfig.APPLICATION_ID == QA_PACKAGE && app.packageName == QA_PACKAGE) { "This test is restricted to the isolated QA package." }
        runId // Validate the dedicated orchestration argument before any reset or fixture write.
    }

    private fun writeEpub(file: File) {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">fresh-qa</dc:identifier><dc:title>Fresh EPUB</dc:title><dc:creator>QA author</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
        )
        (1..2).forEach { index -> entries["OEBPS/chapter$index.xhtml"] =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter $index</title></head><body><h1>EPUB chapter $index</h1>" +
                (1..45).joinToString("") { "<p>${paragraph("EPUB", index)}</p>" } + "</body></html>" }
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, body) ->
            zip.putNextEntry(ZipEntry(path)); zip.write(body.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        } }
    }

    private fun paragraph(format: String, index: Int) = "$format $index 原文😀 UTF-16 e\u0301 保留字符位置与空格。The reader opens restored local content without AI.\n"
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val QA_PACKAGE = "com.lightreader.app.qa"
        const val ARCHIVE = "reader-backup.zip"
        const val EXPECTED = "expected.json"
        const val FAKE_KEY = "qa-fake-backup-key-not-a-provider-credential"
    }
}
