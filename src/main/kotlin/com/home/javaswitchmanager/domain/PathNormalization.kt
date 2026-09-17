package com.home.javaswitchmanager.domain

import com.home.javaswitchmanager.platform.OperatingSystem
import java.nio.file.Path

object PathNormalization {
    fun canonicalKey(path: String?, caseInsensitive: Boolean = OperatingSystem.current() == OperatingSystem.WINDOWS): String? {
        if (path.isNullOrBlank()) return null
        val cleaned = path.trim().trim('"', '\'')
        val normalized = runCatching {
            Path.of(cleaned).toAbsolutePath().normalize().toString()
        }.getOrDefault(cleaned)
            .replace('\\', '/')
            .trimEnd('/')
        return if (caseInsensitive) normalized.lowercase() else normalized
    }

    fun samePath(left: Path, right: String?, caseInsensitive: Boolean = OperatingSystem.current() == OperatingSystem.WINDOWS): Boolean {
        val rightKey = canonicalKey(right, caseInsensitive)
        if (rightKey == null) return false
        return canonicalKey(left.toString(), caseInsensitive) == rightKey
    }

    fun samePath(left: String?, right: String?, caseInsensitive: Boolean = OperatingSystem.current() == OperatingSystem.WINDOWS): Boolean {
        val leftKey = canonicalKey(left, caseInsensitive)
        val rightKey = canonicalKey(right, caseInsensitive)
        if (leftKey == null || rightKey == null) return false
        return leftKey == rightKey
    }
}
