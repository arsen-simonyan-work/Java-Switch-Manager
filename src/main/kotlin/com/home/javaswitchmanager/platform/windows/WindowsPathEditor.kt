package com.home.javaswitchmanager.platform.windows

import java.nio.file.Files
import java.nio.file.Path

object WindowsPathEditor {
    fun rewrite(currentPath: String?, selectedJavaHome: String, currentJavaHomes: Collection<String?> = emptyList()): String {
        val selectedBin = Path.of(selectedJavaHome).resolve("bin").toString()
        val knownHomes = currentJavaHomes.filterNotNull().filter { it.isNotBlank() }
        val entries = currentPath.orEmpty()
            .split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { entry -> isManagedJavaEntry(entry, knownHomes) }
            .filterNot { entry -> samePath(entry, selectedBin) }

        return (listOf(selectedBin) + entries).joinToString(";")
    }

    private fun isManagedJavaEntry(entry: String, knownHomes: Collection<String>): Boolean {
        if (entry.equals("%JAVA_HOME%\\bin", ignoreCase = true) ||
            entry.equals("%JAVA_HOME%/bin", ignoreCase = true)
        ) {
            return true
        }
        if (knownHomes.any { home -> samePath(entry, Path.of(home).resolve("bin").toString()) }) {
            return true
        }

        val expanded = expandKnownEnvironment(entry, knownHomes.firstOrNull())
        return try {
            val bin = Path.of(expanded)
            if (!Files.isDirectory(bin)) return false
            val java = bin.resolve("java.exe")
            val release = bin.parent?.resolve("release")
            Files.isRegularFile(java) && release != null && Files.isRegularFile(release)
        } catch (_: Exception) {
            false
        }
    }

    private fun expandKnownEnvironment(value: String, javaHome: String?): String {
        var result = value
        if (javaHome != null) {
            result = result.replace("%JAVA_HOME%", javaHome, ignoreCase = true)
        }
        Regex("%([^%]+)%").findAll(result).toList().forEach { match ->
            val env = System.getenv(match.groupValues[1]) ?: return@forEach
            result = result.replace(match.value, env, ignoreCase = true)
        }
        return result
    }

    private fun samePath(left: String, right: String): Boolean {
        fun normalize(value: String): String = value.trim().trim('"').replace('/', '\\').trimEnd('\\').lowercase()
        return normalize(left) == normalize(right)
    }
}
