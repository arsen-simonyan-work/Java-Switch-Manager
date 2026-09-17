package com.home.javaswitchmanager.domain

object EnvironmentDiagnostics {
    fun snapshot(platformName: String, aspects: List<EnvironmentAspect>): EnvironmentSnapshot {
        val flagged = withMismatchFlags(aspects)
        return EnvironmentSnapshot(platformName = platformName, aspects = flagged)
    }

    fun withMismatchFlags(aspects: List<EnvironmentAspect>): List<EnvironmentAspect> {
        return aspects.map { aspect ->
            val key = PathNormalization.canonicalKey(aspect.resolvedHome)
            if (key == null) {
                aspect
            } else {
                val mismatched = aspects.any { other ->
                    val otherKey = PathNormalization.canonicalKey(other.resolvedHome)
                    otherKey != null && otherKey != key
                }
                aspect.copy(mismatched = mismatched)
            }
        }
    }

    fun displayLabel(home: String?, installations: List<JavaInstallation>): String? {
        if (home.isNullOrBlank()) return null
        val match = installations.firstOrNull { PathNormalization.samePath(it.home.toString(), home) }
        return match?.displayName ?: home
    }
}
