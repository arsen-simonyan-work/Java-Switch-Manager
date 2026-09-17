package com.home.javaswitchmanager.platform.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShellConfigEditorAdvancedTest {
    @Test
    fun readsExportWithExtraSpacesAndQuotes() {
        val content = """
            export  JAVA_HOME="/opt/jdk-21"
            JAVA_HOME=/legacy
        """.trimIndent()
        assertEquals("/legacy", ShellConfigEditor.readJavaHome(content))
    }

    @Test
    fun ignoresCommentedJavaHomeLines() {
        val content = """
            # export JAVA_HOME=/old/path
            export FOO=bar
        """.trimIndent()
        assertNull(ShellConfigEditor.readJavaHome(content))
    }

    @Test
    fun idempotentManagedBlockDoesNotDuplicateActiveJavaHome() {
        val renderedOnce = ShellConfigEditor.render("", "C:\\Program Files\\Java\\jdk-21")
        val renderedTwice = ShellConfigEditor.render(renderedOnce, "C:\\Program Files\\Java\\jdk-21")
        assertEquals(1, Regex("export JAVA_HOME=").findAll(renderedTwice).count())
    }
}
