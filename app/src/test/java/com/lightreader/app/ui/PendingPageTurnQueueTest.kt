package com.lightreader.app.ui

import com.lightreader.app.feature.reader.ReaderPageTurnQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageTurnQueueIntegrationTest {
    @Test
    fun doubleNextTapIsDeliveredAsTwoQueuedTurns() {
        val queue = ReaderPageTurnQueue()

        queue.enqueue(next = true)
        queue.enqueue(next = true)

        assertEquals(2, queue.pendingDelta)
        assertTrue(queue.poll()!!)
        assertTrue(queue.poll()!!)
        assertNull(queue.poll())
    }

    @Test
    fun doublePreviousTapIsDeliveredAsTwoQueuedTurns() {
        val queue = ReaderPageTurnQueue()

        queue.enqueue(next = false)
        queue.enqueue(next = false)

        assertEquals(-2, queue.pendingDelta)
        assertFalse(queue.poll()!!)
        assertFalse(queue.poll()!!)
        assertNull(queue.poll())
    }

    @Test
    fun oppositeTapsCancelAndQueueIsCapped() {
        val queue = ReaderPageTurnQueue()

        queue.enqueue(next = true)
        queue.enqueue(next = false)
        assertEquals(0, queue.pendingDelta)

        repeat(6) { queue.enqueue(next = true) }
        assertEquals(3, queue.pendingDelta)
    }
}
