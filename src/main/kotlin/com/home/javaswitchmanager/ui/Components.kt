package com.home.javaswitchmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.home.javaswitchmanager.domain.JavaInstallation
import com.home.javaswitchmanager.domain.SwitchTargetState

@Composable
fun JavaCard(
    installation: JavaInstallation,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        backgroundColor = if (selected) AppColors.CardSelected else AppColors.Card,
        border = BorderStroke(1.dp, if (selected) AppColors.Accent else AppColors.Border),
        shape = RoundedCornerShape(18.dp),
        elevation = if (selected) 8.dp else 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (active) AppColors.Success else AppColors.Border),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    installation.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Version ${installation.version}",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!installation.architecture.isNullOrBlank()) {
                Text(installation.architecture, color = AppColors.TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                installation.home.toString(),
                color = if (selected) Color(0xFFCAEFFF) else AppColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text("Найдено: ${installation.source.label}", color = AppColors.TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun TargetRow(
    target: SwitchTargetState,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.SurfaceAlt,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppColors.Border),
    ) {
        Row(
            modifier = Modifier
                .clickable { onCheckedChange(!checked) }
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppColors.AccentStrong,
                    uncheckedColor = AppColors.TextSecondary,
                    checkmarkColor = Color.White,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(target.title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (target.requiresElevation) {
                        Surface(color = Color(0xFF4B3B1D), shape = RoundedCornerShape(8.dp)) {
                            Text("ADMIN", color = AppColors.Warning, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(target.description, color = AppColors.TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    target.currentValue ?: "Не настроено",
                    color = if (target.currentValue == null) AppColors.Warning else AppColors.Success,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
