package com.home.javaswitchmanager.platform.common

import com.home.javaswitchmanager.settings.AppDirectories
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileMutation(
    val path: Path,
    private val newContent: String,
) {
    private val existed = Files.exists(path)
    private val originalPermissions: Set<PosixFilePermission>? = if (existed) {
        runCatching { Files.getPosixFilePermissions(path) }.getOrNull()
    } else {
        null
    }
    private val originalContent: ByteArray? = if (existed) {
        try {
            Files.readAllBytes(path)
        } catch (error: Exception) {
            throw IllegalStateException("Не удалось прочитать $path: ${error.message}", error)
        }
    } else {
        null
    }

    fun apply(): Path? {
        val backup = if (existed) backupOriginal() else null
        path.parent?.let { Files.createDirectories(it) }
        val temp = Files.createTempFile(path.parent ?: Path.of("."), ".jsm-", ".tmp")
        try {
            Files.writeString(temp, newContent, StandardCharsets.UTF_8)
            originalPermissions?.let { permissions ->
                runCatching { Files.setPosixFilePermissions(temp, permissions) }
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        return backup
    }

    fun rollback() {
        if (existed && originalContent != null) {
            path.parent?.let { Files.createDirectories(it) }
            Files.write(path, originalContent)
            originalPermissions?.let { permissions ->
                runCatching { Files.setPosixFilePermissions(path, permissions) }
            }
        } else {
            Files.deleteIfExists(path)
        }
    }

    private fun backupOriginal(): Path {
        val dir = AppDirectories.backupDirectory()
        Files.createDirectories(dir)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"))
        val safeName = path.fileName?.toString()?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "config"
        val backup = dir.resolve("$safeName-$stamp.bak")
        Files.write(backup, originalContent!!)
        return backup
    }
}
