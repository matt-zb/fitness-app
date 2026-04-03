package com.fitapp.data.db.dao

import androidx.room.*
import com.fitapp.data.model.Exercise
import com.fitapp.data.model.ExercisePhase
import com.fitapp.data.model.relations.ExerciseWithPhases
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Transaction
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercisesWithPhases(): Flow<List<ExerciseWithPhases>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseWithPhases(id: Long): ExerciseWithPhases?

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: Long): Exercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise): Long

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhases(phases: List<ExercisePhase>)

    @Query("DELETE FROM exercise_phases WHERE exerciseId = :exerciseId")
    suspend fun deletePhasesForExercise(exerciseId: Long)

    @Transaction
    suspend fun upsertExerciseWithPhases(exercise: Exercise, phases: List<ExercisePhase>): Long {
        val id = if (exercise.id == 0L) insertExercise(exercise) else {
            updateExercise(exercise)
            exercise.id
        }
        deletePhasesForExercise(id)
        if (phases.isNotEmpty()) {
            insertPhases(phases.map { it.copy(exerciseId = id) })
        }
        return id
    }
}
