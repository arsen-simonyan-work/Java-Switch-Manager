package com.home.javaswitchmanager.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class OperatingSystemTest {
    @Test fun detectsWindows() = assertEquals(OperatingSystem.WINDOWS, OperatingSystem.current("Windows 11"))
    @Test fun detectsLinux() = assertEquals(OperatingSystem.LINUX, OperatingSystem.current("Linux"))
    @Test fun detectsMac() = assertEquals(OperatingSystem.MACOS, OperatingSystem.current("Mac OS X"))
}
