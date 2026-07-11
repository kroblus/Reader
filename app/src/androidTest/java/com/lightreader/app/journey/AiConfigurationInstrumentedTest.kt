package com.lightreader.app.journey

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lightreader.app.ReaderApplication
import com.lightreader.app.core.settings.AiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiConfigurationInstrumentedTest {
    private val application: ReaderApplication = ApplicationProvider.getApplicationContext()

    @Test
    fun advancedConfigurationTrimsAndPersistsValidHttpsValues() {
        application.container.aiConfigurationStore.save(
            AiConfiguration("https://localhost:9443/v1/", " qa-model ", 10),
        )
        assertEquals(
            AiConfiguration("https://localhost:9443/v1", "qa-model", 10),
            application.container.aiConfigurationStore.get(),
        )
    }

    @Test
    fun advancedConfigurationRejectsUnsafeOrOutOfRangeValues() {
        val invalid = listOf(
            AiConfiguration("http://example.com", "model", 45),
            AiConfiguration("not-a-url", "model", 45),
            AiConfiguration("https://example.com", " ", 45),
            AiConfiguration("https://example.com", "model", 9),
            AiConfiguration("https://example.com", "model", 121),
        )
        invalid.forEach { value ->
            assertTrue("Expected rejection for $value", runCatching {
                application.container.aiConfigurationStore.save(value)
            }.isFailure)
        }
    }
}
