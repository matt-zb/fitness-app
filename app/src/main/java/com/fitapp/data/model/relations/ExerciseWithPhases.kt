package com.fitapp.data.model.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.fitapp.data.model.Exercise
import com.fitapp.data.model.ExercisePhase

data class ExerciseWithPhases(
    @Embedded val exercise: Exercise,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseId"
    )
    val phases: List<ExercisePhase>
) {
    /** Phases sorted by their defined order. */
    val sortedPhases: List<ExercisePhase> get() = phases.sortedBy { it.orderIndex }

    /** True if this exercise uses phase-based timing instead of a flat duration. */
    val hasPhases: Boolean get() = phases.isNotEmpty()

    /** Total cycle duration derived from phases, or null if no phases are defined. */
    val phaseCycleDurationSeconds: Int? get() =
        if (hasPhases) phases.sumOf { it.durationSeconds } else null
}
