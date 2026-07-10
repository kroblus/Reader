package com.lightreader.app.core.reader

/** Access-ordered cache that evicts by an estimated byte budget instead of item count. */
class MemoryBoundedCache<K, V>(
    private val maxBytes: Long,
    private val estimatedBytesOf: (V) -> Long,
) {
    private val values = LinkedHashMap<K, V>(16, .75f, true)
    private var estimatedBytes = 0L

    operator fun get(key: K): V? = values[key]

    fun put(key: K, value: V) {
        values.remove(key)?.let { estimatedBytes -= estimatedBytesOf(it) }
        values[key] = value
        estimatedBytes += estimatedBytesOf(value)
        while (estimatedBytes > maxBytes && values.size > 1) {
            val iterator = values.entries.iterator()
            val eldest = iterator.next()
            estimatedBytes -= estimatedBytesOf(eldest.value)
            iterator.remove()
        }
    }

    fun clear() {
        values.clear()
        estimatedBytes = 0L
    }

    val size: Int get() = values.size
    val byteSize: Long get() = estimatedBytes
}
