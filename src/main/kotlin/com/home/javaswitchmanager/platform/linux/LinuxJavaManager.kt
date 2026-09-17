package com.home.javaswitchmanager.platform.linux

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
import com.home.javaswitchmanager.platform.common.ConfigFiles
import com.home.javaswitchmanager.platform.common.DiscoverySupport
import com.home.javaswitchmanager.platform.common.FileMutation
import com.home.javaswitchmanager.platform.common.JavaInstallationInspector
import com.home.javaswitchmanager.platform.common.PrivilegeService
import com.home.javaswitchmanager.platform.common.ProcessCommandRunner
import com.home.javaswitchmanager.platform.common.ShellQuoting
import java.nio.file.Files
import java.nio.file.Path

class LinuxJavaManager(
    private val commandRunner: CommandRunner = ProcessCommandRunner(),
) : PlatformJavaManager {
    override val operatingSystem = OperatingSystem.LINUX
    private val inspector = JavaInstallationInspector(commandRunner, windows = false)
    private val privilegeService = PrivilegeService(operatingSystem, commandRunner)
    private val verifier = SwitchPlanVerifier(this)
    private val home = Path.of(System.getProperty("user.home"))

    override fun discoverInstallations(): List<JavaInstallation> {
        val discovery = DiscoverySupport(inspector)
        discovery.addText(System.getenv("JAVA_HOME"), JavaInstallationSource.JAVA_HOME)

        val alternatives = commandRunner.run(listOf("update-alternatives", "--list", "java"), timeoutSeconds = 5)
        if (alternatives.success) {
            alternatives.output.lineSequence().forEach {
                discovery.addText(it, JavaInstallationSource.UPDATE_ALTERNATIVES)
            }
        }

        val which = commandRunner.run(listOf("sh", "-lc", "command -v java"), timeoutSeconds = 3)
        if (which.success) discovery.addText(which.output.lineSequence().firstOrNull(), JavaInstallationSource.PATH)

        discovery.scanRoot(Path.of("/usr/lib/jvm"), JavaInstallationSource.FILE_SCAN, depth = 2)
        discovery.scanRoot(Path.of("/usr/java"), JavaInstallationSource.FILE_SCAN, depth = 2)
        discovery.scanRoot(Path.of("/opt"), JavaInstallationSource.FILE_SCAN, depth = 2, maxEntries = 180)
        discovery.scanRoot(home.resolve(".sdkman").resolve("candidates").resolve("java"), JavaInstallationSource.SDKMAN, depth = 2)
        discovery.scanRoot(home.resolve(".jenv").resolve("versions"), JavaInstallationSource.JENV, depth = 2)
        discovery.scanRoot(home.resolve(".jdks"), JavaInstallationSource.IDE, depth = 2)
        return discovery.inspectAll()
    }

    override fun readEnvironment(): EnvironmentSnapshot {
        val installations = discoverInstallations()
        val processJavaHome = System.getenv("JAVA_HOME")
        val activeCommand = commandRunner.run(listOf("sh", "-lc", "command -v java"), timeoutSeconds = 3)
            .takeIf { it.success }?.output?.lineSequence()?.firstOrNull()
        val pathJavaHome = resolveActiveHome(activeCommand)
        val systemJavaHome = currentAlternativeHome()
        val shellJavaHome = ConfigFiles.readShellJavaHome(home.resolve(".bashrc"))
            ?: ConfigFiles.readShellJavaHome(home.resolve(".profile"))

        return EnvironmentDiagnostics.snapshot(
            operatingSystem.displayName,
            listOf(
                aspect(
                    EnvironmentAspectId.JAVA_HOME,
                    "JAVA_HOME",
                    processJavaHome,
                    processJavaHome,
                    installations,
                ),
                aspect(
                    EnvironmentAspectId.PATH_JAVA,
                    "PATH java",
                    activeCommand,
                    pathJavaHome,
                    installations,
                ),
                aspect(
                    EnvironmentAspectId.SHELL_JAVA_HOME,
                    "Shell JAVA_HOME",
                    shellJavaHome,
                    shellJavaHome,
                    installations,
                ),
                aspect(
                    EnvironmentAspectId.SYSTEM_JAVA,
                    "System Java",
                    systemJavaHome,
                    systemJavaHome,
                    installations,
                ),
            ),
        )
    }

    override fun readTargets(): List<SwitchTargetState> {
        val bashrc = home.resolve(".bashrc")
        val profile = home.resolve(".profile")
        return listOf(
            SwitchTargetState(
                id = BASHRC,
                title = "Shell · ~/.bashrc",
                description = "JAVA_HOME и приоритет выбранного JDK/bin для интерактивного Bash.",
                currentValue = ConfigFiles.readShellJavaHome(bashrc),
                requiresElevation = false,
                defaultSelected = true,
            ),
            SwitchTargetState(
                id = PROFILE,
                title = "Login shell · ~/.profile",
                description = "JAVA_HOME и PATH для login-сессий.",
                currentValue = ConfigFiles.readShellJavaHome(profile),
                requiresElevation = false,
                defaultSelected = true,
            ),
            SwitchTargetState(
                id = ENVIRONMENT,
                title = "System · /etc/environment",
                description = "Системный JAVA_HOME. Требует PolicyKit-подтверждение.",
                currentValue = ConfigFiles.readEnvironmentJavaHome(Path.of("/etc/environment")),
                requiresElevation = true,
                defaultSelected = false,
            ),
            SwitchTargetState(
                id = ALTERNATIVES,
                title = "System · update-alternatives",
                description = "Переключает системный /usr/bin/java на выбранную установку.",
                currentValue = currentAlternativeHome(),
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
                    BASHRC -> "Записать ${installation.home} в ~/.bashrc"
                    PROFILE -> "Записать ${installation.home} в ~/.profile"
                    ENVIRONMENT -> "Записать JAVA_HOME=${installation.home} в /etc/environment"
                    ALTERNATIVES -> "update-alternatives --set java ${installation.home}/bin/java"
                    else -> target.description
                },
                requiresElevation = target.requiresElevation,
            )
        }.sortedBy { it.requiresElevation }
        return SwitchPlan(installation, operations)
    }

    override fun applyPlan(plan: SwitchPlan): ApplyResult {
        if (plan.operations.isEmpty()) {
            return ApplyResult(false, "Не выбрано ни одной области применения.")
        }
        val ids = plan.operations.map { it.targetId }.toSet()
        val targetsBefore = readTargets().associateBy { it.id }
        val previousOutcomes = plan.operations.associate { operation ->
            operation.targetId to verifier.labelForHome(
                targetsBefore[operation.targetId]?.currentValue,
                discoverInstallations(),
            )
        }
        val mutations = mutableListOf<FileMutation>()
        if (BASHRC in ids) mutations += ConfigFiles.shellMutation(home.resolve(".bashrc"), plan.installation.home.toString())
        if (PROFILE in ids) mutations += ConfigFiles.shellMutation(home.resolve(".profile"), plan.installation.home.toString())

        val applied = mutableListOf<FileMutation>()
        val completed = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var rollbackPerformed = false
        try {
            for (mutation in mutations) {
                val backup = mutation.apply()
                applied += mutation
                completed += mutation.path.toString()
                if (backup != null) warnings += "Backup: $backup"
            }

            if (ENVIRONMENT in ids || ALTERNATIVES in ids) {
                val script = buildPrivilegedScript(
                    javaHome = plan.installation.home.toString(),
                    updateEnvironment = ENVIRONMENT in ids,
                    updateAlternatives = ALTERNATIVES in ids,
                )
                val result = privilegeService.runElevatedScript(script)
                if (!result.success) {
                    throw IllegalStateException(
                        result.output.ifBlank { "Системная операция отменена или завершилась ошибкой." },
                    )
                }
                if (ENVIRONMENT in ids) completed += "/etc/environment"
                if (ALTERNATIVES in ids) completed += "update-alternatives"
            }
        } catch (error: Exception) {
            applied.asReversed().forEach { runCatching { it.rollback() } }
            rollbackPerformed = applied.isNotEmpty()
            return ApplyResult(
                success = false,
                message = "Переключение не завершено: ${error.message}",
                outcomes = failedOutcomes(plan, previousOutcomes, completed, rollbackPerformed),
                completedOperations = completed,
                warnings = warnings,
                rollbackPerformed = rollbackPerformed,
            )
        }

        val verifyOutcomes = verifier.verify(plan, ids, previousOutcomes)
        val allVerified = verifyOutcomes.all { it.verified }
        return ApplyResult(
            success = allVerified,
            message = if (allVerified) {
                "Java переключена. Откройте новый терминал, чтобы shell-переменные применились."
            } else {
                "Изменения применены, но verification обнаружила расхождения."
            },
            outcomes = verifyOutcomes,
            completedOperations = completed,
            warnings = warnings,
            rollbackPerformed = false,
        )
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
                verifier.verifyJavaHomeTarget(
                    plan,
                    operation,
                    targets[operation.targetId]?.currentValue,
                    previousLabels[operation.targetId],
                )
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

    private fun failedOutcomes(
        plan: SwitchPlan,
        previousOutcomes: Map<String, String?>,
        completed: List<String>,
        rollbackPerformed: Boolean,
    ): List<OperationOutcome> {
        return plan.operations.map { operation ->
            val applied = completed.any { it.contains(operation.targetId) || operation.title.contains(it) }
            OperationOutcome(
                targetId = operation.targetId,
                title = operation.title,
                previousLabel = previousOutcomes[operation.targetId],
                newLabel = null,
                applied = applied,
                verified = false,
                detail = if (rollbackPerformed) "Rollback выполнен" else null,
            )
        }
    }

    private fun currentAlternativeHome(): String? {
        val result = commandRunner.run(listOf("readlink", "-f", "/usr/bin/java"), timeoutSeconds = 3)
        return if (result.success) resolveActiveHome(result.output) else null
    }

    private fun resolveActiveHome(commandPath: String?): String? {
        if (commandPath.isNullOrBlank()) return null
        return try {
            val path = Path.of(commandPath.trim())
            val real = if (Files.exists(path)) path.toRealPath() else path
            inspector.normalizeCandidate(real)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPrivilegedScript(javaHome: String, updateEnvironment: Boolean, updateAlternatives: Boolean): String {
        val homeQuoted = ShellQuoting.shellQuote(javaHome)
        val javaQuoted = ShellQuoting.shellQuote(Path.of(javaHome).resolve("bin").resolve("java").toString())
        return buildString {
            appendLine("#!/bin/sh")
            appendLine("set -eu")
            appendLine("SUCCESS=0")
            appendLine("ENV_BACKUP=''")
            appendLine("ENV_EXISTED=0")
            appendLine("OLD_ALT=''")
            appendLine("rollback() {")
            appendLine("  if [ -n \"\$ENV_BACKUP\" ] && [ -f \"\$ENV_BACKUP\" ]; then if [ \"\$ENV_EXISTED\" -eq 1 ]; then cp \"\$ENV_BACKUP\" /etc/environment || true; else rm -f /etc/environment || true; fi; fi")
            appendLine("  if [ -n \"\$OLD_ALT\" ] && [ -e \"\$OLD_ALT\" ]; then update-alternatives --set java \"\$OLD_ALT\" >/dev/null 2>&1 || true; fi")
            appendLine("}")
            appendLine("cleanup() { status=\$?; trap - EXIT; if [ \"\$SUCCESS\" -ne 1 ]; then rollback; fi; [ -z \"\$ENV_BACKUP\" ] || rm -f \"\$ENV_BACKUP\"; exit \$status; }")
            appendLine("trap cleanup EXIT")
            if (updateEnvironment) {
                appendLine("ENV_BACKUP=\$(mktemp)")
                appendLine("if [ -f /etc/environment ]; then ENV_EXISTED=1; cp /etc/environment \"\$ENV_BACKUP\"; else : > \"\$ENV_BACKUP\"; : > /etc/environment; fi")
                appendLine("sed -i '/^[[:space:]]*JAVA_HOME[[:space:]]*=/d' /etc/environment")
                appendLine("printf '\\nJAVA_HOME=\"%s\"\\n' $homeQuoted >> /etc/environment")
            }
            if (updateAlternatives) {
                appendLine("command -v update-alternatives >/dev/null 2>&1 || { echo 'update-alternatives is not available' >&2; exit 40; }")
                appendLine("update-alternatives --list java 2>/dev/null | grep -Fx $javaQuoted >/dev/null || { echo 'Selected Java is not registered in update-alternatives' >&2; exit 41; }")
                appendLine("OLD_ALT=\$(readlink -f /etc/alternatives/java 2>/dev/null || true)")
                appendLine("update-alternatives --set java $javaQuoted")
            }
            appendLine("SUCCESS=1")
        }
    }

    companion object {
        const val BASHRC = "linux.bashrc"
        const val PROFILE = "linux.profile"
        const val ENVIRONMENT = "linux.environment"
        const val ALTERNATIVES = "linux.alternatives"
    }
}
