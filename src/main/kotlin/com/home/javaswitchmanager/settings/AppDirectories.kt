package com.home.javaswitchmanager.settings

import com.home.javaswitchmanager.platform.OperatingSystem
import java.nio.file.Path

object AppDirectories {
    fun dataDirectory(): Path {
        val home = Path.of(System.getProperty("user.home"))
        return when (OperatingSystem.current()) {
            OperatingSystem.WINDOWS -> {
                val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                    ?.let { Path.of(it) }
                    ?: home.resolve("AppData").resolve("Local")
                base.resolve("JavaSwitchManager")
            }
            OperatingSystem.MACOS -> home.resolve("Library").resolve("Application Support").resolve("JavaSwitchManager")
            OperatingSystem.LINUX, OperatingSystem.UNSUPPORTED -> {
                val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?.let { Path.of(it) }
                    ?: home.resolve(".local").resolve("share")
                base.resolve("java-switch-manager")
            }
        }
    }

    fun backupDirectory(): Path = dataDirectory().resolve("backups")
}
