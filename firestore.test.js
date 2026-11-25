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
      preferredChatModel: "gemini-3.8-flash",
      preferredTemperature: 0.7,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("UserProfile: create, token & temperature updates, and security bounds", async () => {
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
      preferredChatModel: "gemini-3.8-flash",
      preferredTemperature: 0.7,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalMessages: 1,
      totalTokensUsed: 340,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredTemperature: 1.1,
      updatedAt: new Date(Date.now() - 500),
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

test("Conversations & Messages: pin, rename, params update, and telemetry fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "معماری میکروسرویس",
        modelName: "gemini-4-argon",
        personaId: "coder",
        temperature: 0.4,
        pinned: false,
        lastMessagePreview: "",
        messageCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Pin and rename
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
      .update({
        title: "معماری نهایی",
        updatedAt: new Date(Date.now() - 3000),
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
        modelName: "gemini-4-argon",
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

test("Custom Assistants, Saved Prompts, Image Creations & UsageLogs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("assistants").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("prompts").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("usageLogs").get()
  );

  // Custom Assistant CRUD
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
        modelName: "gemini-3.8-flash",
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
      .collection("assistants")
      .doc("asst_1")
      .update({
        usageCount: 1,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Saved Prompt CRUD
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

  // Usage Log
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("usageLogs")
      .doc("log_1")
      .set({
        userId: ALICE_UID,
        serviceType: "chat",
        modelName: "gemini-3.8-flash",
        promptTokens: 85,
        completionTokens: 210,
        totalTokens: 295,
        latencyMs: 980,
        createdAt: created,
      })
  );

  // Image Creation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("imageCreations")
      .doc("img_1")
      .set({
        userId: ALICE_UID,
        prompt: "نمای شبانه برج مدرن",
        negativePrompt: "تاری، نویز",
        stylePreset: "واقع‌گرایانه",
        aspectRatio: "16:9",
        modelName: "gemini-nano-banana-2.1",
        localFileName: "img_1.png",
        editedFromSource: false,
        latencyMs: 18500,
        createdAt: created,
      })
  );

  // Bob isolation check
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
