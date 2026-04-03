package com.fitapp.data.repository

import com.fitapp.data.db.dao.ExerciseDao
import com.fitapp.data.db.dao.RoutineDao
import com.fitapp.data.db.dao.WorkoutLogDao
import com.fitapp.data.model.Routine
import com.fitapp.data.model.RoutineExercise
import com.fitapp.data.model.WorkoutLog
import com.fitapp.data.model.relations.ExerciseWithPhases
import com.fitapp.data.model.relations.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val logDao: WorkoutLogDao
) {

    fun getAllRoutines(): Flow<List<RoutineWithExercises>> =
        routineDao.getAllRoutinesWithExercises()

    suspend fun getRoutineWithExercises(routineId: Long): RoutineWithExercises? =
        routineDao.getRoutineWithExercises(routineId)

    /**
     * Returns the full exercise detail for each slot in the routine, preserving order.
     */
    suspend fun getFullRoutineExercises(routineId: Long): List<Pair<RoutineExercise, ExerciseWithPhases>> {
        val routineWithExercises = routineDao.getRoutineWithExercises(routineId) ?: return emptyList()
        val sorted = routineWithExercises.routineExercises.sortedBy { it.orderIndex }
        return sorted.mapNotNull { re ->
            val ewp = exerciseDao.getExerciseWithPhases(re.exerciseId)
            if (ewp != null) re to ewp else null
        }
    }

    suspend fun saveRoutine(routine: Routine, exercises: List<RoutineExercise>): Long {
        val routineId = if (routine.id == 0L) {
            routineDao.insertRoutine(routine)
        } else {
            routineDao.updateRoutine(routine)
            routine.id
        }
        routineDao.saveRoutineExercises(routineId, exercises)
        return routineId
    }

    suspend fun deleteRoutine(routine: Routine) =
        routineDao.deleteRoutine(routine)

    fun getAllLogs(): Flow<List<WorkoutLog>> =
        logDao.getAllLogs()

    suspend fun getLogsInRange(startMs: Long, endMs: Long): List<WorkoutLog> =
        logDao.getLogsInRange(startMs, endMs)

    suspend fun logWorkout(log: WorkoutLog): Long =
        logDao.insertLog(log)
}
