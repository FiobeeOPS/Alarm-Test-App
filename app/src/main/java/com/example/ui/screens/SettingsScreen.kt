package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.alarm.AlarmScheduler
import com.example.model.UserSettings
import com.example.ui.components.IndustrialCard
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.ForgeCrimson
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun SettingsScreen(
  settings: UserSettings,
  onSaveSettings: (UserSettings) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val user = Firebase.auth.currentUser
  val canExactAlarm = remember { AlarmScheduler.canScheduleExactAlarms(context) }

  var targetSleepMinutes by remember(settings.targetSleepMinutes) {
    mutableIntStateOf(settings.targetSleepMinutes.toInt().coerceIn(300, 660))
  }
  var defaultSnoozeMinutes by remember(settings.defaultSnoozeMinutes) {
    mutableIntStateOf(settings.defaultSnoozeMinutes.toInt().coerceIn(1, 30))
  }
  var defaultMaxSnoozes by remember(settings.defaultSnoozeMaxCount) {
    mutableIntStateOf(settings.defaultSnoozeMaxCount.toInt().coerceIn(0, 10))
  }
  var defaultRampSeconds by remember(settings.defaultRampSeconds) {
    mutableIntStateOf(settings.defaultRampSeconds.toInt().coerceIn(0, 120))
  }
  var sunriseEnabled by remember(settings.sunriseGlowEnabled) {
    mutableStateOf(settings.sunriseGlowEnabled)
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      IndustrialCard(containerColor = SlateCharcoal, borderColor = OxidizedTerracotta.copy(alpha = 0.4f)) {
        Text("CLOUD SYNC ACCOUNT", style = MaterialTheme.typography.labelMedium, color = BrushedBrass)
        Spacer(Modifier.height(6.dp))
        Text(user?.displayName ?: "Signed-In User", style = MaterialTheme.typography.headlineSmall, color = WarmAlabaster)
        Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
        Spacer(Modifier.height(8.dp))
        Text("Firestore real-time backup active", style = MaterialTheme.typography.labelMedium, color = PatinaSage)
      }
    }

    item {
      IndustrialCard {
        Text("Sleep & Alarm Defaults", style = MaterialTheme.typography.titleLarge, color = WarmAlabaster)
        Spacer(Modifier.height(12.dp))

        Text("Daily Sleep Target: ${targetSleepMinutes / 60}h ${targetSleepMinutes % 60}m")
        Slider(
          value = targetSleepMinutes.toFloat(),
          onValueChange = { targetSleepMinutes = ((it / 15f).toInt() * 15).coerceIn(300, 660) },
          valueRange = 300f..660f,
          colors = SliderDefaults.colors(thumbColor = PatinaSage, activeTrackColor = PatinaSage),
        )

        Text("Default Snooze Interval: ${defaultSnoozeMinutes}m")
        Slider(
          value = defaultSnoozeMinutes.toFloat(),
          onValueChange = { defaultSnoozeMinutes = it.toInt().coerceIn(1, 30) },
          valueRange = 1f..30f,
          colors = SliderDefaults.colors(thumbColor = BrushedBrass, activeTrackColor = BrushedBrass),
        )

        Text("Default Max Snooze Repeats: $defaultMaxSnoozes")
        Slider(
          value = defaultMaxSnoozes.toFloat(),
          onValueChange = { defaultMaxSnoozes = it.toInt().coerceIn(0, 10) },
          valueRange = 0f..10f,
          colors = SliderDefaults.colors(thumbColor = OxidizedTerracotta, activeTrackColor = OxidizedTerracotta),
        )

        Text("Default Volume Ramp: ${defaultRampSeconds}s")
        Slider(
          value = defaultRampSeconds.toFloat(),
          onValueChange = { defaultRampSeconds = ((it / 15f).toInt() * 15).coerceIn(0, 120) },
          valueRange = 0f..120f,
          colors = SliderDefaults.colors(thumbColor = OxidizedTerracotta, activeTrackColor = OxidizedTerracotta),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Sunrise Glow Enabled by Default")
          Switch(checked = sunriseEnabled, onCheckedChange = { sunriseEnabled = it })
        }

        Spacer(Modifier.height(12.dp))
        Button(
          onClick = {
            onSaveSettings(
              settings.copy(
                targetSleepMinutes = targetSleepMinutes.toLong(),
                defaultSnoozeMinutes = defaultSnoozeMinutes.toLong(),
                defaultSnoozeMaxCount = defaultMaxSnoozes.toLong(),
                defaultRampSeconds = defaultRampSeconds.toLong(),
                sunriseGlowEnabled = sunriseEnabled,
              )
            )
          },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_settings_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
        ) {
          Text("Save Defaults", fontWeight = FontWeight.Bold)
        }
      }
    }

    item {
      IndustrialCard {
        Text("Device Alarm Diagnostics", style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
        Spacer(Modifier.height(6.dp))
        Text(
          text =
            if (canExactAlarm) "Exact alarm scheduling is granted."
            else "Exact alarm scheduling requires approval in system settings.",
          style = MaterialTheme.typography.bodyMedium,
          color = if (canExactAlarm) PatinaSage else BrushedBrass,
        )
        if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          Spacer(Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
            },
          ) {
            Text("Open Exact Alarm Settings")
          }
        }
      }
    }

    item {
      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ForgeCrimson.copy(alpha = 0.65f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ForgeCrimson),
      ) {
        Text(stringResource(R.string.sign_out), fontWeight = FontWeight.Bold)
      }
    }
  }
}
