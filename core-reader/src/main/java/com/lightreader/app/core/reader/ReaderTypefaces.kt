package com.lightreader.app.core.reader

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Looper
import com.lightreader.app.core.model.FontFamilyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** One source of typefaces for pagination, page drawing and asynchronous UI previews. */
object ReaderTypefaces {
    private data class Variants(val regular: Typeface, val bold: Typeface) {
        fun forWeight(weight: Int): Typeface = if (weight >= 500) bold else regular
    }

    private val platform = mapOf(
        FontFamilyOption.SYSTEM to variants(Typeface.DEFAULT),
        FontFamilyOption.SANS to variants(Typeface.SANS_SERIF),
        FontFamilyOption.SERIF to variants(Typeface.SERIF),
        FontFamilyOption.MONOSPACE to variants(Typeface.MONOSPACE),
    )
    private val bundled = mapOf(
        FontFamilyOption.LXGW_WENKAI to "fonts/lxgw_wenkai_lite_regular.ttf",
        FontFamilyOption.ZHUQUE_FANGSONG to "fonts/zhuque_fangsong_regular.ttf",
    )
    private val loaded = ConcurrentHashMap<FontFamilyOption, Variants>()
    private val pending = ConcurrentHashMap<FontFamilyOption, CompletableFuture<Variants>>()
    private val loader = Executors.newSingleThreadExecutor { work ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            work.run()
        }, "reader-font-loader").apply { isDaemon = true }
    }
    @Volatile private var assets: AssetManager? = null

    /** Schedules asset reads without doing font I/O on the calling/UI thread. */
    fun preload(context: Context) {
        if (assets == null) synchronized(this) {
            if (assets == null) assets = context.applicationContext.assets
        }
        bundled.keys.forEach { requestLoad(it) }
    }

    /** Never reads assets or waits. Null means a bundled font is still loading. */
    fun cachedTypeface(family: FontFamilyOption, weight: Int = 400): Typeface? =
        (platform[family] ?: loaded[family])?.forWeight(weight)

    /** Preview cancellation must not cancel the shared application preload. */
    suspend fun awaitTypeface(family: FontFamilyOption, weight: Int = 400): Typeface {
        cachedTypeface(family, weight)?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            requestLoad(family).whenComplete { faces, failure ->
                if (failure != null) continuation.resumeWithException(failure)
                else continuation.resume(faces.forWeight(weight))
            }
        }
    }

    /** Pagination already runs on its computation dispatcher. It must not publish
     * fallback-font geometry and later draw that geometry with the loaded font. */
    internal fun forLayout(family: FontFamilyOption, weight: Int): Typeface {
        cachedTypeface(family, weight)?.let { return it }
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Bundled reader fonts must be awaited away from the main thread"
        }
        return requestLoad(family).get().forWeight(weight)
    }

    private fun requestLoad(family: FontFamilyOption): CompletableFuture<Variants> =
        pending.computeIfAbsent(family) {
            val path = requireNotNull(bundled[family])
            val manager = checkNotNull(assets) { "Call ReaderTypefaces.preload from Application.onCreate" }
            CompletableFuture.supplyAsync({
                variants(Typeface.createFromAsset(manager, path)).also { loaded[family] = it }
            }, loader)
        }

    private fun variants(regular: Typeface) = Variants(regular, Typeface.create(regular, Typeface.BOLD))
}
