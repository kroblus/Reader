package com.lightreader.app.core.performance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.lightreader.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/** QA-only structured timings. Metadata must contain counts or booleans, never content or URLs. */
class ReaderEngineMetrics(context: Context) {
    private val enabled = BuildConfig.APPLICATION_ID.endsWith(".qa")
    private val output = File(context.filesDir, "qa-evidence/performance/reader-engine.jsonl")
    private val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun <T> measure(
        operation: String,
        metadata: Map<String, Any> = emptyMap(),
        block: suspend () -> T,
    ): T {
        if (!enabled) return block()
        val started = SystemClock.elapsedRealtimeNanos()
        return try {
            block().also { record(operation, started, true, metadata) }
        } catch (error: Throwable) {
            record(operation, started, false, metadata)
            throw error
        }
    }

    fun record(operation: String, durationMs: Double, metadata: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        write(
            JSONObject()
                .put("operation", operation)
                .put("duration_ms", durationMs)
                .put("success", true)
                .put("timestamp_ms", System.currentTimeMillis())
                .put("metadata", JSONObject(metadata)),
        )
    }

    private fun record(operation: String, startedNanos: Long, success: Boolean, metadata: Map<String, Any>) {
        val durationMs = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0
        write(
            JSONObject()
                .put("operation", operation)
                .put("duration_ms", durationMs)
                .put("success", success)
                .put("timestamp_ms", System.currentTimeMillis())
                .put("metadata", JSONObject(metadata)),
        )
    }

    private fun write(value: JSONObject) {
        Log.i(TAG, value.toString())
        writerScope.launch {
            output.parentFile?.mkdirs()
            synchronized(WRITE_LOCK) { output.appendText(value.toString() + "\n", Charsets.UTF_8) }
        }
    }

    private companion object {
        const val TAG = "ReaderEngineMetric"
        val WRITE_LOCK = Any()
    }
}
