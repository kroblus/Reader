package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.security.EncryptedApiKeyStore
import com.lightreader.app.core.settings.AiConfigurationStore
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
    override suspend fun extract(sourceUrl: String, htmlSample: String): ExtractionPlan {
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
        runCatching { Jsoup.parse(htmlSample).select(plan.chapterLinkSelector) }
            .getOrElse { error("DeepSeek 返回了无效选择器") }
        return plan
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
    }.isSuccess

    private suspend fun execute(body: JsonObject): String = withContext(Dispatchers.IO) {
        val key = keyStore.get() ?: error("请先配置 DeepSeek API Key")
        val configuration = configurationStore.get()
        val request = Request.Builder()
            .url(configuration.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newBuilder().callTimeout(configuration.timeoutSeconds.toLong(), TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("DeepSeek 请求失败：HTTP ${response.code}")
            val root = json.parseToJsonElement(responseBody).jsonObject
            root["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }
    }

    private companion object { val JSON_MEDIA = "application/json; charset=utf-8".toMediaType() }
}
