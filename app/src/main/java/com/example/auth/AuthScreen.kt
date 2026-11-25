package com.example.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.BrushedBrass
import com.example.ui.theme.CastIron
import com.example.ui.theme.DeepCrimsonContainer
import com.example.ui.theme.ForgeCrimson
import com.example.ui.theme.KilnStone
import com.example.ui.theme.OxidizedHairline
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.PatinaSage
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.TerracottaWash
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var isCheckingAuto by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isCheckingAuto = false
        onAuthSuccess()
      },
      onUnauthenticated = { isCheckingAuto = false },
      scope = scope,
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawRect(color = CastIron)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(TerracottaWash.copy(alpha = 0.75f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.22f),
            radius = size.maxDimension * 0.55f,
          ),
        center = Offset(size.width * 0.5f, size.height * 0.22f),
        radius = size.maxDimension * 0.55f,
      )
    }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 32.dp)
          .widthIn(max = 520.dp)
          .align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          shape = CircleShape,
          color = SlateCharcoal,
          border = BorderStroke(1.5.dp, OxidizedTerracotta.copy(alpha = 0.6f)),
          modifier = Modifier.size(96.dp),
        ) {
          Image(
            painter = painterResource(id = R.drawable.rust_alarm_icon_1795623422522),
            contentDescription = stringResource(R.string.cd_alarm_icon),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = "RUST ALARM",
          style = MaterialTheme.typography.labelMedium,
          color = BrushedBrass,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Deliberate Mornings.\nHonest Sleep Metrics.",
          style = MaterialTheme.typography.displaySmall,
          color = WarmAlabaster,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = stringResource(R.string.tagline),
          style = MaterialTheme.typography.bodyLarge,
          color = WeatheredAsh,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Surface(
          color = SlateCharcoal.copy(alpha = 0.92f),
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, OxidizedHairline),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            FeatureRow(
              icon = Icons.Default.Alarm,
              tint = OxidizedTerracotta,
              title = "Recurring Snooze Cadences",
              subtitle = "Fixed, diminishing, or custom interval sequences",
            )
            FeatureRow(
              icon = Icons.Default.WbTwilight,
              tint = BrushedBrass,
              title = "Gradual Ramp & Sunrise Glow",
              subtitle = "Custom chimes, system tones & progressive wake light",
            )
            FeatureRow(
              icon = Icons.Default.CheckCircle,
              tint = PatinaSage,
              title = "Morning Routine Habit Timer",
              subtitle = "Guided step countdowns synced across your devices",
            )
            FeatureRow(
              icon = Icons.Default.Bedtime,
              tint = OxidizedTerracotta,
              title = "Sleep & Readiness Telemetry",
              subtitle = "Manual sleep logs, morning check-in & trend analytics",
            )
          }
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (errorMessage != null) {
          Surface(
            color = DeepCrimsonContainer,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ForgeCrimson.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage.orEmpty(),
              color = WarmAlabaster,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        }

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = { isLoading = false },
            )
          },
          enabled = !isLoading && !isCheckingAuto,
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("google_sign_in_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = OxidizedTerracotta, contentColor = CastIron),
        ) {
          if (isLoading || isCheckingAuto) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = CastIron,
              strokeWidth = 2.5.dp,
            )
          } else {
            Text(
              text = stringResource(R.string.sign_in_with_google),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }

        Text(
          text = "Cloud sync & backup secured with your Google account.",
          style = MaterialTheme.typography.bodyMedium,
          color = WeatheredAsh,
          modifier = Modifier.align(Alignment.CenterHorizontally),
        )
      }
    }
  }
}

@Composable
private fun FeatureRow(
  icon: ImageVector,
  tint: Color,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Surface(
      color = KilnStone,
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, OxidizedHairline),
      modifier = Modifier.size(42.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
      }
    }
    Column {
      Text(text = title, style = MaterialTheme.typography.titleMedium, color = WarmAlabaster)
      Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = WeatheredAsh)
    }
  }
}
