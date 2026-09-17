package com.home.javaswitchmanager.domain

import java.nio.file.Path

enum class JavaInstallationSource(val label: String) {
    JAVA_HOME("JAVA_HOME"),
    UPDATE_ALTERNATIVES("update-alternatives"),
    PATH("PATH"),
    FILE_SCAN("File scan"),
    REGISTRY("Registry"),
    JAVA_HOME_TOOL("java_home"),
    SDKMAN("SDKMAN"),
    JENV("jEnv"),
    HOME_BREW("Homebrew"),
    IDE("IDE JDKs"),
    CUSTOM("Custom"),
    ;

    companion object {
        fun fromLabel(label: String): JavaInstallationSource =
            entries.firstOrNull { it.label == label } ?: CUSTOM
    }
}

data class JavaInstallation(
    val home: Path,
    val version: String,
    val vendor: String?,
    val architecture: String?,
    val source: JavaInstallationSource,
    val javaExecutable: Path,
    val isValid: Boolean,
    val featureVersion: Int? = null,
    val isJdk: Boolean = false,
) {
    val displayName: String
        get() = buildString {
            if (featureVersion != null) {
                append(featureVersion)
            } else if (version != "unknown") {
                append(version)
            }
            if (!vendor.isNullOrBlank()) {
                if (isNotEmpty()) append(' ')
                append(vendor)
            }
            if (isEmpty()) {
                append(if (isJdk) "JDK" else "JRE")
            }
        }
}

enum class EnvironmentAspectId {
    PROCESS_JAVA_HOME,
    JAVA_HOME,
    PATH_JAVA,
    SHELL_JAVA_HOME,
    SYSTEM_JAVA,
}

data class EnvironmentAspect(
    val id: EnvironmentAspectId,
    val label: String,
    val rawValue: String?,
    val resolvedHome: String?,
    val displayName: String?,
    val mismatched: Boolean = false,
)

data class EnvironmentSnapshot(
    val platformName: String,
    val aspects: List<EnvironmentAspect>,
) {
    val hasMismatch: Boolean
        get() = aspects.count { it.mismatched } > 0
}

data class SwitchTargetState(
    val id: String,
    val title: String,
    val description: String,
    val currentValue: String?,
    val requiresElevation: Boolean,
    val defaultSelected: Boolean,
)

data class SwitchOperation(
    val targetId: String,
    val title: String,
    val details: String,
    val requiresElevation: Boolean,
)

data class SwitchPlan(
    val installation: JavaInstallation,
    val operations: List<SwitchOperation>,
) {
    val requiresElevation: Boolean
        get() = operations.any { it.requiresElevation }
}

data class OperationOutcome(
    val targetId: String,
    val title: String,
    val previousLabel: String?,
    val newLabel: String?,
    val applied: Boolean,
    val verified: Boolean,
    val detail: String?,
)

data class ApplyResult(
    val success: Boolean,
    val message: String,
    val outcomes: List<OperationOutcome> = emptyList(),
    val completedOperations: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val rollbackPerformed: Boolean = false,
)

data class AppSnapshot(
    val installations: List<JavaInstallation>,
    val environment: EnvironmentSnapshot,
    val targets: List<SwitchTargetState>,
)
