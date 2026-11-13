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
        val runId = InstrumentationRegistry.getArguments().getString("runId")
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_") ?: "direct-${android.os.Process.myPid()}"
        val safePath = ("$runId/$relativePath").split('/')
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
                // UiAutomation tokenizes the command rather than interpreting shell operators.
                // Keep the sh program in one token; all paths above contain only safe characters.
                val separator = "${'$'}{IFS}"
                val script = "mkdir${separator}-p${separator}$directory&&printf${separator}%s${separator}$encoded|base64${separator}-d>$output&&sha256sum${separator}$output"
                val actual = device.executeShellCommand("sh -c $script").trim()
                val expected = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                check(Regex("^$expected\\s+${Regex.escape(output)}$").matches(actual)) {
                    "QA fallback output was not written and verified: $output; shell result: $actual"
                }
            }.onFailure { fallbackFailure ->
                storageFailure.addSuppressed(fallbackFailure)
                throw storageFailure
            }
            Log.w("LightReaderQaEvidence", "AndroidX Test Storage unavailable; wrote and verified $output", storageFailure)
            output
        }
    }
}
