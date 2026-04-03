package com.fitapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A timed phase within a custom exercise (e.g. "Down" 2s, "Hold" 1s, "Up" 2s).
 * Simple exercises (like the preloaded Morning Routine) have no phases.
 */
@Entity(
    tableName = "exercise_phases",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class ExercisePhase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val label: String,
    val durationSeconds: Int,
    val orderIndex: Int
)
