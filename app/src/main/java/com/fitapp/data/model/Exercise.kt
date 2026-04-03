package com.fitapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    /** Beats per minute. 0 = no metronome. */
    val bpm: Int = 0,
    val isPreloaded: Boolean = false
)
