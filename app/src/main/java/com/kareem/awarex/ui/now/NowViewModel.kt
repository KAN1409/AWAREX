package com.kareem.awarex.ui.now

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.data.AwareRepository
import com.kareem.awarex.data.AwareStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NowUiState(
    val input: String = "",
    val attention: List<AttentionCard> = emptyList(),
    val recentEvidence: List<Observation> = emptyList(),
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class NowViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AwareRepository(AwareStore(application))
    private val _state = MutableStateFlow(NowUiState())
    val state: StateFlow<NowUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onInputChanged(value: String) {
        _state.update { it.copy(input = value, error = null, message = null) }
    }

    fun capture() {
        val text = state.value.input.trim()
        if (text.isEmpty() || state.value.working) return
        _state.update { it.copy(working = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.capture(text) }
                val cards = withContext(Dispatchers.IO) { repository.attentionCards() }
                val evidence = withContext(Dispatchers.IO) { repository.recentEvidence() }
                val message = when {
                    result.resolvedLoopId != null -> "Open loop closed from new evidence."
                    result.createdLoopId != null -> "AWAREX noticed a commitment and is watching it."
                    else -> "Evidence saved."
                }
                _state.update {
                    it.copy(
                        input = "",
                        attention = cards,
                        recentEvidence = evidence,
                        working = false,
                        message = message,
                        error = null
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        working = false,
                        error = t.message ?: t::class.java.simpleName,
                        message = null
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val cards = withContext(Dispatchers.IO) { repository.attentionCards() }
                val evidence = withContext(Dispatchers.IO) { repository.recentEvidence() }
                _state.update { it.copy(attention = cards, recentEvidence = evidence, error = null) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: t::class.java.simpleName) }
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
