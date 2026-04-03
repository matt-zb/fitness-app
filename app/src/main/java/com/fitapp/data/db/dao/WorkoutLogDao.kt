package com.fitapp.data.db.dao

import androidx.room.*
import com.fitapp.data.model.WorkoutLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutLogDao {

    @Query("SELECT * FROM workout_logs ORDER BY completedAt DESC")
    fun getAllLogs(): Flow<List<WorkoutLog>>

    @Query(
        """SELECT * FROM workout_logs
           WHERE completedAt >= :startMs AND completedAt < :endMs
           ORDER BY completedAt ASC"""
    )
    suspend fun getLogsInRange(startMs: Long, endMs: Long): List<WorkoutLog>

    @Insert
    suspend fun insertLog(log: WorkoutLog): Long

    @Delete
    suspend fun deleteLog(log: WorkoutLog)
}
