package com.home.javaswitchmanager.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathNormalizationTest {
    @Test
    fun trickyLinuxSdkmanPath() {
        val left = "/home/user/.sdkman/candidates/java/21.0.8-tem"
        val right = "/home/user/.sdkman/candidates/java/21.0.8-tem/"
        assertTrue(PathNormalization.samePath(left, right, caseInsensitive = false))
    }

    @Test
    fun trickyMacOsJdkPath() {
        val path = "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
        assertEquals(path, PathNormalization.canonicalKey(path, caseInsensitive = false))
    }

    @Test
    fun trickyWindowsPathIsCaseInsensitive() {
        val left = "C:\\Program Files\\Java\\jdk-21"
        val right = "c:/program files/java/jdk-21"
        assertTrue(PathNormalization.samePath(left, right, caseInsensitive = true))
    }
}

class EnvironmentDiagnosticsTest {
    @Test
    fun marksMismatchWhenHomesDiffer() {
        val aspects = listOf(
            EnvironmentAspect(EnvironmentAspectId.JAVA_HOME, "JAVA_HOME", "/a", "/a", "21", false),
            EnvironmentAspect(EnvironmentAspectId.PATH_JAVA, "PATH java", "/b/bin/java", "/b", "17", false),
        )
        val snapshot = EnvironmentDiagnostics.snapshot("Linux", aspects)
        assertTrue(snapshot.hasMismatch)
        assertTrue(snapshot.aspects.single { it.label == "PATH java" }.mismatched)
    }
}
