const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated user cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("alarms").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("alarms")
      .doc("alarm_1")
      .set({
        userId: ALICE_UID,
        label: "Morning",
        hour: 6,
        minute: 30,
        repeatDays: [1, 2, 3, 4, 5],
        enabled: true,
        vibrate: true,
        soundId: "copper_bell",
        soundTitle: "Copper Bell",
        rampSeconds: 30,
        maxVolumePercent: 85,
        sunriseEnabled: true,
        snoozeMode: "DIMINISHING",
        snoozeIntervalMinutes: 9,
        snoozeMaxCount: 3,
        snoozeSequenceMinutes: [9, 6, 3],
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );
});

test("Cross-user isolation blocks Bob from reading or modifying Alice's alarms and sleepLogs", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("alarms")
      .doc("alarm_alice")
      .set({
        userId: ALICE_UID,
        label: "Shift Start",
        hour: 6,
        minute: 0,
        repeatDays: [1, 2, 3],
        enabled: true,
        vibrate: true,
        soundId: "copper_bell",
        soundTitle: "Copper Bell",
        rampSeconds: 45,
        maxVolumePercent: 90,
        sunriseEnabled: true,
        snoozeMode: "FIXED",
        snoozeIntervalMinutes: 10,
        snoozeMaxCount: 3,
        snoozeSequenceMinutes: [10, 10, 10],
        createdAt: pastDate(20000),
        updatedAt: pastDate(20000),
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("alarms")
      .doc("alarm_alice")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("alarms").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("alarms")
      .doc("alarm_alice")
      .update({ enabled: false, updatedAt: pastDate(1000) })
  );
});

test("AlarmProfile: valid Kotlin create, toggle, full update succeed; ghost field, @DocumentId id, and bad types fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(15000);
  const alarmRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("alarms")
    .doc("alarm_1");

  await assertSucceeds(
    alarmRef.set({
      userId: ALICE_UID,
      label: "Foundry Wake",
      hour: 6,
      minute: 15,
      repeatDays: [1, 2, 3, 4, 5],
      enabled: true,
      vibrate: true,
      soundId: "copper_bell",
      soundTitle: "Copper Bell",
      rampSeconds: 60,
      maxVolumePercent: 80,
      sunriseEnabled: true,
      snoozeMode: "DIMINISHING",
      snoozeIntervalMinutes: 9,
      snoozeMaxCount: 3,
      snoozeSequenceMinutes: [9, 6, 3],
      linkedRoutineId: "routine_default",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle update
  await assertSucceeds(
    alarmRef.update({
      enabled: false,
      updatedAt: pastDate(2000),
    })
  );

  // Full edit update preserving createdAt
  await assertSucceeds(
    alarmRef.update({
      label: "Updated Wake",
      hour: 7,
      minute: 0,
      repeatDays: [6, 7],
      enabled: true,
      vibrate: false,
      soundId: "foundry_gong",
      soundTitle: "Foundry Gong",
      rampSeconds: 30,
      maxVolumePercent: 95,
      sunriseEnabled: false,
      snoozeMode: "CUSTOM",
      snoozeIntervalMinutes: 5,
      snoozeMaxCount: 2,
      snoozeSequenceMinutes: [8, 4],
      updatedAt: pastDate(1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    alarmRef.update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject @DocumentId `id` field or ghost field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("alarms")
      .doc("alarm_2")
      .set({
        id: "alarm_2",
        userId: ALICE_UID,
        label: "Bad",
        hour: 6,
        minute: 0,
        repeatDays: [],
        enabled: true,
        vibrate: true,
        soundId: "copper_bell",
        soundTitle: "Copper Bell",
        rampSeconds: 30,
        maxVolumePercent: 80,
        sunriseEnabled: true,
        snoozeMode: "FIXED",
        snoozeIntervalMinutes: 5,
        snoozeMaxCount: 3,
        snoozeSequenceMinutes: [5, 5, 5],
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject invalid hour and future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("alarms")
      .doc("alarm_3")
      .set({
        userId: ALICE_UID,
        label: "Invalid Hour",
        hour: 25,
        minute: 0,
        repeatDays: [],
        enabled: true,
        vibrate: true,
        soundId: "copper_bell",
        soundTitle: "Copper Bell",
        rampSeconds: 30,
        maxVolumePercent: 80,
        sunriseEnabled: true,
        snoozeMode: "FIXED",
        snoozeIntervalMinutes: 5,
        snoozeMaxCount: 3,
        snoozeSequenceMinutes: [5],
        createdAt: new Date(Date.now() + 60000),
        updatedAt: created,
      })
  );

  // Query list on user subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("alarms").get()
  );
});

test("MorningRoutine, RoutineRun, SleepLog, and UserSettings adhere to schema and security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  // UserSettings (only 'settings' docId allowed)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .set({
        userId: ALICE_UID,
        targetSleepMinutes: 480,
        defaultSnoozeMinutes: 9,
        defaultSnoozeMaxCount: 3,
        defaultRampSeconds: 30,
        sunriseGlowEnabled: true,
        createdAt: created,
        updatedAt: created,
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("other_doc")
      .set({
        userId: ALICE_UID,
        targetSleepMinutes: 480,
        defaultSnoozeMinutes: 9,
        defaultSnoozeMaxCount: 3,
        defaultRampSeconds: 30,
        sunriseGlowEnabled: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // MorningRoutine
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("routines")
      .doc("routine_1")
      .set({
        userId: ALICE_UID,
        title: "Iron Morning",
        defaultRoutine: true,
        steps: [
          {
            stepId: "s1",
            title: "Hydrate 500ml",
            durationMinutes: 2,
            iconKey: "water",
          },
          {
            stepId: "s2",
            title: "Sunlight & Mobility",
            durationMinutes: 8,
            iconKey: "sun",
          },
        ],
        createdAt: created,
        updatedAt: created,
      })
  );

  // RoutineRun
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("routineRuns")
      .doc("run_1")
      .set({
        userId: ALICE_UID,
        routineId: "routine_1",
        routineTitle: "Iron Morning",
        dateKey: "2026-11-25",
        completedStepCount: 2,
        totalStepCount: 2,
        elapsedSeconds: 590,
        completed: true,
        createdAt: created,
        updatedAt: created,
      })
  );

  // SleepLog
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sleepLogs")
      .doc("log_1")
      .set({
        userId: ALICE_UID,
        dateKey: "2026-11-25",
        bedtimeEpochMs: Date.now() - 8 * 3600 * 1000,
        wakeEpochMs: Date.now(),
        durationMinutes: 480,
        qualityRating: 4,
        snoozeCountUsed: 1,
        energyScore: 8,
        mood: "REFRESHED",
        readinessScore: 86,
        tags: ["Exercise", "No Caffeine"],
        notes: "Slept deeply after evening stretch.",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject invalid mood enum on SleepLog
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sleepLogs")
      .doc("log_bad")
      .set({
        userId: ALICE_UID,
        dateKey: "2026-11-25",
        bedtimeEpochMs: Date.now() - 8 * 3600 * 1000,
        wakeEpochMs: Date.now(),
        durationMinutes: 480,
        qualityRating: 4,
        snoozeCountUsed: 0,
        energyScore: 8,
        mood: "INVALID_MOOD",
        readinessScore: 86,
        tags: [],
        notes: "",
        createdAt: created,
        updatedAt: created,
      })
  );
});
