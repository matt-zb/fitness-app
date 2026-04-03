package com.fitapp.data.db.dao

import androidx.room.*
import com.fitapp.data.model.Routine
import com.fitapp.data.model.RoutineExercise
import com.fitapp.data.model.relations.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routines ORDER BY isPreloaded DESC, name ASC")
    fun getAllRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineWithExercises(routineId: Long): RoutineWithExercises?

    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutine(routineId: Long): Routine?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(entries: List<RoutineExercise>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: Long)

    @Transaction
    suspend fun saveRoutineExercises(routineId: Long, entries: List<RoutineExercise>) {
        clearRoutineExercises(routineId)
        insertRoutineExercises(entries.mapIndexed { i, re ->
            re.copy(routineId = routineId, orderIndex = i)
        })
    }

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY orderIndex ASC")
    suspend fun getRoutineExercises(routineId: Long): List<RoutineExercise>
}
