package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.alarm.WakeAlarmActivity
import com.example.auth.signOutUser
import com.example.data.AlarmRepository
import com.example.data.WellnessRepository
import com.example.ui.screens.AlarmsScreen
import com.example.ui.screens.MetricsScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CastIron
import com.example.ui.theme.DeepRustEmber
import com.example.ui.theme.OxidizedTerracotta
import com.example.ui.theme.SlateCharcoal
import com.example.ui.theme.WarmAlabaster
import com.example.ui.theme.WeatheredAsh
import com.example.viewmodel.AlarmsViewModel
import com.example.viewmodel.MetricsViewModel
import com.example.viewmodel.RoutinesViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.merge

enum class MainTab(
  val label: String,
  val activeIcon: ImageVector,
  val inactiveIcon: ImageVector,
) {
  ALARMS("Alarms", Icons.Filled.Alarm, Icons.Outlined.Alarm),
  ROUTINES("Routines", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
  METRICS("Metrics", Icons.Filled.Assessment, Icons.Outlined.Assessment),
  SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MainAppShell(
  currentUserId: String,
  postAlarmAction: String?,
  postAlarmRoutineId: String?,
  onPostAlarmActionConsumed: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  val alarmsVm: AlarmsViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
            AlarmsViewModel(AlarmRepository(db), WellnessRepository(db), currentUserId)
          }
        },
    )

  val routinesVm: RoutinesViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
            RoutinesViewModel(WellnessRepository(db), currentUserId)
          }
        },
    )

  val metricsVm: MetricsViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
            MetricsViewModel(WellnessRepository(db), AlarmRepository(db), currentUserId)
          }
        },
    )

  val alarmsState by alarmsVm.alarmsState.collectAsStateWithLifecycle()
  val routinesList by alarmsVm.routinesState.collectAsStateWithLifecycle()
  val settings by alarmsVm.settingsState.collectAsStateWithLifecycle()
  val routinesState by routinesVm.routinesState.collectAsStateWithLifecycle()
  val routineRuns by routinesVm.routineRunsState.collectAsStateWithLifecycle()
  val activeTimer by routinesVm.activeTimer.collectAsStateWithLifecycle()
  val metricsSummary by metricsVm.summaryState.collectAsStateWithLifecycle()

  var selectedTab by rememberSaveable { mutableStateOf(MainTab.ALARMS) }
  var openCheckInOnMetrics by remember { mutableStateOf(false) }

  LaunchedEffect(postAlarmAction, postAlarmRoutineId) {
    when (postAlarmAction) {
      WakeAlarmActivity.POST_ACTION_ROUTINE -> {
        selectedTab = MainTab.ROUTINES
        if (!postAlarmRoutineId.isNullOrBlank()) {
          routinesVm.startRoutineById(postAlarmRoutineId)
        }
        onPostAlarmActionConsumed()
      }
      WakeAlarmActivity.POST_ACTION_CHECKIN -> {
        selectedTab = MainTab.METRICS
        openCheckInOnMetrics = true
        onPostAlarmActionConsumed()
      }
    }
  }

  LaunchedEffect(alarmsVm, routinesVm, metricsVm) {
    merge(alarmsVm.messages, routinesVm.messages, metricsVm.messages).collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 600.dp

    Scaffold(
      containerColor = CastIron,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (!isExpanded && activeTimer == null) {
          NavigationBar(
            containerColor = SlateCharcoal,
            modifier = Modifier.navigationBarsPadding(),
          ) {
            MainTab.entries.forEach { tab ->
              val selected = selectedTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { selectedTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = OxidizedTerracotta,
                    selectedTextColor = WarmAlabaster,
                    indicatorColor = DeepRustEmber,
                    unselectedIconColor = WeatheredAsh,
                    unselectedTextColor = WeatheredAsh,
                  ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isExpanded && activeTimer == null) {
          NavigationRail(
            containerColor = SlateCharcoal,
            modifier = Modifier.fillMaxHeight(),
          ) {
            MainTab.entries.forEach { tab ->
              val selected = selectedTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { selectedTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = OxidizedTerracotta,
                    selectedTextColor = WarmAlabaster,
                    indicatorColor = DeepRustEmber,
                    unselectedIconColor = WeatheredAsh,
                    unselectedTextColor = WeatheredAsh,
                  ),
                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}"),
              )
            }
          }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          Box(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
            when (selectedTab) {
              MainTab.ALARMS ->
                AlarmsScreen(
                  alarmsState = alarmsState,
                  routines = routinesList,
                  settings = settings,
                  currentUserId = currentUserId,
                  onSaveAlarm = { alarm, done -> alarmsVm.saveAlarm(alarm, done) },
                  onToggleAlarm = { id, enabled -> alarmsVm.toggleAlarm(id, enabled) },
                  onDeleteAlarm = { id -> alarmsVm.deleteAlarm(id) },
                  onCreateStarterAlarm = { alarmsVm.createStarterAlarm() },
                  onOpenCheckIn = {
                    openCheckInOnMetrics = true
                    selectedTab = MainTab.METRICS
                  },
                )
              MainTab.ROUTINES ->
                RoutinesScreen(
                  routinesState = routinesState,
                  routineRuns = routineRuns,
                  activeTimer = activeTimer,
                  stepChimeEvents = routinesVm.stepChimeEvents,
                  currentUserId = currentUserId,
                  onCreateStarterRoutine = { routinesVm.createStarterRoutine() },
                  onSaveRoutine = { routine, done -> routinesVm.saveRoutine(routine, done) },
                  onDeleteRoutine = { id -> routinesVm.deleteRoutine(id) },
                  onStartRoutine = { routine -> routinesVm.startRoutine(routine) },
                  onTogglePause = { routinesVm.togglePauseTimer() },
                  onCompleteStep = { routinesVm.completeCurrentStep() },
                  onSkipStep = { routinesVm.skipCurrentStep() },
                  onCancelTimer = { savePartial -> routinesVm.cancelTimer(savePartial) },
                )
              MainTab.METRICS ->
                MetricsScreen(
                  summaryState = metricsSummary,
                  openCheckInImmediately = openCheckInOnMetrics,
                  onCheckInHandled = { openCheckInOnMetrics = false },
                  onSaveSleepLog = { id, dateKey, bedMs, wakeMs, durMin, quality, snoozes, energy, mood, tags, notes, target, done ->
                    metricsVm.saveSleepAndReadiness(
                      existingId = id,
                      dateKey = dateKey,
                      bedtimeEpochMs = bedMs,
                      wakeEpochMs = wakeMs,
                      durationMinutes = durMin,
                      qualityRating = quality,
                      snoozeCountUsed = snoozes,
                      energyScore = energy,
                      mood = mood,
                      tags = tags,
                      notes = notes,
                      targetSleepMinutes = target,
                      onSaved = done,
                    )
                  },
                  onDeleteSleepLog = { id -> metricsVm.deleteSleepLog(id) },
                )
              MainTab.SETTINGS ->
                SettingsScreen(
                  settings = settings,
                  onSaveSettings = { updated -> alarmsVm.saveSettings(updated) },
                  onSignOut = {
                    signOutUser(
                      context = context,
                      credentialManager = credentialManager,
                      scope = scope,
                    )
                  },
                )
            }
          }
        }
      }
    }
  }
}
