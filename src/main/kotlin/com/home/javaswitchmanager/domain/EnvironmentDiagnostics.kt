package com.home.javaswitchmanager.domain

object EnvironmentDiagnostics {
    fun snapshot(platformName: String, aspects: List<EnvironmentAspect>): EnvironmentSnapshot {
        val flagged = withMismatchFlags(aspects)
        return EnvironmentSnapshot(platformName = platformName, aspects = flagged)
    }

    fun withMismatchFlags(aspects: List<EnvironmentAspect>): List<EnvironmentAspect> {
        val comparable = aspects.filter { it.participatesInMismatch }
        return aspects.map { aspect ->
            if (!aspect.participatesInMismatch) {
                aspect.copy(mismatched = false)
            } else {
                val key = PathNormalization.canonicalKey(aspect.resolvedHome)
                if (key == null) {
                    aspect.copy(mismatched = false)
                } else {
                    val mismatched = comparable.any { other ->
                        val otherKey = PathNormalization.canonicalKey(other.resolvedHome)
                        otherKey != null && otherKey != key
                    }
                    aspect.copy(mismatched = mismatched)
                }
            }
        }
    }

    fun displayLabel(home: String?, installations: List<JavaInstallation>): String? {
        if (home.isNullOrBlank()) return null
        val match = installations.firstOrNull { PathNormalization.samePath(it.home.toString(), home) }
        return match?.displayName ?: home
    }
}
