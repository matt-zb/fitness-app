package com.fitapp.ui.screens.builder

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fitapp.data.model.Exercise
import com.fitapp.data.model.ExercisePhase
import com.fitapp.data.model.Routine
import com.fitapp.data.model.RoutineExercise
import com.fitapp.data.model.relations.ExerciseWithPhases
import com.fitapp.data.repository.ExerciseRepository
import com.fitapp.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Exercise editor state
// ---------------------------------------------------------------------------

data class PhaseEditorState(
    val id: Long = 0,
    val label: String = "",
    val durationSeconds: String = "5"
)

data class ExerciseEditorState(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val bpm: String = "0",
    val phases: List<PhaseEditorState> = emptyList()
)

// ---------------------------------------------------------------------------
// Routine editor state
// ---------------------------------------------------------------------------

data class RoutineSlotState(
    val routineExerciseId: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val durationSeconds: String = "35",
    val restAfterSeconds: String = "0",
    val orderIndex: Int = 0
)

data class RoutineEditorState(
    val id: Long = 0,
    val name: String = "",
    val slots: List<RoutineSlotState> = emptyList()
)

class WorkoutBuilderViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val allExercises: StateFlow<List<ExerciseWithPhases>> =
        exerciseRepository.getAllExercisesWithPhases()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exercise editor
    private val _exerciseEditor = MutableStateFlow(ExerciseEditorState())
    val exerciseEditor: StateFlow<ExerciseEditorState> = _exerciseEditor.asStateFlow()

    private val _exerciseSaved = MutableStateFlow(false)
    val exerciseSaved: StateFlow<Boolean> = _exerciseSaved.asStateFlow()

    // Routine editor
    private val _routineEditor = MutableStateFlow(RoutineEditorState())
    val routineEditor: StateFlow<RoutineEditorState> = _routineEditor.asStateFlow()

    private val _routineSaved = MutableStateFlow(false)
    val routineSaved: StateFlow<Boolean> = _routineSaved.asStateFlow()

    // ---------------------------------------------------------------------------
    // Exercise editor
    // ---------------------------------------------------------------------------

    fun loadExerciseForEdit(id: Long) {
        viewModelScope.launch {
            val ewp = exerciseRepository.getExerciseWithPhases(id) ?: return@launch
            _exerciseEditor.value = ExerciseEditorState(
                id = ewp.exercise.id,
                name = ewp.exercise.name,
                description = ewp.exercise.description,
                bpm = ewp.exercise.bpm.toString(),
                phases = ewp.sortedPhases.map { p ->
                    PhaseEditorState(id = p.id, label = p.label, durationSeconds = p.durationSeconds.toString())
                }
            )
        }
    }

    fun resetExerciseEditor() {
        _exerciseEditor.value = ExerciseEditorState()
        _exerciseSaved.value = false
    }

    fun updateExerciseName(name: String) {
        _exerciseEditor.value = _exerciseEditor.value.copy(name = name)
    }

    fun updateExerciseDescription(desc: String) {
        _exerciseEditor.value = _exerciseEditor.value.copy(description = desc)
    }

    fun updateExerciseBpm(bpm: String) {
        _exerciseEditor.value = _exerciseEditor.value.copy(bpm = bpm)
    }

    fun addPhase() {
        val current = _exerciseEditor.value
        _exerciseEditor.value = current.copy(
            phases = current.phases + PhaseEditorState(label = "Phase ${current.phases.size + 1}")
        )
    }

    fun updatePhase(index: Int, phase: PhaseEditorState) {
        val phases = _exerciseEditor.value.phases.toMutableList()
        if (index in phases.indices) {
            phases[index] = phase
            _exerciseEditor.value = _exerciseEditor.value.copy(phases = phases)
        }
    }

    fun removePhase(index: Int) {
        val phases = _exerciseEditor.value.phases.toMutableList()
        if (index in phases.indices) {
            phases.removeAt(index)
            _exerciseEditor.value = _exerciseEditor.value.copy(phases = phases)
        }
    }

    fun saveExercise() {
        viewModelScope.launch {
            val editor = _exerciseEditor.value
            val exercise = Exercise(
                id = editor.id,
                name = editor.name.trim(),
                description = editor.description.trim(),
                bpm = editor.bpm.toIntOrNull() ?: 0
            )
            val phases = editor.phases.mapIndexed { i, p ->
                ExercisePhase(
                    id = p.id,
                    exerciseId = editor.id,
                    label = p.label.trim(),
                    durationSeconds = p.durationSeconds.toIntOrNull() ?: 5,
                    orderIndex = i
                )
            }
            exerciseRepository.saveExercise(exercise, phases)
            _exerciseSaved.value = true
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch { exerciseRepository.deleteExercise(exercise) }
    }

    // ---------------------------------------------------------------------------
    // Routine editor
    // ---------------------------------------------------------------------------

    fun loadRoutineForEdit(routineId: Long) {
        viewModelScope.launch {
            val rwe = workoutRepository.getRoutineWithExercises(routineId) ?: return@launch
            val fullSlots = workoutRepository.getFullRoutineExercises(routineId)
            _routineEditor.value = RoutineEditorState(
                id = rwe.routine.id,
                name = rwe.routine.name,
                slots = fullSlots.mapIndexed { i, (re, ewp) ->
                    RoutineSlotState(
                        routineExerciseId = re.id,
                        exerciseId = re.exerciseId,
                        exerciseName = ewp.exercise.name,
                        durationSeconds = re.durationSeconds.toString(),
                        restAfterSeconds = re.restAfterSeconds.toString(),
                        orderIndex = i
                    )
                }
            )
        }
    }

    fun resetRoutineEditor() {
        _routineEditor.value = RoutineEditorState()
        _routineSaved.value = false
    }

    fun updateRoutineName(name: String) {
        _routineEditor.value = _routineEditor.value.copy(name = name)
    }

    fun addExerciseToRoutine(ewp: ExerciseWithPhases) {
        val current = _routineEditor.value
        val totalPhaseDur = if (ewp.hasPhases) ewp.phaseCycleDurationSeconds ?: 35 else 35
        _routineEditor.value = current.copy(
            slots = current.slots + RoutineSlotState(
                exerciseId = ewp.exercise.id,
                exerciseName = ewp.exercise.name,
                durationSeconds = totalPhaseDur.toString(),
                orderIndex = current.slots.size
            )
        )
    }

    fun removeSlot(index: Int) {
        val slots = _routineEditor.value.slots.toMutableList()
        if (index in slots.indices) {
            slots.removeAt(index)
            _routineEditor.value = _routineEditor.value.copy(slots = slots)
        }
    }

    fun moveSlot(from: Int, to: Int) {
        val slots = _routineEditor.value.slots.toMutableList()
        if (from in slots.indices && to in slots.indices) {
            val item = slots.removeAt(from)
            slots.add(to, item)
            _routineEditor.value = _routineEditor.value.copy(
                slots = slots.mapIndexed { i, s -> s.copy(orderIndex = i) }
            )
        }
    }

    fun updateSlotDuration(index: Int, duration: String) {
        val slots = _routineEditor.value.slots.toMutableList()
        if (index in slots.indices) {
            slots[index] = slots[index].copy(durationSeconds = duration)
            _routineEditor.value = _routineEditor.value.copy(slots = slots)
        }
    }

    fun updateSlotRest(index: Int, rest: String) {
        val slots = _routineEditor.value.slots.toMutableList()
        if (index in slots.indices) {
            slots[index] = slots[index].copy(restAfterSeconds = rest)
            _routineEditor.value = _routineEditor.value.copy(slots = slots)
        }
    }

    fun saveRoutine() {
        viewModelScope.launch {
            val editor = _routineEditor.value
            val routine = Routine(id = editor.id, name = editor.name.trim())
            val exercises = editor.slots.mapIndexed { i, slot ->
                RoutineExercise(
                    id = slot.routineExerciseId,
                    routineId = editor.id,
                    exerciseId = slot.exerciseId,
                    orderIndex = i,
                    restAfterSeconds = slot.restAfterSeconds.toIntOrNull() ?: 0,
                    durationSeconds = slot.durationSeconds.toIntOrNull() ?: 35
                )
            }
            workoutRepository.saveRoutine(routine, exercises)
            _routineSaved.value = true
        }
    }

    class Factory(
        private val exerciseRepository: ExerciseRepository,
        private val workoutRepository: WorkoutRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WorkoutBuilderViewModel(exerciseRepository, workoutRepository) as T
    }
}
