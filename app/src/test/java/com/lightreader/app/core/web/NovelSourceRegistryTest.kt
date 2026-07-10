package com.lightreader.app.core.web

import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelSourceRegistryTest {
    @Test
    fun dispatchesPersistedSourceIdBeforeFallingBackToUrlMatching() = runBlocking {
        val registry = NovelSourceRegistry(listOf(FakeAdapter("alpha"), FakeAdapter("beta")))

        assertEquals(
            "alpha:https://other.example/chapter",
            registry.chapterText("alpha", "https://other.example/chapter", ExtractionPlan(), "Chapter"),
        )
        assertEquals(
            "beta:https://beta.example/chapter",
            registry.chapterText("https://beta.example/chapter", ExtractionPlan(), "Chapter"),
        )
    }

    private class FakeAdapter(override val id: String) : NovelSourceAdapter {
        override fun canHandle(url: String): Boolean = url.contains("$id.example")

        override suspend fun preview(url: String): WebBookPreview = error("Not used by this routing test")

        override suspend fun chapterText(url: String, plan: ExtractionPlan, chapterTitle: String): String = "$id:$url"
    }
}
