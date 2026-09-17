package com.home.javaswitchmanager.platform.common

import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.JavaInstallationSource
import com.home.javaswitchmanager.platform.common.JavaReleaseParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class JavaInstallationInspector(
    private val commandRunner: CommandRunner,
    private val windows: Boolean,
) {
    private val javaExecutableName = if (windows) "java.exe" else "java"
    private val javacExecutableName = if (windows) "javac.exe" else "javac"

    fun inspect(candidate: Path, source: JavaInstallationSource): JavaInstallation? {
        val home = normalizeCandidate(candidate) ?: return null
        val javaExecutable = home.resolve("bin").resolve(javaExecutableName)
        if (!Files.isRegularFile(javaExecutable)) {
            return null
        }

        val release = readRelease(home)
        val version = release["JAVA_VERSION"]
            ?: readVersionFromExecutable(javaExecutable)
            ?: "unknown"
        val vendor = release["IMPLEMENTOR"] ?: release["JAVA_VENDOR"]
        val architecture = release["OS_ARCH"]
        val isJdk = Files.isRegularFile(home.resolve("bin").resolve(javacExecutableName))

        return JavaInstallation(
            home = canonical(home),
            version = version,
            vendor = vendor,
            architecture = architecture,
            source = source,
            javaExecutable = javaExecutable,
            isValid = true,
            featureVersion = JavaReleaseParser.featureVersion(version),
            isJdk = isJdk,
        )
    }

    fun normalizeCandidate(candidate: Path): Path? {
        var path = candidate.toAbsolutePath().normalize()

        if (Files.isRegularFile(path) && path.fileName.toString().equals(javaExecutableName, ignoreCase = windows)) {
            path = runCatching { path.toRealPath() }.getOrDefault(path)
            path = path.parent?.parent ?: return null
        }

        if (path.fileName?.toString().equals("bin", ignoreCase = windows)) {
            path = path.parent ?: return null
        }

        val macHome = path.resolve("Contents").resolve("Home")
        if (macHome.isDirectory()) {
            path = macHome
        }

        val homebrewMacHome = path.resolve("libexec").resolve("openjdk.jdk").resolve("Contents").resolve("Home")
        if (homebrewMacHome.isDirectory()) {
            path = homebrewMacHome
        }

        val javaExecutable = path.resolve("bin").resolve(javaExecutableName)
        return if (Files.isRegularFile(javaExecutable)) path else null
    }

    private fun readRelease(home: Path): Map<String, String> {
        val releaseFile = home.resolve("release")
        if (!releaseFile.exists()) {
            return emptyMap()
        }
        return try {
            JavaReleaseParser.parse(Files.readString(releaseFile))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readVersionFromExecutable(javaExecutable: Path): String? {
        val result = commandRunner.run(listOf(javaExecutable.toString(), "-version"), timeoutSeconds = 5)
        val line = result.output.lineSequence().firstOrNull { it.contains("version", ignoreCase = true) }
            ?: result.output.lineSequence().firstOrNull()
            ?: return null
        return Regex("(?:version\\s+)?\"([^\"]+)\"")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("(\\d+(?:[._+\\-]\\w*)*)").find(line)?.value
    }

    private fun canonical(path: Path): Path {
        return try {
            path.toRealPath()
        } catch (_: Exception) {
            path.toAbsolutePath().normalize()
        }
    }
}
