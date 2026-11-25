package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.alarm.AlarmAudioPlayer
import com.example.model.AlarmProfile
import com.example.model.BuiltInSound
import com.example.model.MorningRoutine
import com.example.model.SnoozeMode
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.DeepBrassContainer
import com.example.ui.theme.DeepRustEmber
import com.example.ui.theme.KilnStone
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TabularTimerStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorSheet(
  initialAlarm: AlarmProfile,
  routines: List<MorningRoutine>,
  onDismiss: () -> Unit,
  onSave: (AlarmProfile) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val audioPlayer = remember(context) { AlarmAudioPlayer(context.applicationContext) }
  var isPreviewing by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onDispose { audioPlayer.stop() }
  }

  var hour by remember { mutableIntStateOf(initialAlarm.hour.toInt().coerceIn(0, 23)) }
  var minute by remember { mutableIntStateOf(initialAlarm.minute.toInt().coerceIn(0, 59)) }
  var label by remember { mutableStateOf(initialAlarm.label) }
  val selectedDays = remember { mutableStateListOf<Int>().apply { addAll(initialAlarm.repeatDays.map { it.toInt() }) } }
  var vibrate by remember { mutableStateOf(initialAlarm.vibrate) }
  var sunriseEnabled by remember { mutableStateOf(initialAlarm.sunriseEnabled) }
  var soundId by remember { mutableStateOf(initialAlarm.soundId) }
  var soundTitle by remember { mutableStateOf(initialAlarm.soundTitle) }
  var soundUri by remember { mutableStateOf(initialAlarm.soundUri) }
  var rampSeconds by remember { mutableIntStateOf(initialAlarm.rampSeconds.toInt()) }
  var maxVolumePercent by remember { mutableIntStateOf(initialAlarm.maxVolumePercent.toInt()) }
  var snoozeMode by remember { mutableStateOf(SnoozeMode.safeValueOf(initialAlarm.snoozeMode)) }
  var snoozeInterval by remember { mutableIntStateOf(initialAlarm.snoozeIntervalMinutes.toInt().coerceIn(1, 30)) }
  var snoozeMaxCount by remember { mutableIntStateOf(initialAlarm.snoozeMaxCount.toInt().coerceIn(0, 10)) }
  val customSeq = remember {
    mutableStateListOf<Int>().apply {
      val initialSeq = initialAlarm.snoozeSequenceMinutes.map { it.toInt().coerceIn(1, 30) }
      if (initialSeq.isEmpty()) addAll(listOf(9, 6, 3)) else addAll(initialSeq)
    }
  }
  var linkedRoutineId by remember { mutableStateOf(initialAlarm.linkedRoutineId) }

  val ringtoneLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        val pickedUri =
          result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (pickedUri != null) {
          val ringtoneTitle = RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context) ?: "System Alarm"
          soundId = BuiltInSound.SYSTEM_RINGTONE.id
          soundTitle = ringtoneTitle.take(80)
          soundUri = pickedUri.toString()
        }
      }
    }

  val documentAudioLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val displayName =
          context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
          } ?: "Custom Audio"
        soundId = BuiltInSound.CUSTOM_AUDIO.id
        soundTitle = displayName.substringBeforeLast('.').take(80).ifBlank { "Custom Audio" }
        soundUri = uri.toString()
      }
    }

  ModalBottomSheet(
    onDismissRequest = {
      audioPlayer.stop()
      onDismiss()
    },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = SlateCharcoal,
    contentColor = WarmAlabaster,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (initialAlarm.id.isBlank()) "Configure Alarm" else "Edit Alarm",
          style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
          color = WarmAlabaster,
        )
        TextButton(onClick = onDismiss) { Text("Cancel", color = WeatheredAsh) }
      }

      // Precision Time Stepper
      Surface(
        color = KilnStone,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OxidizedHairline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 20.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TimeStepperColumn(
            value = hour,
            label = "HOUR",
            onIncrement = { hour = (hour + 1) % 24 },
            onDecrement = { hour = (hour + 23) % 24 },
            testTagPrefix = "hour",
          )
          Text(text = ":", style = TabularTimerStyle, color = OxidizedTerracotta)
          TimeStepperColumn(
            value = minute,
            label = "MIN",
            onIncrement = { minute = (minute + 1) % 60 },
            onDecrement = { minute = (minute + 59) % 60 },
            testTagPrefix = "minute",
          )
        }
      }

      OutlinedTextField(
        value = label,
        onValueChange = { if (it.length <= 60) label = it },
        label = { Text("Alarm Label") },
        placeholder = { Text("e.g., Foundry Shift / Early Run") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("alarm_label_input"),
      )

      // Repeat Days
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Repeat Schedule", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          val dayLabels = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
          dayLabels.forEach { (isoDay, text) ->
            val selected = isoDay in selectedDays
            Surface(
              onClick = { if (selected) selectedDays.remove(isoDay) else selectedDays.add(isoDay) },
              color = if (selected) OxidizedTerracotta else KilnStone,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, if (selected) OxidizedTerracotta else OxidizedHairline),
              modifier = Modifier.size(42.dp).testTag("day_chip_$isoDay"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = text,
                  color = if (selected) CastIron else WeatheredAsh,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }

      // Recurring Snooze Interval Engine
      Surface(
        color = KilnStone,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OxidizedHairline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Recurring Snooze Intervals", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SnoozeMode.entries.forEach { mode ->
              val active = snoozeMode == mode
              FilterChip(
                selected = active,
                onClick = { snoozeMode = mode },
                label = { Text(mode.title) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DeepRustEmber,
                    selectedLabelColor = WarmAlabaster,
                  ),
                modifier = Modifier.testTag("snooze_mode_${mode.name.lowercase()}"),
              )
            }
          }
          Text(text = snoozeMode.subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = WeatheredAsh)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Max Repeats: ${if (snoozeMaxCount == 0) "Off" else "$snoozeMaxCount"}")
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { if (snoozeMaxCount > 0) snoozeMaxCount-- }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease snooze repeats")
              }
              Text("$snoozeMaxCount", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
              IconButton(
                onClick = {
                  if (snoozeMaxCount < 10) {
                    snoozeMaxCount++
                    while (customSeq.size < snoozeMaxCount) {
                      customSeq.add((customSeq.lastOrNull() ?: snoozeInterval).coerceIn(1, 30))
                    }
                  }
                }
              ) {
                Icon(Icons.Default.Add, contentDescription = "Increase snooze repeats")
              }
            }
          }

          if (snoozeMaxCount > 0 && snoozeMode != SnoozeMode.CUSTOM) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Base Interval: ${snoozeInterval} min")
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (snoozeInterval > 1) snoozeInterval-- }) {
                  Icon(Icons.Default.Remove, contentDescription = "Decrease base interval")
                }
                Text("${snoozeInterval}m", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                IconButton(onClick = { if (snoozeInterval < 30) snoozeInterval++ }) {
                  Icon(Icons.Default.Add, contentDescription = "Increase base interval")
                }
              }
            }
          }

          if (snoozeMaxCount > 0 && snoozeMode == SnoozeMode.CUSTOM) {
            Text("Custom Cycle Sequence (min)", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = BrushedBrass)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              for (idx in 0 until snoozeMaxCount) {
                val mins = customSeq.getOrElse(idx) { snoozeInterval }
                Surface(
                  color = SlateCharcoal,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, OxidizedHairline),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(
                      onClick = {
                        while (customSeq.size <= idx) customSeq.add(snoozeInterval)
                        if (customSeq[idx] > 1) customSeq[idx] = customSeq[idx] - 1
                      },
                      modifier = Modifier.size(32.dp),
                    ) { Icon(Icons.Default.Remove, contentDescription = "Decrease step", modifier = Modifier.size(16.dp)) }
                    Text("#${idx + 1}: ${mins}m", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    IconButton(
                      onClick = {
                        while (customSeq.size <= idx) customSeq.add(snoozeInterval)
                        if (customSeq[idx] < 30) customSeq[idx] = customSeq[idx] + 1
                      },
                      modifier = Modifier.size(32.dp),
                    ) { Icon(Icons.Default.Add, contentDescription = "Increase step", modifier = Modifier.size(16.dp)) }
                  }
                }
              }
            }
          }
        }
      }

      // Customizable Alert Sounds, Ramp & Sunrise Glow
      Surface(
        color = KilnStone,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OxidizedHairline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Alert Sound", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
              Text(soundTitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = BrushedBrass)
            }
            OutlinedButton(
              onClick = {
                if (isPreviewing) {
                  audioPlayer.stop()
                  isPreviewing = false
                } else {
                  isPreviewing = true
                  audioPlayer.preview(soundId, soundUri, maxVolumePercent, scope) { isPreviewing = false }
                }
              },
              border = BorderStroke(1.dp, BrushedBrass),
              modifier = Modifier.testTag("preview_sound_button"),
            ) {
              Icon(
                imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = BrushedBrass,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(if (isPreviewing) "Stop" else "Test", color = BrushedBrass)
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(
              BuiltInSound.COPPER_BELL,
              BuiltInSound.SUNRISE_CHORD,
              BuiltInSound.MECHANICAL_PULSE,
              BuiltInSound.FOUNDRY_GONG,
            ).forEach { sound ->
              val selected = soundId == sound.id
              FilterChip(
                selected = selected,
                onClick = {
                  soundId = sound.id
                  soundTitle = sound.title
                  soundUri = null
                },
                label = { Text(sound.title) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DeepBrassContainer,
                    selectedLabelColor = WarmAlabaster,
                  ),
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = {
                val intent =
                  Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                  }
                ringtoneLauncher.launch(intent)
              },
              modifier = Modifier.weight(1f).testTag("pick_system_ringtone_button"),
            ) {
              Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("System Tone")
            }
            OutlinedButton(
              onClick = { documentAudioLauncher.launch(arrayOf("audio/*")) },
              modifier = Modifier.weight(1f).testTag("pick_audio_file_button"),
            ) {
              Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Audio File")
            }
          }

          Text("Gradual Volume Ramp: ${if (rampSeconds == 0) "Instant" else "${rampSeconds}s"}")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 60, 120).forEach { secs ->
              FilterChip(
                selected = rampSeconds == secs,
                onClick = { rampSeconds = secs },
                label = { Text(if (secs == 0) "Off" else "${secs}s") },
              )
            }
          }

          Text("Target Volume: $maxVolumePercent%")
          Slider(
            value = maxVolumePercent.toFloat(),
            onValueChange = { maxVolumePercent = it.toInt().coerceIn(10, 100) },
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(thumbColor = OxidizedTerracotta, activeTrackColor = OxidizedTerracotta),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Sunrise Glow Wake Screen")
            Switch(checked = sunriseEnabled, onCheckedChange = { sunriseEnabled = it })
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Haptic Vibration")
            Switch(checked = vibrate, onCheckedChange = { vibrate = it })
          }
        }
      }

      // Linked Morning Routine
      if (routines.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Launch Morning Routine on Dismiss", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = linkedRoutineId == null,
              onClick = { linkedRoutineId = null },
              label = { Text("Morning Check-In Only") },
            )
            routines.forEach { routine ->
              FilterChip(
                selected = linkedRoutineId == routine.id,
                onClick = { linkedRoutineId = routine.id },
                label = { Text(routine.title) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PatinaSage.copy(alpha = 0.25f),
                    selectedLabelColor = WarmAlabaster,
                  ),
              )
            }
          }
        }
      }

      Button(
        onClick = {
          audioPlayer.stop()
          val cleanSeq =
            if (snoozeMaxCount == 0) emptyList()
            else List(snoozeMaxCount) { idx ->
              customSeq.getOrElse(idx) { snoozeInterval }.coerceIn(1, 30).toLong()
            }
          onSave(
            initialAlarm.copy(
              label = label.trim().ifBlank { "Morning Alarm" },
              hour = hour.toLong(),
              minute = minute.toLong(),
              repeatDays = selectedDays.distinct().sorted().map { it.toLong() },
              enabled = true,
              vibrate = vibrate,
              soundId = soundId,
              soundTitle = soundTitle,
              soundUri = soundUri,
              rampSeconds = rampSeconds.toLong(),
              maxVolumePercent = maxVolumePercent.toLong(),
              sunriseEnabled = sunriseEnabled,
              snoozeMode = snoozeMode.name,
              snoozeIntervalMinutes = snoozeInterval.toLong(),
              snoozeMaxCount = snoozeMaxCount.toLong(),
              snoozeSequenceMinutes = cleanSeq,
              linkedRoutineId = linkedRoutineId,
            )
          )
        },
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_alarm_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
      ) {
        Text("Save Alarm", fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun TimeStepperColumn(
  value: Int,
  label: String,
  onIncrement: () -> Unit,
  onDecrement: () -> Unit,
  testTagPrefix: String,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = WeatheredAsh)
    IconButton(onClick = onIncrement, modifier = Modifier.testTag("${testTagPrefix}_up")) {
      Icon(Icons.Default.Add, contentDescription = "Increase $label", tint = WarmAlabaster)
    }
    Text(
      text = "%02d".format(value),
      style = TabularTimerStyle,
      color = WarmAlabaster,
    )
    IconButton(onClick = onDecrement, modifier = Modifier.testTag("${testTagPrefix}_down")) {
      Icon(Icons.Default.Remove, contentDescription = "Decrease $label", tint = WarmAlabaster)
    }
  }
}
