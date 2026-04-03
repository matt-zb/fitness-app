package com.fitapp.ui.screens.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitapp.data.model.WorkoutLog
import com.fitapp.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class LogViewModel(private val repository: WorkoutRepository) : ViewModel() {

    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

    private val _logsInMonth = MutableStateFlow<Map<LocalDate, List<WorkoutLog>>>(emptyMap())
    val logsInMonth: StateFlow<Map<LocalDate, List<WorkoutLog>>> = _logsInMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    init {
        loadMonth(YearMonth.now())
    }

    fun navigateToPreviousMonth() {
        val prev = _displayedMonth.value.minusMonths(1)
        _displayedMonth.value = prev
        loadMonth(prev)
    }

    fun navigateToNextMonth() {
        val next = _displayedMonth.value.plusMonths(1)
        _displayedMonth.value = next
        loadMonth(next)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    private fun loadMonth(month: YearMonth) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val startMs = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = month.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val logs = repository.getLogsInRange(startMs, endMs)
            val grouped = logs.groupBy { log ->
                java.time.Instant.ofEpochMilli(log.completedAt)
                    .atZone(zone).toLocalDate()
            }
            _logsInMonth.value = grouped
        }
    }

    class Factory(private val repository: WorkoutRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LogViewModel(repository) as T
    }
}
