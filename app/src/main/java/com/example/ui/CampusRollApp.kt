package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AttendanceRecord
import com.example.data.AttendanceRepository
import com.example.data.AttendanceStatus
import com.example.data.Lecture
import com.example.data.LocationTracker
import com.example.data.PortalMode
import com.example.data.VerificationStatus
import com.example.data.authStateFlow
import com.example.data.signOutUser
import com.example.viewmodel.CampusRollViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class CampusTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTagName: String
) {
    STUDENT(R.string.nav_student, Icons.Rounded.Radar, Icons.Outlined.Radar, "tab_student"),
    HISTORY(R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History, "tab_history"),
    TEACHER(R.string.nav_teacher, Icons.Rounded.FactCheck, Icons.Outlined.FactCheck, "tab_teacher")
}

@Composable
fun CampusRollApp(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        key(user.uid) {
            AuthenticatedCampusShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "University Member",
                userEmail = user.email
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedCampusShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CampusRollViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = AttendanceRepository(db)
                val tracker = LocationTracker(app)
                CampusRollViewModel(
                    repository = repo,
                    locationTracker = tracker,
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName
                )
            }
        }
    )

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val gpsState by viewModel.gpsState.collectAsStateWithLifecycle()
    val enrolledLecturesState by viewModel.enrolledLecturesLive.collectAsStateWithLifecycle()
    val studentHistoryState by viewModel.studentAttendanceHistory.collectAsStateWithLifecycle()
    val teacherLecturesState by viewModel.teacherLectures.collectAsStateWithLifecycle()
    val teacherQueueState by viewModel.teacherAttendanceQueue.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }

    var currentTab by remember { mutableStateOf(CampusTab.STUDENT) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateLectureDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var recordToVerify by remember { mutableStateOf<AttendanceRecord?>(null) }
    var lectureForManualEntry by remember { mutableStateOf<Lecture?>(null) }

    if (currentTab != CampusTab.STUDENT) {
        BackHandler {
            currentTab = CampusTab.STUDENT
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val pendingVerificationCount = (teacherQueueState as? UiState.Success)
        ?.data
        ?.count { it.resolvedVerificationStatus() == VerificationStatus.PENDING }
        ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                CampusTab.STUDENT -> "CampusRoll • Student"
                                CampusTab.HISTORY -> "Attendance History"
                                CampusTab.TEACHER -> "CampusRoll • Teacher"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showProfileDialog = true },
                            modifier = Modifier.testTag("profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = "Open university profile and settings",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        CampusTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    currentTab = tab
                                    if (tab == CampusTab.TEACHER) {
                                        viewModel.switchPortalMode(PortalMode.TEACHER)
                                    } else if (tab == CampusTab.STUDENT) {
                                        viewModel.switchPortalMode(PortalMode.STUDENT)
                                    }
                                },
                                icon = {
                                    if (tab == CampusTab.TEACHER && pendingVerificationCount > 0) {
                                        BadgedBox(
                                            badge = { Badge { Text("$pendingVerificationCount") } }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(id = tab.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(id = tab.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTagName)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        CampusTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    currentTab = tab
                                    if (tab == CampusTab.TEACHER) {
                                        viewModel.switchPortalMode(PortalMode.TEACHER)
                                    } else if (tab == CampusTab.STUDENT) {
                                        viewModel.switchPortalMode(PortalMode.STUDENT)
                                    }
                                },
                                icon = {
                                    if (tab == CampusTab.TEACHER && pendingVerificationCount > 0) {
                                        BadgedBox(
                                            badge = { Badge { Text("$pendingVerificationCount") } }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(id = tab.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(id = tab.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTagName)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        when (currentTab) {
                            CampusTab.STUDENT -> StudentDashboardScreen(
                                profile = profile,
                                gpsState = gpsState,
                                enrolledLecturesState = enrolledLecturesState,
                                attendanceHistoryState = studentHistoryState,
                                busyAction = busyAction,
                                onRequestLocationPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onRefreshGps = { viewModel.refreshGpsFix() },
                                onToggleAutoReport = { enabled -> viewModel.toggleAutoReport(enabled) },
                                onOpenJoinLecture = { showJoinDialog = true },
                                onManualCheckIn = { lecture -> viewModel.manualGpsSync(lecture, reportAsAbsent = false) },
                                onReportAbsence = { lecture -> viewModel.manualGpsSync(lecture, reportAsAbsent = true) },
                                onUnenroll = { lectureId -> viewModel.unenrollFromLecture(lectureId) }
                            )

                            CampusTab.HISTORY -> StudentHistoryScreen(
                                attendanceHistoryState = studentHistoryState
                            )

                            CampusTab.TEACHER -> TeacherPortalScreen(
                                teacherLecturesState = teacherLecturesState,
                                verificationQueueState = teacherQueueState,
                                onOpenCreateLecture = { showCreateLectureDialog = true },
                                onToggleSession = { lecture, open -> viewModel.toggleLectureSession(lecture, open) },
                                onRepinGps = { lecture -> viewModel.repinLectureToCurrentGps(lecture) },
                                onOpenManualAbsence = { lecture -> lectureForManualEntry = lecture },
                                onDeleteLecture = { id -> viewModel.deleteLecture(id) },
                                onQuickVerify = { rec ->
                                    viewModel.verifyRecord(
                                        record = rec,
                                        newStatus = rec.resolvedStatus(),
                                        verificationStatus = VerificationStatus.VERIFIED,
                                        teacherNote = rec.teacherNote
                                    )
                                },
                                onQuickFlag = { rec ->
                                    viewModel.verifyRecord(
                                        record = rec,
                                        newStatus = rec.resolvedStatus(),
                                        verificationStatus = VerificationStatus.FLAGGED,
                                        teacherNote = rec.teacherNote.ifBlank { "Flagged for GPS verification" }
                                    )
                                },
                                onQuickMarkAbsent = { rec ->
                                    viewModel.verifyRecord(
                                        record = rec,
                                        newStatus = AttendanceStatus.ABSENT,
                                        verificationStatus = VerificationStatus.VERIFIED,
                                        teacherNote = rec.teacherNote.ifBlank { "Marked absent by lecturer" }
                                    )
                                },
                                onOpenReviewDialog = { rec -> recordToVerify = rec },
                                onDeleteRecord = { id -> viewModel.deleteAttendanceRecord(id) },
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    if (showJoinDialog) {
        JoinLectureDialog(
            busy = busyAction,
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                viewModel.enrollInLecture(code) {
                    showJoinDialog = false
                }
            }
        )
    }

    if (showCreateLectureDialog) {
        CreateLectureDialog(
            latestFix = gpsState.latestFix,
            busy = busyAction,
            onRequestFreshFix = { callback ->
                if (!gpsState.permissionGranted) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    callback(null)
                } else {
                    viewModel.refreshGpsFix(callback)
                }
            },
            onDismiss = { showCreateLectureDialog = false },
            onCreate = { code, title, room, schedule, lat, lng, radius, startNow ->
                viewModel.createLecture(
                    courseCode = code,
                    title = title,
                    roomName = room,
                    scheduleLabel = schedule,
                    latitude = lat,
                    longitude = lng,
                    radiusMeters = radius,
                    startImmediately = startNow
                ) {
                    showCreateLectureDialog = false
                }
            }
        )
    }

    recordToVerify?.let { rec ->
        VerifyRecordDialog(
            record = rec,
            onDismiss = { recordToVerify = null },
            onConfirm = { newStatus, newVerification, note ->
                viewModel.verifyRecord(rec, newStatus, newVerification, note)
                recordToVerify = null
            }
        )
    }

    lectureForManualEntry?.let { lec ->
        ManualAbsenceDialog(
            lecture = lec,
            busy = busyAction,
            onDismiss = { lectureForManualEntry = null },
            onLog = { name, uniId, status, note ->
                viewModel.logManualAbsence(lec, name, uniId, status, note) {
                    lectureForManualEntry = null
                }
            }
        )
    }

    if (showProfileDialog) {
        ProfileSettingsDialog(
            profile = profile,
            fallbackName = userDisplayName,
            userEmail = userEmail,
            busy = busyAction,
            onDismiss = { showProfileDialog = false },
            onSave = { name, uniId, dept, portalMode, autoReport ->
                viewModel.updateProfile(name, uniId, dept, portalMode, autoReport) {
                    showProfileDialog = false
                }
            },
            onSignOut = {
                showProfileDialog = false
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope
                )
            }
        )
    }
}
