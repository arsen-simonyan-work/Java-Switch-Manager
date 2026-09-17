package com.home.javaswitchmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.home.javaswitchmanager.AppController
import com.home.javaswitchmanager.domain.ApplyResult
import com.home.javaswitchmanager.domain.AppSnapshot
import com.home.javaswitchmanager.domain.EnvironmentAspect
import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.OperationOutcome
import com.home.javaswitchmanager.domain.PathNormalization
import com.home.javaswitchmanager.domain.SwitchPlan
import com.home.javaswitchmanager.settings.AppSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun MainScreen(controller: AppController, settings: AppSettings) {
    var snapshot by remember { mutableStateOf<AppSnapshot?>(null) }
    var selected by remember { mutableStateOf<JavaInstallation?>(null) }
    var selectedTargets by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isApplying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ApplyResult?>(null) }
    var confirmPlan by remember { mutableStateOf<SwitchPlan?>(null) }
    val scope = rememberCoroutineScope()
    val operationMutex = remember { Mutex() }

    fun activeJavaHome(snapshot: AppSnapshot): String? =
        snapshot.environment.aspects.firstOrNull { it.id.name.contains("PATH") && it.resolvedHome != null }?.resolvedHome
            ?: snapshot.environment.aspects.firstOrNull { it.resolvedHome != null }?.resolvedHome

    fun chooseDefaults(newSnapshot: AppSnapshot, previousSelection: JavaInstallation?) {
        val remembered = settings.loadSelectedTargets(controller.platformKey)
        selectedTargets = remembered
            ?.intersect(newSnapshot.targets.map { it.id }.toSet())
            ?: newSnapshot.targets.filter { it.defaultSelected }.map { it.id }.toSet()

        val previousHome = previousSelection?.home?.toString()
        selected = newSnapshot.installations.firstOrNull { PathNormalization.samePath(it.home, previousHome) }
            ?: newSnapshot.installations.firstOrNull { PathNormalization.samePath(it.home, activeJavaHome(newSnapshot)) }
            ?: newSnapshot.installations.firstOrNull { installation ->
                newSnapshot.environment.aspects.any { env ->
                    env.resolvedHome != null && PathNormalization.samePath(installation.home, env.resolvedHome)
                }
            }
            ?: newSnapshot.installations.firstOrNull()
    }

    fun refresh() {
        if (isApplying) return
        scope.launch {
            operationMutex.withLock {
                isLoading = true
                error = null
                result = null
                try {
                    val newSnapshot = controller.refresh()
                    val previous = selected
                    snapshot = newSnapshot
                    chooseDefaults(newSnapshot, previous)
                } catch (t: Throwable) {
                    error = t.message ?: t::class.java.simpleName
                } finally {
                    isLoading = false
                }
            }
        }
    }

    fun requestApply() {
        if (isLoading || isApplying || selectedTargets.isEmpty()) return
        val java = selected ?: return
        scope.launch {
            val plan = controller.buildPlan(java, selectedTargets)
            if (plan.operations.isEmpty()) {
                result = ApplyResult(false, "Выберите хотя бы одну область применения.")
            } else {
                confirmPlan = plan
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val newSnapshot = controller.refresh()
            snapshot = newSnapshot
            chooseDefaults(newSnapshot, null)
        } catch (t: Throwable) {
            error = t.message ?: t::class.java.simpleName
        } finally {
            isLoading = false
        }
    }

    Surface(color = AppColors.Background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                if (error != null) {
                    StatusBanner(error!!, AppColors.Error)
                    Spacer(Modifier.height(8.dp))
                }
                if (result != null) {
                    ApplyResultBanner(result!!)
                    Spacer(Modifier.height(8.dp))
                }

                if (isLoading && snapshot == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.Accent)
                    }
                } else {
                    val current = snapshot
                    if (current != null) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wide = maxWidth >= 900.dp
                            if (wide) {
                                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    JdkPane(
                                        current,
                                        selected,
                                        activeHome = activeJavaHome(current),
                                        onSelect = { selected = it },
                                        modifier = Modifier.weight(2f).fillMaxHeight(),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        EnvironmentPane(current, Modifier.fillMaxWidth())
                                        OptionsPane(
                                            snapshot = current,
                                            selected = selected,
                                            selectedTargets = selectedTargets,
                                            onTargetToggle = { id, checked ->
                                                selectedTargets = if (checked) selectedTargets + id else selectedTargets - id
                                                settings.saveSelectedTargets(controller.platformKey, selectedTargets)
                                            },
                                            modifier = Modifier.weight(1.2f).fillMaxWidth(),
                                        )
                                    }
                                }
                            } else {
                                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    EnvironmentPane(current, Modifier.fillMaxWidth())
                                    JdkPane(current, selected, activeJavaHome(current), { selected = it }, Modifier.weight(1f).fillMaxWidth())
                                    OptionsPane(
                                        snapshot = current,
                                        selected = selected,
                                        selectedTargets = selectedTargets,
                                        onTargetToggle = { id, checked ->
                                            selectedTargets = if (checked) selectedTargets + id else selectedTargets - id
                                            settings.saveSelectedTargets(controller.platformKey, selectedTargets)
                                        },
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RefreshFab(
                    loading = isLoading,
                    applying = isApplying,
                    onRefresh = ::refresh,
                )
                ApplyFab(
                    enabled = selected != null && selectedTargets.isNotEmpty() && !isLoading && !isApplying,
                    applying = isApplying,
                    onApply = ::requestApply,
                )
            }
        }
    }

    val plan = confirmPlan
    if (plan != null) {
        ApplyConfirmationDialog(
            plan = plan,
            onDismiss = { if (!isApplying) confirmPlan = null },
            onConfirm = {
                scope.launch {
                    operationMutex.withLock {
                        isApplying = true
                        result = null
                        try {
                            val applyResult = controller.apply(plan)
                            result = applyResult
                            confirmPlan = null
                            if (applyResult.success) {
                                val newSnapshot = controller.refresh()
                                snapshot = newSnapshot
                                selected = newSnapshot.installations.firstOrNull {
                                    PathNormalization.samePath(it.home, plan.installation.home.toString())
                                } ?: plan.installation
                            }
                        } catch (t: Throwable) {
                            result = ApplyResult(false, t.message ?: t::class.java.simpleName)
                        } finally {
                            isApplying = false
                        }
                    }
                }
            },
            applying = isApplying,
        )
    }
}

@Composable
private fun RefreshFab(
    loading: Boolean,
    applying: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !loading && !applying
    FloatingActionButton(
        onClick = { if (enabled) onRefresh() },
        modifier = modifier.size(48.dp),
        backgroundColor = if (enabled) AppColors.AccentStrong else AppColors.Border,
        contentColor = Color.White,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text("↻", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ApplyFab(
    enabled: Boolean,
    applying: Boolean,
    onApply: () -> Unit,
) {
    FloatingActionButton(
        onClick = { if (enabled) onApply() },
        modifier = Modifier.size(48.dp),
        backgroundColor = if (enabled || applying) AppColors.AccentStrong else AppColors.Border,
        contentColor = Color.White,
    ) {
        if (applying) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EnvironmentPane(snapshot: AppSnapshot, modifier: Modifier) {
    Surface(modifier, color = AppColors.Surface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Current environment", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (snapshot.environment.hasMismatch) {
                    Spacer(Modifier.width(6.dp))
                    Text("⚠ mismatch", color = AppColors.Warning, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Divider(color = AppColors.Border)
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                snapshot.environment.aspects.forEach { aspect ->
                    EnvironmentAspectRow(aspect)
                }
            }
        }
    }
}

@Composable
private fun EnvironmentAspectRow(aspect: EnvironmentAspect) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            aspect.label,
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(130.dp),
        )
        Text(
            aspect.displayName ?: aspect.resolvedHome ?: aspect.rawValue ?: "Не найдено",
            color = when {
                aspect.resolvedHome == null && aspect.rawValue == null -> AppColors.Warning
                aspect.mismatched -> AppColors.Warning
                else -> AppColors.TextPrimary
            },
            fontSize = 12.sp,
            fontWeight = if (aspect.mismatched) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (aspect.mismatched) {
            Text("⚠", color = AppColors.Warning, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ApplyResultBanner(result: ApplyResult) {
    val color = if (result.success) AppColors.Success else AppColors.Error
    Surface(
        color = AppColors.SurfaceAlt,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(result.message, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (result.rollbackPerformed) {
                Spacer(Modifier.height(4.dp))
                Text("Rollback выполнен для частично изменённых настроек.", color = AppColors.Warning, fontSize = 11.sp)
            }
            if (result.outcomes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                result.outcomes.forEach { outcome ->
                    OperationOutcomeRow(outcome)
                }
                val verified = result.outcomes.all { it.verified }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Verification ${if (verified) "✓" else "✗"}",
                    color = if (verified) AppColors.Success else AppColors.Error,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            result.warnings.forEach { warning ->
                Spacer(Modifier.height(4.dp))
                Text(warning, color = AppColors.TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun OperationOutcomeRow(outcome: OperationOutcome) {
    val status = when {
        outcome.verified -> "✓"
        outcome.applied -> "✗"
        else -> "–"
    }
    val label = buildString {
        append(outcome.title)
        if (outcome.previousLabel != null || outcome.newLabel != null) {
            append("  ")
            append(outcome.previousLabel ?: "?")
            append(" → ")
            append(outcome.newLabel ?: "?")
        }
    }
    Text("$label  $status", color = AppColors.TextPrimary, fontSize = 11.sp)
    outcome.detail?.let {
        Text(it, color = AppColors.TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun JdkPane(
    snapshot: AppSnapshot,
    selected: JavaInstallation?,
    activeHome: String?,
    onSelect: (JavaInstallation) -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, color = AppColors.Surface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Installed JDKs", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text("${snapshot.installations.size}", color = AppColors.TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (snapshot.installations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Установленные Java не найдены.", color = AppColors.Warning)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(snapshot.installations, key = { it.home.toString() }) { installation ->
                        JavaCard(
                            installation = installation,
                            selected = selected?.home?.let { PathNormalization.samePath(installation.home, it.toString()) } == true,
                            active = PathNormalization.samePath(installation.home, activeHome),
                            onClick = { onSelect(installation) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsPane(
    snapshot: AppSnapshot,
    selected: JavaInstallation?,
    selectedTargets: Set<String>,
    onTargetToggle: (String, Boolean) -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, color = AppColors.Surface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text("Change", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                selected?.home?.toString() ?: "Сначала выберите JDK",
                color = if (selected == null) AppColors.Warning else AppColors.Accent,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Divider(color = AppColors.Border)
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                snapshot.targets.forEach { target ->
                    TargetRow(
                        target = target,
                        checked = target.id in selectedTargets,
                        onCheckedChange = { checked -> onTargetToggle(target.id, checked) },
                    )
                }
                if (snapshot.targets.isEmpty()) {
                    Text("Для этой ОС нет доступных операций.", color = AppColors.Warning)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Изменения shell/JAVA_HOME действуют для новых процессов.",
                color = AppColors.TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ApplyConfirmationDialog(plan: SwitchPlan, onDismiss: () -> Unit, onConfirm: () -> Unit, applying: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Применить Java ${plan.installation.featureVersion ?: plan.installation.version}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(plan.installation.home.toString(), color = AppColors.Accent, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                plan.operations.forEachIndexed { index, operation ->
                    Text("${index + 1}. ${operation.title}", fontWeight = FontWeight.SemiBold)
                    Text(operation.details, color = AppColors.TextSecondary, fontSize = 11.sp)
                    if (operation.requiresElevation) Text("Потребуется системное подтверждение", color = AppColors.Warning, fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !applying) {
                Text(if (applying) "Применение..." else "Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !applying) { Text("Отмена") }
        },
        backgroundColor = AppColors.SurfaceAlt,
        contentColor = AppColors.TextPrimary,
    )
}

@Composable
private fun StatusBanner(message: String, color: Color) {
    Surface(color = AppColors.SurfaceAlt, border = BorderStroke(1.dp, color.copy(alpha = 0.6f)), shape = RoundedCornerShape(12.dp)) {
        Text(message, color = color, modifier = Modifier.fillMaxWidth().padding(10.dp), fontSize = 12.sp)
    }
}
