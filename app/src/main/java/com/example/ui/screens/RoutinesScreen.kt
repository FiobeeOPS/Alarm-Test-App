package com.example.ui.screens

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.MorningRoutine
import com.example.model.RoutineRun
import com.example.model.RoutineStep
import com.example.model.UiState
import com.example.ui.components.IndustrialCard
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.KilnStone
import com.example.ui.theme.MutedIron
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TabularClockDisplayStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import com.example.viewmodel.ActiveRoutineTimerState
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun RoutinesScreen(
  routinesState: UiState<List<MorningRoutine>>,
  routineRuns: List<RoutineRun>,
  activeTimer: ActiveRoutineTimerState?,
  stepChimeEvents: SharedFlow<Unit>,
  currentUserId: String,
  onCreateStarterRoutine: () -> Unit,
  onSaveRoutine: (MorningRoutine, () -> Unit) -> Unit,
  onDeleteRoutine: (String) -> Unit,
  onStartRoutine: (MorningRoutine) -> Unit,
  onTogglePause: () -> Unit,
  onCompleteStep: () -> Unit,
  onSkipStep: () -> Unit,
  onCancelTimer: (Boolean) -> Unit,
) {
  val context = LocalContext.current
  var editingRoutine by remember { mutableStateOf<MorningRoutine?>(null) }

  LaunchedEffect(stepChimeEvents) {
    stepChimeEvents.collect {
      runCatching {
        val player = MediaPlayer.create(context, R.raw.chime_step_complete)
        player?.setOnCompletionListener { it.release() }
        player?.start()
      }
    }
  }

  if (activeTimer != null) {
    BackHandler { onCancelTimer(true) }
    HabitTimerRunnerView(
      state = activeTimer,
      onTogglePause = onTogglePause,
      onCompleteStep = onCompleteStep,
      onSkipStep = onSkipStep,
      onClose = { onCancelTimer(false) },
    )
    return
  }

  Scaffold(
    containerColor = CastIron,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingRoutine =
            MorningRoutine(
              userId = currentUserId,
              title = "",
              steps =
                listOf(
                  RoutineStep(stepId = "step_1", title = "Hydrate 500ml", durationMinutes = 2L),
                  RoutineStep(stepId = "step_2", title = "Stretch & Mobility", durationMinutes = 5L),
                ),
            )
        },
        containerColor = OxidizedTerracotta,
        contentColor = CastIron,
        modifier = Modifier.testTag("add_routine_fab"),
      ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_routine))
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        val completedCount = routineRuns.count { it.completed }
        IndustrialCard(containerColor = SlateCharcoal, borderColor = PatinaSage.copy(alpha = 0.4f)) {
          Text("MORNING ROUTINES & HABIT TIMER", style = MaterialTheme.typography.labelMedium, color = PatinaSage)
          Spacer(Modifier.height(6.dp))
          Text(
            text = "$completedCount Completed Sessions",
            style = MaterialTheme.typography.headlineMedium,
            color = WarmAlabaster,
          )
          Text(
            text = "Chain alarm dismissal into timed morning habits for higher energy & readiness.",
            style = MaterialTheme.typography.bodyMedium,
            color = WeatheredAsh,
          )
        }
      }

      when (routinesState) {
        UiState.Loading -> {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = OxidizedTerracotta)
            }
          }
        }
        is UiState.Error -> {
          item {
            IndustrialCard {
              Text("Unable to load routines", style = MaterialTheme.typography.titleMedium)
              Text(routinesState.message, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
            }
          }
        }
        is UiState.Success -> {
          if (routinesState.data.isEmpty()) {
            item {
              IndustrialCard {
                Icon(Icons.Default.Timer, contentDescription = null, tint = PatinaSage, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("Build Your Morning Protocol", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                  "Start with a proven 16-minute hydration, sunlight, mobility, and focus sequence.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = WeatheredAsh,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                  onClick = onCreateStarterRoutine,
                  colors = ButtonDefaults.buttonColors(containerColor = PatinaSage, contentColor = CastIron),
                  modifier = Modifier.testTag("create_starter_routine_button"),
                ) {
                  Text("Load Foundry Morning Protocol", fontWeight = FontWeight.Bold)
                }
              }
            }
          } else {
            items(routinesState.data, key = { it.id }) { routine ->
              RoutineCard(
                routine = routine,
                onStart = { onStartRoutine(routine) },
                onEdit = { editingRoutine = routine },
                onDelete = { onDeleteRoutine(routine.id) },
              )
            }
          }
        }
      }

      if (routineRuns.isNotEmpty()) {
        item {
          Text(
            text = "Recent Routine Runs",
            style = MaterialTheme.typography.titleMedium,
            color = WarmAlabaster,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        items(routineRuns.take(6), key = { it.id }) { run ->
          IndustrialCard(containerColor = KilnStone) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(run.routineTitle, style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
                Text(
                  "${run.dateKey} • ${run.completedStepCount}/${run.totalStepCount} steps • ${run.elapsedSeconds / 60}m ${run.elapsedSeconds % 60}s",
                  style = MaterialTheme.typography.bodyMedium,
                  color = WeatheredAsh,
                )
              }
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (run.completed) PatinaSage else BrushedBrass,
              )
            }
          }
        }
      }
    }
  }

  editingRoutine?.let { target ->
    RoutineEditorDialog(
      initial = target,
      onDismiss = { editingRoutine = null },
      onSave = { updated -> onSaveRoutine(updated) { editingRoutine = null } },
    )
  }
}

@Composable
private fun RoutineCard(
  routine: MorningRoutine,
  onStart: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  IndustrialCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(routine.title, style = MaterialTheme.typography.titleLarge, color = WarmAlabaster)
        Text(
          "${routine.steps.size} steps • ${routine.totalMinutes()} min total",
          style = MaterialTheme.typography.bodyMedium,
          color = BrushedBrass,
        )
      }
      Row {
        IconButton(onClick = onEdit) {
          Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_item), tint = WeatheredAsh)
        }
        IconButton(onClick = onDelete) {
          Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_item), tint = MutedIron)
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      routine.steps.forEachIndexed { idx, step ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("${idx + 1}. ${step.title}", style = MaterialTheme.typography.bodyMedium, color = WarmAlabaster)
          Text("${step.durationMinutes}m", style = MaterialTheme.typography.labelMedium, color = WeatheredAsh)
        }
      }
    }

    Spacer(Modifier.height(14.dp))
    Button(
      onClick = onStart,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_routine_${routine.id}"),
      shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
    ) {
      Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text("Start Habit Timer", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun HabitTimerRunnerView(
  state: ActiveRoutineTimerState,
  onTogglePause: () -> Unit,
  onCompleteStep: () -> Unit,
  onSkipStep: () -> Unit,
  onClose: () -> Unit,
) {
  val currentStep = state.routine.steps.getOrNull(state.currentStepIndex)
  val progress =
    if (state.finished) 1f
    else 1f - (state.remainingSeconds.toFloat() / state.stepTotalSeconds.coerceAtLeast(1).toFloat())

  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Column {
        Text(state.routine.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = BrushedBrass)
        Text(
          "Step ${minOf(state.currentStepIndex + 1, state.routine.steps.size)} of ${state.routine.steps.size}",
          style = MaterialTheme.typography.titleMedium,
          color = WarmAlabaster,
        )
      }
      IconButton(onClick = onClose, modifier = Modifier.testTag("close_habit_timer")) {
        Icon(Icons.Default.Close, contentDescription = "Close timer", tint = WeatheredAsh)
      }
    }

    if (state.finished) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PatinaSage, modifier = Modifier.size(72.dp))
        Text("Morning Protocol Complete", style = MaterialTheme.typography.displaySmall, color = WarmAlabaster)
        Text(
          "Completed ${state.completedStepIndices.size}/${state.routine.steps.size} habits in ${state.totalElapsedSeconds / 60}m ${state.totalElapsedSeconds % 60}s.",
          style = MaterialTheme.typography.bodyLarge,
          color = WeatheredAsh,
        )
      }
      Button(
        onClick = onClose,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PatinaSage, contentColor = CastIron),
      ) {
        Text("Return to Routines", fontWeight = FontWeight.Bold)
      }
    } else {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
          val stroke = 14.dp.toPx()
          drawArc(
            color = KilnStone,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
          drawArc(
            color = OxidizedTerracotta,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
          )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          val mins = state.remainingSeconds / 60
          val secs = state.remainingSeconds % 60
          Text(text = "%02d:%02d".format(mins, secs), style = TabularClockDisplayStyle, color = WarmAlabaster)
          Spacer(Modifier.height(4.dp))
          Text(
            text = currentStep?.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = BrushedBrass,
          )
        }
      }

      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedButton(
            onClick = onTogglePause,
            modifier = Modifier.weight(1f).height(52.dp).testTag("habit_pause_button"),
          ) {
            Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (state.isPaused) "Resume" else "Pause")
          }
          OutlinedButton(
            onClick = onSkipStep,
            modifier = Modifier.weight(1f).height(52.dp).testTag("habit_skip_button"),
          ) {
            Icon(Icons.Default.SkipNext, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Skip")
          }
        }
        Button(
          onClick = onCompleteStep,
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("habit_complete_step_button"),
          colors = ButtonDefaults.buttonColors(containerColor = PatinaSage, contentColor = CastIron),
        ) {
          Icon(Icons.Default.Check, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Complete Habit Step", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun RoutineEditorDialog(
  initial: MorningRoutine,
  onDismiss: () -> Unit,
  onSave: (MorningRoutine) -> Unit,
) {
  var title by remember { mutableStateOf(initial.title) }
  val steps = remember { mutableStateListOf<RoutineStep>().apply { addAll(initial.steps) } }
  var newStepTitle by remember { mutableStateOf("") }
  var newStepMinutes by remember { mutableStateOf("5") }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = SlateCharcoal,
      shape = RoundedCornerShape(22.dp),
      border = BorderStroke(1.dp, OxidizedHairline),
    ) {
      Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = if (initial.id.isBlank()) "New Morning Routine" else "Edit Routine",
          style = MaterialTheme.typography.headlineSmall,
          color = WarmAlabaster,
        )
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 60) title = it },
          label = { Text("Routine Title") },
          placeholder = { Text("e.g., Iron Morning") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        steps.forEachIndexed { idx, step ->
          Surface(color = KilnStone, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, OxidizedHairline)) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("${idx + 1}. ${step.title} (${step.durationMinutes}m)", modifier = Modifier.weight(1f))
              IconButton(onClick = { if (steps.size > 1) steps.removeAt(idx) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove step", tint = MutedIron)
              }
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = newStepTitle,
            onValueChange = { if (it.length <= 50) newStepTitle = it },
            label = { Text("Add Habit Step") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = newStepMinutes,
            onValueChange = { if (it.length <= 3) newStepMinutes = it.filter { c -> c.isDigit() } },
            label = { Text("Min") },
            singleLine = true,
            modifier = Modifier.width(68.dp),
          )
        }
        TextButton(
          onClick = {
            if (newStepTitle.isNotBlank() && steps.size < 20) {
              val mins = (newStepMinutes.toLongOrNull() ?: 5L).coerceIn(1L, 120L)
              steps.add(RoutineStep(stepId = "step_${steps.size + 1}", title = newStepTitle.trim(), durationMinutes = mins))
              newStepTitle = ""
            }
          },
        ) {
          Text("+ Add Step to Checklist", color = BrushedBrass)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = onDismiss) { Text("Cancel", color = WeatheredAsh) }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = {
              onSave(
                initial.copy(
                  title = title.trim().ifBlank { "Morning Routine" },
                  steps = steps.toList(),
                )
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
          ) {
            Text("Save", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
