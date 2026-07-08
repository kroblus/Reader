package com.lightreader.app.core.web

object AccessRestrictionDetector {
    const val MESSAGE = "This site requires browser/security verification. LightReader will not bypass access restrictions."

    fun isBrowserVerification(html: String): Boolean {
        val compact = html.lowercase()
        return BROWSER_VERIFICATION_MARKERS.any { compact.contains(it) } ||
            (compact.contains("window.location.href") && compact.contains("challenge")) ||
            (compact.contains("challenge=") && compact.contains("token")) ||
            (compact.contains("captcha") && compact.contains("verify"))
    }

    private val BROWSER_VERIFICATION_MARKERS = listOf(
        "<title>正在验证浏览器</title>",
        "正在验证浏览器",
        "验证浏览器",
        "正在进行安全验证",
        "安全验证",
        "正在驗證瀏覽器",
        "驗證瀏覽器",
        "正在進行安全驗證",
        "安全驗證",
        "checking your browser",
        "cloudflare",
    )
}
