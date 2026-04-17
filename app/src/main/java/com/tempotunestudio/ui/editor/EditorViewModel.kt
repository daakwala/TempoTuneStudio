package com.tempotunestudio.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tempotunestudio.processing.ProcessingState
import com.tempotunestudio.processing.VideoProcessor
import com.tempotunestudio.utils.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = VideoProcessor(app)

    private val _localVideoPath = MutableStateFlow<String?>(null)
    val localVideoPath: StateFlow<String?> = _localVideoPath.asStateFlow()

    private val _pitch = MutableStateFlow(0f)   // semitones, -12..+12
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _tempo = MutableStateFlow(1f)   // multiplier, 0.5..2.0
    val tempo: StateFlow<Float> = _tempo.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    fun loadVideo(url: String) {
        if (_processingState.value is ProcessingState.Downloading) return
        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Downloading(0)
                val path = processor.downloadVideo(url) { progress ->
                    _processingState.value = ProcessingState.Downloading(progress)
                }
                _localVideoPath.value = android.net.Uri.fromFile(java.io.File(path)).toString()
                _processingState.value = ProcessingState.Idle
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("Download failed: ${e.message}")
            }
        }
    }

    fun loadLocalVideo(uriString: String) {
        _localVideoPath.value = uriString
        _processingState.value = ProcessingState.Idle
    }

    fun setPitch(semitones: Float) { _pitch.value = semitones }
    fun setTempo(multiplier: Float) { _tempo.value = multiplier }

    fun exportVideo() {
        val inputPath = _localVideoPath.value ?: run {
            _processingState.value = ProcessingState.Error("No video loaded")
            return
        }
        if (_processingState.value is ProcessingState.Processing) return

        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Processing(0, "Starting…")
                val outputPath = processor.processVideo(
                    inputPath = inputPath,
                    tempo = _tempo.value,
                    pitchSemitones = _pitch.value,
                    onProgress = { progress, message ->
                        _processingState.value = ProcessingState.Processing(progress, message)
                    }
                )
                _processingState.value = ProcessingState.Processing(95, "Saving to gallery…")
                FileUtils.saveToGallery(getApplication(), outputPath)
                _processingState.value = ProcessingState.Success(outputPath)
            } catch (e: Exception) {
                _processingState.value = ProcessingState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun resetState() { _processingState.value = ProcessingState.Idle }

    override fun onCleared() {
        super.onCleared()
        processor.clearCache()
    }
}
