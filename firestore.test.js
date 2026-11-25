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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("assistants").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("prompts").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("usageLogs").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      totalChats: 0,
      totalMessages: 0,
      totalImages: 0,
      totalTokensUsed: 0,
      dailyQuotaDate: "2026-11-25",
      dailyRequestsUsed: 0,
      dailyRequestLimit: 50,
      dailyTokensUsed: 0,
      preferredChatModel: "gemini-3.5-flash-lite",
      preferredTemperature: 0.7,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("UserProfile: create, daily quota limits, token updates, and tamper protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "سارا محمدی",
      email: "sara@example.com",
      photoUrl: "https://example.com/avatar.jpg",
      totalChats: 0,
      totalMessages: 0,
      totalImages: 0,
      totalTokensUsed: 0,
      dailyQuotaDate: "2026-11-25",
      dailyRequestsUsed: 0,
      dailyRequestLimit: 50,
      dailyTokensUsed: 0,
      preferredChatModel: "gemini-3.5-flash-lite",
      preferredTemperature: 0.7,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Consume quota unit within daily limit
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyQuotaDate: "2026-11-25",
      dailyRequestsUsed: 1,
      dailyTokensUsed: 0,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Update message and token counters
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalMessages: 1,
      totalTokensUsed: 340,
      dailyTokensUsed: 340,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject exceeding dailyRequestLimit
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyRequestsUsed: 51,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject client attempting to raise its own dailyRequestLimit
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyQuotaDate: "2026-11-25",
      dailyRequestsUsed: 1,
      dailyRequestLimit: 500,
      dailyTokensUsed: 0,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject removed legacy models
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredChatModel: "gemini-4-argon",
      updatedAt: new Date(Date.now() - 350),
    })
  );

  // Reject out-of-range temperature
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredTemperature: 3.5,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Conversations & Messages: create with gemini-3.5-flash-lite, pin, rename, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Reject deprecated/removed models on conversation creation
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_bad")
      .set({
        userId: ALICE_UID,
        title: "مدل قدیمی",
        modelName: "gemini-3.8-flash",
        personaId: "general",
        temperature: 0.7,
        pinned: false,
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "معماری میکروسرویس",
        modelName: "gemini-3.5-flash-lite",
        personaId: "coder",
        temperature: 0.4,
        pinned: false,
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .update({
        pinned: true,
        updatedAt: new Date(Date.now() - 4000),
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        conversationId: "conv_1",
        role: "model",
        text: "پاسخ فنی معماری...",
        modelName: "gemini-3.5-flash-lite",
        hasImageAttachment: false,
        promptTokens: 120,
        completionTokens: 250,
        totalTokens: 370,
        latencyMs: 1420,
        createdAt: new Date(Date.now() - 2000),
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .get()
  );
});

test("Custom Assistants, Saved Prompts & UsageLogs: CRUD and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("assistants")
      .doc("asst_1")
      .set({
        userId: ALICE_UID,
        name: "معمار اندروید",
        roleDescription: "متخصص Jetpack Compose و کاتلین",
        systemPrompt: "همیشه کدهای استاندارد و بهینه ارائه بده.",
        modelName: "gemini-3.5-flash-lite",
        temperature: 0.5,
        usageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("prompts")
      .doc("prm_1")
      .set({
        userId: ALICE_UID,
        title: "تحلیل کد",
        category: "برنامه‌نویسی",
        content: "این کد را از نظر امنیت و کارایی بررسی کن.",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("usageLogs")
      .doc("log_1")
      .set({
        userId: ALICE_UID,
        serviceType: "chat",
        modelName: "gemini-3.5-flash-lite",
        promptTokens: 85,
        completionTokens: 210,
        totalTokens: 295,
        latencyMs: 980,
        createdAt: created,
      })
  );

  // Reject retired image service type in UsageLogs
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("usageLogs")
      .doc("log_bad")
      .set({
        userId: ALICE_UID,
        serviceType: "image",
        modelName: "gemini-nano-banana-2.1",
        promptTokens: 0,
        completionTokens: 0,
        totalTokens: 0,
        latencyMs: 1000,
        createdAt: created,
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("assistants")
      .doc("asst_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("usageLogs")
      .doc("log_1")
      .get()
  );
});
