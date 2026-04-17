package com.tempotunestudio.processing

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow

class VideoProcessor(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun downloadVideo(
        url: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")

        val body = response.body ?: throw IOException("Empty response body")
        val contentLength = body.contentLength()

        val outputFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")

        body.byteStream().use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead = 0L
                var bytes: Int
                while (inputStream.read(buffer).also { bytes = it } != -1) {
                    outputStream.write(buffer, 0, bytes)
                    bytesRead += bytes
                    if (contentLength > 0) {
                        onProgress((bytesRead * 100 / contentLength).toInt().coerceIn(0, 100))
                    }
                }
            }
        }

        outputFile.absolutePath
    }

    suspend fun processVideo(
        inputPath: String,
        tempo: Float,
        pitchSemitones: Float,
        onProgress: (Int, String) -> Unit
    ): String = withContext(Dispatchers.Main) {
        val outputFile = File(
            context.getExternalFilesDir(null),
            "TempoTuneStudio_${System.currentTimeMillis()}.mp4"
        )

        onProgress(5, "Preparing export…")

        val pitchShift = 2.0.pow(pitchSemitones / 12.0).toFloat()

        val sonicProcessor = SonicAudioProcessor().apply {
            if (tempo != 1.0f) setSpeed(tempo)
            if (pitchSemitones != 0.0f) setPitch(pitchShift)
        }

        val audioEffects = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
        if (tempo != 1.0f || pitchSemitones != 0.0f) {
            audioEffects.add(sonicProcessor)
        }

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        if (tempo != 1.0f) {
            videoEffects.add(SpeedChangeEffect(tempo))
        }

        val effects = Effects(audioEffects, videoEffects)

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(inputPath)))
            .setEffects(effects)
            .build()

        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(outputFile.absolutePath)
                    }
                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        continuation.resumeWithException(IOException(exportException.message))
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)

            // Poll progress on the main thread
            val progressHolder = ProgressHolder()
            continuation.invokeOnCancellation { transformer.cancel() }

            // Launch a side-effect coroutine on Main to report progress
            // (Transformer.Listener callbacks already run on Main)
            android.os.Handler(android.os.Looper.getMainLooper()).post(object : Runnable {
                override fun run() {
                    if (continuation.isActive) {
                        val state = transformer.getProgress(progressHolder)
                        if (progressHolder.progress > 0) {
                            val pct = 10 + (progressHolder.progress * 0.8f).toInt()
                            onProgress(pct, "Processing… ${progressHolder.progress}%")
                        }
                        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 500)
                        }
                    }
                }
            })
        }
    }

    fun clearCache() {
        context.cacheDir.listFiles { f -> f.name.startsWith("input_") }
            ?.forEach { it.delete() }
    }
}