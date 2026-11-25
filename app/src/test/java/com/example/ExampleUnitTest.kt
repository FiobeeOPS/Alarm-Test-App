package com.example

import com.example.model.AlarmProfile
import com.example.model.ReadinessCalculator
import com.example.model.SnoozeMode
import com.example.model.WakeMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun diminishingSnoozeSequenceStepsDownAndCapsAtMaxCount() {
    val alarm =
      AlarmProfile(
        snoozeMode = SnoozeMode.DIMINISHING.name,
        snoozeIntervalMinutes = 10L,
        snoozeMaxCount = 4L,
      )
    assertEquals(listOf(10, 7, 4, 2), alarm.resolvedSnoozeSequence())
    assertEquals(10, alarm.nextSnoozeMinutes(0))
    assertEquals(7, alarm.nextSnoozeMinutes(1))
    assertEquals(4, alarm.nextSnoozeMinutes(2))
    assertEquals(2, alarm.nextSnoozeMinutes(3))
    assertNull(alarm.nextSnoozeMinutes(4))
  }

  @Test
  fun customSnoozeSequenceRespectsExplicitOrder() {
    val alarm =
      AlarmProfile(
        snoozeMode = SnoozeMode.CUSTOM.name,
        snoozeIntervalMinutes = 5L,
        snoozeMaxCount = 3L,
        snoozeSequenceMinutes = listOf(12L, 8L, 4L),
      )
    assertEquals(listOf(12, 8, 4), alarm.resolvedSnoozeSequence())
  }

  @Test
  fun readinessCalculatorRewardsTargetSleepAndLowSnoozes() {
    val optimal =
      ReadinessCalculator.calculate(
        durationMinutes = 480,
        targetSleepMinutes = 480,
        qualityRating = 5,
        energyScore = 9,
        snoozeCountUsed = 0,
        mood = WakeMood.REFRESHED,
      )
    val snoozed =
      ReadinessCalculator.calculate(
        durationMinutes = 360,
        targetSleepMinutes = 480,
        qualityRating = 3,
        energyScore = 5,
        snoozeCountUsed = 3,
        mood = WakeMood.GROGGY,
      )
    assertTrue(optimal.totalScore >= 90)
    assertTrue(optimal.totalScore > snoozed.totalScore)
  }
}
