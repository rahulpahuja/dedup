package com.rp.dedup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.ui.theme.DeDupTheme

class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Initialize the NavController once
            val navController: NavHostController = rememberNavController()

            DeDupTheme(darkTheme = themeViewModel.isDarkTheme()) {
                // Hand over the UI rendering to your NavHost!
                AppNavHost(navController = navController)
            }
        }
    }
}