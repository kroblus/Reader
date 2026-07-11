package com.lightreader.app.journey

import android.graphics.Bitmap
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONArray
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class EvidenceCollector(private val persona: TestPersona) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val storage = PlatformTestStorageRegistry.getInstance()

    fun capture(journeyId: String, passed: Boolean, steps: List<String>, failure: Throwable? = null): UserJourneyResult {
        val safeName = journeyId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val directory = "qa-evidence/$safeName"
        if (!passed) {
            runCatching {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    storage.openOutputFile("$directory/screen.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            runCatching { storage.openOutputFile("$directory/window.xml").use { device.dumpWindowHierarchy(it) } }
            runCatching {
                storage.openOutputFile("$directory/logcat.txt").bufferedWriter().use {
                    it.write(device.executeShellCommand("logcat -d -t 300"))
                }
            }
        }
        val metadata = JSONObject()
            .put("journeyId", journeyId)
            .put("persona", persona.name)
            .put("passed", passed)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("fingerprint", Build.FINGERPRINT)
            .put("locale", context.resources.configuration.locales[0].toLanguageTag())
            .put("steps", JSONArray(steps))
            .put("failure", failure?.stackTraceToString())
        runCatching {
        TestEvidenceOutput.writeText("$directory/result.json", metadata.toString(2))
        }
        return UserJourneyResult(journeyId, persona, passed, steps, directory)
    }
}

class UserJourneyEvidenceRule(
    private val persona: TestPersona,
    private val steps: MutableList<String> = mutableListOf(),
) : TestWatcher() {
    fun step(value: String) {
        steps += value
    }

    override fun succeeded(description: Description) {
        EvidenceCollector(persona).capture(description.methodName, true, steps.toList())
    }

    override fun failed(error: Throwable, description: Description) {
        EvidenceCollector(persona).capture(description.methodName, false, steps.toList(), error)
    }
}
