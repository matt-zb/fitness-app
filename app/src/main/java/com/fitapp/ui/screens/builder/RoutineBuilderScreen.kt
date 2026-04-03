package com.fitapp.ui.screens.builder

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitapp.data.model.relations.ExerciseWithPhases

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineBuilderScreen(
    viewModel: WorkoutBuilderViewModel,
    routineId: Long?,
    onBack: () -> Unit
) {
    LaunchedEffect(routineId) {
        if (routineId != null && routineId != 0L) {
            viewModel.loadRoutineForEdit(routineId)
        } else {
            viewModel.resetRoutineEditor()
        }
    }

    val editor by viewModel.routineEditor.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()
    val saved by viewModel.routineSaved.collectAsState()
    var showExercisePicker by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == null || routineId == 0L) "New Routine" else "Edit Routine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveRoutine() },
                        enabled = editor.name.isNotBlank() && editor.slots.isNotEmpty()
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { viewModel.updateRoutineName(it) },
                    label = { Text("Routine Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Exercises (${editor.slots.size})",
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { showExercisePicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }

            if (editor.slots.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "No exercises added yet. Tap Add to pick from the library.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            itemsIndexed(editor.slots, key = { i, s -> "${s.exerciseId}_$i" }) { index, slot ->
                RoutineSlotCard(
                    slot = slot,
                    index = index,
                    total = editor.slots.size,
                    onMoveUp = { if (index > 0) viewModel.moveSlot(index, index - 1) },
                    onMoveDown = { if (index < editor.slots.size - 1) viewModel.moveSlot(index, index + 1) },
                    onRemove = { viewModel.removeSlot(index) },
                    onDurationChange = { viewModel.updateSlotDuration(index, it) },
                    onRestChange = { viewModel.updateSlotRest(index, it) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showExercisePicker) {
        ExercisePickerSheet(
            exercises = allExercises,
            onPick = { ewp ->
                viewModel.addExerciseToRoutine(ewp)
                showExercisePicker = false
            },
            onDismiss = { showExercisePicker = false }
        )
    }
}

@Composable
private fun RoutineSlotCard(
    slot: RoutineSlotState,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDurationChange: (String) -> Unit,
    onRestChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder arrows
                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, null,
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < total - 1,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, null,
                            modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("${index + 1}. ${slot.exerciseName}",
                        style = MaterialTheme.typography.titleSmall)
                    Text("${slot.durationSeconds}s · rest ${slot.restAfterSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand"
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = slot.durationSeconds,
                            onValueChange = onDurationChange,
                            label = { Text("Duration (s)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = slot.restAfterSeconds,
                            onValueChange = onRestChange,
                            label = { Text("Rest after (s)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    exercises: List<ExerciseWithPhases>,
    onPick: (ExerciseWithPhases) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Pick an Exercise",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        if (exercises.isEmpty()) {
            Text(
                "No exercises available. Create one in the Exercise Library tab.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(exercises) { ewp ->
                    val detail = buildString {
                        if (ewp.exercise.bpm > 0) append("${ewp.exercise.bpm} BPM")
                        if (ewp.hasPhases) {
                            if (isNotEmpty()) append(" · ")
                            append("${ewp.phases.size} phases")
                        }
                    }
                    Surface(
                        onClick = { onPick(ewp) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text(ewp.exercise.name) },
                            supportingContent = { if (detail.isNotEmpty()) Text(detail) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
