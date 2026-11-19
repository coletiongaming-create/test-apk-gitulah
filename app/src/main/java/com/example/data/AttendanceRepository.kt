package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong
import kotlin.random.Random

enum class AttendanceSyncOutcome {
    CREATED,
    UPDATED,
    LOCKED_BY_TEACHER
}

class AttendanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun sanitizeIdSegment(raw: String): String {
        val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")
        return cleaned.take(16)
    }

    private fun generateJoinCode(courseCode: String): String {
        val prefix = sanitizeIdSegment(courseCode).ifBlank { "LEC" }.take(8)
        val suffix = (1..4).map {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            chars[Random.nextInt(chars.length)]
        }.joinToString("")
        return "$prefix-$suffix"
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        defaultPortalMode: PortalMode = PortalMode.STUDENT
    ): Result<Unit> {
        val uidOrPlaceholder = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uidOrPlaceholder"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "University Member" }.take(80)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "portalMode" to defaultPortalMode.wireValue,
                    "autoReportEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        universityId: String,
        department: String,
        portalMode: PortalMode,
        autoReportEnabled: Boolean
    ): Result<Unit> {
        val uidOrPlaceholder = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uidOrPlaceholder"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
            val cleanUniId = universityId.trim().take(40)
            val cleanDept = department.trim().take(80)

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "portalMode" to portalMode.wireValue,
                    "autoReportEnabled" to autoReportEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanUniId.isNotEmpty()) createPayload["universityId"] = cleanUniId
                if (cleanDept.isNotEmpty()) createPayload["department"] = cleanDept
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "universityId" to cleanUniId,
                    "department" to cleanDept,
                    "portalMode" to portalMode.wireValue,
                    "autoReportEnabled" to autoReportEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setAutoReportEnabled(enabled: Boolean): Result<Unit> {
        val uidOrPlaceholder = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uidOrPlaceholder"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docRef.update(
                mapOf(
                    "autoReportEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setPortalMode(mode: PortalMode): Result<Unit> {
        val uidOrPlaceholder = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uidOrPlaceholder"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docRef.update(
                mapOf(
                    "portalMode" to mode.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- LECTURES (TEACHER MANAGEMENT & STUDENT LIVE LOOKUP) ---

    fun observeTeacherLectures(teacherId: String): Flow<List<Lecture>> = flow {
        val path = "lectures"
        emitAll(
            db.collection("lectures")
                .whereEqualTo("teacherId", teacherId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Lecture::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, teacherId)
                    throw error
                }
        )
    }

    suspend fun createLecture(
        teacherName: String,
        courseCode: String,
        title: String,
        roomName: String,
        scheduleLabel: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Long,
        startImmediately: Boolean
    ): Result<Lecture> {
        var docPath = "lectures"
        return try {
            val uid = requireUserId()
            val cleanCode = courseCode.trim().uppercase().take(20)
            val cleanTitle = title.trim().take(100)
            val cleanRoom = roomName.trim().take(80)
            val cleanSchedule = scheduleLabel.trim().ifBlank { "Scheduled Session" }.take(60)
            val cleanTeacher = teacherName.trim().ifBlank { "Lecturer" }.take(80)
            val safeRadius = radiusMeters.coerceIn(20L, 2000L)

            require(cleanCode.length >= 2) { "Course code must be at least 2 characters." }
            require(cleanTitle.length >= 2) { "Lecture title must be at least 2 characters." }
            require(cleanRoom.length >= 2) { "Room name must be at least 2 characters." }
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                "Invalid GPS coordinates."
            }

            val joinCode = generateJoinCode(cleanCode)
            val docRef = db.collection("lectures").document(joinCode)
            docPath = docRef.path

            val initialStatus = if (startImmediately) {
                SessionStatus.ACTIVE.wireValue
            } else {
                SessionStatus.SCHEDULED.wireValue
            }

            val payload = mapOf(
                "teacherId" to uid,
                "teacherName" to cleanTeacher,
                "courseCode" to cleanCode,
                "title" to cleanTitle,
                "roomName" to cleanRoom,
                "scheduleLabel" to cleanSchedule,
                "joinCode" to joinCode,
                "latitude" to latitude,
                "longitude" to longitude,
                "radiusMeters" to safeRadius,
                "sessionStatus" to initialStatus,
                "activeSessionNumber" to 1L,
                "autoTrackEnabled" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val created = Lecture(
                id = joinCode,
                teacherId = uid,
                teacherName = cleanTeacher,
                courseCode = cleanCode,
                title = cleanTitle,
                roomName = cleanRoom,
                scheduleLabel = cleanSchedule,
                joinCode = joinCode,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = safeRadius,
                sessionStatus = initialStatus,
                activeSessionNumber = 1L,
                autoTrackEnabled = true
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleLectureSession(lecture: Lecture, openNewSession: Boolean): Result<Unit> {
        val docPath = "lectures/${lecture.id}"
        return try {
            val uid = requireUserId()
            require(lecture.id.isNotBlank()) { "Lecture ID is required." }
            check(lecture.teacherId == uid) { "Only the lecture owner can control sessions." }

            val nextStatus = if (openNewSession) {
                SessionStatus.ACTIVE.wireValue
            } else {
                SessionStatus.CLOSED.wireValue
            }
            val nextSessionNumber = if (openNewSession && lecture.resolvedSessionStatus() == SessionStatus.CLOSED) {
                (lecture.activeSessionNumber + 1L).coerceAtMost(10_000L)
            } else {
                lecture.activeSessionNumber.coerceAtLeast(1L)
            }

            db.collection("lectures").document(lecture.id).update(
                mapOf(
                    "sessionStatus" to nextStatus,
                    "activeSessionNumber" to nextSessionNumber,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLectureGeofence(
        lectureId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Long
    ): Result<Unit> {
        val docPath = "lectures/$lectureId"
        return try {
            requireUserId()
            require(lectureId.isNotBlank()) { "Lecture ID is required." }
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Invalid GPS coordinates." }
            val safeRadius = radiusMeters.coerceIn(20L, 2000L)
            db.collection("lectures").document(lectureId).update(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "radiusMeters" to safeRadius,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteLecture(lectureId: String): Result<Unit> {
        val docPath = "lectures/$lectureId"
        return try {
            requireUserId()
            require(lectureId.isNotBlank()) { "Lecture ID is required." }
            db.collection("lectures").document(lectureId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- STUDENT ENROLLMENTS ---

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStudentEnrolledLectures(userId: String): Flow<List<Pair<Enrollment, Lecture?>>> = flow {
        val enrollmentsPath = "users/$userId/enrollments"
        val enrollmentsFlow: Flow<List<Enrollment>> = db.collection("users")
            .document(userId)
            .collection("enrollments")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Enrollment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt }
            }
            .catch { error ->
                logStreamErrorIfAuthenticated(error, OperationType.LIST, enrollmentsPath, userId)
                throw error
            }

        emitAll(
            enrollmentsFlow.flatMapLatest { enrollments ->
                if (enrollments.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val liveFlows = enrollments.map { enrollment ->
                        val lecPath = "lectures/${enrollment.lectureId}"
                        db.collection("lectures")
                            .document(enrollment.lectureId)
                            .snapshots()
                            .map { snap ->
                                val live = if (snap.exists()) {
                                    snap.toObject(
                                        Lecture::class.java,
                                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                    )
                                } else {
                                    null
                                }
                                enrollment to live
                            }
                            .catch { error ->
                                logStreamErrorIfAuthenticated(error, OperationType.GET, lecPath, userId)
                                emit(enrollment to null)
                            }
                    }
                    combine(liveFlows) { pairs -> pairs.toList() }
                }
            }
        )
    }

    suspend fun enrollByJoinCode(rawCode: String): Result<Lecture> {
        val normalizedCode = rawCode.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")
        var currentPath = "lectures/$normalizedCode"
        return try {
            val uid = requireUserId()
            require(normalizedCode.length in 4..24) {
                "Enter a valid lecture join code (e.g. CS301-4821)."
            }

            val lectureSnap = db.collection("lectures").document(normalizedCode).get().await()
            if (!lectureSnap.exists()) {
                return Result.failure(IllegalArgumentException("No lecture found for code \"$normalizedCode\"."))
            }
            val lecture = lectureSnap.toObject(
                Lecture::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Unable to read lecture details."))

            currentPath = "users/$uid/enrollments/$normalizedCode"
            val enrollRef = db.collection("users")
                .document(uid)
                .collection("enrollments")
                .document(normalizedCode)

            val existingEnroll = enrollRef.get().await()
            if (existingEnroll.exists()) {
                enrollRef.update(
                    mapOf(
                        "courseCode" to lecture.courseCode,
                        "title" to lecture.title,
                        "roomName" to lecture.roomName,
                        "teacherName" to lecture.teacherName,
                        "scheduleLabel" to lecture.scheduleLabel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "lectureId" to lecture.id,
                    "teacherId" to lecture.teacherId,
                    "courseCode" to lecture.courseCode,
                    "title" to lecture.title,
                    "roomName" to lecture.roomName,
                    "teacherName" to lecture.teacherName,
                    "scheduleLabel" to lecture.scheduleLabel,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                enrollRef.set(payload).await()
            }
            Result.success(lecture)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun unenrollLecture(lectureId: String): Result<Unit> {
        val uidOrPlaceholder = auth.currentUser?.uid ?: "unauthenticated"
        val docPath = "users/$uidOrPlaceholder/enrollments/$lectureId"
        return try {
            val uid = requireUserId()
            require(lectureId.isNotBlank()) { "Lecture ID is required." }
            db.collection("users")
                .document(uid)
                .collection("enrollments")
                .document(lectureId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- ATTENDANCE & ABSENCE RECORDS ---

    fun observeStudentAttendance(studentId: String): Flow<List<AttendanceRecord>> = flow {
        val path = "attendance_records"
        emitAll(
            db.collection("attendance_records")
                .whereEqualTo("studentId", studentId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AttendanceRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, studentId)
                    throw error
                }
        )
    }

    fun observeTeacherAttendance(teacherId: String): Flow<List<AttendanceRecord>> = flow {
        val path = "attendance_records"
        emitAll(
            db.collection("attendance_records")
                .whereEqualTo("teacherId", teacherId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AttendanceRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, teacherId)
                    throw error
                }
        )
    }

    suspend fun submitStudentGpsReport(
        lecture: Lecture,
        studentName: String,
        studentUniversityId: String,
        fix: LocationFix,
        distanceMeters: Double,
        withinGeofence: Boolean,
        status: AttendanceStatus,
        trackingMode: TrackingMode
    ): Result<AttendanceSyncOutcome> {
        val uidOrPlaceholder = auth.currentUser?.uid ?: "unauthenticated"
        val recordId = "${lecture.id}_s${lecture.activeSessionNumber}_$uidOrPlaceholder"
        val docPath = "attendance_records/$recordId"
        return try {
            val uid = requireUserId()
            val safeRecordId = "${lecture.id}_s${lecture.activeSessionNumber}_$uid"
            val docRef = db.collection("attendance_records").document(safeRecordId)

            val safeDistance = distanceMeters.coerceIn(0.0, 50_000_000.0)
            val safeAccuracy = fix.accuracyMeters.toDouble().coerceIn(0.0, 10_000.0)
            val cleanStudentName = studentName.trim().ifBlank { "Student" }.take(80)
            val cleanUniId = studentUniversityId.trim().take(40)
            val allowedStatus = when (status) {
                AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT -> status
                AttendanceStatus.EXCUSED -> AttendanceStatus.ABSENT
            }
            val allowedMode = when (trackingMode) {
                TrackingMode.AUTO_GPS, TrackingMode.MANUAL_GPS -> trackingMode
                TrackingMode.TEACHER_MARKED -> TrackingMode.MANUAL_GPS
            }

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "lectureId" to lecture.id,
                    "courseCode" to lecture.courseCode,
                    "lectureTitle" to lecture.title,
                    "roomName" to lecture.roomName,
                    "sessionNumber" to lecture.activeSessionNumber,
                    "teacherId" to lecture.teacherId,
                    "studentId" to uid,
                    "studentName" to cleanStudentName,
                    "status" to allowedStatus.wireValue,
                    "verificationStatus" to VerificationStatus.PENDING.wireValue,
                    "trackingMode" to allowedMode.wireValue,
                    "reportedLatitude" to fix.latitude,
                    "reportedLongitude" to fix.longitude,
                    "distanceMeters" to (safeDistance * 10.0).roundToLong() / 10.0,
                    "accuracyMeters" to (safeAccuracy * 10.0).roundToLong() / 10.0,
                    "withinGeofence" to withinGeofence,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanUniId.isNotEmpty()) {
                    createPayload["studentUniversityId"] = cleanUniId
                }
                docRef.set(createPayload).await()
                Result.success(AttendanceSyncOutcome.CREATED)
            } else {
                val currentVerification = VerificationStatus.fromWire(
                    existing.getString("verificationStatus")
                )
                if (currentVerification != VerificationStatus.PENDING) {
                    return Result.success(AttendanceSyncOutcome.LOCKED_BY_TEACHER)
                }
                val updatePayload = mapOf(
                    "status" to allowedStatus.wireValue,
                    "trackingMode" to allowedMode.wireValue,
                    "reportedLatitude" to fix.latitude,
                    "reportedLongitude" to fix.longitude,
                    "distanceMeters" to (safeDistance * 10.0).roundToLong() / 10.0,
                    "accuracyMeters" to (safeAccuracy * 10.0).roundToLong() / 10.0,
                    "withinGeofence" to withinGeofence,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
                Result.success(AttendanceSyncOutcome.UPDATED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyAttendanceRecord(
        recordId: String,
        newStatus: AttendanceStatus,
        verificationStatus: VerificationStatus,
        teacherNote: String
    ): Result<Unit> {
        val docPath = "attendance_records/$recordId"
        return try {
            val uid = requireUserId()
            require(recordId.isNotBlank()) { "Record ID is required." }
            val cleanNote = teacherNote.trim().take(300)
            val payload = mapOf(
                "status" to newStatus.wireValue,
                "verificationStatus" to verificationStatus.wireValue,
                "teacherNote" to cleanNote,
                "verifiedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("attendance_records").document(recordId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun logManualAbsenceByTeacher(
        lecture: Lecture,
        studentName: String,
        studentUniversityId: String,
        status: AttendanceStatus,
        teacherNote: String
    ): Result<Unit> {
        val slug = sanitizeIdSegment(studentUniversityId.ifBlank { studentName }).ifBlank { "STU" }
        val suffix = Random.nextInt(1000, 9999)
        val recordId = "${lecture.id}_s${lecture.activeSessionNumber}_m_${slug}_$suffix"
        val docPath = "attendance_records/$recordId"
        return try {
            val uid = requireUserId()
            check(lecture.teacherId == uid) { "Only the lecture teacher can log roster entries." }
            val cleanName = studentName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Student name is required." }
            val cleanUniId = studentUniversityId.trim().take(40)
            val cleanNote = teacherNote.trim().take(300)

            val payload = mutableMapOf<String, Any>(
                "lectureId" to lecture.id,
                "courseCode" to lecture.courseCode,
                "lectureTitle" to lecture.title,
                "roomName" to lecture.roomName,
                "sessionNumber" to lecture.activeSessionNumber,
                "teacherId" to uid,
                "studentId" to "manual_${slug}_$suffix",
                "studentName" to cleanName,
                "status" to status.wireValue,
                "verificationStatus" to VerificationStatus.VERIFIED.wireValue,
                "trackingMode" to TrackingMode.TEACHER_MARKED.wireValue,
                "reportedLatitude" to lecture.latitude,
                "reportedLongitude" to lecture.longitude,
                "distanceMeters" to 0.0,
                "accuracyMeters" to 0.0,
                "withinGeofence" to (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE),
                "verifiedBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanUniId.isNotEmpty()) payload["studentUniversityId"] = cleanUniId
            if (cleanNote.isNotEmpty()) payload["teacherNote"] = cleanNote

            db.collection("attendance_records").document(recordId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAttendanceRecord(recordId: String): Result<Unit> {
        val docPath = "attendance_records/$recordId"
        return try {
            requireUserId()
            require(recordId.isNotBlank()) { "Record ID is required." }
            db.collection("attendance_records").document(recordId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfAuthenticated(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception) ?: return
        if (auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
