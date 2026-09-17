package com.home.javaswitchmanager.domain

import com.home.javaswitchmanager.platform.common.CommandResult
import com.home.javaswitchmanager.platform.common.CommandRunner
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class FakeCommandRunner : CommandRunner {
    private val responses = ConcurrentHashMap<String, CommandResult>()
    private val existence = ConcurrentHashMap<String, Boolean>()
    val executedCommands = mutableListOf<List<String>>()

    fun stub(command: List<String>, result: CommandResult) {
        responses[commandKey(command)] = result
    }

    fun stubExists(name: String, exists: Boolean) {
        existence[name] = exists
    }

    override fun run(
        command: List<String>,
        timeoutSeconds: Long,
        environment: Map<String, String>,
        workingDirectory: Path?,
    ): CommandResult {
        executedCommands += command
        return responses[commandKey(command)] ?: CommandResult(0, "")
    }

    override fun commandExists(name: String): Boolean = existence[name] ?: false

    private fun commandKey(command: List<String>): String = command.joinToString("\u0000")
}
