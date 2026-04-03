package com.fitapp.ui.screens.runner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitapp.audio.MetronomeEngine
import com.fitapp.data.model.ExercisePhase
import com.fitapp.data.model.RoutineExercise
import com.fitapp.data.model.WorkoutLog
import com.fitapp.data.model.relations.ExerciseWithPhases
import com.fitapp.data.repository.WorkoutRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RunnerExerciseSlot(
    val routineExercise: RoutineExercise,
    val exerciseWithPhases: ExerciseWithPhases
)

sealed interface RunnerState {
    object Loading : RunnerState
    data class Running(
        val slots: List<RunnerExerciseSlot>,
        val currentSlotIndex: Int,
        val secondsRemaining: Int,
        val isPaused: Boolean,
        val totalDurationSeconds: Int,
        val elapsedSeconds: Int,
        /** Active phase index within the current exercise (null for simple exercises) */
        val activePhaseIndex: Int?
    ) : RunnerState {
        val currentSlot: RunnerExerciseSlot get() = slots[currentSlotIndex]
        val currentExercise get() = currentSlot.exerciseWithPhases.exercise
        val currentPhases: List<ExercisePhase> get() = currentSlot.exerciseWithPhases.sortedPhases
        val progress: Float get() = if (totalDurationSeconds > 0)
            elapsedSeconds.toFloat() / totalDurationSeconds else 0f
    }
    data class Completed(
        val routineName: String,
        val totalDurationSeconds: Int
    ) : RunnerState
    data class Error(val message: String) : RunnerState
}

class WorkoutRunnerViewModel(
    private val routineId: Long,
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _state = MutableStateFlow<RunnerState>(RunnerState.Loading)
    val state: StateFlow<RunnerState> = _state.asStateFlow()

    private val metronome = MetronomeEngine()
    private var countdownJob: Job? = null
    private var sessionStartMs: Long = 0L
    private var routineName: String = ""
    private var totalRoutineDuration: Int = 0

    init {
        loadRoutine()
    }

    private fun loadRoutine() {
        viewModelScope.launch {
            val slots = repository.getFullRoutineExercises(routineId)
            if (slots.isEmpty()) {
                _state.value = RunnerState.Error("Routine not found or has no exercises.")
                return@launch
            }
            routineName = repository.getRoutineWithExercises(routineId)?.routine?.name ?: "Workout"
            totalRoutineDuration = slots.sumOf { (re, ewp) ->
                val dur = if (ewp.hasPhases) ewp.phaseCycleDurationSeconds ?: re.durationSeconds
                          else re.durationSeconds
                dur + re.restAfterSeconds
            }
            sessionStartMs = System.currentTimeMillis()
            _state.value = RunnerState.Running(
                slots = slots.map { (re, ewp) -> RunnerExerciseSlot(re, ewp) },
                currentSlotIndex = 0,
                secondsRemaining = slots.first().first.durationSeconds,
                isPaused = false,
                totalDurationSeconds = totalRoutineDuration,
                elapsedSeconds = 0,
                activePhaseIndex = null
            )
            startExercise(0)
        }
    }

    private fun startExercise(index: Int) {
        val state = _state.value as? RunnerState.Running ?: return
        val slot = state.slots[index]
        val exercise = slot.exerciseWithPhases.exercise
        val phases = slot.exerciseWithPhases.sortedPhases

        metronome.stop()
        metronome.playTransitionCue()

        if (exercise.bpm > 0) {
            if (phases.isNotEmpty()) {
                metronome.startPhased(exercise.bpm, phases) { phaseIdx ->
                    _state.update { s ->
                        (s as? RunnerState.Running)?.copy(activePhaseIndex = phaseIdx) ?: s
                    }
                }
            } else {
                metronome.startSimple(exercise.bpm)
            }
        }

        startCountdown(index)
    }

    private fun startCountdown(slotIndex: Int) {
        countdownJob?.cancel()
        val state = _state.value as? RunnerState.Running ?: return
        val slot = state.slots[slotIndex]
        val durationSec = if (slot.exerciseWithPhases.hasPhases)
            slot.exerciseWithPhases.phaseCycleDurationSeconds ?: slot.routineExercise.durationSeconds
        else slot.routineExercise.durationSeconds

        countdownJob = viewModelScope.launch(Dispatchers.Default) {
            var remaining = durationSec
            var elapsed = computeElapsed(state, slotIndex, durationSec)
            _state.update { s ->
                (s as? RunnerState.Running)?.copy(
                    currentSlotIndex = slotIndex,
                    secondsRemaining = remaining,
                    elapsedSeconds = elapsed,
                    activePhaseIndex = null
                ) ?: s
            }

            val intervalNs = 1_000_000_000L
            var nextTickNs = System.nanoTime() + intervalNs
            while (isActive && remaining > 0) {
                val delayMs = ((nextTickNs - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
                delay(delayMs)
                remaining--
                elapsed++
                _state.update { s ->
                    (s as? RunnerState.Running)?.copy(
                        secondsRemaining = remaining,
                        elapsedSeconds = elapsed
                    ) ?: s
                }
                nextTickNs += intervalNs
            }

            if (isActive) onExerciseComplete(slotIndex)
        }
    }

    private fun computeElapsed(state: RunnerState.Running, upToIndex: Int, currentDuration: Int): Int {
        var elapsed = 0
        for (i in 0 until upToIndex) {
            val s = state.slots[i]
            elapsed += if (s.exerciseWithPhases.hasPhases)
                s.exerciseWithPhases.phaseCycleDurationSeconds ?: s.routineExercise.durationSeconds
            else s.routineExercise.durationSeconds
            elapsed += s.routineExercise.restAfterSeconds
        }
        return elapsed
    }

    private suspend fun onExerciseComplete(slotIndex: Int) {
        val state = _state.value as? RunnerState.Running ?: return
        val slot = state.slots[slotIndex]
        val restSec = slot.routineExercise.restAfterSeconds
        metronome.stop()

        if (restSec > 0) {
            metronome.playTransitionCue()
            // Show rest period
            var restRemaining = restSec
            val intervalNs = 1_000_000_000L
            var nextTickNs = System.nanoTime() + intervalNs
            while (restRemaining > 0) {
                val delayMs = ((nextTickNs - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
                delay(delayMs)
                restRemaining--
                nextTickNs += intervalNs
            }
        }

        val nextIndex = slotIndex + 1
        if (nextIndex >= state.slots.size) {
            val elapsed = System.currentTimeMillis() - sessionStartMs
            _state.value = RunnerState.Completed(
                routineName = routineName,
                totalDurationSeconds = (elapsed / 1000).toInt()
            )
        } else {
            withContext(Dispatchers.Main) {
                startExercise(nextIndex)
            }
        }
    }

    fun pauseResume() {
        val state = _state.value as? RunnerState.Running ?: return
        if (state.isPaused) {
            // Resume
            _state.update { s -> (s as? RunnerState.Running)?.copy(isPaused = false) ?: s }
            val exercise = state.currentSlot.exerciseWithPhases.exercise
            val phases = state.currentSlot.exerciseWithPhases.sortedPhases
            if (exercise.bpm > 0) {
                if (phases.isNotEmpty()) {
                    metronome.startPhased(exercise.bpm, phases) { phaseIdx ->
                        _state.update { s ->
                            (s as? RunnerState.Running)?.copy(activePhaseIndex = phaseIdx) ?: s
                        }
                    }
                } else {
                    metronome.startSimple(exercise.bpm)
                }
            }
            resumeCountdown(state)
        } else {
            // Pause
            metronome.stop()
            countdownJob?.cancel()
            _state.update { s -> (s as? RunnerState.Running)?.copy(isPaused = true) ?: s }
        }
    }

    private fun resumeCountdown(pausedState: RunnerState.Running) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch(Dispatchers.Default) {
            var remaining = pausedState.secondsRemaining
            var elapsed = pausedState.elapsedSeconds
            val intervalNs = 1_000_000_000L
            var nextTickNs = System.nanoTime() + intervalNs
            while (isActive && remaining > 0) {
                val delayMs = ((nextTickNs - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
                delay(delayMs)
                remaining--
                elapsed++
                _state.update { s ->
                    (s as? RunnerState.Running)?.copy(
                        secondsRemaining = remaining,
                        elapsedSeconds = elapsed
                    ) ?: s
                }
                nextTickNs += intervalNs
            }
            if (isActive) onExerciseComplete(pausedState.currentSlotIndex)
        }
    }

    fun skipToNext() {
        val state = _state.value as? RunnerState.Running ?: return
        countdownJob?.cancel()
        metronome.stop()
        val next = state.currentSlotIndex + 1
        if (next >= state.slots.size) {
            val elapsed = System.currentTimeMillis() - sessionStartMs
            _state.value = RunnerState.Completed(
                routineName = routineName,
                totalDurationSeconds = (elapsed / 1000).toInt()
            )
        } else {
            startExercise(next)
        }
    }

    suspend fun logSession(durationSeconds: Int): Long {
        return repository.logWorkout(
            WorkoutLog(
                routineId = routineId,
                routineName = routineName,
                durationSeconds = durationSeconds
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        metronome.release()
    }

    class Factory(
        private val routineId: Long,
        private val repository: WorkoutRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WorkoutRunnerViewModel(routineId, repository) as T
    }
}
