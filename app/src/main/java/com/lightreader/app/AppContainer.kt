package com.lightreader.app

import android.content.Context
import com.lightreader.app.core.data.BookRepository
import com.lightreader.app.core.data.DownloadRepository
import com.lightreader.app.feature.download.DownloadWorkScheduler
import com.lightreader.app.core.data.ReaderDatabase
import com.lightreader.app.core.reader.PaintReaderLayoutEngine
import com.lightreader.app.core.reader.ReaderLayoutEngine
import com.lightreader.app.core.security.EncryptedApiKeyStore
import com.lightreader.app.core.settings.ReaderSettingsRepository
import com.lightreader.app.core.settings.AiConfigurationStore
import com.lightreader.app.core.web.DeepSeekAiExtractionProvider
import com.lightreader.app.core.web.GenericHtmlNovelSourceAdapter
import com.lightreader.app.core.web.JsoupWebSourceParser
import com.lightreader.app.core.web.NovelSourceRegistry
import com.lightreader.app.core.web.WebSourceParser
import com.lightreader.app.core.web.WebImportException
import com.lightreader.app.core.web.WebImportFailure
import com.lightreader.app.feature.download.DownloadFeatureGateway
import com.lightreader.app.feature.download.DownloadImportFailure
import com.lightreader.app.feature.download.DownloadPreviewResult
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val database = ReaderDatabase.create(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    val keyStore = EncryptedApiKeyStore(context)
    val aiConfigurationStore = AiConfigurationStore(context)
    val settingsRepository = ReaderSettingsRepository(context)
    val paginationEngine: ReaderLayoutEngine = PaintReaderLayoutEngine()
    val bookRepository = BookRepository(context, database.readerDao())
    val aiProvider = DeepSeekAiExtractionProvider(keyStore, client, aiConfigurationStore)
    private val genericWebSource = JsoupWebSourceParser(client, aiProvider)
    val sourceRegistry = NovelSourceRegistry(listOf(GenericHtmlNovelSourceAdapter(genericWebSource)))
    val webSourceParser: WebSourceParser = sourceRegistry
    val downloadRepository = DownloadRepository(
        context,
        database.readerDao(),
        json,
        webSourceParser,
        scheduler = DownloadWorkScheduler(context),
    )
    val downloadFeatureGateway: DownloadFeatureGateway = object : DownloadFeatureGateway {
        override val tasks = downloadRepository.tasks

        override suspend fun preview(url: String): DownloadPreviewResult = try {
            DownloadPreviewResult.Success(webSourceParser.preview(url))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            DownloadPreviewResult.Failure(error.toDownloadImportFailure())
        }

        override suspend fun start(preview: com.lightreader.app.core.model.WebBookPreview) {
            downloadRepository.start(preview)
        }
        override suspend fun pause(id: String) = downloadRepository.pause(id)
        override suspend fun resume(id: String) = downloadRepository.resume(id)
        override suspend fun cancel(id: String) = downloadRepository.cancel(id)
        override suspend fun delete(id: String) = downloadRepository.delete(id)
        override suspend fun refreshBook(bookId: String): Int = downloadRepository.refreshBook(bookId)
    }
}

private fun Throwable.toDownloadImportFailure(): DownloadImportFailure = when ((this as? WebImportException)?.failure) {
    WebImportFailure.NETWORK -> DownloadImportFailure.NETWORK
    WebImportFailure.HTTP -> DownloadImportFailure.HTTP
    WebImportFailure.RESPONSE_TOO_LARGE -> DownloadImportFailure.RESPONSE_TOO_LARGE
    WebImportFailure.NON_HTML_RESPONSE -> DownloadImportFailure.NON_HTML_RESPONSE
    WebImportFailure.ACCESS_RESTRICTED -> DownloadImportFailure.ACCESS_RESTRICTED
    null -> if (this is IllegalArgumentException) DownloadImportFailure.INVALID_URL else DownloadImportFailure.PARSE
}
