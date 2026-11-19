package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceRecord
import com.example.data.AttendanceStatus
import com.example.data.Lecture
import com.example.data.SessionStatus
import com.example.data.VerificationStatus
import com.example.ui.theme.LocalCampusStatusColors
import com.example.viewmodel.UiState
import java.util.Locale

private enum class QueueFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    VERIFIED("Verified"),
    FLAGGED("Flagged"),
    ABSENT("Absent")
}

@Composable
fun TeacherPortalScreen(
    teacherLecturesState: UiState<List<Lecture>>,
    verificationQueueState: UiState<List<AttendanceRecord>>,
    onOpenCreateLecture: () -> Unit,
    onToggleSession: (Lecture, Boolean) -> Unit,
    onRepinGps: (Lecture) -> Unit,
    onOpenManualAbsence: (Lecture) -> Unit,
    onDeleteLecture: (String) -> Unit,
    onQuickVerify: (AttendanceRecord) -> Unit,
    onQuickFlag: (AttendanceRecord) -> Unit,
    onQuickMarkAbsent: (AttendanceRecord) -> Unit,
    onOpenReviewDialog: (AttendanceRecord) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val statusColors = LocalCampusStatusColors.current

    var selectedQueueFilter by remember { mutableStateOf(QueueFilter.ALL) }
    var selectedLectureIdFilter by remember { mutableStateOf<String?>(null) }

    val lectures = (teacherLecturesState as? UiState.Success)?.data.orEmpty()
    val allRecords = (verificationQueueState as? UiState.Success)?.data.orEmpty()

    val pendingCount = allRecords.count { it.resolvedVerificationStatus() == VerificationStatus.PENDING }
    val verifiedPresentCount = allRecords.count {
        it.resolvedVerificationStatus() == VerificationStatus.VERIFIED &&
            (it.resolvedStatus() == AttendanceStatus.PRESENT || it.resolvedStatus() == AttendanceStatus.LATE)
    }
    val flaggedOrAbsentCount = allRecords.count {
        it.resolvedVerificationStatus() == VerificationStatus.FLAGGED ||
            it.resolvedStatus() == AttendanceStatus.ABSENT
    }
    val activeSessionsCount = lectures.count { it.resolvedSessionStatus() == SessionStatus.ACTIVE }

    val filteredRecords = remember(allRecords, selectedQueueFilter, selectedLectureIdFilter) {
        allRecords
            .filter { selectedLectureIdFilter == null || it.lectureId == selectedLectureIdFilter }
            .filter { record ->
                when (selectedQueueFilter) {
                    QueueFilter.ALL -> true
                    QueueFilter.PENDING -> record.resolvedVerificationStatus() == VerificationStatus.PENDING
                    QueueFilter.VERIFIED -> record.resolvedVerificationStatus() == VerificationStatus.VERIFIED
                    QueueFilter.FLAGGED -> record.resolvedVerificationStatus() == VerificationStatus.FLAGGED ||
                        record.resolvedVerificationStatus() == VerificationStatus.REJECTED
                    QueueFilter.ABSENT -> record.resolvedStatus() == AttendanceStatus.ABSENT
                }
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Portal Header & Create CTA
        item(key = "teacher_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verification Portal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Manage lecture geofences and verify student GPS check-ins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onOpenCreateLecture,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("create_lecture_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Lecture")
                }
            }
        }

        // 2. Real-Time Summary Metrics
        item(key = "teacher_metrics") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    value = "$activeSessionsCount",
                    label = "Live Halls",
                    accentColor = statusColors.present,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = "$pendingCount",
                    label = "Pending",
                    accentColor = statusColors.warning,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = "$verifiedPresentCount",
                    label = "Verified",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    value = "$flaggedOrAbsentCount",
                    label = "Flag/Abs",
                    accentColor = statusColors.absent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. My Lectures & Geofence Controls
        item(key = "teacher_lectures_title") {
            Text(
                text = "My Lectures & Live Roll Call",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (teacherLecturesState) {
            is UiState.Loading -> {
                item(key = "teacher_lectures_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item(key = "teacher_lectures_error") {
                    ErrorBanner(message = teacherLecturesState.message)
                }
            }

            is UiState.Success -> {
                if (lectures.isEmpty()) {
                    item(key = "teacher_lectures_empty") {
                        EmptyStateCard(
                            icon = Icons.Rounded.School,
                            title = "Create your first lecture hall",
                            subtitle = "Pin your lecture room's GPS coordinates, set the allowed geofence radius, and share the generated Join Code with students.",
                            actionLabel = "Create Lecture",
                            actionTag = "empty_create_lecture_button",
                            onAction = onOpenCreateLecture
                        )
                    }
                } else {
                    items(
                        items = lectures,
                        key = { "lec_${it.id}" }
                    ) { lecture ->
                        TeacherLectureControlCard(
                            lecture = lecture,
                            onCopyJoinCode = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Lecture Join Code", lecture.joinCode)
                                )
                                onShowMessage("Copied Join Code: ${lecture.joinCode}")
                            },
                            onToggleSession = { open -> onToggleSession(lecture, open) },
                            onRepinGps = { onRepinGps(lecture) },
                            onLogManualStudent = { onOpenManualAbsence(lecture) },
                            onDeleteLecture = { onDeleteLecture(lecture.id) }
                        )
                    }
                }
            }
        }

        // 4. Student Verification Queue Header + Filters
        item(key = "verification_queue_header") {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Real-Time Verification Queue",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QueueFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedQueueFilter == filter,
                            onClick = { selectedQueueFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("queue_filter_${filter.name.lowercase()}")
                        )
                    }
                }

                if (lectures.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "course_filter_all") {
                            FilterChip(
                                selected = selectedLectureIdFilter == null,
                                onClick = { selectedLectureIdFilter = null },
                                label = { Text("All Courses") }
                            )
                        }
                        items(lectures, key = { "cf_${it.id}" }) { lec ->
                            FilterChip(
                                selected = selectedLectureIdFilter == lec.id,
                                onClick = { selectedLectureIdFilter = lec.id },
                                label = { Text(lec.courseCode) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Verification Queue List
        when (verificationQueueState) {
            is UiState.Loading -> {
                item(key = "queue_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                item(key = "queue_error") {
                    ErrorBanner(message = verificationQueueState.message)
                }
            }

            is UiState.Success -> {
                if (filteredRecords.isEmpty()) {
                    item(key = "queue_empty") {
                        EmptyStateCard(
                            icon = Icons.Rounded.VerifiedUser,
                            title = if (allRecords.isEmpty()) {
                                "Waiting for student GPS reports"
                            } else {
                                "No records in \"${selectedQueueFilter.label}\""
                            },
                            subtitle = if (allRecords.isEmpty()) {
                                "Open a live roll-call session and share the lecture Join Code. Incoming GPS check-ins and absence reports sync here automatically."
                            } else {
                                "Switch filter chips above to inspect other attendance records."
                            }
                        )
                    }
                } else {
                    items(
                        items = filteredRecords,
                        key = { "rec_${it.id}" }
                    ) { record ->
                        TeacherVerificationCard(
                            record = record,
                            onVerify = { onQuickVerify(record) },
                            onFlag = { onQuickFlag(record) },
                            onMarkAbsent = { onQuickMarkAbsent(record) },
                            onOpenReview = { onOpenReviewDialog(record) },
                            onDelete = { onDeleteRecord(record.id) }
                        )
                    }
                }
            }
        }

        item(key = "teacher_bottom_spacer") {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun TeacherLectureControlCard(
    lecture: Lecture,
    onCopyJoinCode: () -> Unit,
    onToggleSession: (Boolean) -> Unit,
    onRepinGps: () -> Unit,
    onLogManualStudent: () -> Unit,
    onDeleteLecture: () -> Unit
) {
    val statusColors = LocalCampusStatusColors.current
    val isLive = lecture.resolvedSessionStatus() == SessionStatus.ACTIVE

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teacher_lecture_card_${lecture.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isLive) 1.5.dp else 1.dp,
            color = if (isLive) statusColors.present else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelemetryChip(
                        text = lecture.courseCode,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    StatusPill(
                        label = if (isLive) "LIVE • SESSION ${lecture.activeSessionNumber}" else "CLOSED • S${lecture.activeSessionNumber}",
                        containerColor = if (isLive) statusColors.presentContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isLive) statusColors.onPresentContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = if (isLive) Icons.Rounded.Sensors else null
                    )
                }
                IconButton(
                    onClick = onDeleteLecture,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete lecture",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = lecture.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${lecture.roomName} • ${lecture.scheduleLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Shareable Join Code Row
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STUDENT JOIN CODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TelemetryChip(
                            text = lecture.joinCode,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalButton(
                        onClick = onCopyJoinCode,
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("copy_join_code_${lecture.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Code")
                    }
                }
            }

            // Geofence Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                TelemetryChip(
                    text = String.format(
                        Locale.US,
                        "%.4f, %.4f • %dm radius",
                        lecture.latitude,
                        lecture.longitude,
                        lecture.radiusMeters
                    )
                )
            }

            // Action Controls: Open/Close Roll Call + Re-Pin GPS + Manual Log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onToggleSession(!isLive) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("toggle_session_${lecture.id}"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLive) statusColors.absent else statusColors.present
                    )
                ) {
                    Icon(
                        imageVector = if (isLive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLive) "Close Roll Call" else "Start Roll Call",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRepinGps,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GpsFixed,
                        contentDescription = "Re-pin lecture hall to my GPS",
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = onLogManualStudent,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Log student attendance or absence manually",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherVerificationCard(
    record: AttendanceRecord,
    onVerify: () -> Unit,
    onFlag: () -> Unit,
    onMarkAbsent: () -> Unit,
    onOpenReview: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColors = LocalCampusStatusColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("verification_card_${record.id}"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = record.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val idSuffix = if (record.studentUniversityId.isNotBlank()) {
                        "${record.studentUniversityId} • "
                    } else {
                        ""
                    }
                    Text(
                        text = "$idSuffix${record.courseCode} S${record.sessionNumber} • ${formatTimestampShort(record.updatedAt ?: record.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete attendance record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status & Verification Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttendanceStatusPill(status = record.resolvedStatus())
                VerificationStatusPill(status = record.resolvedVerificationStatus())
            }

            // GPS Proof Telemetry Bar
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = if (record.withinGeofence) statusColors.present else statusColors.absent,
                            modifier = Modifier.size(16.dp)
                        )
                        TelemetryChip(
                            text = "${formatDistanceLabel(record.distanceMeters)} • ±${record.accuracyMeters.toInt()}m"
                        )
                    }
                    StatusPill(
                        label = if (record.withinGeofence) "IN GEOFENCE" else "OUT OF ZONE",
                        containerColor = if (record.withinGeofence) {
                            statusColors.presentContainer
                        } else {
                            statusColors.absentContainer
                        },
                        contentColor = if (record.withinGeofence) {
                            statusColors.onPresentContainer
                        } else {
                            statusColors.onAbsentContainer
                        }
                    )
                }
            }

            if (record.teacherNote.isNotBlank()) {
                Text(
                    text = "Note: ${record.teacherNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // One-Tap Verification Action Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onVerify,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("verify_record_${record.id}"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = statusColors.presentContainer,
                        contentColor = statusColors.onPresentContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verify", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onFlag,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("flag_record_${record.id}"),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColors.warning)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = "Flag record",
                        modifier = Modifier.size(16.dp)
                    )
                }

                OutlinedButton(
                    onClick = onMarkAbsent,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("mark_absent_${record.id}"),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, statusColors.absent.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColors.absent)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EventBusy,
                        contentDescription = "Mark student absent",
                        modifier = Modifier.size(16.dp)
                    )
                }

                OutlinedButton(
                    onClick = onOpenReview,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("edit_note_${record.id}"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = "Edit verification note and status",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
