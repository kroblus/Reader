package com.lightreader.app.core.formats

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.ChapterParser
import com.lightreader.app.core.reader.ReaderTextLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.mozilla.universalchardet.UniversalDetector

class TxtBookFormatPlugin : BookFormatPlugin {
    private val normalizer = BookTextNormalizer()
    private val chapterParser = ChapterParser(normalizer)
    private val cleaner = TxtTextCleaner()

    override fun supports(displayName: String, mimeType: String?): Boolean =
        displayName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"

    override suspend fun import(
        context: Context,
        source: Uri,
        displayName: String,
        bookDirectory: File,
        options: BookImportOptions,
        onProgress: ImportProgressCallback,
    ): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val charset = resolver.openInputStream(source)?.use(::detectCharset)
            ?: throw BookImportException(BookImportFailure.FILE_UNREADABLE, "无法读取文件")
        val chaptersDirectory = File(bookDirectory, "chapters").apply { mkdirs() }
        val chapters = mutableListOf<ImportedChapter>()
        var index = 0
        var title = "正文"
        var writer: BufferedWriter? = null
        var outputFile: File? = null
        var charCount = 0
        var continuation = 1
        var decodedCharacters = 0L
        var replacementCharacters = 0L
        val totalBytes = sourceSize(context, source)
        val importContext = currentCoroutineContext()

        fun openChapter(chapterTitle: String) {
            title = chapterTitle.trim().ifBlank { "正文" }
            outputFile = File(chaptersDirectory, "%05d.txt".format(index++))
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(outputFile!!), StandardCharsets.UTF_8))
            charCount = 0
        }

        fun closeChapter() {
            writer?.flush()
            writer?.close()
            val file = outputFile
            if (file != null && charCount > 0) {
                chapters += ImportedChapter(title, file, charCount)
            } else {
                file?.delete()
                index--
            }
            writer = null
            outputFile = null
        }

        fun rotateChapter() {
            val continuedTitle = "$title（续${continuation++}）"
            closeChapter()
            openChapter(continuedTitle)
        }

        fun appendContent(text: String) {
            var offset = 0
            while (offset < text.length) {
                if (charCount >= MAX_CHAPTER_CHARS - 1) rotateChapter()
                val available = (MAX_CHAPTER_CHARS - charCount - 1).coerceAtLeast(1)
                val end = (offset + available).coerceAtMost(text.length)
                writer?.append(text, offset, end)?.append('\n')
                charCount += end - offset + 1
                offset = end
            }
        }

        openChapter("正文")
        resolver.openInputStream(source)?.use { rawInput ->
            val input = CountingInputStream(rawInput)
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            InputStreamReader(input, decoder).use { reader ->
                reader.forEachBoundedLine(MAX_INPUT_SEGMENT_CHARS) { rawLine ->
                    importContext.ensureActive()
                    decodedCharacters += rawLine.length
                    replacementCharacters += rawLine.count { it == REPLACEMENT_CHAR }
                    val normalized = normalizer.normalizeLine(rawLine)?.text
                    val line = normalized?.let { if (options.cleanTxtNoise) cleaner.clean(it) else it }
                    val chapterHeading = line?.let(chapterParser::chapterTitle)
                    if (chapterHeading != null) {
                        closeChapter()
                        continuation = 1
                        openChapter(chapterHeading)
                    } else if (line != null) {
                        appendContent(line)
                    }
                    val readBytes = input.count
                    val fraction = totalBytes?.takeIf { it > 0L }
                        ?.let { .2f + .68f * (readBytes.toFloat() / it).coerceIn(0f, 1f) }
                        ?: .2f
                    onProgress(ImportProgress(ImportStage.READING, fraction, readBytes, totalBytes))
                }
            }
        } ?: throw BookImportException(BookImportFailure.FILE_UNREADABLE, "无法再次打开文件")
        onProgress(ImportProgress(ImportStage.PARSING, .9f, decodedCharacters, decodedCharacters))
        closeChapter()
        if (replacementCharacters >= MIN_REPLACEMENT_CHARS &&
            replacementCharacters.toDouble() / decodedCharacters.coerceAtLeast(1) > MAX_REPLACEMENT_RATIO
        ) {
            throw BookImportException(BookImportFailure.ENCODING_QUALITY, "TXT 编码质量异常，请转换编码后重试")
        }
        if (chapters.isEmpty()) {
            throw BookImportException(BookImportFailure.EMPTY_CONTENT, "TXT 文件没有可阅读内容")
        }
        ImportResult(safeBaseName(displayName), format = BookFormat.TXT, chapters = chapters)
    }

    companion object {
        const val MAX_CHAPTER_CHARS = ReaderTextLimits.MAX_CHAPTER_CHARS
        internal const val CHARSET_SAMPLE_SIZE = 256 * 1024
        internal const val MAX_INPUT_SEGMENT_CHARS = 32_000
        val CHAPTER_PATTERN = ChapterParser.CHAPTER_PATTERN
        private const val REPLACEMENT_CHAR = '\uFFFD'
        private const val MIN_REPLACEMENT_CHARS = 12
        private const val MAX_REPLACEMENT_RATIO = .005

        internal fun detectCharset(input: InputStream): Charset {
            val output = ByteArrayOutputStream(CHARSET_SAMPLE_SIZE)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (output.size() < CHARSET_SAMPLE_SIZE) {
                val count = input.read(buffer, 0, minOf(buffer.size, CHARSET_SAMPLE_SIZE - output.size()))
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
            return detectCharset(output.toByteArray())
        }

        internal fun detectCharset(sample: ByteArray): Charset {
            val count = sample.size
            if (count >= 3 && sample[0] == 0xEF.toByte() && sample[1] == 0xBB.toByte() && sample[2] == 0xBF.toByte()) {
                return StandardCharsets.UTF_8
            }
            if (count >= 2 && sample[0] == 0xFF.toByte() && sample[1] == 0xFE.toByte()) return StandardCharsets.UTF_16LE
            if (count >= 2 && sample[0] == 0xFE.toByte() && sample[1] == 0xFF.toByte()) return StandardCharsets.UTF_16BE
            detectBomlessUtf16(sample)?.let { return it }
            if (isValidUtf8(sample)) return StandardCharsets.UTF_8

            val detector = UniversalDetector(null)
            detector.handleData(sample, 0, sample.size)
            detector.dataEnd()
            val detected = detector.detectedCharset
            detector.reset()
            if (!detected.isNullOrBlank()) {
                runCatching { Charset.forName(detected) }.getOrNull()?.let { detectedCharset ->
                    val gb18030 = Charset.forName("GB18030")
                    val detectedCjk = decodeStrict(sample, detectedCharset)?.count(::isCjkCharacter) ?: 0
                    val gbCjk = decodeStrict(sample, gb18030)?.count(::isCjkCharacter) ?: 0
                    if (gbCjk >= 2 && gbCjk > detectedCjk * 2) return gb18030
                    return detectedCharset
                }
            }
            return Charset.forName("GB18030")
        }

        private fun isValidUtf8(bytes: ByteArray): Boolean = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
        }.isSuccess

        private fun detectBomlessUtf16(bytes: ByteArray): Charset? {
            if (bytes.size < 8) return null
            val leCjk = decodeStrict(bytes, StandardCharsets.UTF_16LE)?.count(::isCjkCharacter) ?: 0
            val beCjk = decodeStrict(bytes, StandardCharsets.UTF_16BE)?.count(::isCjkCharacter) ?: 0
            if (leCjk >= 2 && leCjk > beCjk * 2) return StandardCharsets.UTF_16LE
            if (beCjk >= 2 && beCjk > leCjk * 2) return StandardCharsets.UTF_16BE

            val pairs = minOf(bytes.size, 4096) / 2
            var evenZeroes = 0
            var oddZeroes = 0
            repeat(pairs) { pair ->
                if (bytes[pair * 2] == 0.toByte()) evenZeroes++
                if (bytes[pair * 2 + 1] == 0.toByte()) oddZeroes++
            }
            val allowedOppositeZeroes = maxOf(1, pairs / 20)
            return when {
                oddZeroes * 10 >= pairs && evenZeroes <= allowedOppositeZeroes -> StandardCharsets.UTF_16LE
                evenZeroes * 10 >= pairs && oddZeroes <= allowedOppositeZeroes -> StandardCharsets.UTF_16BE
                else -> null
            }
        }

        private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

        private fun isCjkCharacter(character: Char): Boolean = character.code in 0x3400..0x9FFF ||
            character.code in 0xF900..0xFAFF
    }
}

private fun sourceSize(context: Context, source: Uri): Long? {
    context.contentResolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0).takeIf { it >= 0 }
    }
    return null
}

private class CountingInputStream(private val delegate: InputStream) : InputStream() {
    var count: Long = 0
        private set

    override fun read(): Int = delegate.read().also { if (it >= 0) count++ }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length).also { if (it > 0) count += it }

    override fun close() = delegate.close()
}

private fun InputStreamReader.forEachBoundedLine(
    maxSegmentChars: Int,
    onLine: (String) -> Unit,
) {
    val buffer = CharArray(DEFAULT_BUFFER_SIZE)
    val line = StringBuilder(maxSegmentChars)
    var previousWasCarriageReturn = false

    fun emit() {
        onLine(line.toString())
        line.clear()
    }

    while (true) {
        val read = read(buffer)
        if (read < 0) break
        for (index in 0 until read) {
            when (val character = buffer[index]) {
                '\r' -> {
                    emit()
                    previousWasCarriageReturn = true
                }
                '\n' -> {
                    if (!previousWasCarriageReturn) emit()
                    previousWasCarriageReturn = false
                }
                else -> {
                    previousWasCarriageReturn = false
                    line.append(character)
                    if (line.length >= maxSegmentChars) emit()
                }
            }
        }
    }
    if (line.isNotEmpty()) emit()
}
