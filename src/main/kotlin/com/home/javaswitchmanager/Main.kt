package com.home.javaswitchmanager

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.home.javaswitchmanager.platform.PlatformJavaManagerFactory
import com.home.javaswitchmanager.settings.AppSettings
import com.home.javaswitchmanager.ui.JavaSwitchTheme
import com.home.javaswitchmanager.ui.MainScreen

fun main() = application {
    val manager = remember { PlatformJavaManagerFactory.create() }
    val controller = remember { AppController(manager) }
    val settings = remember { AppSettings() }
    val icon = painterResource("icons/app-icon.png")
    val state = rememberWindowState(size = DpSize(1100.dp, 760.dp))

    Window(
        onCloseRequest = ::exitApplication,
        title = "Java Switch Manager",
        state = state,
        icon = icon,
    ) {
        window.minimumSize = java.awt.Dimension(820, 620)
        JavaSwitchTheme {
            MainScreen(controller, settings)
        }
    }
}
