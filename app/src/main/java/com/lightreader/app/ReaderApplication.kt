package com.lightreader.app

import android.app.Application
import android.content.ComponentCallbacks2
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ReaderApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val mutableMemoryTrimEvents = MutableSharedFlow<Int>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val memoryTrimEvents: SharedFlow<Int> = mutableMemoryTrimEvents.asSharedFlow()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        mutableMemoryTrimEvents.tryEmit(level)
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        mutableMemoryTrimEvents.tryEmit(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
