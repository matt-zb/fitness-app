package com.fitapp.ui.screens.runner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitapp.data.model.ExercisePhase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutRunnerScreen(
    viewModel: WorkoutRunnerViewModel,
    onFinished: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Workout") },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(Icons.Default.Close, contentDescription = "Exit")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val s = state) {
                is RunnerState.Loading -> CircularProgressIndicator()

                is RunnerState.Running -> RunningContent(
                    state = s,
                    onPauseResume = { viewModel.pauseResume() },
                    onSkip = { viewModel.skipToNext() }
                )

                is RunnerState.Completed -> CompletedContent(
                    state = s,
                    onLog = {
                        scope.launch {
                            viewModel.logSession(s.totalDurationSeconds)
                            snackbarHostState.showSnackbar("Workout logged!")
                            onFinished()
                        }
                    },
                    onDismiss = onFinished
                )

                is RunnerState.Error -> ErrorContent(message = s.message, onDismiss = onFinished)
            }
        }
    }
}

@Composable
private fun RunningContent(
    state: RunnerState.Running,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit
) {
    val exercise = state.currentExercise
    val phases = state.currentPhases

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Overall progress
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Exercise ${state.currentSlotIndex + 1} of ${state.slots.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small),
                strokeCap = StrokeCap.Round
            )
        }

        // Exercise name + description
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = exercise.name,
                transitionSpec = {
                    slideInVertically { it } + fadeIn() togetherWith
                            slideOutVertically { -it } + fadeOut()
                },
                label = "exercise-name"
            ) { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = exercise.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Phase indicator
            if (phases.isNotEmpty() && state.activePhaseIndex != null) {
                Spacer(Modifier.height(16.dp))
                val activePhase = phases.getOrNull(state.activePhaseIndex)
                PhaseIndicator(phases = phases, activeIndex = state.activePhaseIndex)
                if (activePhase != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = activePhase.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Countdown timer
        Box(contentAlignment = Alignment.Center) {
            val animatedProgress by animateFloatAsState(
                targetValue = if (state.secondsRemaining > 0) {
                    val total = state.currentSlot.routineExercise.durationSeconds.toFloat()
                    state.secondsRemaining / total
                } else 0f,
                animationSpec = tween(900),
                label = "timer-progress"
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(180.dp),
                strokeWidth = 10.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(state.secondsRemaining),
                    style = MaterialTheme.typography.displayMedium,
                )
                if (state.isPaused) {
                    Text("Paused", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (exercise.bpm > 0) {
                    Text("${exercise.bpm} BPM", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPauseResume,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                    modifier = Modifier.size(36.dp)
                )
            }
            FilledTonalIconButton(
                onClick = onSkip,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip",
                    modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun PhaseIndicator(phases: List<com.fitapp.data.model.ExercisePhase>, activeIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        phases.forEachIndexed { i, phase ->
            val isActive = i == activeIndex
            Surface(
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(0.dp)
            ) {
                Text(
                    text = phase.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompletedContent(
    state: RunnerState.Completed,
    onLog: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("Workout Complete!", style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.routineName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Duration: ${formatTime(state.totalDurationSeconds)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onLog, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Log Session")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Dismiss")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null,
            modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss) { Text("Go Back") }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "${m}m ${s.toString().padStart(2, '0')}s"
    else "${s}s"
}
