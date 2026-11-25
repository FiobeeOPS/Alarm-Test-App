package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alarm.WakeAlarmActivity
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.ui.MainAppShell
import com.example.ui.theme.RustAlarmTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
  private var postAlarmAction by mutableStateOf<String?>(null)
  private var postAlarmRoutineId by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    extractPostAlarmExtras(intent)

    setContent {
      RustAlarmTheme {
        AppNavigation(
          postAlarmAction = postAlarmAction,
          postAlarmRoutineId = postAlarmRoutineId,
          onPostAlarmActionConsumed = {
            postAlarmAction = null
            postAlarmRoutineId = null
          },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    extractPostAlarmExtras(intent)
  }

  private fun extractPostAlarmExtras(intent: Intent?) {
    val action = intent?.getStringExtra(WakeAlarmActivity.EXTRA_POST_ALARM_ACTION)
    if (!action.isNullOrBlank()) {
      postAlarmAction = action
      postAlarmRoutineId = intent.getStringExtra(WakeAlarmActivity.EXTRA_POST_ROUTINE_ID)
    }
  }
}

@Composable
fun AppNavigation(
  postAlarmAction: String?,
  postAlarmRoutineId: String?,
  onPostAlarmActionConsumed: () -> Unit,
  auth: FirebaseAuth = Firebase.auth,
) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      MainAppShell(
        currentUserId = user.uid,
        postAlarmAction = postAlarmAction,
        postAlarmRoutineId = postAlarmRoutineId,
        onPostAlarmActionConsumed = onPostAlarmActionConsumed,
      )
    }
  }
}
