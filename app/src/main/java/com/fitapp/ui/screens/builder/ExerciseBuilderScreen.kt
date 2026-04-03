package com.fitapp.ui.screens.builder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.fitapp.data.model.Exercise
import com.fitapp.data.model.relations.ExerciseWithPhases

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    viewModel: WorkoutBuilderViewModel,
    onCreateExercise: () -> Unit,
    onEditExercise: (Long) -> Unit
) {
    val exercises by viewModel.allExercises.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercise Library") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateExercise) {
                Icon(Icons.Default.Add, contentDescription = "Create Exercise")
            }
        }
    ) { padding ->
        if (exercises.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No exercises yet. Tap + to add one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(exercises) { _, ewp ->
                    ExerciseLibraryItem(
                        ewp = ewp,
                        onEdit = { onEditExercise(ewp.exercise.id) },
                        onDelete = { viewModel.deleteExercise(ewp.exercise) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ExerciseLibraryItem(
    ewp: ExerciseWithPhases,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ewp.exercise.name, style = MaterialTheme.typography.titleSmall)
                val detail = buildString {
                    if (ewp.exercise.bpm > 0) append("${ewp.exercise.bpm} BPM")
                    if (ewp.hasPhases) {
                        if (isNotEmpty()) append(" · ")
                        append("${ewp.phases.size} phases")
                    }
                    if (ewp.exercise.isPreloaded) {
                        if (isNotEmpty()) append(" · ")
                        append("Built-in")
                    }
                }
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    if (!ewp.exercise.isPreloaded) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null,
                                tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Exercise editor form
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(
    viewModel: WorkoutBuilderViewModel,
    exerciseId: Long?,
    onBack: () -> Unit
) {
    LaunchedEffect(exerciseId) {
        if (exerciseId != null && exerciseId != 0L) {
            viewModel.loadExerciseForEdit(exerciseId)
        } else {
            viewModel.resetExerciseEditor()
        }
    }

    val editor by viewModel.exerciseEditor.collectAsState()
    val saved by viewModel.exerciseSaved.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (exerciseId == null || exerciseId == 0L) "New Exercise" else "Edit Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveExercise() },
                        enabled = editor.name.isNotBlank()
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
                    onValueChange = { viewModel.updateExerciseName(it) },
                    label = { Text("Exercise Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = { viewModel.updateExerciseDescription(it) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            item {
                OutlinedTextField(
                    value = editor.bpm,
                    onValueChange = { viewModel.updateExerciseBpm(it) },
                    label = { Text("BPM (0 = no metronome)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            item {
                Text("Phases (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            itemsIndexed(editor.phases) { index, phase ->
                PhaseRow(
                    phase = phase,
                    onUpdate = { viewModel.updatePhase(index, it) },
                    onDelete = { viewModel.removePhase(index) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.addPhase() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Phase")
                }
            }
        }
    }
}

@Composable
private fun PhaseRow(
    phase: PhaseEditorState,
    onUpdate: (PhaseEditorState) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = phase.label,
                onValueChange = { onUpdate(phase.copy(label = it)) },
                label = { Text("Label") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = phase.durationSeconds,
                onValueChange = { onUpdate(phase.copy(durationSeconds = it)) },
                label = { Text("Sec") },
                modifier = Modifier.width(72.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Remove phase",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
