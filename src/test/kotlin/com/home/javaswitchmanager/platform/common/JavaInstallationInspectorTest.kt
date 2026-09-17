package com.home.javaswitchmanager.platform.common

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JavaInstallationInspectorTest {
    @Test
    fun resolvesJavaExecutableSymlinkBeforeDerivingHome() {
        if (System.getProperty("os.name", "").lowercase().contains("win")) return

        val root = Files.createTempDirectory("jsm-inspector-")
        try {
            val home = root.resolve("jdk-21")
            Files.createDirectories(home.resolve("bin"))
            Files.writeString(home.resolve("bin/java"), "")
            Files.writeString(home.resolve("bin/javac"), "")
            Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.1\"\n")
            val launchDir = root.resolve("usr/bin")
            Files.createDirectories(launchDir)
            val launcher = launchDir.resolve("java")
            Files.createSymbolicLink(launcher, home.resolve("bin/java"))

            val inspector = JavaInstallationInspector(ProcessCommandRunner(), windows = false)
            val normalized = inspector.normalizeCandidate(launcher)

            assertNotNull(normalized)
            assertEquals(home.toRealPath(), normalized.toRealPath())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
