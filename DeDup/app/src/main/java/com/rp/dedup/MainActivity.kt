package com.rp.dedup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.viewmodels.ThemeViewModel
import com.rp.dedup.ui.theme.DeDupTheme

class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(DataStoreManager(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Initialize the NavController once
            val navController: NavHostController = rememberNavController()

            DeDupTheme(darkTheme = themeViewModel.isDarkTheme()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Hand over the UI rendering to your NavHost!
                    AppNavHost(navController = navController)

                    // Demo Activity Trigger Button
                    Button(
                        onClick = {
                            startActivity(Intent(this@MainActivity, DemotestActivity::class.java))
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 16.dp)
                    ) {
                        Text("D")
                    }
                }
            }
        }
    }
}
