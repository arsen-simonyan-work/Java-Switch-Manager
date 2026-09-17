package com.home.javaswitchmanager.platform.windows

import com.home.javaswitchmanager.domain.ApplyResult
import com.home.javaswitchmanager.domain.EnvironmentAspect
import com.home.javaswitchmanager.domain.EnvironmentAspectId
import com.home.javaswitchmanager.domain.EnvironmentDiagnostics
import com.home.javaswitchmanager.domain.EnvironmentSnapshot
import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.JavaInstallationSource
import com.home.javaswitchmanager.domain.OperationOutcome
import com.home.javaswitchmanager.domain.SwitchOperation
import com.home.javaswitchmanager.domain.SwitchPlan
import com.home.javaswitchmanager.domain.SwitchPlanVerifier
import com.home.javaswitchmanager.domain.SwitchTargetState
import com.home.javaswitchmanager.platform.OperatingSystem
import com.home.javaswitchmanager.platform.PlatformJavaManager
import com.home.javaswitchmanager.platform.common.CommandRunner
import com.home.javaswitchmanager.platform.common.DiscoverySupport
import com.home.javaswitchmanager.platform.common.JavaInstallationInspector
import com.home.javaswitchmanager.platform.common.PrivilegeService
import com.home.javaswitchmanager.platform.common.ProcessCommandRunner
import java.nio.file.Files
import java.nio.file.Path

class WindowsJavaManager(
    private val commandRunner: CommandRunner = ProcessCommandRunner(),
) : PlatformJavaManager {
    override val operatingSystem = OperatingSystem.WINDOWS
    private val inspector = JavaInstallationInspector(commandRunner, windows = true)
    private val privilegeService = PrivilegeService(operatingSystem, commandRunner)
    private val verifier = SwitchPlanVerifier(this)
    private val home = Path.of(System.getProperty("user.home"))

    override fun discoverInstallations(): List<JavaInstallation> {
        val discovery = DiscoverySupport(inspector)
        discovery.addText(System.getenv("JAVA_HOME"), JavaInstallationSource.JAVA_HOME)

        val where = commandRunner.run(listOf("where.exe", "java.exe"), timeoutSeconds = 5)
        if (where.success) where.output.lineSequence().forEach { discovery.addText(it, JavaInstallationSource.PATH) }

        registryJavaHomes().forEach { discovery.addText(it, JavaInstallationSource.REGISTRY) }

        val roots = mutableListOf<Path>()
        listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("LOCALAPPDATA"))
            .distinct()
            .forEach { base ->
                val b = Path.of(base)
                roots.add(b.resolve("Java"))
                roots.add(b.resolve("Eclipse Adoptium"))
                roots.add(b.resolve("Microsoft"))
                roots.add(b.resolve("Amazon Corretto"))
                roots.add(b.resolve("BellSoft"))
                roots.add(b.resolve("Zulu"))
            }
        roots.add(home.resolve(".jdks"))
        roots.forEach { discovery.scanRoot(it, JavaInstallationSource.FILE_SCAN, depth = 3, maxEntries = 220) }
        return discovery.inspectAll()
    }

    override fun readEnvironment(): EnvironmentSnapshot {
        val installations = discoverInstallations()
        val where = commandRunner.run(listOf("where.exe", "java.exe"), timeoutSeconds = 5)
        val command = where.takeIf { it.success }?.output?.lineSequence()?.firstOrNull()
        val pathJavaHome = resolveActiveHome(command)
        val userJavaHome = userJavaHome()
        val machineJavaHome = machineJavaHome()
        val processJavaHome = System.getenv("JAVA_HOME")

        return EnvironmentDiagnostics.snapshot(
            operatingSystem.displayName,
            listOf(
                aspect(EnvironmentAspectId.JAVA_HOME, "User JAVA_HOME", userJavaHome, userJavaHome, installations),
                aspect(EnvironmentAspectId.PROCESS_JAVA_HOME, "Process JAVA_HOME", processJavaHome, processJavaHome, installations),
                aspect(EnvironmentAspectId.PATH_JAVA, "PATH java", command, pathJavaHome, installations),
                aspect(EnvironmentAspectId.SYSTEM_JAVA, "System JAVA_HOME", machineJavaHome, machineJavaHome, installations),
            ),
        )
    }

    override fun readTargets(): List<SwitchTargetState> {
        val userJava = userJavaHome()
        val machineJava = machineJavaHome()
        return listOf(
            SwitchTargetState(
                id = USER_JAVA_HOME,
                title = "User · JAVA_HOME",
                description = "JAVA_HOME текущего пользователя. Не требует UAC.",
                currentValue = userJava,
                requiresElevation = false,
                defaultSelected = true,
            ),
            SwitchTargetState(
                id = USER_PATH,
                title = "User · Path",
                description = "Ставит выбранный JDK/bin первым в пользовательском Path. Системный Path Windows может иметь более высокий приоритет.",
                currentValue = queryRegistry(USER_ENV_KEY, "Path").value,
                requiresElevation = false,
                defaultSelected = true,
            ),
            SwitchTargetState(
                id = MACHINE_JAVA_HOME,
                title = "System · JAVA_HOME",
                description = "Системный JAVA_HOME. Windows покажет стандартный UAC prompt.",
                currentValue = machineJava,
                requiresElevation = true,
                defaultSelected = false,
            ),
            SwitchTargetState(
                id = MACHINE_PATH,
                title = "System · Path",
                description = "Ставит выбранный JDK/bin первым в системном Path. Требует UAC.",
                currentValue = queryRegistry(MACHINE_ENV_KEY, "Path").value,
                requiresElevation = true,
                defaultSelected = false,
            ),
        )
    }

    override fun buildPlan(installation: JavaInstallation, selectedTargetIds: Set<String>): SwitchPlan {
        val targets = readTargets().associateBy { it.id }
        val operations = selectedTargetIds.mapNotNull { id ->
            val target = targets[id] ?: return@mapNotNull null
            SwitchOperation(
                targetId = id,
                title = target.title,
                details = when (id) {
                    USER_JAVA_HOME, MACHINE_JAVA_HOME -> "Установить JAVA_HOME=${installation.home}"
                    USER_PATH, MACHINE_PATH -> "Поставить ${installation.home}\\bin первым в Path"
                    else -> target.description
                },
                requiresElevation = target.requiresElevation,
            )
        }.sortedBy { it.requiresElevation }
        return SwitchPlan(installation, operations)
    }

    override fun applyPlan(plan: SwitchPlan): ApplyResult {
        if (plan.operations.isEmpty()) return ApplyResult(false, "Не выбрано ни одной области применения.")
        val ids = plan.operations.map { it.targetId }.toSet()
        val targetsBefore = readTargets().associateBy { it.id }
        val previousOutcomes = plan.operations.associate { operation ->
            operation.targetId to verifier.labelForHome(
                targetsBefore[operation.targetId]?.currentValue,
                discoverInstallations(),
            )
        }
        val selectedHome = plan.installation.home.toString()
        val originalUserJava = queryRegistry(USER_ENV_KEY, "JAVA_HOME")
        val originalUserPath = queryRegistry(USER_ENV_KEY, "Path")
        val machineJava = queryRegistry(MACHINE_ENV_KEY, "JAVA_HOME")
        val machinePath = queryRegistry(MACHINE_ENV_KEY, "Path")
        val completed = mutableListOf<String>()

        try {
            if (USER_JAVA_HOME in ids) {
                setRegistry(USER_ENV_KEY, "JAVA_HOME", selectedHome, "REG_SZ")
                completed += "User JAVA_HOME"
            }
            if (USER_PATH in ids) {
                val rewritten = WindowsPathEditor.rewrite(
                    originalUserPath.value,
                    selectedHome,
                    listOf(originalUserJava.value, machineJava.value),
                )
                setRegistry(USER_ENV_KEY, "Path", rewritten, "REG_EXPAND_SZ")
                completed += "User Path"
            }

            if (MACHINE_JAVA_HOME in ids || MACHINE_PATH in ids) {
                val newMachinePath = if (MACHINE_PATH in ids) {
                    WindowsPathEditor.rewrite(
                        machinePath.value,
                        selectedHome,
                        listOf(machineJava.value, originalUserJava.value),
                    )
                } else null
                val script = buildMachineScript(
                    selectedHome = selectedHome,
                    setJavaHome = MACHINE_JAVA_HOME in ids,
                    newPath = newMachinePath,
                )
                val elevated = privilegeService.runElevatedScript(script)
                if (!elevated.success) {
                    throw IllegalStateException(elevated.output.ifBlank { "UAC operation cancelled or failed." })
                }
                if (MACHINE_JAVA_HOME in ids) completed += "System JAVA_HOME"
                if (MACHINE_PATH in ids) completed += "System Path"
            }

            broadcastEnvironmentChange()
            val verifyOutcomes = verifier.verify(plan, ids, previousOutcomes)
            val allVerified = verifyOutcomes.all { it.verified }
            return ApplyResult(
                success = allVerified,
                message = if (allVerified) {
                    "Java переключена. Новые приложения и терминалы получат обновлённое окружение."
                } else {
                    "Изменения применены, но verification обнаружила расхождения."
                },
                outcomes = verifyOutcomes,
                completedOperations = completed,
            )
        } catch (error: Exception) {
            runCatching { restoreRegistry(USER_ENV_KEY, "JAVA_HOME", originalUserJava) }
            runCatching { restoreRegistry(USER_ENV_KEY, "Path", originalUserPath) }
            broadcastEnvironmentChange()
            return ApplyResult(
                false,
                "Переключение не завершено: ${error.message}",
                completedOperations = completed,
                rollbackPerformed = completed.isNotEmpty(),
            )
        }
    }

    override fun verifyAppliedOperations(
        plan: SwitchPlan,
        appliedTargetIds: Set<String>,
        previousLabels: Map<String, String?>,
    ): List<OperationOutcome> {
        val targets = readTargets().associateBy { it.id }
        return plan.operations
            .filter { it.targetId in appliedTargetIds }
            .map { operation ->
                when (operation.targetId) {
                    USER_PATH, MACHINE_PATH -> verifier.verifyPathTarget(
                        plan,
                        operation,
                        targets[operation.targetId]?.currentValue,
                        previousLabels[operation.targetId],
                    )
                    else -> verifier.verifyJavaHomeTarget(
                        plan,
                        operation,
                        targets[operation.targetId]?.currentValue,
                        previousLabels[operation.targetId],
                    )
                }
            }
    }

    private fun aspect(
        id: EnvironmentAspectId,
        label: String,
        rawValue: String?,
        resolvedHome: String?,
        installations: List<JavaInstallation>,
    ): EnvironmentAspect = EnvironmentAspect(
        id = id,
        label = label,
        rawValue = rawValue,
        resolvedHome = resolvedHome,
        displayName = EnvironmentDiagnostics.displayLabel(resolvedHome, installations),
    )

    private fun registryJavaHomes(): List<String> {
        val keys = listOf(
            "HKLM\\SOFTWARE\\JavaSoft\\JDK",
            "HKLM\\SOFTWARE\\JavaSoft\\Java Development Kit",
            "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft\\JDK",
            "HKLM\\SOFTWARE\\WOW6432Node\\JavaSoft\\Java Development Kit",
        )
        val homes = mutableListOf<String>()
        for (key in keys) {
            val result = commandRunner.run(listOf("reg.exe", "query", key, "/s", "/v", "JavaHome"), timeoutSeconds = 5)
            if (!result.success) continue
            Regex("(?im)^\\s*JavaHome\\s+REG_\\w+\\s+(.+?)\\s*$").findAll(result.output).forEach {
                homes += it.groupValues[1].trim()
            }
        }
        return homes.distinct()
    }

    private fun resolveActiveHome(command: String?): String? {
        if (command.isNullOrBlank()) return null
        return try {
            val path = Path.of(command.trim())
            val real = if (Files.exists(path)) path.toRealPath() else path
            inspector.normalizeCandidate(real)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun userJavaHome(): String? = queryRegistry(USER_ENV_KEY, "JAVA_HOME").value
    private fun machineJavaHome(): String? = queryRegistry(MACHINE_ENV_KEY, "JAVA_HOME").value

    private fun queryRegistry(key: String, name: String): RegistryValue {
        val result = commandRunner.run(listOf("reg.exe", "query", key, "/v", name), timeoutSeconds = 5)
        if (!result.success) return RegistryValue(false, "REG_SZ", null)
        val escaped = Regex.escape(name)
        val match = Regex("(?im)^\\s*$escaped\\s+(REG_\\w+)\\s+(.*?)\\s*$").find(result.output)
            ?: return RegistryValue(false, "REG_SZ", null)
        return RegistryValue(true, match.groupValues[1], match.groupValues[2])
    }

    private fun setRegistry(key: String, name: String, value: String, type: String) {
        val result = commandRunner.run(
            listOf("reg.exe", "add", key, "/v", name, "/t", type, "/d", value, "/f"),
            timeoutSeconds = 10,
        )
        if (!result.success) throw IllegalStateException(result.output.ifBlank { "reg.exe failed" })
    }

    private fun restoreRegistry(key: String, name: String, value: RegistryValue) {
        if (value.exists && value.value != null) {
            setRegistry(key, name, value.value, value.type)
        } else {
            commandRunner.run(listOf("reg.exe", "delete", key, "/v", name, "/f"), timeoutSeconds = 10)
        }
    }

    private fun buildMachineScript(selectedHome: String, setJavaHome: Boolean, newPath: String?): String {
        fun ps(value: String): String = value.replace("'", "''")
        val java = ps(selectedHome)
        val path = newPath?.let(::ps)
        return buildString {
            appendLine("\$ErrorActionPreference = 'Stop'")
            appendLine("\$target = [System.EnvironmentVariableTarget]::Machine")
            appendLine("\$oldJava = [Environment]::GetEnvironmentVariable('JAVA_HOME', \$target)")
            appendLine("\$oldPath = [Environment]::GetEnvironmentVariable('Path', \$target)")
            appendLine("try {")
            if (setJavaHome) appendLine("  [Environment]::SetEnvironmentVariable('JAVA_HOME', '$java', \$target)")
            if (path != null) appendLine("  [Environment]::SetEnvironmentVariable('Path', '$path', \$target)")
            appendLine(environmentBroadcastPowerShell().prependIndent("  "))
            appendLine("} catch {")
            if (setJavaHome) appendLine("  [Environment]::SetEnvironmentVariable('JAVA_HOME', \$oldJava, \$target)")
            if (path != null) appendLine("  [Environment]::SetEnvironmentVariable('Path', \$oldPath, \$target)")
            appendLine("  throw")
            appendLine("}")
        }
    }

    private fun broadcastEnvironmentChange() {
        val script = environmentBroadcastPowerShell()
        commandRunner.run(listOf("powershell.exe", "-NoProfile", "-Command", script), timeoutSeconds = 10)
    }

    private fun environmentBroadcastPowerShell(): String = """
        if (-not ('EnvBroadcast' -as [type])) {
          Add-Type -TypeDefinition @'
        using System;
        using System.Runtime.InteropServices;
        public static class EnvBroadcast {
          [DllImport("user32.dll", SetLastError=true, CharSet=CharSet.Auto)]
          public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, UIntPtr wParam, string lParam, uint fuFlags, uint uTimeout, out UIntPtr lpdwResult);
        }
        '@
        }
        ${'$'}result = [UIntPtr]::Zero
        [void][EnvBroadcast]::SendMessageTimeout([IntPtr]0xffff, 0x001A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]${'$'}result)
    """.trimIndent()

    private data class RegistryValue(val exists: Boolean, val type: String, val value: String?)

    companion object {
        const val USER_JAVA_HOME = "windows.user.javaHome"
        const val USER_PATH = "windows.user.path"
        const val MACHINE_JAVA_HOME = "windows.machine.javaHome"
        const val MACHINE_PATH = "windows.machine.path"
        private const val USER_ENV_KEY = "HKCU\\Environment"
        private const val MACHINE_ENV_KEY = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"
    }
}
