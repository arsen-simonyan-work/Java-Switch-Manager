package com.home.javaswitchmanager.platform.common

import com.home.javaswitchmanager.platform.OperatingSystem
import java.nio.file.Files
import kotlin.io.path.writeText

class PrivilegeService(
    private val operatingSystem: OperatingSystem,
    private val commandRunner: CommandRunner,
) {
    fun runElevatedScript(script: String): CommandResult {
        return when (operatingSystem) {
            OperatingSystem.LINUX -> runLinux(script)
            OperatingSystem.WINDOWS -> runWindows(script)
            OperatingSystem.MACOS -> CommandResult(-1, "Elevated operations are not used on macOS in this application")
            OperatingSystem.UNSUPPORTED -> CommandResult(-1, "Unsupported operating system")
        }
    }

    private fun runLinux(script: String): CommandResult {
        if (!commandRunner.commandExists("pkexec")) {
            return CommandResult(
                -1,
                "Для системных изменений нужен pkexec (PolicyKit). Установите PolicyKit или снимите системные опции.",
            )
        }
        val file = Files.createTempFile("java-switch-manager-", ".sh")
        return try {
            file.writeText(script)
            commandRunner.run(listOf("pkexec", "/bin/sh", file.toString()), timeoutSeconds = 120)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun runWindows(script: String): CommandResult {
        val file = Files.createTempFile("java-switch-manager-", ".ps1")
        return try {
            file.writeText(script)
            val escaped = file.toString().replace("'", "''")
            val argumentLine = "-NoProfile -ExecutionPolicy Bypass -File \"$escaped\""
                .replace("'", "''")
            val command = """
                ${'$'}p = Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru -ArgumentList '$argumentLine';
                exit ${'$'}p.ExitCode
            """.trimIndent().replace("\n", " ")
            commandRunner.run(
                listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command),
                timeoutSeconds = 180,
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }

}
