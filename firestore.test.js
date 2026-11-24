const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const { test, before, after, beforeEach } = require('node:test');
const fs = require('node:fs');

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || 'demo-no-project';
const PUBLIC_UID = 'public_user_1';
const OTHER_UID = 'other_user_2';
const ENTRY_UID = 'entry_clerk_3';
const SUPERVISOR_UID = 'supervisor_4';
const ADMIN_EMAIL = 'jubayerhossaingalaxy@gmail.com';
const ADMIN_UID = 'platform_admin_5';
const OFFICE_ID = 'office_dhaka_sadar';
const OTHER_OFFICE_ID = 'office_chattogram_1';

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8085'
).split(':');
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, role = 'PUBLIC_USER', officeId = '') {
  const now = new Date(Date.now() - 2000);
  const payload = {
    userId: uid,
    fullName: 'Md. Karim Uddin',
    email: `${uid}@example.com`,
    role,
    status: 'ACTIVE',
    preferredLanguage: 'bn',
    themeMode: 'system',
    createdAt: now,
    updatedAt: now
  };
  if (officeId) {
    payload.organizationId = 'org_bd_land';
    payload.officeId = officeId;
    payload.officeName = 'Dhaka Sadar Sub-Registry';
  }
  return payload;
}

function validDeedPayload(createdBy, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    createdBy,
    updatedBy: createdBy,
    organizationId: 'org_bd_land',
    officeId: OFFICE_ID,
    officeName: 'Dhaka Sadar Sub-Registry',
    deedType: 'SAF_KABALA',
    deedNumber: '১২৫৬/২০২৪',
    normalizedDeedNumber: '1256/2024',
    deedYear: 2024,
    registrationDateMillis: 1715000000000,
    districtName: 'Dhaka',
    upazilaName: 'Savar',
    unionName: 'Birulia',
    mouzaName: 'Birulia',
    jlNumber: '42',
    status: 'DRAFT',
    visibility: 'PRIVATE_DRAFT',
    rowVersion: 1,
    primaryGrantorName: 'Abdul Karim',
    primaryGranteeName: 'Fatema Begum',
    totalAreaDecimalText: '12.50',
    totalAreaSortValue: 12.5,
    grantors: [
      {
        fullName: 'Abdul Karim',
        normalizedName: 'abdul karim',
        fatherName: 'Md. Rahim',
        motherName: 'Amena Khatun',
        spouseName: '',
        address: 'Savar, Dhaka'
      }
    ],
    grantees: [
      {
        fullName: 'Fatema Begum',
        normalizedName: 'fatema begum',
        fatherName: 'Md. Salam',
        motherName: 'Hasina Begum',
        spouseName: 'Kamal Hossain',
        address: 'Mirpur, Dhaka'
      }
    ],
    parcels: [
      {
        surveyType: 'RS',
        khatianNumber: '450',
        normalizedKhatian: '450',
        dagNumber: '1024',
        normalizedDag: '1024',
        landClass: 'NAL',
        landAreaDecimal: '12.50',
        landUnit: 'DECIMAL',
        boundaryDescription: 'North: Road, South: Canal',
        notes: ''
      }
    ],
    searchTokens: ['1256/2024', 'abdul karim', 'fatema begum', '450', '1024', 'birulia'],
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

before(async () => {
  const rules = fs.readFileSync('./firestore.rules', 'utf8');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort
    }
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

test('Unauthenticated users cannot read or write any collection', async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection('deeds').where('visibility', '==', 'PUBLIC').get());
  await assertFails(unauth.collection('users').doc(PUBLIC_UID).get());
  await assertFails(unauth.collection('deeds').doc('deed_1').set(validDeedPayload(PUBLIC_UID)));
});

test('User profile creation prevents self-role escalation and email spoofing', async () => {
  const publicDb = testEnv
    .authenticatedContext(PUBLIC_UID, { email: 'user@example.com', email_verified: true })
    .firestore();

  // Allowed: PUBLIC_USER self-registration
  await assertSucceeds(
    publicDb.collection('users').doc(PUBLIC_UID).set(validUserPayload(PUBLIC_UID, 'PUBLIC_USER'))
  );

  // Denied: Self-assigning OFFICE_SUPERVISOR or PLATFORM_ADMIN
  await assertFails(
    publicDb.collection('users').doc(OTHER_UID).set(validUserPayload(OTHER_UID, 'PLATFORM_ADMIN'))
  );

  // Denied: Spoofed admin email with email_verified = false
  const spoofDb = testEnv
    .authenticatedContext('spoof_uid', {
      email: ADMIN_EMAIL,
      email_verified: false
    })
    .firestore();
  await assertFails(
    spoofDb
      .collection('users')
      .doc('spoof_uid')
      .set(validUserPayload('spoof_uid', 'PLATFORM_ADMIN'))
  );

  // Denied: Self-updating role after creation
  await assertFails(
    publicDb
      .collection('users')
      .doc(PUBLIC_UID)
      .update({
        role: 'OFFICE_SUPERVISOR',
        updatedAt: new Date(Date.now() - 500)
      })
  );

  // Allowed: Self-updating language and theme preferences
  await assertSucceeds(
    publicDb
      .collection('users')
      .doc(PUBLIC_UID)
      .update({
        preferredLanguage: 'en',
        themeMode: 'dark',
        updatedAt: new Date(Date.now() - 500)
      })
  );
});

test('Public user cannot read private drafts or office-only records of other users', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection('deeds')
      .doc('public_approved')
      .set(validDeedPayload(ENTRY_UID, { status: 'APPROVED', visibility: 'PUBLIC' }));
    await db
      .collection('deeds')
      .doc('office_only_1')
      .set(validDeedPayload(ENTRY_UID, { status: 'APPROVED', visibility: 'OFFICE_ONLY' }));
    await db
      .collection('deeds')
      .doc('other_draft')
      .set(validDeedPayload(OTHER_UID, { status: 'DRAFT', visibility: 'PRIVATE_DRAFT' }));
  });

  const publicDb = testEnv.authenticatedContext(PUBLIC_UID).firestore();

  // Query alignment: public approved query succeeds
  await assertSucceeds(
    publicDb
      .collection('deeds')
      .where('visibility', '==', 'PUBLIC')
      .where('status', '==', 'APPROVED')
      .get()
  );

  // Unfiltered list on top-level deeds must fail
  await assertFails(publicDb.collection('deeds').get());

  // Cross-user private draft and office-only get must fail
  await assertFails(publicDb.collection('deeds').doc('office_only_1').get());
  await assertFails(publicDb.collection('deeds').doc('other_draft').get());

  // Public approved doc get succeeds
  await assertSucceeds(publicDb.collection('deeds').doc('public_approved').get());
});

test('Public user cannot submit APPROVED deed directly, can only save PRIVATE_DRAFT', async () => {
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID).firestore();

  await assertSucceeds(
    publicDb.collection('deeds').doc('my_draft_1').set(validDeedPayload(PUBLIC_UID))
  );

  await assertFails(
    publicDb
      .collection('deeds')
      .doc('my_fake_approved')
      .set(validDeedPayload(PUBLIC_UID, { status: 'APPROVED', visibility: 'PUBLIC' }))
  );

  // Shadow update with ghost field must fail
  await assertFails(
    publicDb
      .collection('deeds')
      .doc('my_draft_2')
      .set({
        ...validDeedPayload(PUBLIC_UID),
        isVerifiedByMinister: true
      })
  );

  // Reject storing @DocumentId `id` inside document body
  await assertFails(
    publicDb
      .collection('deeds')
      .doc('my_draft_3')
      .set({
        ...validDeedPayload(PUBLIC_UID),
        id: 'my_draft_3'
      })
  );
});

test('Office RBAC, optimistic locking rowVersion, soft delete, restore, and immutable audit logs', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection('users')
      .doc(ENTRY_UID)
      .set(validUserPayload(ENTRY_UID, 'OFFICE_DATA_ENTRY', OFFICE_ID));
    await db
      .collection('users')
      .doc(SUPERVISOR_UID)
      .set(validUserPayload(SUPERVISOR_UID, 'OFFICE_SUPERVISOR', OFFICE_ID));
  });

  const entryDb = testEnv.authenticatedContext(ENTRY_UID).firestore();
  const supDb = testEnv.authenticatedContext(SUPERVISOR_UID).firestore();

  // Entry clerk creates PENDING_REVIEW deed
  await assertSucceeds(
    entryDb
      .collection('deeds')
      .doc('office_deed_1')
      .set(
        validDeedPayload(ENTRY_UID, {
          status: 'PENDING_REVIEW',
          visibility: 'OFFICE_ONLY',
          officeViewerUids: [ENTRY_UID, SUPERVISOR_UID]
        })
      )
  );

  // Entry clerk cannot create directly in a different office
  await assertFails(
    entryDb
      .collection('deeds')
      .doc('cross_office_deed')
      .set(
        validDeedPayload(ENTRY_UID, {
          officeId: OTHER_OFFICE_ID,
          status: 'PENDING_REVIEW',
          visibility: 'OFFICE_ONLY'
        })
      )
  );

  // Entry clerk cannot approve record directly
  await assertFails(
    entryDb
      .collection('deeds')
      .doc('office_deed_1')
      .update({
        status: 'APPROVED',
        visibility: 'PUBLIC',
        rowVersion: 2,
        updatedBy: ENTRY_UID,
        updatedAt: new Date(Date.now() - 500)
      })
  );

  // Optimistic lock mismatch (rowVersion stays 1 instead of 2) must fail
  await assertFails(
    supDb
      .collection('deeds')
      .doc('office_deed_1')
      .update({
        status: 'APPROVED',
        visibility: 'PUBLIC',
        rowVersion: 1,
        updatedBy: SUPERVISOR_UID,
        updatedAt: new Date(Date.now() - 500)
      })
  );

  // Supervisor approves with rowVersion = 2
  await assertSucceeds(
    supDb
      .collection('deeds')
      .doc('office_deed_1')
      .update({
        status: 'APPROVED',
        visibility: 'PUBLIC',
        rowVersion: 2,
        updatedBy: SUPERVISOR_UID,
        updatedAt: new Date(Date.now() - 500)
      })
  );

  // Controlled soft deletion by supervisor (rowVersion = 3)
  await assertSucceeds(
    supDb
      .collection('deeds')
      .doc('office_deed_1')
      .update({
        status: 'SOFT_DELETED',
        visibility: 'OFFICE_ONLY',
        rowVersion: 3,
        deletedAt: new Date(Date.now() - 400),
        updatedBy: SUPERVISOR_UID,
        updatedAt: new Date(Date.now() - 400)
      })
  );

  // Hard delete is always forbidden
  await assertFails(supDb.collection('deeds').doc('office_deed_1').delete());

  // Restore by supervisor (rowVersion = 4)
  await assertSucceeds(
    supDb
      .collection('deeds')
      .doc('office_deed_1')
      .update({
        status: 'APPROVED',
        visibility: 'PUBLIC',
        rowVersion: 4,
        deletedAt: null,
        updatedBy: SUPERVISOR_UID,
        updatedAt: new Date(Date.now() - 300)
      })
  );

  // Audit log creation succeeds and is immutable
  await assertSucceeds(
    supDb
      .collection('audit_logs')
      .doc('log_1')
      .set({
        actorId: SUPERVISOR_UID,
        actorName: 'Supervisor Rahman',
        organizationId: 'org_bd_land',
        officeId: OFFICE_ID,
        action: 'APPROVE',
        entityType: 'DEED',
        entityId: 'office_deed_1',
        previousVersion: 1,
        newVersion: 2,
        summary: 'Approved deed 1256/2024',
        createdAt: new Date(Date.now() - 300)
      })
  );
  await assertFails(supDb.collection('audit_logs').doc('log_1').update({ action: 'CREATE' }));
  await assertFails(supDb.collection('audit_logs').doc('log_1').delete());
});
