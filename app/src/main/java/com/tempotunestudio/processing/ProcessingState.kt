package com.tempotunestudio.processing

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class Downloading(val progress: Int) : ProcessingState()
    data class Processing(val progress: Int, val message: String) : ProcessingState()
    data class Success(val outputPath: String) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
