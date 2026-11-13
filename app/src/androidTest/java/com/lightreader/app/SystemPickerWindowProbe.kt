package com.lightreader.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Observes input focus across Reader/SAF without relying on the active accessibility root. */
internal class SystemPickerWindowProbe(private val activity: () -> MainActivity) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val automation = instrumentation.uiAutomation

    init {
        // Initialize before opening SAF. This is a test-service observation flag, not a device setting.
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
    }

    fun awaitAppWindow(beforeSnapshot: () -> Unit = {}) {
        instrumentation.waitForIdleSync()
        await("Reader resumed with window/input focus", beforeSnapshot) { it.appReady }
    }

    fun awaitDocumentsUi(beforeSnapshot: () -> Unit = {}) {
        await("actual DocumentsUI input window", beforeSnapshot) { it.focusedPackage in DOCUMENT_PACKAGES }
    }

    fun documentsUiHasFocus(): Boolean = snapshot().focusedPackage in DOCUMENT_PACKAGES

    fun appHasFocus(): Boolean = snapshot().appReady

    private fun await(description: String, beforeSnapshot: () -> Unit, ready: (Snapshot) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var previous: Snapshot? = null
        do {
            beforeSnapshot()
            // Always read both sides: a missing Activity focus must not short-circuit window evidence.
            val current = snapshot()
            if (current != previous) Log.i(TAG, "$description: $current")
            previous = current
            if (ready(current)) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        val windowManager = runCatching {
            device.executeShellCommand("dumpsys window windows").lineSequence()
                .filter { it.contains("mCurrentFocus") || it.contains("mFocusedApp") ||
                    it.contains("mTopFocusedDisplayId") || it.contains("mObscuringWindow") }
                .joinToString("\n")
        }.getOrElse { "WindowManager observation failed: ${it.javaClass.simpleName}" }
        val message = "Timed out after 5000ms waiting for $description; $previous\n$windowManager"
        Log.e(TAG, message)
        throw AssertionError(message)
    }

    @Suppress("DEPRECATION") // Recycle remains necessary on the supported API 26–32 devices.
    private fun snapshot(): Snapshot {
        var lifecycle = "unknown"
        var resumed = false
        var activityFocus = false
        var attached = false
        var shown = false
        instrumentation.runOnMainSync {
            val current = activity()
            lifecycle = current.lifecycle.currentState.name
            resumed = current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            activityFocus = current.hasWindowFocus()
            attached = current.window.decorView.isAttachedToWindow
            shown = current.window.decorView.isShown
        }
        // currentPackageName queries getRootInActiveWindow (and performs an implicit idle wait).
        // That root may belong to an IME or another recently touched window. Inspect the fresh
        // interactive APPLICATION window with input focus instead, never a background package match.
        val cacheCleared = if (Build.VERSION.SDK_INT >= 34) automation.clearCache() else null
        val windows = automation.windows.map { window ->
            val root = window.root
            try {
                val refreshed = root?.refresh() == true
                val bounds = Rect().also { root?.getBoundsInScreen(it) }
                WindowSnapshot(window.id, window.type, window.isFocused, window.isActive,
                    root?.packageName?.toString(), refreshed,
                    root?.isVisibleToUser == true && !bounds.isEmpty)
            } finally {
                root?.recycle()
                window.recycle()
            }
        }
        return Snapshot(lifecycle, resumed, activityFocus, attached, shown, cacheCleared, windows)
    }

    private data class WindowSnapshot(
        val id: Int,
        val type: Int,
        val focused: Boolean,
        val active: Boolean,
        val packageName: String?,
        val refreshed: Boolean,
        val visible: Boolean,
    )

    private data class Snapshot(
        val lifecycle: String,
        val resumed: Boolean,
        val activityFocus: Boolean,
        val attached: Boolean,
        val shown: Boolean,
        val cacheCleared: Boolean?,
        val windows: List<WindowSnapshot>,
    ) {
        val focusedPackage: String? get() = windows.singleOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.focused && it.refreshed && it.visible
        }?.packageName
        val appReady: Boolean get() = resumed && activityFocus && attached && shown &&
            focusedPackage == BuildConfig.APPLICATION_ID
    }

    private companion object {
        const val TAG = "ReaderWindowProbe"
        val DOCUMENT_PACKAGES = setOf("com.android.documentsui", "com.google.android.documentsui")
    }
}
