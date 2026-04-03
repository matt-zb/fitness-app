package com.fitapp

import android.app.Application
import com.fitapp.data.db.AppDatabase
import com.fitapp.data.repository.ExerciseRepository
import com.fitapp.data.repository.WorkoutRepository

class FitnessApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val exerciseRepository: ExerciseRepository by lazy {
        ExerciseRepository(database.exerciseDao())
    }

    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository(
            database.routineDao(),
            database.exerciseDao(),
            database.workoutLogDao()
        )
    }
}
