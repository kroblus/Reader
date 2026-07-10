package com.lightreader.app.feature.download

import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.WebChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun previewFailureIsExposedAsTypedState() = runTest(dispatcher) {
        val gateway = FakeGateway(DownloadPreviewResult.Failure(DownloadImportFailure.RESPONSE_TOO_LARGE))
        val viewModel = DownloadViewModel(gateway)

        viewModel.updateWebUrl("https://example.com/novel")
        viewModel.previewWebBook()
        advanceUntilIdle()

        assertEquals(DownloadImportFailure.RESPONSE_TOO_LARGE, viewModel.webState.value.error)
        assertNull(viewModel.webState.value.preview)
    }

    @Test
    fun successfulPreviewStartsTaskAndEmitsSemanticNotice() = runTest(dispatcher) {
        val preview = WebBookPreview(
            title = "测试小说",
            author = null,
            description = null,
            sourceUrl = "https://example.com/novel",
            finalUrl = "https://example.com/novel",
            chapters = listOf(WebChapter("第一章", "https://example.com/novel/1")),
            sample = "正文",
            extractionPlan = ExtractionPlan(),
        )
        val gateway = FakeGateway(DownloadPreviewResult.Success(preview))
        val viewModel = DownloadViewModel(gateway)

        viewModel.updateWebUrl(preview.sourceUrl)
        viewModel.previewWebBook()
        advanceUntilIdle()
        viewModel.startWebDownload()
        advanceUntilIdle()

        assertSame(preview, gateway.startedPreview)
        assertEquals(DownloadNotice.TASK_CREATED, viewModel.events.first().notice)
        assertNull(viewModel.webState.value.preview)
    }

    private class FakeGateway(
        private val previewResult: DownloadPreviewResult,
    ) : DownloadFeatureGateway {
        override val tasks: Flow<List<DownloadTaskEntity>> = emptyFlow()
        var startedPreview: WebBookPreview? = null

        override suspend fun preview(url: String): DownloadPreviewResult = previewResult
        override suspend fun start(preview: WebBookPreview) {
            startedPreview = preview
        }
        override suspend fun pause(id: String) = Unit
        override suspend fun resume(id: String) = Unit
        override suspend fun cancel(id: String) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun refreshBook(bookId: String): Int = 0
    }
}
