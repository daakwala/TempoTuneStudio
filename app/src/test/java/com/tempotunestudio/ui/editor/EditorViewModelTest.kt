package com.tempotunestudio.ui.editor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.tempotunestudio.processing.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for EditorViewModel.
 *
 * Covers the state changes that were previously untested and led to
 * the pitch/tempo slider bug (sliders updated ViewModel but nothing
 * verified the values were correct).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = EditorViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial pitch is 0 semitones`() = runTest {
        assertEquals(0f, viewModel.pitch.value)
    }

    @Test
    fun `initial tempo is 1x (100 percent)`() = runTest {
        assertEquals(1f, viewModel.tempo.value)
    }

    @Test
    fun `initial localVideoPath is null`() = runTest {
        assertNull(viewModel.localVideoPath.value)
    }

    @Test
    fun `initial processing state is Idle`() = runTest {
        assertTrue(viewModel.processingState.value is ProcessingState.Idle)
    }

    // ── Pitch ────────────────────────────────────────────────────────────────

    @Test
    fun `setPitch emits new semitone value`() = runTest {
        viewModel.pitch.test {
            awaitItem() // consume initial 0f
            viewModel.setPitch(5f)
            assertEquals(5f, awaitItem())
        }
    }

    @Test
    fun `setPitch negative semitones is accepted`() = runTest {
        viewModel.setPitch(-7f)
        assertEquals(-7f, viewModel.pitch.value)
    }

    @Test
    fun `setPitch to 0 resets pitch`() = runTest {
        viewModel.setPitch(12f)
        viewModel.setPitch(0f)
        assertEquals(0f, viewModel.pitch.value)
    }

    // ── Tempo ────────────────────────────────────────────────────────────────

    @Test
    fun `setTempo emits new multiplier value`() = runTest {
        viewModel.tempo.test {
            awaitItem() // consume initial 1f
            viewModel.setTempo(1.5f)
            assertEquals(1.5f, awaitItem())
        }
    }

    @Test
    fun `setTempo slow motion value is accepted`() = runTest {
        viewModel.setTempo(0.5f)
        assertEquals(0.5f, viewModel.tempo.value)
    }

    @Test
    fun `setTempo to 1 resets tempo`() = runTest {
        viewModel.setTempo(2f)
        viewModel.setTempo(1f)
        assertEquals(1f, viewModel.tempo.value)
    }

    // ── Load local video ─────────────────────────────────────────────────────

    @Test
    fun `loadLocalVideo sets path and transitions to Idle`() = runTest {
        val uri = "content://media/external/video/1234"
        viewModel.loadLocalVideo(uri)

        assertEquals(uri, viewModel.localVideoPath.value)
        assertTrue(viewModel.processingState.value is ProcessingState.Idle)
    }

    @Test
    fun `loadLocalVideo emits path through flow`() = runTest {
        viewModel.localVideoPath.test {
            awaitItem() // initial null
            viewModel.loadLocalVideo("content://media/video/1")
            assertEquals("content://media/video/1", awaitItem())
        }
    }

    // ── Export without video ─────────────────────────────────────────────────

    @Test
    fun `exportVideo with no video loaded emits Error state`() = runTest {
        viewModel.processingState.test {
            awaitItem() // initial Idle
            viewModel.exportVideo()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()
            assertTrue("Expected Error but got $state", state is ProcessingState.Error)
            val error = state as ProcessingState.Error
            assertTrue(error.message.contains("No video", ignoreCase = true))
        }
    }

    // ── Reset state ──────────────────────────────────────────────────────────

    @Test
    fun `resetState returns processing state to Idle`() = runTest {
        // Manually put it into an error state first
        viewModel.exportVideo()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.processingState.value is ProcessingState.Error)

        viewModel.resetState()
        assertTrue(viewModel.processingState.value is ProcessingState.Idle)
    }
}
