package com.home.javaswitchmanager.domain

import com.home.javaswitchmanager.platform.PlatformJavaManager
import com.home.javaswitchmanager.platform.windows.WindowsPathEditor
import java.nio.file.Path

class SwitchPlanVerifier(
    private val manager: PlatformJavaManager,
) {
    fun verify(
        plan: SwitchPlan,
        appliedTargetIds: Set<String>,
        previousLabels: Map<String, String?>,
    ): List<OperationOutcome> = manager.verifyAppliedOperations(plan, appliedTargetIds, previousLabels)

    fun labelForHome(home: String?, installations: List<JavaInstallation>): String? =
        EnvironmentDiagnostics.displayLabel(home, installations)

    fun verifyJavaHomeTarget(
        plan: SwitchPlan,
        operation: SwitchOperation,
        currentValue: String?,
        previousLabel: String?,
    ): OperationOutcome {
        val verified = currentValue != null && PathNormalization.samePath(plan.installation.home.toString(), currentValue)
        return OperationOutcome(
            targetId = operation.targetId,
            title = operation.title,
            previousLabel = previousLabel,
            newLabel = if (verified) plan.installation.displayName else labelForHome(currentValue, manager.discoverInstallations()),
            applied = true,
            verified = verified,
            detail = if (verified) null else "Ожидалось: ${plan.installation.home}, фактически: ${currentValue ?: "не найдено"}",
        )
    }

    fun verifyPathTarget(
        plan: SwitchPlan,
        operation: SwitchOperation,
        currentPath: String?,
        previousLabel: String?,
    ): OperationOutcome {
        val expectedBin = Path.of(plan.installation.home.toString()).resolve("bin").toString()
        val firstEntry = currentPath.orEmpty().split(';').firstOrNull { it.isNotBlank() }
        val verified = firstEntry != null && PathNormalization.samePath(firstEntry, expectedBin)
        return OperationOutcome(
            targetId = operation.targetId,
            title = operation.title,
            previousLabel = previousLabel,
            newLabel = if (verified) plan.installation.displayName else previousLabel,
            applied = true,
            verified = verified,
            detail = if (verified) null else "Первый Path entry: ${firstEntry ?: "пусто"}, ожидалось: $expectedBin",
        )
    }
}
