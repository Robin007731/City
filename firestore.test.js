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

function validCityPayload(uid, nowMs = Date.now() - 1000) {
  const ts = new Date(nowMs);
  return {
    userId: uid,
    cityName: "Aldea Inicial",
    coins: 500.0,
    civ: 10.0,
    gameTime: 12.5,
    era: 0,
    exp: 0,
    tax: 10,
    spend: 100,
    bless: 0,
    logEntries: ["Tres familias fundaron la aldea."],
    cells: [
      { x: 33, y: 35, t: "road", k: "", s: 0.0, p: 1.0, dur: 0.0, auto: false, nm: "", br: false, rx: "", st: false, big: 0, ox: 0, oy: 0, ax: 0, ay: 0 },
      { x: 33, y: 34, t: "house", k: "", s: 0.42, p: 1.0, dur: 0.0, auto: false, nm: "", br: false, rx: "", st: false, big: 0, ox: 0, oy: 0, ax: 0, ay: 0 },
    ],
    createdAt: ts,
    updatedAt: ts,
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

test("Unauthenticated user cannot read or write cities", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cities").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("cities")
      .doc("main_city")
      .set(validCityPayload(ALICE_UID))
  );
});

test("Owner can create, read, list, and partially update own city", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cityRef = aliceDb.collection("users").doc(ALICE_UID).collection("cities").doc("main_city");
  const initial = validCityPayload(ALICE_UID, Date.now() - 5000);

  await assertSucceeds(cityRef.set(initial));
  await assertSucceeds(cityRef.get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("cities").get());

  await assertSucceeds(
    cityRef.update({
      cityName: "Metrópolis Solar",
      coins: 245.5,
      civ: 120.0,
      gameTime: 90.0,
      era: 1,
      exp: 6,
      tax: 14,
      spend: 120,
      bless: 3,
      logEntries: ["Nueva era: Pueblo.", "Tres familias fundaron la aldea."],
      cells: [{ x: 33, y: 35, t: "road", k: "", s: 0.0, p: 1.0, dur: 0.0, auto: false, nm: "", br: false, rx: "", st: false, big: 0, ox: 0, oy: 0, ax: 0, ay: 0 }],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Cross-user isolation: Alice cannot read, list, update, or delete Bob's city", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("cities")
      .doc("main_city")
      .set(validCityPayload(BOB_UID, Date.now() - 5000));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobRef = aliceDb.collection("users").doc(BOB_UID).collection("cities").doc("main_city");

  await assertFails(bobRef.get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("cities").get());
  await assertFails(bobRef.set(validCityPayload(BOB_UID)));
  await assertFails(bobRef.update({ coins: 99999, updatedAt: new Date(Date.now() - 500) }));
  await assertFails(bobRef.delete());
});

test("Rejects ghost fields, @DocumentId id field, immutable createdAt changes, and invalid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb.collection("users").doc(ALICE_UID).collection("cities");

  // Extra @DocumentId 'id' or ghost field fails create
  const withGhost = { ...validCityPayload(ALICE_UID), id: "main_city" };
  await assertFails(col.doc("ghost_city").set(withGhost));

  // Future timestamp fails
  const futurePayload = validCityPayload(ALICE_UID, Date.now() + 60000);
  await assertFails(col.doc("future_city").set(futurePayload));

  // Out-of-bounds era and tax fail
  const badEra = { ...validCityPayload(ALICE_UID), era: 9 };
  await assertFails(col.doc("bad_era").set(badEra));
  const badTax = { ...validCityPayload(ALICE_UID), tax: 35 };
  await assertFails(col.doc("bad_tax").set(badTax));
  const badSpend = { ...validCityPayload(ALICE_UID), spend: 20 };
  await assertFails(col.doc("bad_spend").set(badSpend));

  // Valid create followed by invalid createdAt mutation fails
  const ref = col.doc("main_city");
  await assertSucceeds(ref.set(validCityPayload(ALICE_UID, Date.now() - 5000)));
  await assertFails(
    ref.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    ref.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
