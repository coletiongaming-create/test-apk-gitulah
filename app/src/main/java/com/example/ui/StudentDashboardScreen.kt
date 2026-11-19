package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceRecord
import com.example.data.AttendanceStatus
import com.example.data.EnrolledLectureLive
import com.example.data.Lecture
import com.example.data.SessionStatus
import com.example.data.UserProfile
import com.example.data.VerificationStatus
import com.example.ui.theme.CobaltSlate
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.LocalCampusStatusColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OxfordNavy
import com.example.ui.theme.OxfordNavyDark
import com.example.viewmodel.GpsUiState
import com.example.viewmodel.UiState
import java.util.Locale

@Composable
fun StudentDashboardScreen(
    profile: UserProfile?,
    gpsState: GpsUiState,
    enrolledLecturesState: UiState<List<EnrolledLectureLive>>,
    attendanceHistoryState: UiState<List<AttendanceRecord>>,
    busyAction: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRefreshGps: () -> Unit,
    onToggleAutoReport: (Boolean) -> Unit,
    onOpenJoinLecture: () -> Unit,
    onManualCheckIn: (Lecture) -> Unit,
    onReportAbsence: (Lecture) -> Unit,
    onUnenroll: (String) -> Unit
) {
    val statusColors = LocalCampusStatusColors.current
    val history = (attendanceHistoryState as? UiState.Success)?.data.orEmpty()
    val presentCount = history.count {
        it.resolvedStatus() == AttendanceStatus.PRESENT || it.resolvedStatus() == AttendanceStatus.LATE
    }
    val absentCount = history.count { it.resolvedStatus() == AttendanceStatus.ABSENT }
    val attendanceRate = if (history.isEmpty()) 100 else ((presentCount * 100) / history.size)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live GPS Radar Command Card
        item(key = "gps_radar_card") {
            LiveGpsRadarCard(
                gpsState = gpsState,
                autoReportEnabled = profile?.autoReportEnabled ?: true,
                onRequestLocationPermission = onRequestLocationPermission,
                onRefreshGps = onRefreshGps,
                onToggleAutoReport = onToggleAutoReport
            )
        }

        // 2. Quick Attendance Telemetry Strip
        item(key = "student_metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    value = "$attendanceRate%",
                    label = "Attendance Rate",
                    accentColor = statusColors.present,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = "$presentCount",
                    label = "Present",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = "$absentCount",
                    label = "Absences",
                    accentColor = statusColors.absent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Section Header: Enrolled Lectures
        item(key = "enrolled_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enrolled Lectures",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Auto-syncs attendance when you enter the lecture hall",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onOpenJoinLecture,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("join_lecture_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Join")
                }
            }
        }

        // 4. Enrolled Lectures Content
        when (enrolledLecturesState) {
            is UiState.Loading -> {
                item(key = "enrolled_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item(key = "enrolled_error") {
                    ErrorBanner(message = enrolledLecturesState.message)
                }
            }

            is UiState.Success -> {
                val lectures = enrolledLecturesState.data
                if (lectures.isEmpty()) {
                    item(key = "enrolled_empty") {
                        EmptyStateCard(
                            icon = Icons.Rounded.School,
                            title = "No lectures enrolled yet",
                            subtitle = "Enter a Join Code from your lecturer (or create a lecture in the Teacher tab to test live GPS check-in) to start automatic attendance tracking.",
                            actionLabel = "Join with Code",
                            actionTag = "empty_join_lecture_button",
                            onAction = onOpenJoinLecture
                        )
                    }
                } else {
                    items(
                        items = lectures,
                        key = { it.enrollment.lectureId }
                    ) { item ->
                        EnrolledLectureCard(
                            item = item,
                            hasGpsFix = gpsState.latestFix != null,
                            busyAction = busyAction,
                            onCheckIn = { live -> onManualCheckIn(live) },
                            onReportAbsence = { live -> onReportAbsence(live) },
                            onUnenroll = { onUnenroll(item.enrollment.lectureId) }
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LiveGpsRadarCard(
    gpsState: GpsUiState,
    autoReportEnabled: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRefreshGps: () -> Unit,
    onToggleAutoReport: (Boolean) -> Unit
) {
    val statusColors = LocalCampusStatusColors.current
    val fix = gpsState.latestFix
    val beaconColor = when {
        !gpsState.permissionGranted -> statusColors.absent
        fix != null -> EmeraldBright
        else -> statusColors.warning
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = OxfordNavy,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(OxfordNavyDark, OxfordNavy, CobaltSlate)
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PulsingRadarBeacon(
                        active = gpsState.permissionGranted && gpsState.trackingActive,
                        accentColor = beaconColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (gpsState.permissionGranted) "Live GPS Geofence Radar" else "GPS Tracking Inactive",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = gpsState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (gpsState.permissionGranted) {
                        IconButton(
                            onClick = onRefreshGps,
                            modifier = Modifier.testTag("refresh_gps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh GPS fix",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (!gpsState.permissionGranted) {
                    Button(
                        onClick = onRequestLocationPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("grant_location_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldBright,
                            contentColor = OxfordNavyDark
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GpsFixed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Real-Time Location",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val coordText = if (fix != null) {
                            String.format(Locale.US, "%.5f, %.5f", fix.latitude, fix.longitude)
                        } else {
                            "Waiting for GPS fix…"
                        }
                        TelemetryChip(
                            text = coordText,
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        if (fix != null) {
                            TelemetryChip(
                                text = "±${fix.accuracyMeters.toInt()}m",
                                containerColor = EmeraldBright.copy(alpha = 0.22f),
                                contentColor = Color(0xFFA7F3D0)
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = Color.White.copy(alpha = 0.10f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = EmeraldBright,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Automatic Database Check-In",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Auto-report Present upon entering active lecture zone",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.72f)
                                    )
                                }
                            }
                            Switch(
                                checked = autoReportEnabled,
                                onCheckedChange = onToggleAutoReport,
                                modifier = Modifier.testTag("auto_report_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OxfordNavyDark,
                                    checkedTrackColor = EmeraldBright
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrolledLectureCard(
    item: EnrolledLectureLive,
    hasGpsFix: Boolean,
    busyAction: Boolean,
    onCheckIn: (Lecture) -> Unit,
    onReportAbsence: (Lecture) -> Unit,
    onUnenroll: () -> Unit
) {
    val statusColors = LocalCampusStatusColors.current
    val live = item.liveLecture
    val enrollment = item.enrollment
    val isLiveSession = live?.resolvedSessionStatus() == SessionStatus.ACTIVE
    val record = item.currentSessionRecord
    val isLockedByTeacher = record != null &&
        record.resolvedVerificationStatus() != VerificationStatus.PENDING

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("enrolled_lecture_card_${enrollment.lectureId}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isLiveSession && item.withinGeofence) 1.5.dp else 1.dp,
            color = if (isLiveSession && item.withinGeofence) {
                statusColors.present
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Course code + Live Roll Call pill + Unenroll
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryChip(
                        text = live?.courseCode ?: enrollment.courseCode,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isLiveSession) {
                        StatusPill(
                            label = "LIVE • S${live?.activeSessionNumber ?: 1}",
                            containerColor = statusColors.presentContainer,
                            contentColor = statusColors.onPresentContainer,
                            icon = Icons.Rounded.Sensors
                        )
                    } else {
                        StatusPill(
                            label = live?.resolvedSessionStatus()?.name ?: "OFFLINE",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onUnenroll,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove enrolled lecture",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Course title & hall metadata
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = live?.title ?: enrollment.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${live?.roomName ?: enrollment.roomName} • ${live?.teacherName ?: enrollment.teacherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = live?.scheduleLabel ?: enrollment.scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Real-Time Geofence Distance Meter
            if (live != null) {
                val radius = live.radiusMeters.coerceAtLeast(20L).toDouble()
                val dist = item.distanceMeters
                val proximityProgress = if (dist != null) {
                    (1.0 - (dist / (radius * 2.0)).coerceIn(0.0, 1.0)).toFloat()
                } else {
                    0f
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasGpsFix) Icons.Rounded.LocationOn else Icons.Rounded.GpsOff,
                                    contentDescription = null,
                                    tint = when {
                                        !hasGpsFix -> MaterialTheme.colorScheme.onSurfaceVariant
                                        item.withinGeofence -> statusColors.present
                                        else -> statusColors.absent
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (dist != null) {
                                        "${formatDistanceLabel(dist)} away (zone: ${live.radiusMeters}m)"
                                    } else {
                                        "Enable GPS to measure distance (${live.radiusMeters}m zone)"
                                    },
                                    style = MonoCodeStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (dist != null) {
                                StatusPill(
                                    label = if (item.withinGeofence) "IN ZONE" else "OUTSIDE",
                                    containerColor = if (item.withinGeofence) {
                                        statusColors.presentContainer
                                    } else {
                                        statusColors.absentContainer
                                    },
                                    contentColor = if (item.withinGeofence) {
                                        statusColors.onPresentContainer
                                    } else {
                                        statusColors.onAbsentContainer
                                    }
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { proximityProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (item.withinGeofence) statusColors.present else statusColors.absent,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }

            // Current Session Synced Record Banner
            if (record != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttendanceStatusPill(status = record.resolvedStatus())
                                VerificationStatusPill(status = record.resolvedVerificationStatus())
                            }
                            Text(
                                text = record.resolvedTrackingMode().label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (record.teacherNote.isNotBlank()) {
                            Text(
                                text = "Lecturer note: ${record.teacherNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Action Buttons (Active Session & Not Teacher-Locked)
            if (live != null && isLiveSession && !isLockedByTeacher) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onCheckIn(live) },
                        enabled = !busyAction && hasGpsFix,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("gps_checkin_button_${live.id}"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.withinGeofence) {
                                statusColors.present
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (item.withinGeofence) "Check In Now" else "Sync GPS Status",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { onReportAbsence(live) },
                        enabled = !busyAction && hasGpsFix,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("report_absence_button_${live.id}"),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, statusColors.absent.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColors.absent)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Absent")
                    }
                }
            } else if (live != null && !isLiveSession) {
                Text(
                    text = "Roll call is currently closed. Attendance will activate automatically when your lecturer opens the session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
