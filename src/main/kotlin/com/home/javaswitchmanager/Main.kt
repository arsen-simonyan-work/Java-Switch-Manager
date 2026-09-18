package com.home.javaswitchmanager

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import javax.imageio.ImageIO
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.home.javaswitchmanager.platform.PlatformJavaManagerFactory
import com.home.javaswitchmanager.settings.AppSettings
import com.home.javaswitchmanager.ui.JavaSwitchTheme
import com.home.javaswitchmanager.ui.MainScreen

private fun appVersion(): String? =
    Thread.currentThread().contextClassLoader
        .getResourceAsStream("app-version.txt")
        ?.bufferedReader()
        ?.use { it.readText().trim() }
        ?.takeIf { it.isNotBlank() }

fun main() = application {
    val manager = remember { PlatformJavaManagerFactory.create() }
    val controller = remember { AppController(manager) }
    val settings = remember { AppSettings() }
    val icons = remember {
        listOf(16, 24, 32, 48, 64, 96, 128, 256, 512).map { size ->
            val resource = checkNotNull(Thread.currentThread().contextClassLoader
                .getResource("icons/sizes/app-icon-$size.png"))
            checkNotNull(ImageIO.read(resource))
        }
    }
    val state = rememberWindowState(size = DpSize(1100.dp, 760.dp))
    val version = remember { appVersion() }
    val windowTitle = remember(version) {
        if (version == null) "Java Switch Manager" else "Java Switch Manager $version"
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle,
        state = state,
    ) {
        LaunchedEffect(window, icons) {
            window.iconImages = icons
        }
        window.minimumSize = java.awt.Dimension(820, 620)
        JavaSwitchTheme {
            MainScreen(controller, settings)
        }
    }
}
