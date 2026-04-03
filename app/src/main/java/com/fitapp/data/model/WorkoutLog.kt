package com.fitapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_logs",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [Index("routineId")]
)
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long = 0,
    /** Snapshot of the routine name at time of logging. */
    val routineName: String,
    /** Unix epoch milliseconds. */
    val completedAt: Long = System.currentTimeMillis(),
    val durationSeconds: Int
)
