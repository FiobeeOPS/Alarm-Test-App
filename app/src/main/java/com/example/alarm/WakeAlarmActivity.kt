package com.example.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.model.AlarmProfile
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.KilnStone
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.RustAlarmTheme
import com.example.ui.theme.SunriseCoreGold
import com.example.ui.theme.SunriseDeepEmber
import com.example.ui.theme.SunriseTerracotta
import com.example.ui.theme.SunriseWarmAmber
import com.example.ui.theme.TabularClockDisplayStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WakeAlarmActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    configureLockScreenWake()

    val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
    val fallbackLabel = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty().ifBlank { "Morning Wake" }
    val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
    val isPreview = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PREVIEW, false)
    val alarm = AlarmRuntimeStore.findAlarmById(this, alarmId) ?: AlarmProfile(id = alarmId, label = fallbackLabel)

    setContent {
      RustAlarmTheme {
        val activeState by AlarmService.activeState.collectAsStateWithLifecycle()
        val rampProgress = activeState?.rampProgress ?: if (alarm.rampSeconds > 0L) 0.25f else 1f

        WakeAlarmScreen(
          alarm = alarm,
          label = activeState?.label ?: fallbackLabel,
          snoozeCount = snoozeCount,
          rampProgress = rampProgress,
          isPreview = isPreview,
          onSnooze = { delayMin ->
            AlarmService.stopService(this)
            if (!isPreview && alarmId.isNotBlank()) {
              val nextCount = snoozeCount + 1
              AlarmRuntimeStore.recordSnoozeUsed(this, nextCount)
              AlarmScheduler.scheduleSnooze(this, alarmId, alarm.label.ifBlank { fallbackLabel }, delayMin, nextCount)
            }
            finish()
          },
          onDismiss = {
            AlarmService.stopService(this)
            if (!isPreview) {
              AlarmRuntimeStore.recordDismissedContext(this, alarmId, alarm.linkedRoutineId, snoozeCount)
              val mainIntent =
                Intent(this, MainActivity::class.java).apply {
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                  putExtra(
                    EXTRA_POST_ALARM_ACTION,
                    if (!alarm.linkedRoutineId.isNullOrBlank()) POST_ACTION_ROUTINE else POST_ACTION_CHECKIN,
                  )
                  putExtra(EXTRA_POST_ROUTINE_ID, alarm.linkedRoutineId.orEmpty())
                }
              startActivity(mainIntent)
            }
            finish()
          },
        )
      }
    }
  }

  private fun configureLockScreenWake() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
          WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
      )
    }
  }

  companion object {
    const val EXTRA_POST_ALARM_ACTION = "extra_post_alarm_action"
    const val EXTRA_POST_ROUTINE_ID = "extra_post_routine_id"
    const val POST_ACTION_ROUTINE = "start_routine"
    const val POST_ACTION_CHECKIN = "morning_checkin"
  }
}

@Composable
private fun WakeAlarmScreen(
  alarm: AlarmProfile,
  label: String,
  snoozeCount: Int,
  rampProgress: Float,
  isPreview: Boolean,
  onSnooze: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      currentTime = System.currentTimeMillis()
      delay(1000L)
    }
  }

  val animatedGlow by animateFloatAsState(
    targetValue = if (alarm.sunriseEnabled) rampProgress.coerceIn(0.1f, 1f) else 0.15f,
    animationSpec = tween(durationMillis = 700),
    label = "sunriseGlow",
  )

  val nextSnoozeMin = alarm.nextSnoozeMinutes(snoozeCount)
  val totalSnoozes = alarm.snoozeMaxCount.toInt()
  val timeFormatted = remember(currentTime) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime)) }
  val dateFormatted = remember(currentTime) { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(currentTime)).uppercase() }

  Box(modifier = Modifier.fillMaxSize()) {
    // Progressive Sunrise Glow Canvas
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(color = CastIron)
      val center = Offset(size.width / 2f, size.height * 0.42f)
      val maxRadius = size.maxDimension * (0.45f + 0.55f * animatedGlow)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                SunriseCoreGold.copy(alpha = 0.42f * animatedGlow),
                SunriseWarmAmber.copy(alpha = 0.34f * animatedGlow),
                SunriseTerracotta.copy(alpha = 0.25f * animatedGlow),
                SunriseDeepEmber.copy(alpha = 0.16f * animatedGlow),
                Color.Transparent,
              ),
            center = center,
            radius = maxRadius,
          ),
        radius = maxRadius,
        center = center,
      )
    }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp, vertical = 24.dp)
          .widthIn(max = 520.dp)
          .align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          color = KilnStone.copy(alpha = 0.85f),
          shape = RoundedCornerShape(999.dp),
          border = BorderStroke(1.dp, OxidizedHairline),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.WbTwilight,
              contentDescription = null,
              tint = BrushedBrass,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = if (isPreview) "SUNRISE WAKE PREVIEW" else dateFormatted,
              style = MaterialTheme.typography.labelMedium,
              color = WarmAlabaster,
            )
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
          text = timeFormatted,
          style = TabularClockDisplayStyle.copy(fontSize = 76.sp, lineHeight = 80.sp),
          color = WarmAlabaster,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = label,
          style = MaterialTheme.typography.displaySmall,
          color = WarmAlabaster,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        val cycleText =
          when {
            totalSnoozes == 0 -> "No snooze repeats configured"
            snoozeCount == 0 -> "Snooze cadence: ${alarm.snoozeSummary()}"
            nextSnoozeMin != null -> "Snooze cycle $snoozeCount of $totalSnoozes • Next: ${nextSnoozeMin}m"
            else -> "Final wake-up ($snoozeCount/$totalSnoozes snoozes used)"
          }
        Text(
          text = cycleText,
          style = MaterialTheme.typography.bodyLarge,
          color = WeatheredAsh,
          textAlign = TextAlign.Center,
        )

        if (alarm.rampSeconds > 0L) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Volume ramp ${(animatedGlow * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = BrushedBrass,
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (nextSnoozeMin != null) {
          OutlinedButton(
            onClick = { onSnooze(nextSnoozeMin) },
            modifier = Modifier.fillMaxWidth().height(64.dp).testTag("wake_snooze_button"),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, BrushedBrass),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrushedBrass, containerColor = KilnStone.copy(alpha = 0.72f)),
          ) {
            Icon(imageVector = Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(
              text = "Snooze $nextSnoozeMin min (${snoozeCount + 1}/$totalSnoozes)",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        Button(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth().height(68.dp).testTag("wake_dismiss_button"),
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
        ) {
          Icon(imageVector = Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.size(10.dp))
          Text(
            text =
              when {
                isPreview -> "Close Preview"
                !alarm.linkedRoutineId.isNullOrBlank() -> "Dismiss & Start Routine"
                else -> "Dismiss & Morning Check-In"
              },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
        }
      }
    }
  }
}
