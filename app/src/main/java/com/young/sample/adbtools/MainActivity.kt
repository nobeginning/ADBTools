package com.young.sample.adbtools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.young.sample.adbtools.ui.navigation.AppNavigation
import com.young.sample.adbtools.ui.theme.ADBToolsTheme
import com.young.sample.adbtools.ui.viewmodel.DeviceViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ADBToolsTheme(dynamicColor = true) {
                ADBToolsApp()
            }
        }
    }
}

@Composable
fun ADBToolsApp() {
    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        AppNavigation(
            navController = navController,
            deviceViewModel = deviceViewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
