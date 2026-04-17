package com.tempotunestudio.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class UrlState {
    object Empty : UrlState()
    object Valid : UrlState()
    data class Invalid(val reason: String) : UrlState()
}

class HomeViewModel : ViewModel() {

    private val _urlState = MutableStateFlow<UrlState>(UrlState.Empty)
    val urlState: StateFlow<UrlState> = _urlState.asStateFlow()

    fun validateUrl(url: String) {
        _urlState.value = when {
            url.isBlank() -> UrlState.Empty
            !url.startsWith("http://") && !url.startsWith("https://") ->
                UrlState.Invalid("URL must start with http:// or https://")
            !url.contains(".") -> UrlState.Invalid("Enter a valid URL")
            else -> UrlState.Valid
        }
    }
}
