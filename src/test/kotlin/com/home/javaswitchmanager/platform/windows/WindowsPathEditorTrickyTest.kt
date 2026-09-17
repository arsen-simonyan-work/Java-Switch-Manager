package com.home.javaswitchmanager.platform.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsPathEditorTrickyTest {
    @Test
    fun prependsProgramFilesJdkBin() {
        val current = "C:\\Windows\\System32;C:\\Program Files\\Java\\jdk-17\\bin"
        val result = WindowsPathEditor.rewrite(
            current,
            "C:\\Program Files\\Java\\jdk-21",
            listOf("C:\\Program Files\\Java\\jdk-17"),
        )
        assertEquals(
            "C:\\Program Files\\Java\\jdk-21\\bin".replace('\\', '/'),
            result.split(';').first().replace('\\', '/'),
        )
        assertTrue(result.contains("C:\\Windows\\System32"))
    }
}
