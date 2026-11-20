package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.UnoChessScreen
import com.example.ui.theme.UnoChessTheme
import com.example.viewmodel.UnoChessViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: UnoChessViewModel = viewModel()
            LaunchedEffect(Unit) {
                viewModel.loadPreferences(applicationContext)
            }

            val darkThemePref by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = darkThemePref ?: systemDark

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            UnoChessTheme(darkTheme = darkTheme) {
                UnoChessScreen(
                    viewModel = viewModel,
                    isDarkTheme = darkTheme
                )
            }
        }
    }
}
