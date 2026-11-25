package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ReadinessCalculator
import com.example.model.SleepLog
import com.example.model.WakeMood
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.DeepPatinaContainer
import com.example.ui.theme.DeepRustEmber
import com.example.ui.theme.KilnStone
import com.example.ui.theme.MutedIron
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TabularTimerStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepCheckInSheet(
  existingLog: SleepLog?,
  defaultSnoozesUsed: Int,
  targetSleepMinutes: Int,
  onDismiss: () -> Unit,
  onSave: (
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
  ) -> Unit,
) {
  val nowCal = remember { Calendar.getInstance() }
  val initialWakeMinOfDay =
    if (existingLog != null) {
      val c = Calendar.getInstance().apply { timeInMillis = existingLog.wakeEpochMs }
      c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    } else {
      nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    }
  val initialBedMinOfDay =
    if (existingLog != null) {
      val c = Calendar.getInstance().apply { timeInMillis = existingLog.bedtimeEpochMs }
      c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    } else {
      22 * 60 + 45
    }

  var bedMinuteOfDay by remember { mutableIntStateOf(initialBedMinOfDay) }
  var wakeMinuteOfDay by remember { mutableIntStateOf(initialWakeMinOfDay) }
  var qualityRating by remember { mutableIntStateOf(existingLog?.qualityRating?.toInt() ?: 4) }
  var snoozeCountUsed by remember { mutableIntStateOf(existingLog?.snoozeCountUsed?.toInt() ?: defaultSnoozesUsed) }
  var energyScore by remember { mutableIntStateOf(existingLog?.energyScore?.toInt() ?: 7) }
  var mood by remember { mutableStateOf(existingLog?.let { WakeMood.safeValueOf(it.mood) } ?: WakeMood.STEADY) }
  val selectedTags = remember {
    mutableStateListOf<String>().apply { existingLog?.tags?.let { addAll(it) } }
  }
  var notes by remember { mutableStateOf(existingLog?.notes.orEmpty()) }

  val computedDurationMinutes =
    remember(bedMinuteOfDay, wakeMinuteOfDay) {
      val diff = wakeMinuteOfDay - bedMinuteOfDay
      (if (diff <= 0) diff + 24 * 60 else diff).coerceIn(15, 1440)
    }

  val previewBreakdown =
    remember(computedDurationMinutes, targetSleepMinutes, qualityRating, energyScore, snoozeCountUsed, mood) {
      ReadinessCalculator.calculate(
        durationMinutes = computedDurationMinutes,
        targetSleepMinutes = targetSleepMinutes,
        qualityRating = qualityRating,
        energyScore = energyScore,
        snoozeCountUsed = snoozeCountUsed,
        mood = mood,
      )
    }

  val availableTags = listOf("Exercise", "No Caffeine", "Late Screen", "Magnesium", "Early Dinner", "Cool Room")

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = SlateCharcoal,
    contentColor = WarmAlabaster,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (existingLog == null) "Sleep & Morning Check-In" else "Edit Sleep Log",
          style = MaterialTheme.typography.headlineMedium,
        )
        TextButton(onClick = onDismiss) { Text("Cancel", color = WeatheredAsh) }
      }

      // Live Readiness Preview Card
      Surface(
        color = KilnStone,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PatinaSage.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("PROJECTED READINESS", style = MaterialTheme.typography.labelSmall, color = PatinaSage)
            Text(
              text = "${previewBreakdown.totalScore} / 100",
              style = TabularTimerStyle,
              color = WarmAlabaster,
            )
            Text(
              text = "Duration ${computedDurationMinutes / 60}h ${computedDurationMinutes % 60}m",
              style = MaterialTheme.typography.bodyMedium,
              color = BrushedBrass,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("Duration: ${previewBreakdown.durationPoints}/35", style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
            Text("Quality: ${previewBreakdown.qualityPoints}/25", style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
            Text("Energy: ${previewBreakdown.energyPoints}/25", style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
            Text("Snooze: ${previewBreakdown.snoozePoints}/15", style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
          }
        }
      }

      // Bedtime & Wake Time Steppers (15-min precision)
      Surface(
        color = KilnStone,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OxidizedHairline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          MinuteOfDayStepper(
            label = "BEDTIME",
            minuteOfDay = bedMinuteOfDay,
            onChange = { bedMinuteOfDay = (it + 24 * 60) % (24 * 60) },
          )
          MinuteOfDayStepper(
            label = "WAKE TIME",
            minuteOfDay = wakeMinuteOfDay,
            onChange = { wakeMinuteOfDay = (it + 24 * 60) % (24 * 60) },
          )
        }
      }

      // Sleep Quality 1..5
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sleep Quality ($qualityRating / 5)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          (1..5).forEach { star ->
            IconButton(
              onClick = { qualityRating = star },
              modifier = Modifier.testTag("quality_star_$star"),
            ) {
              Icon(
                imageVector = if (star <= qualityRating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "Rate $star stars",
                tint = if (star <= qualityRating) BrushedBrass else MutedIron,
                modifier = Modifier.size(30.dp),
              )
            }
          }
        }
      }

      // Morning Energy 1..10
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Morning Energy: $energyScore / 10", style = MaterialTheme.typography.titleMedium)
        Slider(
          value = energyScore.toFloat(),
          onValueChange = { energyScore = it.toInt().coerceIn(1, 10) },
          valueRange = 1f..10f,
          steps = 8,
          colors = SliderDefaults.colors(thumbColor = PatinaSage, activeTrackColor = PatinaSage),
          modifier = Modifier.testTag("energy_slider"),
        )
      }

      // Wake Mood
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wake Mood", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          WakeMood.entries.forEach { entry ->
            FilterChip(
              selected = mood == entry,
              onClick = { mood = entry },
              label = { Text(entry.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = DeepRustEmber,
                  selectedLabelColor = WarmAlabaster,
                ),
            )
          }
        }
      }

      // Snooze Count Used
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Snoozes Used This Morning", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { if (snoozeCountUsed > 0) snoozeCountUsed-- }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease snooze count")
          }
          Text("$snoozeCountUsed", style = MaterialTheme.typography.titleMedium)
          IconButton(onClick = { if (snoozeCountUsed < 20) snoozeCountUsed++ }) {
            Icon(Icons.Default.Add, contentDescription = "Increase snooze count")
          }
        }
      }

      // Lifestyle Tags
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Factors & Habits", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          availableTags.forEach { tag ->
            val selected = tag in selectedTags
            FilterChip(
              selected = selected,
              onClick = { if (selected) selectedTags.remove(tag) else if (selectedTags.size < 10) selectedTags.add(tag) },
              label = { Text(tag) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = DeepPatinaContainer,
                  selectedLabelColor = WarmAlabaster,
                ),
            )
          }
        }
      }

      OutlinedTextField(
        value = notes,
        onValueChange = { if (it.length <= 300) notes = it },
        label = { Text("Morning Notes (Optional)") },
        placeholder = { Text("How rested do you feel?") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth().testTag("sleep_notes_input"),
      )

      Button(
        onClick = {
          val wakeCal =
            Calendar.getInstance().apply {
              set(Calendar.HOUR_OF_DAY, wakeMinuteOfDay / 60)
              set(Calendar.MINUTE, wakeMinuteOfDay % 60)
              set(Calendar.SECOND, 0)
              set(Calendar.MILLISECOND, 0)
            }
          val wakeEpoch = wakeCal.timeInMillis
          val bedEpoch = wakeEpoch - computedDurationMinutes * 60_000L
          val dateKey = existingLog?.dateKey ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(wakeEpoch))

          onSave(
            existingLog?.id.orEmpty(),
            dateKey,
            bedEpoch,
            wakeEpoch,
            computedDurationMinutes,
            qualityRating,
            snoozeCountUsed,
            energyScore,
            mood,
            selectedTags.toList(),
            notes,
          )
        },
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("save_sleep_log_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
      ) {
        Text("Save Sleep & Readiness", fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun MinuteOfDayStepper(
  label: String,
  minuteOfDay: Int,
  onChange: (Int) -> Unit,
) {
  val hrs = minuteOfDay / 60
  val mins = minuteOfDay % 60
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = { onChange(minuteOfDay - 15) }) {
        Icon(Icons.Default.Remove, contentDescription = "Earlier 15m", tint = WarmAlabaster)
      }
      Text("%02d:%02d".format(hrs, mins), style = MaterialTheme.typography.headlineSmall, color = BrushedBrass)
      IconButton(onClick = { onChange(minuteOfDay + 15) }) {
        Icon(Icons.Default.Add, contentDescription = "Later 15m", tint = WarmAlabaster)
      }
    }
  }
}
