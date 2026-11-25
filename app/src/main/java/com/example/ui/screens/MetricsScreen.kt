package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alarm.AlarmRuntimeStore
import com.example.model.SleepLog
import com.example.model.UiState
import com.example.model.WakeMood
import com.example.ui.components.IndustrialCard
import com.example.ui.components.ReadinessRingCanvas
import com.example.ui.components.SleepTrendChartCanvas
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.KilnStone
import com.example.ui.theme.MutedIron
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TabularMetricStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import com.example.viewmodel.HealthMetricsSummary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricsScreen(
  summaryState: UiState<HealthMetricsSummary>,
  openCheckInImmediately: Boolean,
  onCheckInHandled: () -> Unit,
  onSaveSleepLog: (
    existingId: String,
    dateKey: String,
    bedtimeEpochMs: Long,
    wakeEpochMs: Long,
    durationMinutes: Int,
    qualityRating: Int,
    snoozeCountUsed: Int,
    energyScore: Int,
    mood: WakeMood,
    tags: List<String>,
    notes: String,
    targetSleepMinutes: Int,
    onSaved: () -> Unit,
  ) -> Unit,
  onDeleteSleepLog: (String) -> Unit,
) {
  val context = LocalContext.current
  var showSheet by remember(openCheckInImmediately) { mutableStateOf(openCheckInImmediately) }
  var editingLog by remember { mutableStateOf<SleepLog?>(null) }

  Scaffold(
    containerColor = CastIron,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingLog = null
          showSheet = true
        },
        containerColor = OxidizedTerracotta,
        contentColor = CastIron,
        modifier = Modifier.testTag("log_sleep_fab"),
      ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_log_sleep))
      }
    },
  ) { innerPadding ->
    when (summaryState) {
      UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = OxidizedTerracotta)
        }
      }
      is UiState.Error -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          IndustrialCard {
            Text("Unable to load health metrics", style = MaterialTheme.typography.titleMedium)
            Text(summaryState.message, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
          }
        }
      }
      is UiState.Success -> {
        val summary = summaryState.data
        LazyColumn(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Composite Morning Readiness & 7-Day Averages
          item {
            IndustrialCard(containerColor = SlateCharcoal, borderColor = OxidizedTerracotta.copy(alpha = 0.4f)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                ReadinessRingCanvas(score = summary.latestReadiness)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("SLEEP & READINESS", style = MaterialTheme.typography.labelMedium, color = BrushedBrass)
                  MetricLine(
                    label = "7d Avg Sleep",
                    value =
                      if (summary.avgSleepMinutes7d > 0) "${summary.avgSleepMinutes7d / 60}h ${summary.avgSleepMinutes7d % 60}m"
                      else "--",
                  )
                  MetricLine(
                    label = "7d Avg Readiness",
                    value = if (summary.avgReadiness7d > 0) "${summary.avgReadiness7d}/100" else "--",
                  )
                  MetricLine(
                    label = "Avg Snoozes/Day",
                    value = String.format(Locale.US, "%.1f", summary.avgSnoozes7d),
                  )
                }
              }
            }
          }

          // Routine Discipline Cards
          item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              IndustrialCard(modifier = Modifier.weight(1f), containerColor = KilnStone) {
                Text("ROUTINE COMPLETION", style = MaterialTheme.typography.labelSmall, color = PatinaSage)
                Spacer(Modifier.height(4.dp))
                Text("${summary.routineCompletionRate}%", style = TabularMetricStyle, color = WarmAlabaster)
                Text("${summary.routineStreakDays} active days", style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
              }
              IndustrialCard(modifier = Modifier.weight(1f), containerColor = KilnStone) {
                Text("TARGET WINDOW", style = MaterialTheme.typography.labelSmall, color = BrushedBrass)
                Spacer(Modifier.height(4.dp))
                val target = summary.settings.targetSleepMinutes.toInt()
                Text("${target / 60}h ${target % 60}m", style = TabularMetricStyle, color = WarmAlabaster)
                Text("${summary.logs.size} logged nights", style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
              }
            }
          }

          // 10-Night Sleep Duration & Readiness Chart
          item {
            IndustrialCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Sleep Duration & Readiness Trend", style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
                Text("Gold: Readiness", style = MaterialTheme.typography.labelSmall, color = BrushedBrass)
              }
              Spacer(Modifier.height(12.dp))
              if (summary.logs.isEmpty()) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(Icons.Default.Bedtime, contentDescription = null, tint = OxidizedTerracotta, modifier = Modifier.size(32.dp))
                  Spacer(Modifier.height(8.dp))
                  Text("No sleep telemetry logged yet", style = MaterialTheme.typography.titleMedium)
                  Text(
                    "Log your bedtime, wake quality, and morning check-in to unlock trends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeatheredAsh,
                  )
                  Spacer(Modifier.height(12.dp))
                  Button(
                    onClick = { showSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
                    modifier = Modifier.testTag("empty_log_sleep_button"),
                  ) {
                    Text("Log First Sleep Session", fontWeight = FontWeight.Bold)
                  }
                }
              } else {
                SleepTrendChartCanvas(
                  logs = summary.logs,
                  targetSleepMinutes = summary.settings.targetSleepMinutes.toInt(),
                )
              }
            }
          }

          // Sleep Log History
          if (summary.logs.isNotEmpty()) {
            item {
              Text(
                text = "Sleep & Check-In History",
                style = MaterialTheme.typography.titleMedium,
                color = WarmAlabaster,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
            items(summary.logs, key = { it.id }) { log ->
              Surface(
                onClick = {
                  editingLog = log
                  showSheet = true
                },
                color = SlateCharcoal,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, OxidizedHairline),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column {
                      Text(
                        text = "${log.dateKey} • ${log.durationMinutes / 60}h ${log.durationMinutes % 60}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = WarmAlabaster,
                      )
                      Text(
                        text = "Quality ${log.qualityRating}/5 • Energy ${log.energyScore}/10 • ${WakeMood.safeValueOf(log.mood).label} • ${log.snoozeCountUsed} snoozes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh,
                      )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Surface(
                        color = KilnStone,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PatinaSage.copy(alpha = 0.45f)),
                      ) {
                        Text(
                          text = "${log.readinessScore}",
                          style = TabularMetricStyle,
                          color = PatinaSage,
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                      }
                      IconButton(onClick = { onDeleteSleepLog(log.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_item), tint = MutedIron)
                      }
                    }
                  }
                  if (log.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      log.tags.forEach { tag ->
                        Surface(color = KilnStone, shape = RoundedCornerShape(8.dp)) {
                          Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrushedBrass,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                          )
                        }
                      }
                    }
                  }
                  if (log.notes.isNotBlank()) {
                    Text(text = log.notes, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showSheet) {
    val targetSleep = (summaryState as? UiState.Success)?.data?.settings?.targetSleepMinutes?.toInt() ?: 480
    val lastSnoozes = remember(context) { AlarmRuntimeStore.getLastMorningSnoozes(context) }
    SleepCheckInSheet(
      existingLog = editingLog,
      defaultSnoozesUsed = lastSnoozes,
      targetSleepMinutes = targetSleep,
      onDismiss = {
        showSheet = false
        editingLog = null
        onCheckInHandled()
      },
      onSave = { id, dateKey, bedMs, wakeMs, durMin, quality, snoozes, energy, mood, tags, notes ->
        onSaveSleepLog(id, dateKey, bedMs, wakeMs, durMin, quality, snoozes, energy, mood, tags, notes, targetSleep) {
          showSheet = false
          editingLog = null
          onCheckInHandled()
        }
      },
    )
  }
}

@Composable
private fun MetricLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
    Text(text = value, style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
  }
}
