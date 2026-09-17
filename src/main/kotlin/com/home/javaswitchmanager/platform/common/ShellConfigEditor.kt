package com.home.javaswitchmanager.platform.common

object ShellConfigEditor {
    const val START_MARKER = "# >>> Java Switch Manager >>>"
    const val END_MARKER = "# <<< Java Switch Manager <<<"

    private val javaHomeRegex = Regex("^(?:export\\s+)?JAVA_HOME\\s*=\\s*(.+?)\\s*$")

    fun readJavaHome(content: String): String? {
        var result: String? = null
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) {
                return@forEach
            }
            val match = javaHomeRegex.matchEntire(line) ?: return@forEach
            result = unquote(match.groupValues[1].trim())
        }
        return result
    }

    fun render(content: String, javaHome: String): String {
        val withoutManagedBlock = removeManagedBlock(content).trimEnd()
        val escapedHome = escapeForDoubleQuotedShell(javaHome)
        val managedBlock = buildString {
            appendLine(START_MARKER)
            appendLine("export JAVA_HOME=\"$escapedHome\"")
            appendLine("case \":\$PATH:\" in *\":\$JAVA_HOME/bin:\"*) ;; *) export PATH=\"\$JAVA_HOME/bin:\$PATH\" ;; esac")
            append(END_MARKER)
        }
        return if (withoutManagedBlock.isBlank()) {
            "$managedBlock\n"
        } else {
            "$withoutManagedBlock\n\n$managedBlock\n"
        }
    }

    fun removeManagedBlock(content: String): String {
        val lines = content.lines()
        val output = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            if (lines[index].trim() != START_MARKER) {
                output += lines[index]
                index++
                continue
            }

            val endIndex = (index + 1 until lines.size).firstOrNull { lines[it].trim() == END_MARKER }
            if (endIndex == null) {
                // Preserve a malformed/incomplete block instead of dropping the rest of the user's file.
                output += lines[index]
                index++
            } else {
                index = endIndex + 1
            }
        }
        return output.joinToString("\n")
    }

    private fun escapeForDoubleQuotedShell(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\', '"', '$', '`' -> append('\\').append(char)
                else -> append(char)
            }
        }
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && value.first() == value.last() && value.first() in charArrayOf('\'', '"')) {
            return value.substring(1, value.length - 1)
        }
        return value
    }
}
