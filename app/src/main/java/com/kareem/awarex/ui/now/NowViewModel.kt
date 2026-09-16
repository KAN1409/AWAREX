package com.kareem.awarex.ui.now

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.InsightCard
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.SituationCard
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
    val insights: List<InsightCard> = emptyList(),
    val situations: List<SituationCard> = emptyList(),
    val attention: List<AttentionCard> = emptyList(),
    val recentEvidence: List<Observation> = emptyList(),
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class NowViewModel(application: Application) : AndroidViewModel(application) {
    private data class DashboardData(
        val attention: List<AttentionCard>,
        val insights: List<InsightCard>,
        val situations: List<SituationCard>,
        val evidence: List<Observation>
    )

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
                val dashboard = withContext(Dispatchers.IO) { loadDashboard() }
                val connectedInsight = dashboard.insights.any { insight ->
                    insight.evidenceTexts.any { it.equals(result.observation.text, ignoreCase = true) }
                }
                val message = when {
                    result.resolvedLoopId != null -> "AWAREX matched this to an earlier commitment and closed the loop."
                    connectedInsight -> "AWAREX connected this to earlier evidence and found a change."
                    result.createdLoopId != null -> "AWAREX noticed a commitment and is tracking it."
                    else -> "Evidence added to the world model."
                }
                _state.update {
                    it.copy(
                        input = "",
                        insights = dashboard.insights,
                        situations = dashboard.situations,
                        attention = dashboard.attention,
                        recentEvidence = dashboard.evidence,
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
                val dashboard = withContext(Dispatchers.IO) { loadDashboard() }
                _state.update {
                    it.copy(
                        insights = dashboard.insights,
                        situations = dashboard.situations,
                        attention = dashboard.attention,
                        recentEvidence = dashboard.evidence,
                        error = null
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: t::class.java.simpleName) }
            }
        }
    }

    private fun loadDashboard(): DashboardData {
        val world = repository.worldSnapshot()
        return DashboardData(
            attention = repository.attentionCards(),
            insights = world.insights,
            situations = world.situations,
            evidence = repository.recentEvidence(8)
        )
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
