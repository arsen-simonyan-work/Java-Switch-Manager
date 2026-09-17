package com.home.javaswitchmanager.platform

import com.home.javaswitchmanager.platform.linux.LinuxJavaManager
import com.home.javaswitchmanager.platform.macos.MacOsJavaManager
import com.home.javaswitchmanager.platform.windows.WindowsJavaManager

object PlatformJavaManagerFactory {
    fun create(os: OperatingSystem = OperatingSystem.current()): PlatformJavaManager {
        return when (os) {
            OperatingSystem.LINUX -> LinuxJavaManager()
            OperatingSystem.MACOS -> MacOsJavaManager()
            OperatingSystem.WINDOWS -> WindowsJavaManager()
            OperatingSystem.UNSUPPORTED -> UnsupportedJavaManager()
        }
    }
}
