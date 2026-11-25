package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IndustrialTerracottaColorScheme =
  darkColorScheme(
    primary = OxidizedTerracotta,
    onPrimary = CastIron,
    primaryContainer = DeepRustEmber,
    onPrimaryContainer = WarmAlabaster,
    secondary = BrushedBrass,
    onSecondary = CastIron,
    secondaryContainer = DeepBrassContainer,
    onSecondaryContainer = WarmAlabaster,
    tertiary = PatinaSage,
    onTertiary = CastIron,
    tertiaryContainer = DeepPatinaContainer,
    onTertiaryContainer = WarmAlabaster,
    background = CastIron,
    onBackground = WarmAlabaster,
    surface = SlateCharcoal,
    onSurface = WarmAlabaster,
    surfaceVariant = KilnStone,
    onSurfaceVariant = WeatheredAsh,
    surfaceContainerLow = SlateCharcoal,
    surfaceContainer = KilnStone,
    surfaceContainerHigh = KilnStoneElevated,
    outline = OxidizedHairline,
    outlineVariant = OxidizedHairline,
    error = ForgeCrimson,
    onError = CastIron,
    errorContainer = DeepCrimsonContainer,
    onErrorContainer = WarmAlabaster,
  )

val RustAlarmShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun RustAlarmTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = IndustrialTerracottaColorScheme,
    typography = Typography,
    shapes = RustAlarmShapes,
    content = content,
  )
}
