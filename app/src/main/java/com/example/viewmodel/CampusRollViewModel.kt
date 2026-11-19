package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import com.example.data.AttendanceStatus
import com.example.data.AttendanceSyncOutcome
import com.example.data.EnrolledLectureLive
import com.example.data.Lecture
import com.example.data.LocationFix
import com.example.data.LocationTracker
import com.example.data.PortalMode
import com.example.data.SessionStatus
import com.example.data.TrackingMode
import com.example.data.UserProfile
import com.example.data.VerificationStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GpsUiState(
    val permissionGranted: Boolean = false,
    val trackingActive: Boolean = false,
    val latestFix: LocationFix? = null,
    val statusMessage: String = "Location permission needed for automatic geofence tracking"
)

class CampusRollViewModel(
    private val repository: AttendanceRepository,
    private val locationTracker: LocationTracker,
    val currentUserId: String,
    val initialDisplayName: String
) : ViewModel() {

    private val _gpsState = MutableStateFlow(
        GpsUiState(
            permissionGranted = locationTracker.hasLocationPermission(),
            statusMessage = if (locationTracker.hasLocationPermission()) {
                "Acquiring GPS satellite fix…"
            } else {
                "Enable GPS to automatically verify lecture hall presence"
            }
        )
    )
    val gpsState: StateFlow<GpsUiState> = _gpsState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _busyAction = MutableStateFlow(false)
    val busyAction: StateFlow<Boolean> = _busyAction.asStateFlow()

    // Tracks (lectureId_sessionNumber) keys already auto-reported during this session to prevent write spam
    private val autoReportedSessionKeys = mutableSetOf<String>()
    private var locationJob: Job? = null

    val userProfile: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> handleStreamError("profile", error, ::emit) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val teacherLectures: StateFlow<UiState<List<Lecture>>> =
        repository.observeTeacherLectures(currentUserId)
            .map<List<Lecture>, UiState<List<Lecture>>> { UiState.Success(it) }
            .catch { error -> handleStreamError("teacher lectures", error, ::emit) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val studentAttendanceHistory: StateFlow<UiState<List<AttendanceRecord>>> =
        repository.observeStudentAttendance(currentUserId)
            .map<List<AttendanceRecord>, UiState<List<AttendanceRecord>>> { UiState.Success(it) }
            .catch { error -> handleStreamError("student attendance", error, ::emit) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val teacherAttendanceQueue: StateFlow<UiState<List<AttendanceRecord>>> =
        repository.observeTeacherAttendance(currentUserId)
            .map<List<AttendanceRecord>, UiState<List<AttendanceRecord>>> { UiState.Success(it) }
            .catch { error -> handleStreamError("teacher attendance queue", error, ::emit) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val enrolledLecturesLive: StateFlow<UiState<List<EnrolledLectureLive>>> = combine(
        repository.observeStudentEnrolledLectures(currentUserId),
        studentAttendanceHistory,
        gpsState
    ) { enrolledPairs, historyState, gps ->
        val records = (historyState as? UiState.Success)?.data.orEmpty()
        val fix = gps.latestFix
        enrolledPairs.map { (enrollment, liveLecture) ->
            val distance = if (fix != null && liveLecture != null) {
                LocationTracker.distanceBetweenMeters(
                    fix.latitude,
                    fix.longitude,
                    liveLecture.latitude,
                    liveLecture.longitude
                )
            } else {
                null
            }
            val withinZone = distance != null &&
                liveLecture != null &&
                distance <= liveLecture.radiusMeters.toDouble()
            val matchingRecord = liveLecture?.let { lec ->
                records.firstOrNull {
                    it.lectureId == lec.id && it.sessionNumber == lec.activeSessionNumber
                }
            }
            EnrolledLectureLive(
                enrollment = enrollment,
                liveLecture = liveLecture,
                distanceMeters = distance,
                withinGeofence = withinZone,
                currentSessionRecord = matchingRecord
            )
        }
    }
        .map<List<EnrolledLectureLive>, UiState<List<EnrolledLectureLive>>> { UiState.Success(it) }
        .catch { error -> handleStreamError("enrolled lectures", error, ::emit) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName)
        }
        if (locationTracker.hasLocationPermission()) {
            startLocationTracking()
        }
        observeAutoReportTriggers()
    }

    private fun observeAutoReportTriggers() {
        viewModelScope.launch {
            combine(
                userProfile,
                enrolledLecturesLive,
                gpsState
            ) { profileState, lecturesState, gps ->
                Triple(profileState, lecturesState, gps)
            }.collect { (profileState, lecturesState, gps) ->
                val profile = (profileState as? UiState.Success)?.data ?: return@collect
                if (!profile.autoReportEnabled) return@collect
                val fix = gps.latestFix ?: return@collect
                val lectures = (lecturesState as? UiState.Success)?.data ?: return@collect

                for (item in lectures) {
                    val live = item.liveLecture ?: continue
                    if (live.resolvedSessionStatus() != SessionStatus.ACTIVE) continue
                    if (!live.autoTrackEnabled) continue

                    val distance = item.distanceMeters ?: continue
                    val sessionKey = "${live.id}_s${live.activeSessionNumber}"

                    // Auto-report PRESENT immediately when student enters lecture hall geofence
                    if (item.withinGeofence) {
                        val existing = item.currentSessionRecord
                        val needsAutoCheckIn = existing == null ||
                            (existing.resolvedStatus() == AttendanceStatus.ABSENT &&
                                existing.resolvedVerificationStatus() == VerificationStatus.PENDING)
                        if (needsAutoCheckIn && autoReportedSessionKeys.add("present_$sessionKey")) {
                            val result = repository.submitStudentGpsReport(
                                lecture = live,
                                studentName = profile.displayName.ifBlank { initialDisplayName },
                                studentUniversityId = profile.universityId,
                                fix = fix,
                                distanceMeters = distance,
                                withinGeofence = true,
                                status = AttendanceStatus.PRESENT,
                                trackingMode = TrackingMode.AUTO_GPS
                            )
                            result.onSuccess { outcome ->
                                if (outcome != AttendanceSyncOutcome.LOCKED_BY_TEACHER) {
                                    _snackbarEvents.tryEmit(
                                        "Auto-reported Present for ${live.courseCode} (${distance.toInt()}m from room)"
                                    )
                                }
                            }.onFailure {
                                autoReportedSessionKeys.remove("present_$sessionKey")
                            }
                        }
                    }
                }
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            _gpsState.value = _gpsState.value.copy(
                permissionGranted = true,
                statusMessage = "Acquiring live GPS coordinates…"
            )
            startLocationTracking()
        } else {
            _gpsState.value = _gpsState.value.copy(
                permissionGranted = false,
                trackingActive = false,
                statusMessage = "Location permission denied — enable GPS to check in"
            )
        }
    }

    fun startLocationTracking() {
        if (!locationTracker.hasLocationPermission()) return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            _gpsState.value = _gpsState.value.copy(
                permissionGranted = true,
                trackingActive = true,
                statusMessage = "Live GPS active — scanning lecture geofences"
            )
            locationTracker.observeLocationUpdates(intervalMs = 4000L)
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "GPS update stream interrupted", e)
                    _gpsState.value = _gpsState.value.copy(
                        trackingActive = false,
                        statusMessage = "GPS signal paused — tap refresh to reconnect"
                    )
                }
                .collect { fix ->
                    _gpsState.value = GpsUiState(
                        permissionGranted = true,
                        trackingActive = true,
                        latestFix = fix,
                        statusMessage = "Live GPS locked (±${fix.accuracyMeters.toInt()}m accuracy)"
                    )
                }
        }
    }

    fun refreshGpsFix(onFixReady: ((LocationFix?) -> Unit)? = null) {
        viewModelScope.launch {
            if (!locationTracker.hasLocationPermission()) {
                _snackbarEvents.tryEmit("Please grant location permission first.")
                onFixReady?.invoke(null)
                return@launch
            }
            val fix = locationTracker.getFreshLocation()
            if (fix != null) {
                _gpsState.value = GpsUiState(
                    permissionGranted = true,
                    trackingActive = true,
                    latestFix = fix,
                    statusMessage = "Live GPS locked (±${fix.accuracyMeters.toInt()}m accuracy)"
                )
            } else {
                _snackbarEvents.tryEmit("Waiting for device GPS fix. Ensure location services are enabled.")
            }
            onFixReady?.invoke(fix)
        }
    }

    // --- STUDENT ACTIONS ---

    fun toggleAutoReport(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoReportEnabled(enabled)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (enabled) "Automatic GPS attendance reporting enabled"
                        else "Automatic GPS reporting paused"
                    )
                }
                .onFailure {
                    _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update auto-report setting")
                }
        }
    }

    fun enrollInLecture(joinCode: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _busyAction.value = true
            val result = repository.enrollByJoinCode(joinCode)
            _busyAction.value = false
            result.onSuccess { lecture ->
                _snackbarEvents.tryEmit("Enrolled in ${lecture.courseCode}: ${lecture.title}")
                onSuccess()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Could not join lecture")
            }
        }
    }

    fun unenrollFromLecture(lectureId: String) {
        viewModelScope.launch {
            repository.unenrollLecture(lectureId)
                .onSuccess { _snackbarEvents.tryEmit("Removed lecture from your dashboard") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to remove lecture") }
        }
    }

    fun manualGpsSync(lecture: Lecture, reportAsAbsent: Boolean = false) {
        viewModelScope.launch {
            _busyAction.value = true
            val fix = _gpsState.value.latestFix ?: locationTracker.getFreshLocation()
            if (fix == null) {
                _busyAction.value = false
                _snackbarEvents.tryEmit("No live GPS fix yet. Enable Location Services and retry.")
                return@launch
            }
            val distance = LocationTracker.distanceBetweenMeters(
                fix.latitude,
                fix.longitude,
                lecture.latitude,
                lecture.longitude
            )
            val withinZone = distance <= lecture.radiusMeters.toDouble()
            val status = when {
                reportAsAbsent -> AttendanceStatus.ABSENT
                withinZone -> AttendanceStatus.PRESENT
                else -> AttendanceStatus.ABSENT
            }
            val profile = (userProfile.value as? UiState.Success)?.data
            val result = repository.submitStudentGpsReport(
                lecture = lecture,
                studentName = profile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName,
                studentUniversityId = profile?.universityId.orEmpty(),
                fix = fix,
                distanceMeters = distance,
                withinGeofence = withinZone,
                status = status,
                trackingMode = TrackingMode.MANUAL_GPS
            )
            _busyAction.value = false
            result.onSuccess { outcome ->
                val message = when (outcome) {
                    AttendanceSyncOutcome.LOCKED_BY_TEACHER ->
                        "Record already reviewed by your lecturer and locked."
                    else -> if (status == AttendanceStatus.PRESENT) {
                        "Checked in Present for ${lecture.courseCode} (${distance.toInt()}m from hall)"
                    } else {
                        "Reported Absent for ${lecture.courseCode} (${distance.toInt()}m outside ${lecture.radiusMeters}m zone)"
                    }
                }
                _snackbarEvents.tryEmit(message)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Attendance sync failed")
            }
        }
    }

    // --- TEACHER ACTIONS ---

    fun createLecture(
        courseCode: String,
        title: String,
        roomName: String,
        scheduleLabel: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Long,
        startImmediately: Boolean,
        onCreated: (Lecture) -> Unit
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val profile = (userProfile.value as? UiState.Success)?.data
            val teacherName = profile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
            val result = repository.createLecture(
                teacherName = teacherName,
                courseCode = courseCode,
                title = title,
                roomName = roomName,
                scheduleLabel = scheduleLabel,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                startImmediately = startImmediately
            )
            _busyAction.value = false
            result.onSuccess { lecture ->
                _snackbarEvents.tryEmit("Created ${lecture.courseCode} — Join Code: ${lecture.joinCode}")
                onCreated(lecture)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to create lecture")
            }
        }
    }

    fun toggleLectureSession(lecture: Lecture, open: Boolean) {
        viewModelScope.launch {
            repository.toggleLectureSession(lecture, open)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (open) "Live roll call opened for ${lecture.courseCode}"
                        else "Roll call closed for ${lecture.courseCode}"
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to update session status")
                }
        }
    }

    fun repinLectureToCurrentGps(lecture: Lecture) {
        viewModelScope.launch {
            val fix = _gpsState.value.latestFix ?: locationTracker.getFreshLocation()
            if (fix == null) {
                _snackbarEvents.tryEmit("Enable GPS first to pin this lecture hall to your phone location.")
                return@launch
            }
            repository.updateLectureGeofence(
                lectureId = lecture.id,
                latitude = fix.latitude,
                longitude = fix.longitude,
                radiusMeters = lecture.radiusMeters
            ).onSuccess {
                _snackbarEvents.tryEmit("Updated ${lecture.courseCode} geofence to your live GPS coordinates")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to update geofence")
            }
        }
    }

    fun deleteLecture(lectureId: String) {
        viewModelScope.launch {
            repository.deleteLecture(lectureId)
                .onSuccess { _snackbarEvents.tryEmit("Lecture deleted") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to delete lecture") }
        }
    }

    fun verifyRecord(
        record: AttendanceRecord,
        newStatus: AttendanceStatus,
        verificationStatus: VerificationStatus,
        teacherNote: String
    ) {
        viewModelScope.launch {
            repository.verifyAttendanceRecord(
                recordId = record.id,
                newStatus = newStatus,
                verificationStatus = verificationStatus,
                teacherNote = teacherNote
            ).onSuccess {
                _snackbarEvents.tryEmit(
                    "${record.studentName} marked ${newStatus.label} (${verificationStatus.label})"
                )
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to verify attendance record")
            }
        }
    }

    fun logManualAbsence(
        lecture: Lecture,
        studentName: String,
        studentUniversityId: String,
        status: AttendanceStatus,
        teacherNote: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val result = repository.logManualAbsenceByTeacher(
                lecture = lecture,
                studentName = studentName,
                studentUniversityId = studentUniversityId,
                status = status,
                teacherNote = teacherNote
            )
            _busyAction.value = false
            result.onSuccess {
                _snackbarEvents.tryEmit("Logged ${status.label} for $studentName")
                onSuccess()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to log roster entry")
            }
        }
    }

    fun deleteAttendanceRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteAttendanceRecord(recordId)
                .onSuccess { _snackbarEvents.tryEmit("Attendance record removed") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to remove record") }
        }
    }

    // --- PROFILE ACTIONS ---

    fun updateProfile(
        displayName: String,
        universityId: String,
        department: String,
        portalMode: PortalMode,
        autoReportEnabled: Boolean,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _busyAction.value = true
            val result = repository.updateUserProfile(
                displayName = displayName,
                universityId = universityId,
                department = department,
                portalMode = portalMode,
                autoReportEnabled = autoReportEnabled
            )
            _busyAction.value = false
            result.onSuccess {
                _snackbarEvents.tryEmit("Profile saved")
                onSaved()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to save profile")
            }
        }
    }

    fun switchPortalMode(mode: PortalMode) {
        viewModelScope.launch {
            repository.setPortalMode(mode)
        }
    }

    private suspend fun <T> handleStreamError(
        label: String,
        error: Throwable,
        emitState: suspend (UiState<T>) -> Unit
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        emitState(UiState.Error(rootError.localizedMessage ?: "Failed to observe $label"))
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
    }

    private companion object {
        const val TAG = "CampusRollVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
