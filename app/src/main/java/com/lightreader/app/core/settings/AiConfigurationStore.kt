package com.lightreader.app.core.settings

import android.content.Context
import java.net.URI

data class AiConfiguration(
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val timeoutSeconds: Int = 45,
    val enabled: Boolean = false,
)

class AiConfigurationStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_configuration", Context.MODE_PRIVATE)

    fun get(): AiConfiguration = AiConfiguration(
        baseUrl = preferences.getString(BASE_URL, null) ?: "https://api.deepseek.com",
        model = preferences.getString(MODEL, null) ?: "deepseek-chat",
        timeoutSeconds = preferences.getInt(TIMEOUT, 45).coerceIn(10, 120),
        enabled = preferences.getBoolean(ENABLED, false),
    )

    fun save(value: AiConfiguration) {
        val uri = runCatching { URI(value.baseUrl) }.getOrElse { error("API 地址格式不正确") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "API 地址必须是 HTTPS" }
        require(value.model.isNotBlank()) { "模型名称不能为空" }
        require(value.timeoutSeconds in 10..120) { "超时必须在 10 到 120 秒之间" }
        preferences.edit()
            .putString(BASE_URL, value.baseUrl.trimEnd('/'))
            .putString(MODEL, value.model.trim())
            .putInt(TIMEOUT, value.timeoutSeconds)
            .putBoolean(ENABLED, value.enabled)
            .apply()
    }

    private companion object {
        const val BASE_URL = "base_url"
        const val MODEL = "model"
        const val TIMEOUT = "timeout"
        const val ENABLED = "enabled"
    }
}
