package com.home.javaswitchmanager.platform.common

import kotlin.test.Test
import kotlin.test.assertEquals

class JavaReleaseParserTest {
    @Test
    fun parsesQuotedReleaseValues() {
        val values = JavaReleaseParser.parse(
            """
            JAVA_VERSION="21.0.8"
            IMPLEMENTOR="Eclipse Adoptium"
            OS_ARCH="aarch64"
            """.trimIndent(),
        )
        assertEquals("21.0.8", values["JAVA_VERSION"])
        assertEquals("Eclipse Adoptium", values["IMPLEMENTOR"])
        assertEquals(21, JavaReleaseParser.featureVersion(values["JAVA_VERSION"]))
    }

    @Test
    fun handlesLegacyVersion() {
        assertEquals(8, JavaReleaseParser.featureVersion("1.8.0_472"))
    }
}
