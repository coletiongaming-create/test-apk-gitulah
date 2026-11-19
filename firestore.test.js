const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TEACHER_UID = "teacher_uid_001";
const STUDENT_UID = "student_uid_002";
const OTHER_UID = "outsider_uid_003";
const LECTURE_ID = "CS301-4821";
const RECORD_ID = `${LECTURE_ID}_s1_${STUDENT_UID}`;

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validLecturePayload(teacherId = TEACHER_UID, joinCode = LECTURE_ID) {
  const now = pastDate(5000);
  return {
    teacherId,
    teacherName: "Dr. Elena Vance",
    courseCode: "CS-301",
    title: "Distributed Systems",
    roomName: "Engineering Hall B-204",
    scheduleLabel: "Mon/Wed 10:00 AM",
    joinCode,
    latitude: 37.4275,
    longitude: -122.1697,
    radiusMeters: 100,
    sessionStatus: "active",
    activeSessionNumber: 1,
    autoTrackEnabled: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validStudentAttendancePayload(studentId = STUDENT_UID, teacherId = TEACHER_UID) {
  const now = pastDate(3000);
  return {
    lectureId: LECTURE_ID,
    courseCode: "CS-301",
    lectureTitle: "Distributed Systems",
    roomName: "Engineering Hall B-204",
    sessionNumber: 1,
    teacherId,
    studentId,
    studentName: "Jordan Patel",
    studentUniversityId: "UNI-2026-441",
    status: "present",
    verificationStatus: "pending",
    trackingMode: "auto_gps",
    reportedLatitude: 37.4276,
    reportedLongitude: -122.1696,
    distanceMeters: 14.5,
    accuracyMeters: 6.0,
    withinGeofence: true,
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. Unauthenticated bounds
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(STUDENT_UID).get());
  await assertFails(unauthDb.collection("lectures").doc(LECTURE_ID).get());
  await assertFails(unauthDb.collection("lectures").get());
  await assertFails(unauthDb.collection("attendance_records").get());
  await assertFails(
    unauthDb.collection("lectures").doc(LECTURE_ID).set(validLecturePayload())
  );
});

// 2. Private User Profile CRUD & Isolation
test("User profile: owner can create and update; other user denied; shadow/id field rejected", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "Jordan Patel",
      universityId: "UNI-2026-441",
      department: "Computer Science",
      portalMode: "student",
      autoReportEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).update({
      portalMode: "teacher",
      autoReportEnabled: false,
      updatedAt: pastDate(1000),
    })
  );

  // Other user cannot read or update
  await assertFails(otherDb.collection("users").doc(STUDENT_UID).get());
  await assertFails(
    otherDb.collection("users").doc(STUDENT_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(1000),
    })
  );

  // Shadow field / @DocumentId 'id' rejected
  await assertFails(
    otherDb.collection("users").doc(OTHER_UID).set({
      id: OTHER_UID,
      userId: OTHER_UID,
      displayName: "Bad Payload",
      portalMode: "student",
      autoReportEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp rejected
  await assertFails(
    otherDb.collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      displayName: "Future User",
      portalMode: "student",
      autoReportEnabled: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

// 3. Lectures CRUD, Query Alignment & Teacher Ownership
test("Lectures: teacher creates/lists/updates own lecture; student can get by code; unfiltered list denied", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();

  await assertSucceeds(
    teacherDb.collection("lectures").doc(LECTURE_ID).set(validLecturePayload())
  );

  // Teacher's filtered query succeeds (mirrors LectureRepository.observeTeacherLectures)
  await assertSucceeds(
    teacherDb.collection("lectures").where("teacherId", "==", TEACHER_UID).get()
  );

  // Unfiltered list query MUST fail
  await assertFails(teacherDb.collection("lectures").get());
  await assertFails(studentDb.collection("lectures").get());

  // Student can look up lecture by exact join code
  await assertSucceeds(studentDb.collection("lectures").doc(LECTURE_ID).get());

  // Teacher toggles session status (Action 1)
  await assertSucceeds(
    teacherDb.collection("lectures").doc(LECTURE_ID).update({
      sessionStatus: "closed",
      activeSessionNumber: 2,
      updatedAt: pastDate(1000),
    })
  );

  // Teacher updates geofence (Action 2)
  await assertSucceeds(
    teacherDb.collection("lectures").doc(LECTURE_ID).update({
      radiusMeters: 150,
      latitude: 37.428,
      longitude: -122.17,
      updatedAt: pastDate(500),
    })
  );

  // Student cannot modify teacher's lecture
  await assertFails(
    studentDb.collection("lectures").doc(LECTURE_ID).update({
      sessionStatus: "active",
      activeSessionNumber: 3,
      updatedAt: pastDate(500),
    })
  );
});

// 4. Student Enrollments Subcollection & Global Consistency
test("Enrollments: student enrolls only in existing lecture; cross-user denied", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  await assertSucceeds(
    teacherDb.collection("lectures").doc(LECTURE_ID).set(validLecturePayload())
  );

  const now = pastDate(2000);
  // Valid enrollment referencing existing lecture
  await assertSucceeds(
    studentDb
      .collection("users")
      .doc(STUDENT_UID)
      .collection("enrollments")
      .doc(LECTURE_ID)
      .set({
        lectureId: LECTURE_ID,
        teacherId: TEACHER_UID,
        courseCode: "CS-301",
        title: "Distributed Systems",
        roomName: "Engineering Hall B-204",
        teacherName: "Dr. Elena Vance",
        scheduleLabel: "Mon/Wed 10:00 AM",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Unfiltered list on own user subcollection succeeds (mirrors observeEnrollments)
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("enrollments").get()
  );

  // Orphan enrollment for non-existent lecture fails
  await assertFails(
    studentDb
      .collection("users")
      .doc(STUDENT_UID)
      .collection("enrollments")
      .doc("FAKE-999")
      .set({
        lectureId: "FAKE-999",
        teacherId: TEACHER_UID,
        courseCode: "XX-999",
        title: "Ghost Class",
        roomName: "Nowhere",
        teacherName: "Ghost",
        scheduleLabel: "Never",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Other user cannot read student's enrollments
  await assertFails(
    otherDb.collection("users").doc(STUDENT_UID).collection("enrollments").get()
  );
});

// 5. Attendance Records: Auto GPS Report, Anti-Self-Verify, Terminal Lock & Query Parity
test("Attendance records: student auto-reports pending; cannot self-verify; teacher verifies and locks student edits", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();

  await assertSucceeds(
    teacherDb.collection("lectures").doc(LECTURE_ID).set(validLecturePayload())
  );

  // Student CANNOT self-verify on create
  await assertFails(
    studentDb.collection("attendance_records").doc(RECORD_ID).set({
      ...validStudentAttendancePayload(),
      verificationStatus: "verified",
    })
  );

  // Student CANNOT spoof teacherId
  await assertFails(
    studentDb.collection("attendance_records").doc(RECORD_ID).set({
      ...validStudentAttendancePayload(STUDENT_UID, OTHER_UID),
    })
  );

  // Student valid auto GPS report succeeds
  await assertSucceeds(
    studentDb
      .collection("attendance_records")
      .doc(RECORD_ID)
      .set(validStudentAttendancePayload())
  );

  // Student filtered query succeeds; teacher filtered query succeeds; outsider & unfiltered fail
  await assertSucceeds(
    studentDb
      .collection("attendance_records")
      .where("studentId", "==", STUDENT_UID)
      .get()
  );
  await assertSucceeds(
    teacherDb
      .collection("attendance_records")
      .where("teacherId", "==", TEACHER_UID)
      .get()
  );
  await assertFails(studentDb.collection("attendance_records").get());
  await assertFails(
    otherDb.collection("attendance_records").doc(RECORD_ID).get()
  );

  // Student can re-sync GPS while pending
  await assertSucceeds(
    studentDb.collection("attendance_records").doc(RECORD_ID).update({
      status: "present",
      trackingMode: "manual_gps",
      reportedLatitude: 37.4275,
      reportedLongitude: -122.1697,
      distanceMeters: 4.2,
      accuracyMeters: 4.0,
      withinGeofence: true,
      updatedAt: pastDate(1500),
    })
  );

  // Student CANNOT delete their own attendance/absence record
  await assertFails(
    studentDb.collection("attendance_records").doc(RECORD_ID).delete()
  );

  // Teacher verifies the record
  await assertSucceeds(
    teacherDb.collection("attendance_records").doc(RECORD_ID).update({
      status: "present",
      verificationStatus: "verified",
      teacherNote: "Confirmed in row 3",
      verifiedBy: TEACHER_UID,
      updatedAt: pastDate(1000),
    })
  );

  // TERMINAL STATE LOCK: Student can no longer update once verified by teacher
  await assertFails(
    studentDb.collection("attendance_records").doc(RECORD_ID).update({
      status: "present",
      trackingMode: "manual_gps",
      reportedLatitude: 37.4275,
      reportedLongitude: -122.1697,
      distanceMeters: 1.0,
      accuracyMeters: 3.0,
      withinGeofence: true,
      updatedAt: pastDate(500),
    })
  );

  // Teacher can delete a record if needed
  await assertSucceeds(
    teacherDb.collection("attendance_records").doc(RECORD_ID).delete()
  );
});
