package com.home.javaswitchmanager.settings

import java.nio.file.Files
import java.util.Properties

class AppSettings {
    private val file = AppDirectories.dataDirectory().resolve("settings.properties")

    fun loadSelectedTargets(platformKey: String): Set<String>? {
        val properties = load()
        val raw = properties.getProperty("targets.$platformKey") ?: return null
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun saveSelectedTargets(platformKey: String, ids: Set<String>) {
        val properties = load()
        properties.setProperty("targets.$platformKey", ids.sorted().joinToString(","))
        try {
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { properties.store(it, "Java Switch Manager") }
        } catch (_: Exception) {
            // Settings persistence must never block Java switching.
        }
    }

    private fun load(): Properties {
        val properties = Properties()
        try {
            if (Files.exists(file)) {
                Files.newInputStream(file).use(properties::load)
            }
        } catch (_: Exception) {
            // Ignore unreadable settings and continue with defaults.
        }
        return properties
    }
}
