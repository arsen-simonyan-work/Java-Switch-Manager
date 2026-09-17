package com.home.javaswitchmanager

import com.home.javaswitchmanager.domain.AppSnapshot
import com.home.javaswitchmanager.domain.ApplyResult
import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.SwitchPlan
import com.home.javaswitchmanager.platform.PlatformJavaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppController(
    private val manager: PlatformJavaManager,
) {
    val platformKey: String
        get() = manager.operatingSystem.name.lowercase()

    suspend fun refresh(): AppSnapshot = withContext(Dispatchers.IO) {
        val installations = manager.discoverInstallations()
            .filter { it.source.systemRegistered }

        AppSnapshot(
            installations = installations,
            environment = manager.readEnvironment(),
            targets = manager.readTargets(),
        )
    }

    suspend fun buildPlan(installation: JavaInstallation, selectedTargetIds: Set<String>): SwitchPlan =
        withContext(Dispatchers.IO) { manager.buildPlan(installation, selectedTargetIds) }

    suspend fun apply(plan: SwitchPlan): ApplyResult = withContext(Dispatchers.IO) {
        manager.applyPlan(plan)
    }
}
