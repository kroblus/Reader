package com.lightreader.app.core.formats

import android.content.Context
import android.net.Uri
import com.lightreader.app.core.model.BookFormat
import com.lightreader.app.core.reader.BookTextNormalizer
import com.lightreader.app.core.reader.ChapterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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

    override fun supports(displayName: String, mimeType: String?): Boolean =
        displayName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"

    override suspend fun import(
        context: Context,
        source: Uri,
        displayName: String,
        bookDirectory: File,
    ): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val charset = resolver.openInputStream(source)?.use(::detectCharset)
            ?: error("无法读取文件")
        val chaptersDirectory = File(bookDirectory, "chapters").apply { mkdirs() }
        val chapters = mutableListOf<ImportedChapter>()
        var index = 0
        var title = "正文"
        var writer: BufferedWriter? = null
        var outputFile: File? = null
        var charCount = 0
        var continuation = 1

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

        openChapter("正文")
        resolver.openInputStream(source)?.use { input ->
            BufferedReader(InputStreamReader(input, charset), DEFAULT_BUFFER_SIZE).useLines { lines ->
                lines.forEach { rawLine ->
                    val line = normalizer.normalizeLine(rawLine)?.text
                    val chapterHeading = line?.let(chapterParser::chapterTitle)
                    if (chapterHeading != null) {
                        closeChapter()
                        continuation = 1
                        openChapter(chapterHeading)
                    } else if (charCount >= MAX_CHAPTER_CHARS && line == null) {
                        val continuedTitle = "$title（续${continuation++}）"
                        closeChapter()
                        openChapter(continuedTitle)
                    } else if (line != null) {
                        writer?.append(line)?.append('\n')
                        charCount += line.length + 1
                    }
                }
            }
        } ?: error("无法再次打开文件")
        closeChapter()
        if (chapters.isEmpty()) error("TXT 文件没有可阅读内容")
        ImportResult(safeBaseName(displayName), format = BookFormat.TXT, chapters = chapters)
    }

    companion object {
        internal const val MAX_CHAPTER_CHARS = 256_000
        internal const val CHARSET_SAMPLE_SIZE = 256 * 1024
        internal val CHAPTER_PATTERN = ChapterParser.CHAPTER_PATTERN

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
