package com.home.javaswitchmanager.platform.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsPathEditorTest {
    @Test
    fun selectedJavaBinIsFirstAndKnownJavaHomeIsRemoved() {
        val current = "%JAVA_HOME%\\bin;C:\\Tools;C:\\OldJdk\\bin"
        val result = WindowsPathEditor.rewrite(current, "C:\\NewJdk", listOf("C:\\OldJdk"))
        val parts = result.split(';')

        assertEquals("C:\\NewJdk\\bin".replace('\\', '/'), parts.first().replace('\\', '/'))
        assertFalse(parts.any { it.equals("%JAVA_HOME%\\bin", ignoreCase = true) })
        assertFalse(parts.any { it.equals("C:\\OldJdk\\bin", ignoreCase = true) })
        assertTrue(parts.contains("C:\\Tools"))
    }
}
