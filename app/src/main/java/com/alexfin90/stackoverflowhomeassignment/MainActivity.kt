package com.alexfin90.stackoverflowhomeassignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alexfin90.stackoverflowhomeassignment.core.designsystem.theme.StackOverFlowTheme
import com.alexfin90.stackoverflowhomeassignment.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StackOverFlowTheme {
                AppNavHost()
            }
        }
    }
}
