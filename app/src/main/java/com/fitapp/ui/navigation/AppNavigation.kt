package com.fitapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.fitapp.FitnessApplication
import com.fitapp.ui.screens.builder.*
import com.fitapp.ui.screens.home.*
import com.fitapp.ui.screens.log.*
import com.fitapp.ui.screens.runner.*
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------------------
// Route constants
// ---------------------------------------------------------------------------

object Route {
    const val HOME = "home"
    const val EXERCISES = "exercises"
    const val LOG = "log"
    const val ROUTINE_RUNNER = "runner/{routineId}"
    const val ROUTINE_BUILDER = "routine_builder?routineId={routineId}"
    const val EXERCISE_EDITOR = "exercise_editor?exerciseId={exerciseId}"

    fun runnerFor(routineId: Long) = "runner/$routineId"
    fun routineBuilderFor(routineId: Long?) =
        if (routineId != null) "routine_builder?routineId=$routineId" else "routine_builder?routineId=0"
    fun exerciseEditorFor(exerciseId: Long?) =
        if (exerciseId != null) "exercise_editor?exerciseId=$exerciseId" else "exercise_editor?exerciseId=0"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

private val bottomNavItems = listOf(
    BottomNavItem(Route.HOME, "Routines", Icons.Default.FitnessCenter),
    BottomNavItem(Route.EXERCISES, "Exercises", Icons.Default.ListAlt),
    BottomNavItem(Route.LOG, "Log", Icons.Default.CalendarMonth)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FitnessApplication

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    // Shared ViewModels scoped to the nav graph
    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app.workoutRepository))
    val builderVm: WorkoutBuilderViewModel = viewModel(
        factory = WorkoutBuilderViewModel.Factory(app.exerciseRepository, app.workoutRepository)
    )
    val logVm: LogViewModel = viewModel(factory = LogViewModel.Factory(app.workoutRepository))

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.HOME,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            // ----------------------------------------------------------------
            // Home / Routines list
            // ----------------------------------------------------------------
            composable(Route.HOME) {
                HomeScreen(
                    viewModel = homeVm,
                    onStartRoutine = { routineId ->
                        navController.navigate(Route.runnerFor(routineId))
                    },
                    onEditRoutine = { routineId ->
                        navController.navigate(Route.routineBuilderFor(routineId))
                    },
                    onCreateRoutine = {
                        navController.navigate(Route.routineBuilderFor(null))
                    }
                )
            }

            // ----------------------------------------------------------------
            // Exercise library + editor
            // ----------------------------------------------------------------
            composable(Route.EXERCISES) {
                ExerciseLibraryScreen(
                    viewModel = builderVm,
                    onCreateExercise = {
                        navController.navigate(Route.exerciseEditorFor(null))
                    },
                    onEditExercise = { exerciseId ->
                        navController.navigate(Route.exerciseEditorFor(exerciseId))
                    }
                )
            }

            composable(
                route = Route.EXERCISE_EDITOR,
                arguments = listOf(navArgument("exerciseId") {
                    type = NavType.LongType; defaultValue = 0L
                })
            ) { backStackEntry ->
                val exerciseId = backStackEntry.arguments?.getLong("exerciseId").takeIf { it != 0L }
                ExerciseEditorScreen(
                    viewModel = builderVm,
                    exerciseId = exerciseId,
                    onBack = { navController.popBackStack() }
                )
            }

            // ----------------------------------------------------------------
            // Routine builder
            // ----------------------------------------------------------------
            composable(
                route = Route.ROUTINE_BUILDER,
                arguments = listOf(navArgument("routineId") {
                    type = NavType.LongType; defaultValue = 0L
                })
            ) { backStackEntry ->
                val routineId = backStackEntry.arguments?.getLong("routineId").takeIf { it != 0L }
                RoutineBuilderScreen(
                    viewModel = builderVm,
                    routineId = routineId,
                    onBack = { navController.popBackStack() }
                )
            }

            // ----------------------------------------------------------------
            // Workout runner (full-screen, no bottom bar)
            // ----------------------------------------------------------------
            composable(
                route = Route.ROUTINE_RUNNER,
                arguments = listOf(navArgument("routineId") { type = NavType.LongType })
            ) { backStackEntry ->
                val routineId = backStackEntry.arguments!!.getLong("routineId")
                val runnerVm: WorkoutRunnerViewModel = viewModel(
                    factory = WorkoutRunnerViewModel.Factory(routineId, app.workoutRepository)
                )
                WorkoutRunnerScreen(
                    viewModel = runnerVm,
                    onFinished = { navController.popBackStack() }
                )
            }

            // ----------------------------------------------------------------
            // Log / calendar
            // ----------------------------------------------------------------
            composable(Route.LOG) {
                LogScreen(viewModel = logVm)
            }
        }
    }
}
