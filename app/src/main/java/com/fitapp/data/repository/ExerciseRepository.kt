package com.fitapp.data.repository

import com.fitapp.data.db.dao.ExerciseDao
import com.fitapp.data.model.Exercise
import com.fitapp.data.model.ExercisePhase
import com.fitapp.data.model.relations.ExerciseWithPhases
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: ExerciseDao) {

    fun getAllExercisesWithPhases(): Flow<List<ExerciseWithPhases>> =
        dao.getAllExercisesWithPhases()

    suspend fun getExerciseWithPhases(id: Long): ExerciseWithPhases? =
        dao.getExerciseWithPhases(id)

    suspend fun saveExercise(exercise: Exercise, phases: List<ExercisePhase>): Long =
        dao.upsertExerciseWithPhases(exercise, phases)

    suspend fun deleteExercise(exercise: Exercise) =
        dao.deleteExercise(exercise)
}
