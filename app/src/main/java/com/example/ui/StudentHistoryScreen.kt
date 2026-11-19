package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceRecord
import com.example.data.AttendanceStatus
import com.example.data.VerificationStatus
import com.example.ui.theme.LocalCampusStatusColors
import com.example.viewmodel.UiState

private enum class StudentHistoryFilter(val label: String) {
    ALL("All"),
    PRESENT("Present"),
    ABSENT("Absent"),
    PENDING("Pending"),
    VERIFIED("Verified"),
    FLAGGED("Flagged")
}

@Composable
fun StudentHistoryScreen(
    attendanceHistoryState: UiState<List<AttendanceRecord>>
) {
    val statusColors = LocalCampusStatusColors.current
    var selectedFilter by remember { mutableStateOf(StudentHistoryFilter.ALL) }

    when (attendanceHistoryState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                ErrorBanner(message = attendanceHistoryState.message)
            }
        }

        is UiState.Success -> {
            val allRecords = attendanceHistoryState.data
            val filtered = remember(allRecords, selectedFilter) {
                when (selectedFilter) {
                    StudentHistoryFilter.ALL -> allRecords
                    StudentHistoryFilter.PRESENT -> allRecords.filter {
                        it.resolvedStatus() == AttendanceStatus.PRESENT ||
                            it.resolvedStatus() == AttendanceStatus.LATE
                    }
                    StudentHistoryFilter.ABSENT -> allRecords.filter {
                        it.resolvedStatus() == AttendanceStatus.ABSENT
                    }
                    StudentHistoryFilter.PENDING -> allRecords.filter {
                        it.resolvedVerificationStatus() == VerificationStatus.PENDING
                    }
                    StudentHistoryFilter.VERIFIED -> allRecords.filter {
                        it.resolvedVerificationStatus() == VerificationStatus.VERIFIED
                    }
                    StudentHistoryFilter.FLAGGED -> allRecords.filter {
                        it.resolvedVerificationStatus() == VerificationStatus.FLAGGED ||
                            it.resolvedVerificationStatus() == VerificationStatus.REJECTED
                    }
                }
            }

            val total = allRecords.size
            val present = allRecords.count {
                it.resolvedStatus() == AttendanceStatus.PRESENT ||
                    it.resolvedStatus() == AttendanceStatus.LATE ||
                    it.resolvedStatus() == AttendanceStatus.EXCUSED
            }
            val absent = allRecords.count { it.resolvedStatus() == AttendanceStatus.ABSENT }
            val verified = allRecords.count { it.resolvedVerificationStatus() == VerificationStatus.VERIFIED }
            val rate = if (total == 0) 100 else ((present * 100) / total)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "history_summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Attendance & Absence Log",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                value = "$rate%",
                                label = "Standing",
                                accentColor = statusColors.present,
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                value = "$verified",
                                label = "Verified",
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                value = "$absent",
                                label = "Absences",
                                accentColor = statusColors.absent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item(key = "history_filters") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(StudentHistoryFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "history_empty") {
                        EmptyStateCard(
                            icon = Icons.Rounded.HistoryEdu,
                            title = if (allRecords.isEmpty()) {
                                "No attendance reports yet"
                            } else {
                                "No records match \"${selectedFilter.label}\""
                            },
                            subtitle = if (allRecords.isEmpty()) {
                                "Your real-time GPS check-ins and absence reports will appear here with lecturer verification statuses."
                            } else {
                                "Try selecting a different filter chip to view other attendance records."
                            }
                        )
                    }
                } else {
                    items(
                        items = filtered,
                        key = { it.id }
                    ) { record ->
                        StudentHistoryRecordCard(record = record)
                    }
                }

                item(key = "history_spacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun StudentHistoryRecordCard(record: AttendanceRecord) {
    val statusColors = LocalCampusStatusColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_record_${record.id}"),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelemetryChip(
                        text = "${record.courseCode} • S${record.sessionNumber}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = record.resolvedTrackingMode().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatTimestampShort(record.updatedAt ?: record.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.lectureTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = record.roomName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttendanceStatusPill(status = record.resolvedStatus())
                VerificationStatusPill(status = record.resolvedVerificationStatus())
            }

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
                    text = "${formatDistanceLabel(record.distanceMeters)} from hall • ±${record.accuracyMeters.toInt()}m"
                )
            }

            if (record.teacherNote.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = record.teacherNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
