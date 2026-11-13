package com.lightreader.app.journey

import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.UiDevice

/** Keeps the standard collector output and a SHA-verified public QA mirror, including direct runs. */
object TestEvidenceOutput {
    private const val FALLBACK_ROOT = "/sdcard/Download/LightReader-QA-Evidence"

    fun writeText(relativePath: String, text: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "com.lightreader.app.qa") {
            "Public test evidence is restricted to the isolated QA package"
        }
        val runId = InstrumentationRegistry.getArguments().getString("runId")
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_") ?: "direct-${android.os.Process.myPid()}"
        val segments = ("$runId/$relativePath").split('/').filter { it.isNotBlank() }
        require(segments.none { it == "." || it == ".." }) { "Evidence paths must stay inside their run directory" }
        val safePath = segments.joinToString("/") { it.replace(Regex("[^A-Za-z0-9_.-]"), "_") }
        require(safePath.isNotBlank())
        val collector = runCatching {
            PlatformTestStorageRegistry.getInstance().openOutputFile(safePath)
                .bufferedWriter().use { it.write(text) }
        }
        // Some direct-run PlatformTestStorage implementations return a successful no-op stream.
        // A successful collector call therefore cannot replace an independently verified file.
        val device = UiDevice.getInstance(instrumentation)
        val parent = safePath.substringBeforeLast('/', missingDelimiterValue = "")
        val directory = if (parent.isBlank()) FALLBACK_ROOT else "$FALLBACK_ROOT/$parent"
        val output = "$FALLBACK_ROOT/$safePath"
        val encodedFile = "$output.base64-part"
        val bytes = text.toByteArray(Charsets.UTF_8)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val separator = "${'$'}{IFS}"
        try {
            // UiAutomation tokenizes the command rather than interpreting shell operators. Keep
            // each sh program one safe token, and chunk larger reports below shell argument limits.
            device.executeShellCommand("sh -c mkdir${separator}-p${separator}$directory&&:>$encodedFile")
            encoded.chunked(16_384).forEach { chunk ->
                device.executeShellCommand("sh -c printf${separator}%s${separator}$chunk>>$encodedFile")
            }
            val actual = device.executeShellCommand(
                "sh -c base64${separator}-d${separator}$encodedFile>$output&&sha256sum${separator}$output",
            ).trim()
            val expected = java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
            check(Regex("^$expected\\s+${Regex.escape(output)}$").matches(actual)) {
                "QA evidence mirror was not written and verified: $output; shell result: $actual"
            }
        } catch (failure: Throwable) {
            collector.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            runCatching { device.executeShellCommand("rm -f $encodedFile") }.onFailure {
                Log.w("LightReaderQaEvidence", "Could not remove the QA mirror staging file", it)
            }
        }
        collector.exceptionOrNull()?.let {
            Log.w("LightReaderQaEvidence", "Standard collector failed; verified QA mirror remains available", it)
        }
        Log.i("LightReaderQaEvidence", "Wrote and SHA-verified $output; collector_call_succeeded=${collector.isSuccess}")
        return output
    }
}
