package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.example.R
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmService
import com.example.model.AlarmProfile
import com.example.model.MorningRoutine
import com.example.model.UiState
import com.example.model.UserSettings
import com.example.ui.components.IndustrialCard
import com.example.ui.components.PermissionBanner
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.DeepRustEmber
import com.example.ui.theme.KilnStone
import com.example.ui.theme.MutedIron
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TabularClockDisplayStyle
import com.example.ui.theme.TabularTimerStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmsScreen(
  alarmsState: UiState<List<AlarmProfile>>,
  routines: List<MorningRoutine>,
  settings: UserSettings,
  currentUserId: String,
  onSaveAlarm: (AlarmProfile, () -> Unit) -> Unit,
  onToggleAlarm: (String, Boolean) -> Unit,
  onDeleteAlarm: (String) -> Unit,
  onCreateStarterAlarm: () -> Unit,
  onOpenCheckIn: () -> Unit,
) {
  val context = LocalContext.current
  var editingAlarm by remember { mutableStateOf<AlarmProfile?>(null) }

  var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      nowMillis = System.currentTimeMillis()
      delay(1000L)
    }
  }

  var hasNotifPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
  }
  val notifLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotifPermission = granted
    }
  val canExactAlarm = remember(nowMillis) { AlarmScheduler.canScheduleExactAlarms(context) }

  if (alarmsState is UiState.Success) {
    LaunchedEffect(alarmsState.data) {
      AlarmScheduler.syncAlarms(context.applicationContext, alarmsState.data)
    }
  }

  Scaffold(
    containerColor = CastIron,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editingAlarm =
            AlarmProfile(
              userId = currentUserId,
              hour = 7L,
              minute = 0L,
              repeatDays = listOf(1L, 2L, 3L, 4L, 5L),
              rampSeconds = settings.defaultRampSeconds,
              sunriseEnabled = settings.sunriseGlowEnabled,
              snoozeIntervalMinutes = settings.defaultSnoozeMinutes,
              snoozeMaxCount = settings.defaultSnoozeMaxCount,
              linkedRoutineId = routines.firstOrNull()?.id,
            )
        },
        containerColor = OxidizedTerracotta,
        contentColor = CastIron,
        modifier = Modifier.testTag("add_alarm_fab"),
      ) {
        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_alarm))
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        LiveClockHeader(
          nowMillis = nowMillis,
          alarms = (alarmsState as? UiState.Success)?.data.orEmpty(),
          onQuickCheckIn = onOpenCheckIn,
        )
      }

      if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          PermissionBanner(
            icon = Icons.Default.NotificationsActive,
            title = stringResource(R.string.permission_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            actionLabel = "Allow",
            onAction = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
          )
        }
      }

      if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item {
          PermissionBanner(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.permission_exact_alarm_title),
            body = stringResource(R.string.permission_exact_alarm_body),
            actionLabel = "Settings",
            onAction = {
              runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
              }
            },
          )
        }
      }

      when (alarmsState) {
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
              Text("Unable to sync alarms", style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
              Spacer(Modifier.height(4.dp))
              Text(alarmsState.message, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
            }
          }
        }
        is UiState.Success -> {
          if (alarmsState.data.isEmpty()) {
            item {
              IndustrialCard {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = OxidizedTerracotta, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("No Alarms Armed", style = MaterialTheme.typography.headlineSmall, color = WarmAlabaster)
                Spacer(Modifier.height(4.dp))
                Text(
                  "Create a custom alarm with diminishing snooze nudges, sunrise glow, and morning routine launch.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = WeatheredAsh,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                  onClick = onCreateStarterAlarm,
                  colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
                  modifier = Modifier.testTag("create_starter_alarm_button"),
                ) {
                  Text("Add 06:30 Starter Alarm", fontWeight = FontWeight.Bold)
                }
              }
            }
          } else {
            items(alarmsState.data, key = { it.id }) { alarm ->
              AlarmProfileCard(
                alarm = alarm,
                routineTitle = routines.firstOrNull { it.id == alarm.linkedRoutineId }?.title,
                onToggle = { enabled -> onToggleAlarm(alarm.id, enabled) },
                onEdit = { editingAlarm = alarm },
                onDelete = { onDeleteAlarm(alarm.id) },
                onPreview = {
                  val previewIntent =
                    Intent(context, AlarmService::class.java).apply {
                      action = AlarmService.ACTION_START
                      putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                      putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarm.label)
                      putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
                      putExtra(AlarmScheduler.EXTRA_IS_PREVIEW, true)
                    }
                  ContextCompat.startForegroundService(context, previewIntent)
                },
              )
            }
          }
        }
      }
    }
  }

  editingAlarm?.let { target ->
    AlarmEditorSheet(
      initialAlarm = target,
      routines = routines,
      onDismiss = { editingAlarm = null },
      onSave = { updated -> onSaveAlarm(updated) { editingAlarm = null } },
    )
  }
}

@Composable
private fun LiveClockHeader(
  nowMillis: Long,
  alarms: List<AlarmProfile>,
  onQuickCheckIn: () -> Unit,
) {
  val timeStr = remember(nowMillis) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nowMillis)) }
  val dateStr = remember(nowMillis) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(nowMillis)).uppercase() }
  val nextAlarmMillis =
    remember(alarms, nowMillis / 60_000L) {
      alarms.filter { it.enabled }.minOfOrNull { AlarmScheduler.computeNextTriggerMillis(it, nowMillis) }
    }

  IndustrialCard(containerColor = SlateCharcoal, borderColor = OxidizedTerracotta.copy(alpha = 0.45f)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = dateStr, style = MaterialTheme.typography.labelMedium, color = BrushedBrass)
      TextButton(onClick = onQuickCheckIn, modifier = Modifier.testTag("quick_sleep_checkin_button")) {
        Text("Log Sleep", color = PatinaSage, fontWeight = FontWeight.SemiBold)
      }
    }
    Text(text = timeStr, style = TabularClockDisplayStyle, color = WarmAlabaster)
    Spacer(modifier = Modifier.height(6.dp))
    val countdownText =
      if (nextAlarmMillis == null) {
        "All alarms disarmed"
      } else {
        val diffMin = ((nextAlarmMillis - nowMillis) / 60_000L).coerceAtLeast(1L)
        val hrs = diffMin / 60L
        val mins = diffMin % 60L
        "Next wake in ${hrs}h ${mins}m"
      }
    Text(text = countdownText, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
  }
}

@Composable
private fun AlarmProfileCard(
  alarm: AlarmProfile,
  routineTitle: String?,
  onToggle: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onPreview: () -> Unit,
) {
  val dayShorts = mapOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
  Surface(
    onClick = onEdit,
    color = if (alarm.enabled) SlateCharcoal else CastIron.copy(alpha = 0.85f),
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, if (alarm.enabled) OxidizedHairline else OxidizedHairline.copy(alpha = 0.5f)),
    modifier = Modifier.fillMaxWidth().testTag("alarm_card_${alarm.id}"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "%02d:%02d".format(alarm.hour, alarm.minute),
            style = TabularTimerStyle,
            color = if (alarm.enabled) WarmAlabaster else MutedIron,
          )
          Text(
            text = alarm.label.ifBlank { "Morning Alarm" },
            style = MaterialTheme.typography.titleMedium,
            color = if (alarm.enabled) WeatheredAsh else MutedIron,
          )
        }
        Switch(
          checked = alarm.enabled,
          onCheckedChange = onToggle,
          colors = SwitchDefaults.colors(checkedThumbColor = CastIron, checkedTrackColor = OxidizedTerracotta),
          modifier = Modifier.testTag("alarm_toggle_${alarm.id}"),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (alarm.repeatDays.isEmpty()) {
          BadgePill(text = "Once", accent = WeatheredAsh)
        } else {
          (1..7).forEach { day ->
            val active = day.toLong() in alarm.repeatDays
            Text(
              text = dayShorts[day].orEmpty(),
              style = MaterialTheme.typography.labelMedium,
              color = if (active) OxidizedTerracotta else MutedIron,
            )
          }
        }
        Spacer(Modifier.width(6.dp))
        BadgePill(text = alarm.snoozeSummary(), accent = BrushedBrass)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${alarm.soundTitle} • Ramp ${alarm.rampSeconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = WeatheredAsh,
          )
          if (routineTitle != null) {
            Text(
              text = "Routine: $routineTitle",
              style = MaterialTheme.typography.labelMedium,
              color = PatinaSage,
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedButton(
            onClick = onPreview,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DeepRustEmber),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.testTag("preview_alarm_${alarm.id}"),
          ) {
            Icon(Icons.Default.WbTwilight, contentDescription = stringResource(R.string.cd_preview_alarm), tint = BrushedBrass, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Test Wake", style = MaterialTheme.typography.labelMedium, color = BrushedBrass)
          }
          IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_alarm_${alarm.id}")) {
            Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.cd_delete_item), tint = MutedIron)
          }
        }
      }
    }
  }
}

@Composable
private fun BadgePill(text: String, accent: androidx.compose.ui.graphics.Color) {
  Surface(
    color = KilnStone,
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, OxidizedHairline),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = accent,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
