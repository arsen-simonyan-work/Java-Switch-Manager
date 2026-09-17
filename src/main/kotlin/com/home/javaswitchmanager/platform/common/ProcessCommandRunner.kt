package com.home.javaswitchmanager.platform.common

import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProcessCommandRunner : CommandRunner {
    override fun run(
        command: List<String>,
        timeoutSeconds: Long,
        environment: Map<String, String>,
        workingDirectory: Path?,
    ): CommandResult {
        return try {
            val builder = ProcessBuilder(command)
                .redirectErrorStream(true)
            if (environment.isNotEmpty()) {
                builder.environment().putAll(environment)
            }
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile())
            }

            val process = builder.start()
            val outputExecutor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "java-switch-command-output").apply { isDaemon = true }
            }
            val outputFuture = outputExecutor.submit<String> {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            try {
                val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroy()
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly()
                        process.waitFor(2, TimeUnit.SECONDS)
                    }
                    val partialOutput = runCatching { outputFuture.get(2, TimeUnit.SECONDS) }.getOrDefault("").trim()
                    val message = buildString {
                        append("Command timed out: ")
                        append(command.joinToString(" "))
                        if (partialOutput.isNotBlank()) {
                            appendLine()
                            append(partialOutput)
                        }
                    }
                    return CommandResult(-1, message, timedOut = true)
                }
                val output = outputFuture.get(2, TimeUnit.SECONDS).trim()
                CommandResult(process.exitValue(), output)
            } finally {
                outputExecutor.shutdownNow()
            }
        } catch (error: Exception) {
            CommandResult(-1, error.message ?: error::class.java.simpleName)
        }
    }

    override fun commandExists(name: String): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        val command = if (os.contains("win")) {
            listOf("where.exe", name)
        } else {
            listOf("sh", "-c", "command -v ${ShellQuoting.shellQuote(name)} >/dev/null 2>&1")
        }
        return run(command, timeoutSeconds = 3).success
    }
}
