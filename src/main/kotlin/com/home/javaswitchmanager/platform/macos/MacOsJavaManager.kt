package com.home.javaswitchmanager.platform.macos

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
import com.home.javaswitchmanager.platform.common.ProcessCommandRunner
import java.nio.file.Files
import java.nio.file.Path

class MacOsJavaManager(
    private val commandRunner: CommandRunner = ProcessCommandRunner(),
) : PlatformJavaManager {
    override val operatingSystem = OperatingSystem.MACOS
    private val inspector = JavaInstallationInspector(commandRunner, windows = false)
    private val verifier = SwitchPlanVerifier(this)
    private val home = Path.of(System.getProperty("user.home"))

    override fun discoverInstallations(): List<JavaInstallation> {
        val discovery = DiscoverySupport(inspector)
        discovery.addText(System.getenv("JAVA_HOME"), JavaInstallationSource.JAVA_HOME)

        val javaHomeList = commandRunner.run(listOf("/usr/libexec/java_home", "-V"), timeoutSeconds = 5)
        if (javaHomeList.output.isNotBlank()) {
            Regex("(/[^\\n]+?/Contents/Home)").findAll(javaHomeList.output).forEach { match ->
                discovery.addText(match.groupValues[1].trim(), JavaInstallationSource.JAVA_HOME_TOOL)
            }
        }

        discovery.scanRoot(Path.of("/Library/Java/JavaVirtualMachines"), JavaInstallationSource.FILE_SCAN, depth = 3)
        discovery.scanRoot(home.resolve("Library").resolve("Java").resolve("JavaVirtualMachines"), JavaInstallationSource.FILE_SCAN, depth = 3)
        discovery.scanRoot(Path.of("/opt/homebrew/opt"), JavaInstallationSource.HOME_BREW, depth = 3, maxEntries = 220)
        discovery.scanRoot(Path.of("/usr/local/opt"), JavaInstallationSource.HOME_BREW, depth = 3, maxEntries = 220)
        discovery.scanRoot(home.resolve(".sdkman").resolve("candidates").resolve("java"), JavaInstallationSource.SDKMAN, depth = 2)
        discovery.scanRoot(home.resolve(".jdks"), JavaInstallationSource.IDE, depth = 2)
        return discovery.inspectAll()
    }

    override fun readEnvironment(): EnvironmentSnapshot {
        val installations = discoverInstallations()
        val activeCommand = commandRunner.run(listOf("sh", "-lc", "command -v java"), timeoutSeconds = 3)
            .takeIf { it.success }?.output?.lineSequence()?.firstOrNull()
        val pathJavaHome = resolveActiveHome(activeCommand)
        val processJavaHome = System.getenv("JAVA_HOME")
        val processHomeResolved = processJavaHome?.let {
            runCatching { inspector.normalizeCandidate(Path.of(it))?.toString() }.getOrNull()
        }
        val javaHomeResult = commandRunner.run(listOf("/usr/libexec/java_home"), timeoutSeconds = 5)
        val systemDefaultHome = javaHomeResult.takeIf { it.success }?.output?.lineSequence()?.firstOrNull()
        val shellJavaHome = ConfigFiles.readShellJavaHome(home.resolve(".zshrc"))
            ?: ConfigFiles.readShellJavaHome(home.resolve(".zprofile"))

        return EnvironmentDiagnostics.snapshot(
            operatingSystem.displayName,
            listOf(
                aspect(EnvironmentAspectId.JAVA_HOME, "JAVA_HOME", processJavaHome, processHomeResolved, installations),
                aspect(EnvironmentAspectId.PATH_JAVA, "PATH java", activeCommand, pathJavaHome, installations),
                aspect(EnvironmentAspectId.SHELL_JAVA_HOME, "Shell JAVA_HOME", shellJavaHome, shellJavaHome, installations),
                aspect(EnvironmentAspectId.SYSTEM_JAVA, "System Java", systemDefaultHome, systemDefaultHome, installations),
            ),
        )
    }

    override fun readTargets(): List<SwitchTargetState> {
        val zshrc = home.resolve(".zshrc")
        val zprofile = home.resolve(".zprofile")
        return listOf(
            SwitchTargetState(
                id = ZSHRC,
                title = "Shell · ~/.zshrc",
                description = "JAVA_HOME и PATH для интерактивного zsh.",
                currentValue = ConfigFiles.readShellJavaHome(zshrc),
                requiresElevation = false,
                defaultSelected = true,
            ),
            SwitchTargetState(
                id = ZPROFILE,
                title = "Login shell · ~/.zprofile",
                description = "JAVA_HOME и PATH для login-сессий macOS.",
                currentValue = ConfigFiles.readShellJavaHome(zprofile),
                requiresElevation = false,
                defaultSelected = true,
            ),
        )
    }

    override fun buildPlan(installation: JavaInstallation, selectedTargetIds: Set<String>): SwitchPlan {
        val targets = readTargets().associateBy { it.id }
        return SwitchPlan(
            installation,
            selectedTargetIds.mapNotNull { id ->
                val target = targets[id] ?: return@mapNotNull null
                SwitchOperation(
                    targetId = id,
                    title = target.title,
                    details = "Записать ${installation.home} в ${if (id == ZSHRC) "~/.zshrc" else "~/.zprofile"}",
                    requiresElevation = false,
                )
            },
        )
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
        val mutations = mutableListOf<FileMutation>()
        if (ZSHRC in ids) mutations += ConfigFiles.shellMutation(home.resolve(".zshrc"), plan.installation.home.toString())
        if (ZPROFILE in ids) mutations += ConfigFiles.shellMutation(home.resolve(".zprofile"), plan.installation.home.toString())

        val applied = mutableListOf<FileMutation>()
        val completed = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        return try {
            mutations.forEach { mutation ->
                val backup = mutation.apply()
                applied += mutation
                completed += mutation.path.toString()
                if (backup != null) warnings += "Backup: $backup"
            }
            val verifyOutcomes = verifier.verify(plan, ids, previousOutcomes)
            val allVerified = verifyOutcomes.all { it.verified }
            ApplyResult(
                success = allVerified,
                message = if (allVerified) {
                    "Java переключена для новых shell-сессий. Откройте новый Terminal."
                } else {
                    "Изменения применены, но verification обнаружила расхождения."
                },
                outcomes = verifyOutcomes,
                completedOperations = completed,
                warnings = warnings,
            )
        } catch (error: Exception) {
            applied.asReversed().forEach { runCatching { it.rollback() } }
            ApplyResult(
                false,
                "Переключение не завершено: ${error.message}",
                completedOperations = completed,
                warnings = warnings,
                rollbackPerformed = applied.isNotEmpty(),
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

    companion object {
        const val ZSHRC = "macos.zshrc"
        const val ZPROFILE = "macos.zprofile"
    }
}
