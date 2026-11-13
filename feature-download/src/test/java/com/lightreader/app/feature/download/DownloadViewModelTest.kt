package com.lightreader.app.feature.download

import androidx.lifecycle.SavedStateHandle
import com.lightreader.app.core.data.DownloadTaskEntity
import com.lightreader.app.core.model.ExtractionPlan
import com.lightreader.app.core.model.WebBookPreview
import com.lightreader.app.core.model.WebChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun slowOldPreviewCannotOverwriteNewInputOrPreviewEvenIfProviderIgnoresCancellation() = runTest(dispatcher) {
        val old = CompletableDeferred<DownloadPreviewResult>()
        val gateway = object : FakeGateway(DownloadPreviewResult.Success(preview("new"))) {
            override suspend fun preview(url: String): DownloadPreviewResult =
                if (url.endsWith("old")) withContext(NonCancellable) { old.await() }
                else DownloadPreviewResult.Success(this@DownloadViewModelTest.preview("new"))
        }
        val vm = DownloadViewModel(gateway)
        vm.updateWebUrl("https://example.com/old")
        vm.previewWebBook()
        runCurrent()
        vm.updateWebUrl("https://example.com/new")
        assertFalse(vm.webState.value.loading)
        vm.previewWebBook()
        runCurrent()
        old.complete(DownloadPreviewResult.Success(preview("old")))
        advanceUntilIdle()
        assertEquals("new", vm.webState.value.preview?.title)
        assertEquals("https://example.com/new", vm.webState.value.url)
        assertFalse(vm.webState.value.loading)
    }

    @Test
    fun leavingPreviewInvalidatesLateFailureAndRecreationRestoresOnlyUrl() = runTest(dispatcher) {
        val pending = CompletableDeferred<DownloadPreviewResult>()
        val handle = SavedStateHandle()
        val gateway = object : FakeGateway(DownloadPreviewResult.Success(preview("book"))) {
            override suspend fun preview(url: String) = withContext(NonCancellable) { pending.await() }
        }
        val vm = DownloadViewModel(gateway, handle)
        vm.updateWebUrl("https://example.com/book")
        vm.previewWebBook()
        runCurrent()
        vm.dismissWebPreview()
        pending.complete(DownloadPreviewResult.Failure(DownloadImportFailure.HTTP))
        advanceUntilIdle()
        assertNull(vm.webState.value.error)
        assertFalse(vm.webState.value.loading)
        val recreated = DownloadViewModel(gateway, handle)
        assertEquals("https://example.com/book", recreated.webState.value.url)
        assertNull(recreated.webState.value.preview)
    }

    @Test
    fun doubleConfirmSubmitsOnceAndFailureKeepsInputAndPreview() = runTest(dispatcher) {
        val page = preview("book")
        val pending = CompletableDeferred<Unit>()
        var submissions = 0
        val gateway = object : FakeGateway(DownloadPreviewResult.Success(page)) {
            override suspend fun start(preview: WebBookPreview) {
                submissions++
                pending.await()
                error("Database unavailable")
            }
        }
        val vm = DownloadViewModel(gateway)
        vm.updateWebUrl(page.sourceUrl)
        vm.previewWebBook()
        advanceUntilIdle()
        vm.startWebDownload()
        vm.startWebDownload()
        assertTrue(vm.webState.value.submitting)
        runCurrent()
        assertEquals(1, submissions)
        pending.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.webState.value.submitting)
        assertSame(page, vm.webState.value.preview)
        assertEquals(page.sourceUrl, vm.webState.value.url)
        assertEquals(DownloadImportFailure.SUBMISSION, vm.webState.value.error)
    }

    private fun preview(title: String) = WebBookPreview(title, null, null,
        "https://example.com/$title", "https://example.com/$title",
        listOf(WebChapter("Chapter", "https://example.com/$title/1")), "Body", ExtractionPlan())

    private open class FakeGateway(
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
