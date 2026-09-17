package com.home.javaswitchmanager.platform

import com.home.javaswitchmanager.domain.ApplyResult
import com.home.javaswitchmanager.domain.EnvironmentSnapshot
import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.OperationOutcome
import com.home.javaswitchmanager.domain.SwitchPlan
import com.home.javaswitchmanager.domain.SwitchTargetState

class UnsupportedJavaManager : PlatformJavaManager {
    override val operatingSystem = OperatingSystem.UNSUPPORTED

    override fun discoverInstallations(): List<JavaInstallation> = emptyList()
    override fun readEnvironment(): EnvironmentSnapshot = EnvironmentSnapshot("Unsupported", emptyList())
    override fun readTargets(): List<SwitchTargetState> = emptyList()
    override fun buildPlan(installation: JavaInstallation, selectedTargetIds: Set<String>) = SwitchPlan(installation, emptyList())
    override fun applyPlan(plan: SwitchPlan) = ApplyResult(false, "Эта операционная система пока не поддерживается.")
    override fun verifyAppliedOperations(
        plan: SwitchPlan,
        appliedTargetIds: Set<String>,
        previousLabels: Map<String, String?>,
    ): List<OperationOutcome> = emptyList()
}
