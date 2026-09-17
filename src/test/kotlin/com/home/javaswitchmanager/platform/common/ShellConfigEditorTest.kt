package com.home.javaswitchmanager.platform.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellConfigEditorTest {
    @Test
    fun appendsAndReplacesManagedBlockIdempotently() {
        val original = "export FOO=bar\nexport JAVA_HOME=/old/jdk\n"
        val first = ShellConfigEditor.render(original, "/new/jdk-21")
        val second = ShellConfigEditor.render(first, "/new/jdk-17")

        assertEquals(1, Regex(Regex.escape(ShellConfigEditor.START_MARKER)).findAll(second).count())
        assertTrue(second.contains("export FOO=bar"))
        assertTrue(second.contains("export JAVA_HOME=\"/new/jdk-17\""))
        assertEquals("/new/jdk-17", ShellConfigEditor.readJavaHome(second))
    }

    @Test
    fun preservesIncompleteManagedBlock() {
        val content = "export FOO=bar\n${ShellConfigEditor.START_MARKER}\nexport JAVA_HOME=/broken\nexport AFTER=keep\n"
        val cleaned = ShellConfigEditor.removeManagedBlock(content)

        assertTrue(cleaned.contains(ShellConfigEditor.START_MARKER))
        assertTrue(cleaned.contains("export AFTER=keep"))
    }

    @Test
    fun escapesShellSensitiveCharactersInJavaHome() {
        val home = "/tmp/jdk${'$'}HOME`x`\\quoted\""
        val rendered = ShellConfigEditor.render("", home)

        assertTrue(rendered.contains("\\${'$'}HOME"))
        assertTrue(rendered.contains("\\`x\\`"))
        assertTrue(rendered.contains("\\\\quoted\\\""))
    }

    @Test
    fun ignoresCommentedJavaHome() {
        assertEquals(null, ShellConfigEditor.readJavaHome("# export JAVA_HOME=/old\n"))
    }
}
