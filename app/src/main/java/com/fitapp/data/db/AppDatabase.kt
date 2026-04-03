package com.fitapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitapp.data.db.dao.ExerciseDao
import com.fitapp.data.db.dao.RoutineDao
import com.fitapp.data.db.dao.WorkoutLogDao
import com.fitapp.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Exercise::class,
        ExercisePhase::class,
        Routine::class,
        RoutineExercise::class,
        WorkoutLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutLogDao(): WorkoutLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitapp.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let { database ->
                                seedMorningRoutine(
                                    database.exerciseDao(),
                                    database.routineDao()
                                )
                            }
                        }
                    }
                })
                .build()

        // ---------------------------------------------------------------------------
        // Pre-loaded "Morning Routine" – Chinese Radio Calisthenics 8th Set
        // ---------------------------------------------------------------------------
        private suspend fun seedMorningRoutine(
            exerciseDao: ExerciseDao,
            routineDao: RoutineDao
        ) {
            data class Seed(
                val name: String,
                val description: String,
                val durationSeconds: Int,
                val bpm: Int
            )

            val seeds = listOf(
                Seed(
                    "Limb Stretching",
                    "Swing both arms forward and upward while stepping forward, alternating feet with each swing. Keep the motion fluid and rhythmic.",
                    35, 80
                ),
                Seed(
                    "Chest Expansion",
                    "Raise your fists to chest height, then thrust both arms back behind you repeatedly. Feel your chest open with each repetition.",
                    35, 80
                ),
                Seed(
                    "Leg Kicks",
                    "Perform controlled forward leg raises, alternating sides. Keep the supporting leg straight and the raised leg extended.",
                    35, 80
                ),
                Seed(
                    "Side Bending",
                    "Reach one arm overhead and lean laterally to the opposite side, alternating sides. Keep your hips square and the stretch smooth.",
                    35, 80
                ),
                Seed(
                    "Torso Twisting",
                    "Swing both arms in crossing diagonal patterns while rotating your torso. Let your arms drive the rotation naturally.",
                    35, 80
                ),
                Seed(
                    "Whole-Body Movement",
                    "Perform a compound sequence of squat, lunge, and arm raise. Move through each position in rhythm with the beat.",
                    35, 72
                ),
                Seed(
                    "Jumping",
                    "Perform jumping jacks with an overhead clap variation — jump feet wide while clapping hands above your head, then return.",
                    35, 96
                ),
                Seed(
                    "Dead Hangs",
                    "Hang from a bar with arms fully extended, letting your shoulders relax away from your ears. Breathe steadily and decompress.",
                    40, 0
                )
            )

            val exerciseIds = seeds.map { seed ->
                exerciseDao.insertExercise(
                    Exercise(
                        name = seed.name,
                        description = seed.description,
                        bpm = seed.bpm,
                        isPreloaded = true
                    )
                )
            }

            val routineId = routineDao.insertRoutine(
                Routine(name = "Morning Routine", isPreloaded = true)
            )

            val routineExercises = exerciseIds.mapIndexed { index, exerciseId ->
                RoutineExercise(
                    routineId = routineId,
                    exerciseId = exerciseId,
                    orderIndex = index,
                    restAfterSeconds = 0,
                    durationSeconds = seeds[index].durationSeconds
                )
            }

            routineDao.insertRoutineExercises(routineExercises)
        }
    }
}
