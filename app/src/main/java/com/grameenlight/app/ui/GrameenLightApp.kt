package com.grameenlight.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grameenlight.app.data.Pole
import com.grameenlight.app.data.PoleReportEntity
import com.grameenlight.app.data.PoleStatus
import com.grameenlight.app.data.RepairState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrameenLightApp(
    state: GrameenLightUiState,
    onPoleSelected: (String) -> Unit,
    onDismissPoleSheet: () -> Unit,
    onReportPole: (String, PoleStatus) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeaderSection(
                    isDarkAudit = state.isDarkAudit
                )
            }
            item {
                EnergySavedSection(
                    energySavedKwh = state.energySavedKwh,
                    monthlyBaselineKwh = state.monthlyBaselineKwh,
                    activeDaytimeBurningCount = state.activeDaytimeBurningCount,
                    activeDaytimeWasteKwh = state.activeDaytimeWasteKwh,
                    repairedDelayWasteKwh = state.repairedDelayWasteKwh
                )
            }
            item {
                PoleMapSection(
                    poles = state.poles,
                    isDarkAudit = state.isDarkAudit,
                    onPoleSelected = onPoleSelected
                )
            }
            item {
                StatusLegend()
            }
            item {
                RepairTrackerSection(
                    reports = state.reports
                )
            }
        }
    }

    state.selectedPole?.let { pole ->
        ModalBottomSheet(onDismissRequest = onDismissPoleSheet) {
            QuickReportSheet(
                pole = pole,
                onReportPole = onReportPole,
                onDismiss = onDismissPoleSheet
            )
        }
    }
}

@Composable
private fun HeaderSection(isDarkAudit: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Grameen-Light",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Citizen-led streetlight audit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.size(58.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isDarkAudit) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    contentDescription = if (isDarkAudit) "Night audit mode" else "Day audit mode",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EnergySavedSection(
    energySavedKwh: Double,
    monthlyBaselineKwh: Double,
    activeDaytimeBurningCount: Int,
    activeDaytimeWasteKwh: Double,
    repairedDelayWasteKwh: Double
) {
    val progress = (energySavedKwh / monthlyBaselineKwh.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
    val statusText = if (activeDaytimeBurningCount > 0) {
        "$activeDaytimeBurningCount daytime light still burning"
    } else {
        "All poles within the monthly saving target"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFE0A800)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Energy Saved This Month",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeDaytimeBurningCount > 0) {
                        Text(
                            text = "$activeDaytimeWasteKwh kWh lost so far from daytime burning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (repairedDelayWasteKwh > 0.0) {
                        Text(
                            text = "$repairedDelayWasteKwh kWh locked after lights were switched back",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = "$energySavedKwh kWh",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Color(0xFFE0A800),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun PoleMapSection(
    poles: List<Pole>,
    isDarkAudit: Boolean,
    onPoleSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pole Map",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${poles.size} poles",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawVillageMap(isDarkAudit)
                }

                Text(
                    text = "Village Zone A",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                poles.forEach { pole ->
                    PoleDot(
                        pole = pole,
                        maxWidthDp = maxWidth,
                        maxHeightDp = maxHeight,
                        onPoleSelected = onPoleSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun PoleDot(
    pole: Pole,
    maxWidthDp: androidx.compose.ui.unit.Dp,
    maxHeightDp: androidx.compose.ui.unit.Dp,
    onPoleSelected: (String) -> Unit
) {
    val density = LocalDensity.current
    val dotSize = 40.dp
    val dotSizePx = with(density) { dotSize.toPx() }
    val x = with(density) { (maxWidthDp.toPx() * pole.xPercent - dotSizePx / 2).roundToInt() }
    val y = with(density) { (maxHeightDp.toPx() * pole.yPercent - dotSizePx / 2).roundToInt() }

    Box(
        modifier = Modifier
            .offset { IntOffset(x, y) }
            .size(dotSize)
            .clip(CircleShape)
            .background(statusColor(pole.status))
            .border(2.dp, Color.White, CircleShape)
            .clickable { onPoleSelected(pole.id) }
            .semantics {
                contentDescription = "${pole.name}, ${pole.status.label}"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = pole.status.shortLabel,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

private fun DrawScope.drawVillageMap(isDarkAudit: Boolean) {
    val base = if (isDarkAudit) Color(0xFF17221D) else Color(0xFFEAF1E5)
    val road = if (isDarkAudit) Color(0xFF303B3A) else Color(0xFFE7E0D1)
    val roadLine = if (isDarkAudit) Color(0xFF59615F) else Color(0xFFFFFFFF)
    val building = if (isDarkAudit) Color(0xFF26352E) else Color(0xFFF7E7C5)
    val buildingAlt = if (isDarkAudit) Color(0xFF20323B) else Color(0xFFDDE9D7)
    val field = if (isDarkAudit) Color(0xFF183C25) else Color(0xFFCDE6BE)
    val water = if (isDarkAudit) Color(0xFF143642) else Color(0xFFC9E8F2)

    drawRect(base)

    drawRoundRect(
        color = field,
        topLeft = Offset(size.width * 0.05f, size.height * 0.58f),
        size = Size(size.width * 0.22f, size.height * 0.22f),
        cornerRadius = CornerRadius(18f, 18f)
    )
    drawRoundRect(
        color = water,
        topLeft = Offset(size.width * 0.72f, size.height * 0.78f),
        size = Size(size.width * 0.20f, size.height * 0.10f),
        cornerRadius = CornerRadius(24f, 24f)
    )

    listOf(
        Offset(0.05f, 0.27f) to Offset(0.95f, 0.27f),
        Offset(0.08f, 0.53f) to Offset(0.92f, 0.53f),
        Offset(0.12f, 0.75f) to Offset(0.90f, 0.75f),
        Offset(0.30f, 0.08f) to Offset(0.24f, 0.92f),
        Offset(0.58f, 0.08f) to Offset(0.63f, 0.92f),
        Offset(0.82f, 0.16f) to Offset(0.72f, 0.92f)
    ).forEach { (start, end) ->
        drawLine(
            color = road,
            start = Offset(size.width * start.x, size.height * start.y),
            end = Offset(size.width * end.x, size.height * end.y),
            strokeWidth = size.width * 0.075f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = roadLine.copy(alpha = 0.55f),
            start = Offset(size.width * start.x, size.height * start.y),
            end = Offset(size.width * end.x, size.height * end.y),
            strokeWidth = size.width * 0.008f,
            cap = StrokeCap.Round
        )
    }

    val buildings = listOf(
        RectSpec(0.07f, 0.08f, 0.12f, 0.09f, building),
        RectSpec(0.43f, 0.08f, 0.14f, 0.10f, buildingAlt),
        RectSpec(0.70f, 0.08f, 0.16f, 0.12f, building),
        RectSpec(0.08f, 0.35f, 0.14f, 0.12f, buildingAlt),
        RectSpec(0.39f, 0.35f, 0.13f, 0.10f, building),
        RectSpec(0.66f, 0.36f, 0.13f, 0.11f, buildingAlt),
        RectSpec(0.34f, 0.60f, 0.16f, 0.10f, building),
        RectSpec(0.78f, 0.58f, 0.12f, 0.12f, building),
        RectSpec(0.35f, 0.82f, 0.14f, 0.10f, buildingAlt)
    )

    buildings.forEach { rect ->
        drawRoundRect(
            color = rect.color,
            topLeft = Offset(size.width * rect.x, size.height * rect.y),
            size = Size(size.width * rect.width, size.height * rect.height),
            cornerRadius = CornerRadius(12f, 12f)
        )
    }
}

private data class RectSpec(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val color: Color
)

@Composable
private fun StatusLegend() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(PoleStatus.WORKING, Modifier.weight(1f))
            LegendItem(PoleStatus.FUSED, Modifier.weight(1f))
            LegendItem(PoleStatus.BURNING_IN_DAY, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LegendItem(
    status: PoleStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor(status))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RepairTrackerSection(reports: List<PoleReportEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Repair Tracker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${reports.size} reports",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (reports.isEmpty()) {
            EmptyTracker()
        } else {
            reports.take(10).forEach { report ->
                ReportRow(report = report)
            }
        }
    }
}

@Composable
private fun EmptyTracker() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Tap any pole on the map to create the first complaint.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportRow(report: PoleReportEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.complaintId,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${report.poleName} - ${report.street}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatReportTime(report.reportedAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SyncChip(synced = report.synced)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(status = report.statusEnum)
                RepairChip(repairState = report.repairStateEnum)
            }
        }
    }
}

@Composable
private fun StatusChip(status: PoleStatus) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = statusColor(status).copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    PoleStatus.WORKING -> Icons.Rounded.CheckCircle
                    PoleStatus.FUSED -> Icons.Rounded.ReportProblem
                    PoleStatus.BURNING_IN_DAY -> Icons.Rounded.Bolt
                },
                contentDescription = null,
                tint = statusColor(status),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(status)
            )
        }
    }
}

@Composable
private fun RepairChip(repairState: RepairState) {
    val color = when (repairState) {
        RepairState.REPORTED -> Color(0xFF8A5A00)
        RepairState.ASSIGNED -> Color(0xFF1565C0)
        RepairState.FIXED -> Color(0xFF2E7D32)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = repairState.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun SyncChip(synced: Boolean) {
    val color = if (synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (synced) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (synced) "Synced" else "Local",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun QuickReportSheet(
    pole: Pole,
    onReportPole: (String, PoleStatus) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pole.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pole.street,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }

        PoleStatus.entries.forEach { status ->
            Button(
                onClick = { onReportPole(pole.id, status) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = statusColor(status),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = when (status) {
                        PoleStatus.WORKING -> Icons.Rounded.CheckCircle
                        PoleStatus.FUSED -> Icons.Rounded.ReportProblem
                        PoleStatus.BURNING_IN_DAY -> Icons.Rounded.Bolt
                    },
                    contentDescription = null
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun statusColor(status: PoleStatus): Color = when (status) {
    PoleStatus.WORKING -> Color(0xFF2E7D32)
    PoleStatus.FUSED -> Color(0xFFC62828)
    PoleStatus.BURNING_IN_DAY -> Color(0xFFE0A800)
}

private fun formatReportTime(millis: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))
