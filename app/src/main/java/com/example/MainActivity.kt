package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ConnectMeViewModel
import com.example.ui.navigation.ConnectMeNavHost
import com.example.ui.theme.ConnectMeTheme

class MainActivity : ComponentActivity() {
  private val viewModel: ConnectMeViewModel by viewModels {
    val app = application as ConnectMeApplication
    ConnectMeViewModel.Factory(app.repository, app.pollingManager, app.settingsStore)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val app = application as ConnectMeApplication
    setContent {
      val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
      ConnectMeTheme(themePreference = themePref) {
        ConnectMeNavHost(viewModel = viewModel, mediaHelper = app.mediaHelper)
      }
    }
  }
}
