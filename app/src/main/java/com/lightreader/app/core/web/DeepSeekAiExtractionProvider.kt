package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.security.EncryptedApiKeyStore
import com.lightreader.app.core.settings.AiConfigurationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

interface AiExtractionProvider {
    suspend fun extract(sourceUrl: String, htmlSample: String): ExtractionPlan
    suspend fun testConnection(): Boolean
}

class DeepSeekAiExtractionProvider(
    private val keyStore: EncryptedApiKeyStore,
    private val client: OkHttpClient,
    private val configurationStore: AiConfigurationStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AiExtractionProvider {
    override suspend fun extract(sourceUrl: String, htmlSample: String): ExtractionPlan = try {
        if (!configurationStore.get().enabled) throw WebImportException(WebImportFailure.AI_CONFIGURATION,
            "AI fallback is disabled. Enable it in API settings to use DeepSeek.")
        if (!keyStore.hasKey()) throw WebImportException(WebImportFailure.AI_CONFIGURATION,
            "AI fallback has no API key. Save a key in API settings first.")
        val cleaned = Jsoup.parse(htmlSample).apply { select("script,style,svg,iframe,noscript").remove() }.html().take(50_000)
        val prompt = """
            分析以下用户有权访问的小说网页 HTML。只返回 JSON，不要下载网页，不要返回正文。
            JSON 字段必须是 titleSelector、chapterLinkSelector、contentSelector、removeSelectors。
            选择器必须是简单且稳定的 CSS selector；removeSelectors 是字符串数组。
            URL: $sourceUrl
            HTML: $cleaned
        """.trimIndent()
        val body = buildJsonObject {
            put("model", JsonPrimitive(configurationStore.get().model))
            put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
            put("temperature", JsonPrimitive(0))
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(prompt))
                })
            })
        }
        val content = execute(body)
        val normalized = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val plan = json.decodeFromString<ExtractionPlan>(normalized)
        require(plan.chapterLinkSelector.isNotBlank() && plan.contentSelector.isNotBlank()) {
            "DeepSeek 未返回可用的网页选择器"
        }
        runCatching {
            val document = Jsoup.parse(htmlSample)
            document.select(plan.chapterLinkSelector)
            document.select(plan.contentSelector)
            plan.removeSelectors.filter(String::isNotBlank).forEach { document.select(it) }
        }
            .getOrElse { error("DeepSeek 返回了无效选择器") }
        plan
    } catch (error: CancellationException) {
        throw error
    } catch (error: WebImportException) {
        throw error
    } catch (error: Exception) {
        throw WebImportException(WebImportFailure.AI_FAILURE, "AI could not return a usable extraction plan.", error)
    }

    override suspend fun testConnection(): Boolean = runCatching {
        execute(buildJsonObject {
            put("model", JsonPrimitive(configurationStore.get().model))
            put("max_tokens", JsonPrimitive(4))
            put("messages", JsonArray(listOf(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("只回答 OK"))
            })))
        })
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        return false
    }.let { true }

    private suspend fun execute(body: JsonObject): String = withContext(Dispatchers.IO) {
        val key = keyStore.get() ?: throw WebImportException(WebImportFailure.AI_CONFIGURATION, "AI fallback has no API key.")
        val configuration = configurationStore.get()
        val request = Request.Builder()
            .url(configuration.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newBuilder().callTimeout(configuration.timeoutSeconds.toLong(), TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build().newCall(request).readCancellable { response ->
            if (!response.isSuccessful) throw WebImportException(WebImportFailure.AI_FAILURE, "DeepSeek request failed: HTTP ${response.code}", httpStatus = response.code)
            val bodySource = response.body?.source() ?: error("DeepSeek response was empty")
            bodySource.request(1024 * 1024L + 1)
            require(bodySource.buffer.size <= 1024 * 1024L) { "DeepSeek response was too large" }
            val responseBody = bodySource.readUtf8()
            val root = json.parseToJsonElement(responseBody).jsonObject
            root["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }
    }

    private companion object { val JSON_MEDIA = "application/json; charset=utf-8".toMediaType() }
}
