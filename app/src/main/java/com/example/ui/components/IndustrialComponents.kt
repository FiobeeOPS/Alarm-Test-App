package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.SleepLog
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.DeepBrassContainer
import com.example.ui.theme.KilnStone
import com.example.ui.theme.MutedIron
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TabularTimerStyle
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh

@Composable
fun IndustrialCard(
  modifier: Modifier = Modifier,
  containerColor: Color = SlateCharcoal,
  borderColor: Color = OxidizedHairline,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = containerColor,
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, borderColor),
  ) {
    Column(modifier = Modifier.padding(18.dp), content = content)
  }
}

@Composable
fun PermissionBanner(
  icon: ImageVector,
  title: String,
  body: String,
  actionLabel: String,
  onAction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = DeepBrassContainer.copy(alpha = 0.72f),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, BrushedBrass.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = BrushedBrass, modifier = Modifier.size(22.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
      }
      TextButton(
        onClick = onAction,
        colors = ButtonDefaults.textButtonColors(contentColor = BrushedBrass),
      ) {
        Text(text = actionLabel, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun ReadinessRingCanvas(
  score: Int,
  modifier: Modifier = Modifier,
  ringSize: Dp = 124.dp,
) {
  val animatedFraction by animateFloatAsState(
    targetValue = (score.coerceIn(0, 100) / 100f),
    animationSpec = tween(durationMillis = 700),
    label = "readinessRing",
  )
  val ringColor =
    when {
      score >= 78 -> PatinaSage
      score >= 55 -> BrushedBrass
      else -> OxidizedTerracotta
    }

  Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      val stroke = 10.dp.toPx()
      drawArc(
        color = KilnStone,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
      drawArc(
        color = ringColor,
        startAngle = -90f,
        sweepAngle = 360f * animatedFraction,
        useCenter = false,
        style = Stroke(width = stroke, cap = StrokeCap.Round),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (score > 0) "$score" else "--",
        style = TabularTimerStyle,
        color = WarmAlabaster,
      )
      Text(
        text = "READY",
        style = MaterialTheme.typography.labelSmall,
        color = WeatheredAsh,
      )
    }
  }
}

@Composable
fun SleepTrendChartCanvas(
  logs: List<SleepLog>,
  targetSleepMinutes: Int,
  modifier: Modifier = Modifier,
) {
  val recent = logs.take(10).reversed()
  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
      if (recent.isEmpty()) return@Canvas
      val maxMins = maxOf(targetSleepMinutes + 90, recent.maxOf { it.durationMinutes.toInt() }, 540).toFloat()
      val targetY = size.height * (1f - (targetSleepMinutes / maxMins))

      // Target sleep guideline
      drawLine(
        color = BrushedBrass.copy(alpha = 0.55f),
        start = Offset(0f, targetY),
        end = Offset(size.width, targetY),
        strokeWidth = 1.5.dp.toPx(),
      )

      val slotWidth = size.width / recent.size.coerceAtLeast(1)
      val barWidth = (slotWidth * 0.52f).coerceAtMost(28.dp.toPx())

      val readinessPath = Path()
      recent.forEachIndexed { idx, log ->
        val barRatio = (log.durationMinutes / maxMins).coerceIn(0.05f, 1f)
        val barHeight = size.height * barRatio
        val left = idx * slotWidth + (slotWidth - barWidth) / 2f
        val top = size.height - barHeight
        val metGoal = log.durationMinutes >= targetSleepMinutes
        drawRoundRect(
          color = if (metGoal) PatinaSage else OxidizedTerracotta,
          topLeft = Offset(left, top),
          size = Size(barWidth, barHeight),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )

        val readinessY = size.height * (1f - (log.readinessScore.coerceIn(0L, 100L) / 100f))
        val cx = idx * slotWidth + slotWidth / 2f
        if (idx == 0) readinessPath.moveTo(cx, readinessY) else readinessPath.lineTo(cx, readinessY)
      }

      if (recent.size > 1) {
        drawPath(
          path = readinessPath,
          color = BrushedBrass,
          style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
      }
      recent.forEachIndexed { idx, log ->
        val cx = idx * slotWidth + slotWidth / 2f
        val readinessY = size.height * (1f - (log.readinessScore.coerceIn(0L, 100L) / 100f))
        drawCircle(color = BrushedBrass, radius = 3.5.dp.toPx(), center = Offset(cx, readinessY))
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      recent.forEach { log ->
        Text(
          text = log.dateKey.takeLast(5),
          style = MaterialTheme.typography.labelSmall,
          color = MutedIron,
        )
      }
    }
  }
}
