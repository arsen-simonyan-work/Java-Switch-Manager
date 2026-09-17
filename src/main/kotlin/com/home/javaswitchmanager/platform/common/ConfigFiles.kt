package com.home.javaswitchmanager.platform.common

import java.nio.file.Files
import java.nio.file.Path

object ConfigFiles {
    fun readText(path: Path): String {
        return try {
            if (Files.exists(path)) Files.readString(path) else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun readShellJavaHome(path: Path): String? = ShellConfigEditor.readJavaHome(readText(path))

    fun shellMutation(path: Path, javaHome: String): FileMutation {
        return FileMutation(path, ShellConfigEditor.render(readText(path), javaHome))
    }

    fun readEnvironmentJavaHome(path: Path): String? {
        var result: String? = null
        readText(path).lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith('#')) return@forEach
            val match = Regex("^JAVA_HOME\\s*=\\s*(.+)$").matchEntire(line) ?: return@forEach
            result = match.groupValues[1].trim().trim('"', '\'')
        }
        return result
    }
}
