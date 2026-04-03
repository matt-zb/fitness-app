package com.fitapp.data.model.relations

import androidx.room.Embedded
import androidx.room.Relation
import com.fitapp.data.model.Routine
import com.fitapp.data.model.RoutineExercise

data class RoutineWithExercises(
    @Embedded val routine: Routine,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId",
        entity = RoutineExercise::class
    )
    val routineExercises: List<RoutineExercise>
)
