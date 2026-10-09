package com.qbili.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.BuildConfig
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.AppUpdateRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.UpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AboutViewModel(private val repository: AppUpdateRepository) : ViewModel() {
    data class UiState(
        val checking: Boolean = false,
        val result: UpdateCheckResult? = null,
        val error: String? = null,
        val showResult: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    fun checkForUpdates() {
        if (_uiState.value.checking) return
        _uiState.value = UiState(checking = true)
        viewModelScope.launch {
            try {
                val result = repository.check(BuildConfig.VERSION_NAME, BuildConfig.DEBUG)
                _uiState.value = UiState(result = result, showResult = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = UiState(error = error.friendlyMessage(), showResult = true)
            }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(showResult = false) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AboutViewModel(container.appUpdateRepository) }
        }
    }
}
