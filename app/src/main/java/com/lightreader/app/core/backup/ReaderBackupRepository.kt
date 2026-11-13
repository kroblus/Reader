package com.lightreader.app.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lightreader.app.core.data.*
import com.lightreader.app.core.settings.ReaderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ReaderBackupRepository(
    private val context: Context,
    private val database: ReaderDatabase,
    private val settings: ReaderSettingsRepository,
    private val availableBytes: () -> Long = { context.filesDir.usableSpace },
) {
    private val dao = database.readerDao()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val mutex = Mutex()
    private val stagingRoot get() = File(context.cacheDir, "manual-backup").apply { mkdirs() }
    private val restoreRoot get() = File(context.filesDir, "restored-books").apply { mkdirs() }
    private val recovery = CoroutineScope(SupervisorJob() + Dispatchers.IO).async(start = CoroutineStart.LAZY) { recoverInterruptedRestores() }
    suspend fun awaitReady() { recovery.await() }

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val archive = File(stagingRoot, "export-${UUID.randomUUID()}.zip")
        try {
            val count = exportTo(archive)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                archive.inputStream().use { copyBounded(it, output, BackupValidator.MAX_TOTAL_BYTES) }
            } ?: error("Cannot open backup destination")
            count
        } finally { archive.delete() }
    }

    /** Snapshot metadata transactionally; immutable chapter files are copied outside the DB transaction. */
    suspend fun exportTo(archive: File): Int = withContext(Dispatchers.IO) {
        awaitReady()
        val files = linkedMapOf<String, File>()
        val snapshots = database.withTransaction {
            dao.observeBooks().first().mapIndexed { index, book ->
                currentCoroutineContext().ensureActive()
                val chapters = dao.chapters(book.id).map { chapter ->
                    val file = File(chapter.contentPath)
                    check(file.isFile && file.length() <= BackupValidator.MAX_ENTRY_BYTES) { "Missing or oversized book content" }
                    val path = "books/$index/chapters/${chapter.id}.txt"
                    files[path] = file
                    BackupChapter(chapter.id, chapter.orderIndex, chapter.title, path, chapter.charCount,
                        chapter.sourceUrl, file.length(), "")
                }
                val progress = dao.progress(book.id)?.let {
                    BackupProgress(it.chapterId, it.charOffset, it.chapterIndex, it.pageIndex, it.chapterTitle, it.styleHash, it.updatedAt)
                }
                val bookmarks = dao.observeBookmarks(book.id).first().map {
                    BackupBookmark(it.id, it.chapterId, it.charOffset, it.excerpt, it.createdAt)
                }
                BackupBook(book.id, book.title, book.author, book.format, book.addedAt, book.lastReadAt,
                    book.sourceUrl, book.contentFingerprint, chapters, progress, bookmarks)
            }
        }
        val operationContext = currentCoroutineContext()
        val books = snapshots.map { book -> book.copy(chapters = book.chapters.map { chapter ->
            chapter.copy(sha256 = BackupValidator.sha256(files.getValue(chapter.path)) { operationContext.ensureActive() })
        }) }
        require(files.size <= BackupValidator.MAX_FILES && files.values.sumOf { it.length() } <= BackupValidator.MAX_TOTAL_BYTES)
        val manifest = BackupManifest(createdAt = System.currentTimeMillis(), books = books, settings = settings.preferences.first())
        archive.parentFile?.mkdirs()
        try {
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                val manifestBytes = json.encodeToString(manifest).toByteArray()
                require(manifestBytes.size <= BackupValidator.MAX_MANIFEST_BYTES)
                zip.write(manifestBytes)
                zip.closeEntry()
                files.forEach { (path, file) ->
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { copyBounded(it, zip, BackupValidator.MAX_ENTRY_BYTES) }
                    zip.closeEntry()
                }
            }
            books.size
        } catch (error: Throwable) { archive.delete(); throw error }
    }

    suspend fun preview(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { preview(it) } ?: error("Cannot open backup")
    }

    suspend fun preview(input: InputStream): RestorePreview = withContext(Dispatchers.IO) {
        awaitReady()
        val token = UUID.randomUUID().toString()
        val stage = File(stagingRoot, token).apply { mkdirs() }
        try {
            val names = linkedSetOf<String>()
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && names.add(entry.name) && names.size <= BackupValidator.MAX_FILES + 1) { "Invalid archive entries" }
                    val destination = BackupValidator.safeFile(stage, entry.name)
                    val limit = if (entry.name == "manifest.json") BackupValidator.MAX_MANIFEST_BYTES else BackupValidator.MAX_ENTRY_BYTES
                    if (entry.size >= 0) require(entry.size <= limit)
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { output ->
                        total += copyBounded(zip, output, minOf(limit, BackupValidator.MAX_TOTAL_BYTES - total), checkSpace = true)
                    }
                    zip.closeEntry()
                }
            }
            val manifestFile = File(stage, "manifest.json")
            require(manifestFile.isFile) { "Missing backup manifest" }
            val manifest = json.decodeFromString<BackupManifest>(manifestFile.readText())
            val operationContext = currentCoroutineContext()
            val invalid = BackupValidator.validate(manifest, stage, names) { operationContext.ensureActive() }
            val required = manifest.books.filter { it.id !in invalid }.flatMap { it.chapters }.sumOf { it.bytes }
            require(availableBytes() > required + 16L * 1024 * 1024) { "Insufficient storage for restore" }
            val existing = dao.observeBooks().first()
            val seenIds = existing.mapTo(hashSetOf()) { it.id }
            val seenFingerprints = existing.mapNotNullTo(hashSetOf()) { it.contentFingerprint }
            val duplicate = hashSetOf<String>()
            manifest.books.filter { it.id !in invalid }.forEach { candidate ->
                if (candidate.id in seenIds || candidate.fingerprint != null && candidate.fingerprint in seenFingerprints) duplicate += candidate.id
                else { seenIds += candidate.id; candidate.fingerprint?.let { seenFingerprints += it } }
            }
            RestorePreview(token, manifest, duplicate, invalid)
        } catch (cancelled: CancellationException) {
            stage.deleteRecursively()
            throw cancelled
        } catch (error: Exception) {
            stage.deleteRecursively()
            throw BackupValidationException(error.message ?: "Invalid backup archive", error)
        }
    }

    /** All accepted books commit in one transaction; private prepared files survive process death.
     * Recovery removes only unreferenced restore-owned directories. Existing library paths are untouched. */
    suspend fun restore(preview: RestorePreview, restoreSettings: Boolean = false): RestoreResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(preview.token.matches(Regex("[a-f0-9-]{36}")))
            val stage = File(stagingRoot, preview.token)
            require(stage.isDirectory) { "Restore preview expired; select the archive again" }
            // Revalidate staged bytes immediately before publish, including all paths and references.
            val names = stage.walkTopDown().filter { it.isFile }.map { it.relativeTo(stage).invariantSeparatorsPath }.toSet()
            val operationContext = currentCoroutineContext()
            val invalid = BackupValidator.validate(preview.manifest, stage, names) { operationContext.ensureActive() }
            require(invalid == preview.invalidBooks) { "Archive changed after preview" }
            val prepared = linkedMapOf<BackupBook, File>()
            var restored = 0
            var skipped = preview.duplicateBookIds.size
            try {
                val required = preview.restorableBooks.flatMap { it.chapters }.sumOf { it.bytes }
                require(availableBytes() > required + 16L * 1024 * 1024) { "Insufficient storage for restore" }
                preview.restorableBooks.forEach { book ->
                    currentCoroutineContext().ensureActive()
                    val directory = File(restoreRoot, UUID.randomUUID().toString()).apply { mkdirs() }
                    prepared[book] = directory
                    book.chapters.forEach { chapter ->
                        val source = BackupValidator.safeFile(stage, chapter.path)
                        val destination = File(directory, "${chapter.id}.txt")
                        FileOutputStream(destination).use { out ->
                            source.inputStream().use { copyBounded(it, out, BackupValidator.MAX_ENTRY_BYTES, checkSpace = true) }
                            out.fd.sync()
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                database.withTransaction {
                    val existing = dao.observeBooks().first()
                    val seenIds = existing.mapTo(hashSetOf()) { it.id }
                    val seenFingerprints = existing.mapNotNullTo(hashSetOf()) { it.contentFingerprint }
                    prepared.forEach { (book, directory) ->
                        currentCoroutineContext().ensureActive()
                        if (book.id in seenIds || book.fingerprint != null && book.fingerprint in seenFingerprints) {
                            skipped++
                        } else {
                            val row = BookEntity(book.id, book.title, book.author, book.format, directory.absolutePath,
                                book.addedAt, book.lastReadAt, book.chapters.sumOf { it.charCount.toLong() }, book.chapters.size, book.sourceUrl, book.fingerprint)
                            dao.insertBook(row)
                            val identity = linkedMapOf<Long, Long>()
                            book.chapters.sortedBy { it.order }.forEach { chapter ->
                                val retainedId = if (dao.chapter(chapter.id) == null) chapter.id else 0L
                                val inserted = dao.insertChapters(listOf(ChapterEntity(retainedId, book.id, chapter.order, chapter.title,
                                    File(directory, "${chapter.id}.txt").absolutePath, chapter.charCount, chapter.sourceUrl))).single()
                                identity[chapter.id] = inserted
                            }
                            book.progress?.let { p ->
                                val chapter = book.chapters.single { it.id == p.chapterId }
                                dao.saveProgress(ReadingProgressEntity(book.id, identity.getValue(p.chapterId), p.charOffset,
                                    chapter.order, p.pageIndex, p.chapterTitle, p.styleHash, p.updatedAt))
                            }
                            book.bookmarks.forEach { mark ->
                                val id = if (dao.bookmark(mark.id) == null) mark.id else UUID.nameUUIDFromBytes("${book.id}:${mark.id}".toByteArray()).toString()
                                check(dao.bookmark(id) == null) { "Bookmark identity conflict" }
                                dao.insertBookmark(BookmarkEntity(id, book.id, identity.getValue(mark.chapterId), mark.charOffset, mark.excerpt, mark.createdAt))
                            }
                            restored++
                            seenIds += book.id
                            book.fingerprint?.let { seenFingerprints += it }
                        }
                    }
                }
                // Optional settings are independent from book progress. Default is to keep local settings.
                val settingsError = if (restoreSettings) withContext(NonCancellable) {
                    try { settings.save(preview.manifest.settings); null }
                    catch (error: Exception) { error.message ?: "Settings could not be restored" }
                } else null
                RestoreResult(restored, skipped, invalid.size, restoreSettings && settingsError == null, settingsError)
            } finally {
                withContext(NonCancellable) {
                    val referenced = dao.bookRootPaths().mapTo(hashSetOf()) { File(it).canonicalPath }
                    prepared.values.filter { it.canonicalPath !in referenced }.forEach { it.deleteRecursively() }
                    stage.deleteRecursively()
                }
            }
        }
    }

    suspend fun discard(preview: RestorePreview?) = withContext(Dispatchers.IO) {
        if (preview != null && preview.token.matches(Regex("[a-f0-9-]{36}"))) File(stagingRoot, preview.token).deleteRecursively()
    }

    suspend fun recoverInterruptedRestores() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val referenced = dao.bookRootPaths().mapTo(hashSetOf()) { File(it).canonicalPath }
            restoreRoot.listFiles()?.filter { it.isDirectory && it.canonicalPath !in referenced }?.forEach { it.deleteRecursively() }
            // Preview caches are intentionally not persisted across process death.
            stagingRoot.listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    private suspend fun copyBounded(input: InputStream, output: OutputStream, limit: Long, checkSpace: Boolean = false): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            copied += n
            require(copied <= limit) { "Archive exceeds size limit" }
            if (checkSpace) require(availableBytes() > n + 1024L * 1024) { "Insufficient storage" }
            output.write(buffer, 0, n)
        }
        return copied
    }
}
