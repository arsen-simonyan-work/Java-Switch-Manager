package com.home.javaswitchmanager.platform

import com.home.javaswitchmanager.domain.ApplyResult
import com.home.javaswitchmanager.domain.EnvironmentSnapshot
import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.OperationOutcome
import com.home.javaswitchmanager.domain.SwitchPlan
import com.home.javaswitchmanager.domain.SwitchTargetState

interface PlatformJavaManager {
    val operatingSystem: OperatingSystem

    fun discoverInstallations(): List<JavaInstallation>
    fun readEnvironment(): EnvironmentSnapshot
    fun readTargets(): List<SwitchTargetState>
    fun buildPlan(installation: JavaInstallation, selectedTargetIds: Set<String>): SwitchPlan
    fun applyPlan(plan: SwitchPlan): ApplyResult
    fun verifyAppliedOperations(
        plan: SwitchPlan,
        appliedTargetIds: Set<String>,
        previousLabels: Map<String, String?>,
    ): List<OperationOutcome>
}
