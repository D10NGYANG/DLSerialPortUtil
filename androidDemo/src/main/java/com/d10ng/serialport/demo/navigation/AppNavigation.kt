package com.d10ng.serialport.demo.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.d10ng.serialport.demo.ui.screen.CommunicationScreen
import com.d10ng.serialport.demo.ui.screen.HomeScreen
import com.d10ng.serialport.demo.ui.screen.SerialConfigScreen
import com.d10ng.serialport.demo.ui.screen.UsbConfigScreen
import com.d10ng.serialport.demo.viewmodel.SerialPortViewModel

/**
 * 应用主导航组件
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    viewModel: SerialPortViewModel = viewModel()
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME
    ) {
        // 首页
        composable(NavRoutes.HOME) {
            HomeScreen(
                onNavigateToSerialConfig = {
                    navController.navigate(NavRoutes.SERIAL_CONFIG)
                },
                onNavigateToUsbConfig = {
                    navController.navigate(NavRoutes.USB_CONFIG)
                }
            )
        }
        
        // 机内串口配置页面
        composable(NavRoutes.SERIAL_CONFIG) {
            SerialConfigScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCommunication = {
                    navController.navigate(NavRoutes.COMMUNICATION) {
                        // 清除返回栈，防止用户通过返回键回到配置页面
                        popUpTo(NavRoutes.HOME) {
                            inclusive = false
                        }
                    }
                },
                viewModel = viewModel
            )
        }
        
        // USB串口配置页面
        composable(NavRoutes.USB_CONFIG) {
            UsbConfigScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCommunication = {
                    navController.navigate(NavRoutes.COMMUNICATION) {
                        // 清除返回栈，防止用户通过返回键回到配置页面
                        popUpTo(NavRoutes.HOME) {
                            inclusive = false
                        }
                    }
                },
                viewModel = viewModel
            )
        }
        
        // 通讯测试页面
        composable(NavRoutes.COMMUNICATION) {
            CommunicationScreen(
                onNavigateHome = {
                    // 返回首页并清除所有返回栈
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.HOME) {
                            inclusive = true
                        }
                    }
                },
                viewModel = viewModel
            )
        }
    }
}