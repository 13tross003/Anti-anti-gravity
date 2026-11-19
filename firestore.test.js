const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user cannot read or write user profiles, workspaces, sessions, or artifacts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("workspaces").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("artifacts").get());
});

test("Authenticated owner can create and update AgentProfile while blocking ghost fields and immutable tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Dev",
      preferredModel: "gemini-3.8-flash",
      agentMode: "PLANNING",
      readOnlyMode: false,
      enabledTools: ["READ_FILE", "WRITE_FILE", "RUN_COMMAND"],
      blockDestructiveHooks: true,
      darkTheme: true,
      browserAllowlist: ["antigravity.google", "localhost"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredModel: "gemini-4-argon",
      darkTheme: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkTheme: true,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijacked", updatedAt: new Date(Date.now() - 1000) }));
});

test("Workspaces, nested files, sessions, messages, and artifacts enforce parent gates and schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Orphaned file creation without parent workspace must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1").collection("files").doc("f_1").set({
      userId: ALICE_UID,
      workspaceId: "ws_1",
      path: "main.py",
      language: "python",
      content: "print('hi')",
      previousContent: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create workspace
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1").set({
      userId: ALICE_UID,
      name: "antigravity-sdk-starter",
      description: "Google Antigravity SDK quickstart workspace",
      language: "Python",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create file inside existing workspace
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1").collection("files").doc("f_1").set({
      userId: ALICE_UID,
      workspaceId: "ws_1",
      path: "agent_quickstart.py",
      language: "python",
      content: "from google.antigravity import Agent",
      previousContent: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update file content
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workspaces").doc("ws_1").collection("files").doc("f_1").update({
      previousContent: "from google.antigravity import Agent",
      content: "from google.antigravity import Agent, LocalAgentConfig",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create session & message
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      userId: ALICE_UID,
      workspaceId: "ws_1",
      workspaceName: "antigravity-sdk-starter",
      title: "Configure pre_turn safety hook",
      status: "NEEDS_REVIEW",
      mode: "PLANNING",
      modelName: "gemini-3.8-flash",
      summary: "Plan ready for approval",
      tasksTotal: 3,
      tasksCompleted: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      sessionId: "sess_1",
      role: "USER",
      content: "Add a pre_turn hook to block rm -rf",
      toolName: "",
      toolStatus: "NONE",
      artifactId: "",
      createdAt: now,
    })
  );

  // Create & approve artifact
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("artifacts").doc("art_1").set({
      userId: ALICE_UID,
      workspaceId: "ws_1",
      sessionId: "sess_1",
      title: "Implementation Plan",
      kind: "PLAN",
      content: "# Plan\n- Add @hooks.pre_turn",
      approvalStatus: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("artifacts").doc("art_1").update({
      approvalStatus: "APPROVED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/list rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("workspaces").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("artifacts").get());
});
