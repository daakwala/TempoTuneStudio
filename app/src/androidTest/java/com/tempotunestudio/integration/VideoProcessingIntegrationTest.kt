package com.tempotunestudio.integration

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tempotunestudio.processing.VideoProcessor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests that exercise the full pipeline:
 *   download → pitch-shift / tempo-change → export → verify output
 *
 * Uses a short (10 s) royalty-free MP4 so the suite stays fast.
 * No yt-dlp or ffmpeg required — download is done via the same
 * OkHttp client the app itself uses.
 */
@RunWith(AndroidJUnit4::class)
class VideoProcessingIntegrationTest {

    // Short royalty-free clip — Big Buck Bunny 10 s sample (360p, ~1 MB)
    private val testVideoUrl =
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"

    private lateinit var processor: VideoProcessor
    private lateinit var downloadedPath: String
    private val outputFiles = mutableListOf<File>()

    @Before
    fun setUp(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        processor = VideoProcessor(context)

        // Download once, reuse across all tests in this class
        var downloadProgress = 0
        downloadedPath = processor.downloadVideo(testVideoUrl) { progress ->
            downloadProgress = progress
        }
        assertTrue("Download must complete to 100%", downloadProgress == 100)
        assertTrue("Downloaded file must exist", File(downloadedPath).exists())
        assertTrue("Downloaded file must not be empty", File(downloadedPath).length() > 0)
    }

    @After
    fun tearDown() {
        outputFiles.forEach { it.delete() }
        processor.clearCache()
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    @Test
    fun downloadedFileIsValidMp4() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(downloadedPath)
        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull()
        retriever.release()

        assertNotNull("Metadata retriever must read duration", durationMs)
        assertTrue("Video must be at least 5 s long", durationMs!! >= 5_000L)
    }

    @Test
    fun downloadedFileHasAudioTrack() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(downloadedPath)
        val hasAudio = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
        )
        retriever.release()
        assertEquals("yes", hasAudio)
    }

    @Test
    fun downloadedFileHasVideoTrack() {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(downloadedPath)
        val hasVideo = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
        )
        retriever.release()
        assertEquals("yes", hasVideo)
    }

    // ── Pitch shifting ───────────────────────────────────────────────────────

    @Test
    fun pitchShiftUpProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.0f, pitchSemitones = 5f)
        assertOutputValid(output)
    }

    @Test
    fun pitchShiftDownProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.0f, pitchSemitones = -5f)
        assertOutputValid(output)
    }

    @Test
    fun pitchShiftOneOctaveUpProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.0f, pitchSemitones = 12f)
        assertOutputValid(output)
    }

    @Test
    fun pitchShiftOneOctaveDownProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.0f, pitchSemitones = -12f)
        assertOutputValid(output)
    }

    @Test
    fun zeroPitchShiftProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.0f, pitchSemitones = 0f)
        assertOutputValid(output)
    }

    // ── Tempo (time-stretching) ──────────────────────────────────────────────

    @Test
    fun tempoSpeedUpProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.5f, pitchSemitones = 0f)
        assertOutputValid(output)
    }

    @Test
    fun tempoSlowDownProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 0.5f, pitchSemitones = 0f)
        assertOutputValid(output)
    }

    @Test
    fun tempoSpeedUpShortensDuration(): Unit = runBlocking {
        val originalDuration = getFileDurationMs(downloadedPath)
        val output = processAndTrack(tempo = 2.0f, pitchSemitones = 0f)

        val exportedDuration = getFileDurationMs(output)
        val expectedDuration = (originalDuration / 2.0f).toLong()

        // Allow ±15% tolerance for encoder/muxer rounding
        val tolerance = expectedDuration * 0.15
        assertTrue(
            "2x speed export ($exportedDuration ms) should be ~${expectedDuration} ms",
            kotlin.math.abs(exportedDuration - expectedDuration) < tolerance
        )
    }

    @Test
    fun tempoSlowDownLengthensDuration(): Unit = runBlocking {
        val originalDuration = getFileDurationMs(downloadedPath)
        val output = processAndTrack(tempo = 0.5f, pitchSemitones = 0f)

        val exportedDuration = getFileDurationMs(output)
        val expectedDuration = (originalDuration * 2.0f).toLong()

        val tolerance = expectedDuration * 0.15
        assertTrue(
            "0.5x speed export ($exportedDuration ms) should be ~${expectedDuration} ms",
            kotlin.math.abs(exportedDuration - expectedDuration) < tolerance
        )
    }

    // ── Combined pitch + tempo ───────────────────────────────────────────────

    @Test
    fun combinedPitchAndTempoProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 1.25f, pitchSemitones = 3f)
        assertOutputValid(output)
    }

    @Test
    fun combinedPitchDownAndTempoDownProducesValidFile(): Unit = runBlocking {
        val output = processAndTrack(tempo = 0.75f, pitchSemitones = -3f)
        assertOutputValid(output)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun processAndTrack(tempo: Float, pitchSemitones: Float): String {
        val outputPath = processor.processVideo(
            inputPath = downloadedPath,
            tempo = tempo,
            pitchSemitones = pitchSemitones,
            onProgress = { _, _ -> }
        )
        outputFiles.add(File(outputPath))
        return outputPath
    }

    private fun assertOutputValid(path: String) {
        val file = File(path)
        assertTrue("Output file must exist at $path", file.exists())
        assertTrue("Output file must not be empty", file.length() > 0)

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        retriever.release()

        assertNotNull("Exported file must have readable duration", duration)
        assertTrue("Exported file must have positive duration", duration!!.toLong() > 0)
        assertEquals("Exported file must have audio track", "yes", hasAudio)
    }

    private fun getFileDurationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val duration = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L
        retriever.release()
        return duration
    }

    private fun assertNotNull(message: String, value: Any?) {
        assertTrue(message, value != null)
    }
}
