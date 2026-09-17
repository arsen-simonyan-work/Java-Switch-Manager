package com.home.javaswitchmanager.platform.common

import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.JavaInstallationSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import kotlin.io.path.isDirectory

class DiscoverySupport(
    private val inspector: JavaInstallationInspector,
) {
    private val candidates = LinkedHashMap<Path, JavaInstallationSource>()

    fun add(path: Path?, source: JavaInstallationSource) {
        if (path == null) {
            return
        }
        val normalized = path.toAbsolutePath().normalize()
        val previous = candidates[normalized]
        if (previous == null || (!previous.systemRegistered && source.systemRegistered)) {
            candidates[normalized] = source
        }
    }

    fun addText(path: String?, source: JavaInstallationSource) {
        if (path.isNullOrBlank()) {
            return
        }
        try {
            add(Path.of(path.trim().trim('"')), source)
        } catch (_: Exception) {
            // Ignore malformed environment/command candidates.
        }
    }

    fun scanRoot(root: Path?, source: JavaInstallationSource, depth: Int = 2, maxEntries: Int = 250) {
        if (root == null || !root.isDirectory()) {
            return
        }
        add(root, source)
        var count = 0
        try {
            Files.walk(root, depth.coerceAtLeast(1)).use { stream ->
                val iterator = stream.iterator()
                while (iterator.hasNext() && count < maxEntries) {
                    val path = iterator.next()
                    count++
                    if (Files.isDirectory(path)) {
                        add(path, source)
                    }
                }
            }
        } catch (_: Exception) {
            // Discovery is best-effort; inaccessible folders are expected.
        }
    }

    fun inspectAll(): List<JavaInstallation> {
        val unique = LinkedHashMap<String, JavaInstallation>()
        for ((candidate, source) in candidates) {
            val installation = inspector.inspect(candidate, source) ?: continue
            val key = normalizedKey(installation.home)
            val previous = unique[key]
            val shouldReplace = previous == null ||
                (!previous.source.systemRegistered && installation.source.systemRegistered) ||
                (previous.source.systemRegistered == installation.source.systemRegistered &&
                    !previous.isJdk && installation.isJdk)
            if (shouldReplace) {
                unique[key] = installation
            }
        }
        return unique.values.sortedWith(
            compareByDescending<JavaInstallation> { it.featureVersion ?: -1 }
                .thenBy { it.vendor.orEmpty().lowercase() }
                .thenBy { it.home.toString().lowercase() },
        )
    }

    private fun normalizedKey(path: Path): String = path.toString().trimEnd('/', '\\')
}
