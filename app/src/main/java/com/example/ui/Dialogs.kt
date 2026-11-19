package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceRecord
import com.example.data.AttendanceStatus
import com.example.data.Lecture
import com.example.data.LocationFix
import com.example.data.PortalMode
import com.example.data.UserProfile
import com.example.data.VerificationStatus
import com.example.ui.theme.MonoCodeStyle
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun JoinLectureDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
        title = { Text("Join a University Lecture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the lecture Join Code shared by your professor (e.g. CS301-4821) to enable live GPS geofence tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase().take(24)
                        localError = null
                    },
                    label = { Text("Lecture Join Code") },
                    placeholder = { Text("CS301-4821") },
                    singleLine = true,
                    isError = localError != null,
                    supportingText = localError?.let { msg -> { Text(msg) } },
                    textStyle = MonoCodeStyle,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = code.trim()
                    if (cleaned.length < 4) {
                        localError = "Code must be at least 4 characters"
                    } else {
                        onJoin(cleaned)
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_join_lecture_button")
            ) {
                Text(if (busy) "Enrolling…" else "Enroll")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateLectureDialog(
    latestFix: LocationFix?,
    busy: Boolean,
    onRequestFreshFix: ((LocationFix?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (
        courseCode: String,
        title: String,
        roomName: String,
        scheduleLabel: String,
        lat: Double,
        lng: Double,
        radiusMeters: Long,
        startImmediately: Boolean
    ) -> Unit
) {
    var courseCode by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var scheduleLabel by remember { mutableStateOf("Mon/Wed 10:00 AM") }
    var latText by remember {
        mutableStateOf(
            latestFix?.let { String.format(Locale.US, "%.6f", it.latitude) } ?: ""
        )
    }
    var lngText by remember {
        mutableStateOf(
            latestFix?.let { String.format(Locale.US, "%.6f", it.longitude) } ?: ""
        )
    }
    var radiusSlider by remember { mutableFloatStateOf(100f) }
    var startImmediately by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Lecture & Geofence") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it.uppercase().take(20); validationError = null },
                    label = { Text("Course Code") },
                    placeholder = { Text("CS-301") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_course_code_input")
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100); validationError = null },
                    label = { Text("Lecture Title") },
                    placeholder = { Text("Distributed Systems") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_title_input")
                )
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it.take(80); validationError = null },
                    label = { Text("Lecture Hall / Room") },
                    placeholder = { Text("Science Hall 204") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_input")
                )
                OutlinedTextField(
                    value = scheduleLabel,
                    onValueChange = { scheduleLabel = it.take(60) },
                    label = { Text("Schedule") },
                    placeholder = { Text("Tue/Thu 09:00 AM") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            Text(
                                text = "Lecture Hall GPS Pin",
                                style = MaterialTheme.typography.titleSmall
                            )
                            OutlinedButton(
                                onClick = {
                                    onRequestFreshFix { fix ->
                                        if (fix != null) {
                                            latText = String.format(Locale.US, "%.6f", fix.latitude)
                                            lngText = String.format(Locale.US, "%.6f", fix.longitude)
                                            validationError = null
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("pin_current_gps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Use My GPS")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latText,
                                onValueChange = { latText = it; validationError = null },
                                label = { Text("Latitude") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MonoCodeStyle,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lngText,
                                onValueChange = { lngText = it; validationError = null },
                                label = { Text("Longitude") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MonoCodeStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Geofence Radius",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TelemetryChip(text = "${radiusSlider.roundToLong()}m")
                        }
                        Slider(
                            value = radiusSlider,
                            onValueChange = { radiusSlider = it },
                            valueRange = 30f..500f,
                            steps = 46
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Open Roll Call Immediately",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Students inside the geofence can auto-report right away",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = startImmediately,
                        onCheckedChange = { startImmediately = it }
                    )
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.trim().toDoubleOrNull()
                    val lng = lngText.trim().toDoubleOrNull()
                    when {
                        courseCode.trim().length < 2 -> validationError = "Enter a course code (min 2 chars)"
                        title.trim().length < 2 -> validationError = "Enter a lecture title (min 2 chars)"
                        roomName.trim().length < 2 -> validationError = "Enter a lecture hall/room name"
                        lat == null || lat !in -90.0..90.0 -> validationError = "Tap 'Use My GPS' or enter valid latitude"
                        lng == null || lng !in -180.0..180.0 -> validationError = "Tap 'Use My GPS' or enter valid longitude"
                        else -> onCreate(
                            courseCode.trim(),
                            title.trim(),
                            roomName.trim(),
                            scheduleLabel.trim().ifBlank { "Scheduled Session" },
                            lat,
                            lng,
                            radiusSlider.roundToLong(),
                            startImmediately
                        )
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_create_lecture_button")
            ) {
                Text(if (busy) "Creating…" else "Create Lecture")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerifyRecordDialog(
    record: AttendanceRecord,
    onDismiss: () -> Unit,
    onConfirm: (AttendanceStatus, VerificationStatus, String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(record.resolvedStatus()) }
    var selectedVerification by remember {
        mutableStateOf(
            if (record.resolvedVerificationStatus() == VerificationStatus.PENDING) {
                VerificationStatus.VERIFIED
            } else {
                record.resolvedVerificationStatus()
            }
        )
    }
    var note by remember { mutableStateOf(record.teacherNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify ${record.studentName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${record.courseCode} • Session ${record.sessionNumber} • ${formatDistanceLabel(record.distanceMeters)} from room (±${record.accuracyMeters.toInt()}m GPS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Attendance Outcome", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AttendanceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                Text("Verification Decision", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        VerificationStatus.VERIFIED,
                        VerificationStatus.FLAGGED,
                        VerificationStatus.REJECTED
                    ).forEach { vs ->
                        FilterChip(
                            selected = selectedVerification == vs,
                            onClick = { selectedVerification = vs },
                            label = { Text(vs.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Lecturer Note (Optional)") },
                    placeholder = { Text("Confirmed in seat / Medical excuse approved") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teacher_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedStatus, selectedVerification, note) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_verification_button")
            ) {
                Text("Save Verification")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualAbsenceDialog(
    lecture: Lecture,
    busy: Boolean,
    onDismiss: () -> Unit,
    onLog: (studentName: String, studentId: String, status: AttendanceStatus, note: String) -> Unit
) {
    var studentName by remember { mutableStateOf("") }
    var universityId by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(AttendanceStatus.ABSENT) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Roster Entry — ${lecture.courseCode}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Manually record an absence, excused leave, or check-in for Session ${lecture.activeSessionNumber}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = studentName,
                    onValueChange = { studentName = it.take(80); error = null },
                    label = { Text("Student Full Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = universityId,
                    onValueChange = { universityId = it.take(40) },
                    label = { Text("Student ID (Optional)") },
                    placeholder = { Text("UNI-2026-104") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AttendanceStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("Absent during roll call / Sick note") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (studentName.trim().isEmpty()) {
                        error = "Enter the student's name"
                    } else {
                        onLog(studentName.trim(), universityId.trim(), status, note.trim())
                    }
                },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (busy) "Saving…" else "Log Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProfileSettingsDialog(
    profile: UserProfile?,
    fallbackName: String,
    userEmail: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        displayName: String,
        universityId: String,
        department: String,
        portalMode: PortalMode,
        autoReportEnabled: Boolean
    ) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile) {
        mutableStateOf(profile?.displayName?.ifBlank { fallbackName } ?: fallbackName)
    }
    var universityId by remember(profile) { mutableStateOf(profile?.universityId.orEmpty()) }
    var department by remember(profile) { mutableStateOf(profile?.department.orEmpty()) }
    var autoReport by remember(profile) { mutableStateOf(profile?.autoReportEnabled ?: true) }
    val portalMode = profile?.resolvedPortalMode() ?: PortalMode.STUDENT
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("University Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!userEmail.isNullOrBlank()) {
                    Text(
                        text = userEmail,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80); error = null },
                    label = { Text("Full Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = universityId,
                    onValueChange = { universityId = it.take(40) },
                    label = { Text("Student / Faculty ID") },
                    placeholder = { Text("UNI-2026-0412") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it.take(80) },
                    label = { Text("Department") },
                    placeholder = { Text("Computer Science") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-Report Presence via GPS",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = autoReport, onCheckedChange = { autoReport = it })
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sign_out_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.trim().isEmpty()) {
                        error = "Display name cannot be empty"
                    } else {
                        onSave(displayName.trim(), universityId.trim(), department.trim(), portalMode, autoReport)
                    }
                },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close")
            }
        }
    )
}
