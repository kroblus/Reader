package com.lightreader.app

import android.content.Context
import com.lightreader.app.core.data.BookRepository
import com.lightreader.app.core.data.DownloadRepository
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
    val downloadRepository = DownloadRepository(context, database.readerDao(), json, webSourceParser)
}
