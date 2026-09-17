package com.home.javaswitchmanager.platform

enum class OperatingSystem(val displayName: String) {
    LINUX("Linux"),
    MACOS("macOS"),
    WINDOWS("Windows"),
    UNSUPPORTED("Unsupported"),
    ;

    companion object {
        fun current(osName: String = System.getProperty("os.name", "")): OperatingSystem {
            val value = osName.lowercase()
            return when {
                value.contains("mac") || value.contains("darwin") -> MACOS
                value.contains("win") -> WINDOWS
                value.contains("linux") -> LINUX
                else -> UNSUPPORTED
            }
        }
    }
}
