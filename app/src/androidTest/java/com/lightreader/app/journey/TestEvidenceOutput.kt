package com.lightreader.app.journey

import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.UiDevice

/** Writes Gradle-collected evidence, with a public QA-only fallback for OEMs that omit Test Storage. */
object TestEvidenceOutput {
    private const val FALLBACK_ROOT = "/sdcard/Download/LightReader-QA-Evidence"

    fun writeText(relativePath: String, text: String): String {
        val safePath = relativePath.split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { it.replace(Regex("[^A-Za-z0-9_.-]"), "_") }
        require(safePath.isNotBlank())
        return runCatching {
            PlatformTestStorageRegistry.getInstance().openOutputFile(safePath)
                .bufferedWriter().use { it.write(text) }
            safePath
        }.getOrElse { storageFailure ->
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val parent = safePath.substringBeforeLast('/', missingDelimiterValue = "")
            val directory = if (parent.isBlank()) FALLBACK_ROOT else "$FALLBACK_ROOT/$parent"
            val output = "$FALLBACK_ROOT/$safePath"
            val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            runCatching {
                device.executeShellCommand("mkdir -p '$directory' && echo '$encoded' | base64 -d > '$output'")
            }.onFailure { fallbackFailure ->
                storageFailure.addSuppressed(fallbackFailure)
                throw storageFailure
            }
            Log.w("LightReaderQaEvidence", "AndroidX Test Storage unavailable; wrote $output", storageFailure)
            output
        }
    }
}
