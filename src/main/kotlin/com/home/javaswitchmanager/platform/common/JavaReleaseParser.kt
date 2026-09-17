package com.home.javaswitchmanager.platform.common

object JavaReleaseParser {
    fun parse(content: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) {
                return@forEach
            }
            val index = line.indexOf('=')
            if (index <= 0) {
                return@forEach
            }
            val key = line.substring(0, index).trim()
            val rawValue = line.substring(index + 1).trim()
            result[key] = unquote(rawValue)
        }
        return result
    }

    fun featureVersion(version: String?): Int? {
        if (version.isNullOrBlank()) {
            return null
        }
        val normalized = version.trim().removePrefix("1.")
        return Regex("^(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return value
    }
}
