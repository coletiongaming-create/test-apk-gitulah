package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PortalMode(val wireValue: String) {
    STUDENT("student"),
    TEACHER("teacher");

    companion object {
        fun fromWire(raw: String?): PortalMode =
            entries.firstOrNull { it.wireValue == raw } ?: STUDENT
    }
}

enum class SessionStatus(val wireValue: String) {
    SCHEDULED("scheduled"),
    ACTIVE("active"),
    CLOSED("closed");

    companion object {
        fun fromWire(raw: String?): SessionStatus =
            entries.firstOrNull { it.wireValue == raw } ?: SCHEDULED
    }
}

enum class AttendanceStatus(val wireValue: String, val label: String) {
    PRESENT("present", "Present"),
    LATE("late", "Late"),
    ABSENT("absent", "Absent"),
    EXCUSED("excused", "Excused");

    companion object {
        fun fromWire(raw: String?): AttendanceStatus =
            entries.firstOrNull { it.wireValue == raw } ?: ABSENT
    }
}

enum class VerificationStatus(val wireValue: String, val label: String) {
    PENDING("pending", "Pending Review"),
    VERIFIED("verified", "Verified"),
    FLAGGED("flagged", "Flagged"),
    REJECTED("rejected", "Rejected");

    companion object {
        fun fromWire(raw: String?): VerificationStatus =
            entries.firstOrNull { it.wireValue == raw } ?: PENDING
    }
}

enum class TrackingMode(val wireValue: String, val label: String) {
    AUTO_GPS("auto_gps", "Auto GPS"),
    MANUAL_GPS("manual_gps", "GPS Sync"),
    TEACHER_MARKED("teacher_marked", "Teacher Log");

    companion object {
        fun fromWire(raw: String?): TrackingMode =
            entries.firstOrNull { it.wireValue == raw } ?: MANUAL_GPS
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val universityId: String = "",
    val department: String = "",
    val portalMode: String = PortalMode.STUDENT.wireValue,
    val autoReportEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedPortalMode(): PortalMode = PortalMode.fromWire(portalMode)
}

@Keep
@IgnoreExtraProperties
data class Enrollment(
    @DocumentId val id: String = "",
    val lectureId: String = "",
    val teacherId: String = "",
    val courseCode: String = "",
    val title: String = "",
    val roomName: String = "",
    val teacherName: String = "",
    val scheduleLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Lecture(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val courseCode: String = "",
    val title: String = "",
    val roomName: String = "",
    val scheduleLabel: String = "",
    val joinCode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Long = 100L,
    val sessionStatus: String = SessionStatus.SCHEDULED.wireValue,
    val activeSessionNumber: Long = 1L,
    val autoTrackEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedSessionStatus(): SessionStatus = SessionStatus.fromWire(sessionStatus)
}

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val lectureId: String = "",
    val courseCode: String = "",
    val lectureTitle: String = "",
    val roomName: String = "",
    val sessionNumber: Long = 1L,
    val teacherId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentUniversityId: String = "",
    val status: String = AttendanceStatus.PRESENT.wireValue,
    val verificationStatus: String = VerificationStatus.PENDING.wireValue,
    val trackingMode: String = TrackingMode.AUTO_GPS.wireValue,
    val reportedLatitude: Double = 0.0,
    val reportedLongitude: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val withinGeofence: Boolean = false,
    val teacherNote: String = "",
    val verifiedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedStatus(): AttendanceStatus = AttendanceStatus.fromWire(status)
    fun resolvedVerificationStatus(): VerificationStatus = VerificationStatus.fromWire(verificationStatus)
    fun resolvedTrackingMode(): TrackingMode = TrackingMode.fromWire(trackingMode)
}

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long
)

data class EnrolledLectureLive(
    val enrollment: Enrollment,
    val liveLecture: Lecture?,
    val distanceMeters: Double?,
    val withinGeofence: Boolean,
    val currentSessionRecord: AttendanceRecord?
)
