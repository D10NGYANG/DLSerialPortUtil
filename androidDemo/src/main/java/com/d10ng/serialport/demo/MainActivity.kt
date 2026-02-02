package com.d10ng.serialport.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.d10ng.log.LogLevel
import com.d10ng.serialport.SerialPortManagerLog
import com.d10ng.serialport.demo.navigation.AppNavigation
import com.d10ng.serialport.demo.ui.theme.DLSerialPortUtilTheme
import com.d10ng.serialport.demo.viewmodel.SerialPortViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: SerialPortViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SerialPortManagerLog.miniLevel = LogLevel.VERBOSE
        setContent {
            DLSerialPortUtilTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SerialPortApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun SerialPortApp(
    viewModel: SerialPortViewModel = viewModel()
) {
    val navController = rememberNavController()
    
    AppNavigation(
        navController = navController,
        viewModel = viewModel
    )
}

@Preview(showBackground = true)
@Composable
fun SerialPortAppPreview() {
    DLSerialPortUtilTheme {
        SerialPortApp()
    }
}