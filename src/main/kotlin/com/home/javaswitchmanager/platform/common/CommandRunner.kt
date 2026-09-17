package com.home.javaswitchmanager.platform.common

import java.nio.file.Path

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
) {
    val success: Boolean
        get() = !timedOut && exitCode == 0
}

interface CommandRunner {
    fun run(
        command: List<String>,
        timeoutSeconds: Long = 15,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: Path? = null,
    ): CommandResult

    fun commandExists(name: String): Boolean
}

object ShellQuoting {
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
