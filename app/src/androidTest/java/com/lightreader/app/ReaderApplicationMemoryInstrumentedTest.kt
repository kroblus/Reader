package com.lightreader.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderApplicationMemoryInstrumentedTest {
    @Test
    fun forwardsSystemMemoryTrimEventsToLongLivedReaderState() = runBlocking {
        val application = androidx.test.core.app.ApplicationProvider.getApplicationContext<ReaderApplication>()
        val event = async(start = CoroutineStart.UNDISPATCHED) { application.memoryTrimEvents.first() }

        application.onTrimMemory(42)

        assertEquals(42, event.await())
    }
}
