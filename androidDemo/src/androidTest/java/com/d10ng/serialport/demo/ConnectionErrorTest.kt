package com.d10ng.serialport.demo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import com.d10ng.serialport.SerialPortInfo
import com.d10ng.serialport.demo.viewmodel.SerialPortViewModel
import org.junit.Rule
import org.junit.Test

class ConnectionErrorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun failedDeviceOpenShowsErrorWithoutLeavingConfiguration() {
        compose.onNodeWithText("机内串口通讯").performClick()
        compose.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[SerialPortViewModel::class.java]
                .openSerialPort(SerialPortInfo("/dev/dlserial-missing-test-device"))
        }
        val message = "打开串口失败: Serial port [/dev/dlserial-missing-test-device] does not exist"
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(message)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("机内串口配置").assertIsDisplayed()
    }
}
