const test = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
  getDocs,
  serverTimestamp
} = require('firebase/firestore');

const PROJECT_ID = 'gen-lang-client-0728630889';
const DB_ID = 'ai-studio-android-jiopanal-cb12b503-7e7b-483a-9507-067a970a1525';
const ADMIN_EMAIL = 'vishalsaini60223@gmail.com';

let testEnv;

function validUserPayload(uid, email, overrides = {}) {
  return {
    userId: uid,
    email,
    fullName: 'Partner Name',
    phone: '',
    city: '',
    businessName: '',
    status: 'INACTIVE',
    walletBalance: 0,
    commissionEarned: 0,
    totalDeposited: 0,
    referralCode: 'JP-USER-15',
    referredBy: '',
    loginCount: 1,
    isLoggedIn: true,
    lastLoginAt: serverTimestamp(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides
  };
}

function validPendingTxPayload(uid, email, type = 'ACTIVATION', overrides = {}) {
  return {
    userId: uid,
    userEmail: email,
    type,
    amount: 5000,
    commissionAmount: 750,
    utrOrRef: 'UTR12345678',
    title: 'Account Activation Deposit',
    subtitle: 'To vishalkumar90@upi',
    status: 'PENDING',
    paymentMethod: 'UPI',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides
  };
}

test.before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8085
    }
  });
});

test.after(async () => {
  await testEnv.cleanup();
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
});

test('1. Unauthenticated requests fail on all collections', async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DB_ID);
  await assertFails(getDoc(doc(unauthDb, 'users', 'userA')));
  await assertFails(getDoc(doc(unauthDb, 'admin_config', 'ADMIN_SINGLETON')));
  await assertFails(getDoc(doc(unauthDb, 'app_config', 'PUBLIC_SETTINGS')));
  await assertFails(getDocs(collection(unauthDb, 'transactions')));
  await assertFails(setDoc(doc(unauthDb, 'users', 'userA'), validUserPayload('userA', 'a@gmail.com')));
});

test('2. Owner user profile creation enforces INACTIVE & zero balances', async () => {
  const userADb = testEnv.authenticatedContext('userA', {
    email: 'usera@gmail.com',
    email_verified: true
  }).firestore(DB_ID);

  // Valid creation succeeds
  await assertSucceeds(
    setDoc(doc(userADb, 'users', 'userA'), validUserPayload('userA', 'usera@gmail.com'))
  );

  // Self-activation at creation must FAIL
  await assertFails(
    setDoc(
      doc(userADb, 'users', 'userA_bad'),
      validUserPayload('userA_bad', 'usera@gmail.com', { status: 'ACTIVE' })
    )
  );

  // Self-balance inflation at creation must FAIL
  const userBDb = testEnv.authenticatedContext('userB', {
    email: 'userb@gmail.com',
    email_verified: true
  }).firestore(DB_ID);
  await assertFails(
    setDoc(
      doc(userBDb, 'users', 'userB'),
      validUserPayload('userB', 'userb@gmail.com', { walletBalance: 5000 })
    )
  );

  // Spoofed email at creation must FAIL
  await assertFails(
    setDoc(
      doc(userBDb, 'users', 'userB'),
      validUserPayload('userB', 'victim@gmail.com')
    )
  );
});

test('3. Cross-user isolation and privilege escalation prevention', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminBypass = ctx.firestore(DB_ID);
    await setDoc(doc(adminBypass, 'users', 'userA'), validUserPayload('userA', 'usera@gmail.com'));
    await setDoc(doc(adminBypass, 'users', 'userB'), validUserPayload('userB', 'userb@gmail.com'));
  });

  const userADb = testEnv.authenticatedContext('userA', {
    email: 'usera@gmail.com',
    email_verified: true
  }).firestore(DB_ID);

  // Cross-user read & list fail
  await assertFails(getDoc(doc(userADb, 'users', 'userB')));
  await assertFails(getDocs(collection(userADb, 'users')));

  // Scoped owner list succeeds
  await assertSucceeds(getDocs(query(collection(userADb, 'users'), where('userId', '==', 'userA'))));

  // Owner safe profile update succeeds
  await assertSucceeds(
    updateDoc(doc(userADb, 'users', 'userA'), {
      fullName: 'Updated Partner',
      phone: '9876543210',
      updatedAt: serverTimestamp()
    })
  );

  // Owner attempting to self-activate or inflate balance must FAIL
  await assertFails(
    updateDoc(doc(userADb, 'users', 'userA'), {
      status: 'ACTIVE',
      updatedAt: serverTimestamp()
    })
  );
  await assertFails(
    updateDoc(doc(userADb, 'users', 'userA'), {
      walletBalance: 99999,
      updatedAt: serverTimestamp()
    })
  );

  // Owner cannot delete account
  await assertFails(deleteDoc(doc(userADb, 'users', 'userA')));
});

test('4. Transaction state machine prevents non-admin SUCCESS minting', async () => {
  const userADb = testEnv.authenticatedContext('userA', {
    email: 'usera@gmail.com',
    email_verified: true
  }).firestore(DB_ID);

  // Pending activation succeeds
  await assertSucceeds(
    setDoc(doc(userADb, 'transactions', 'tx1'), validPendingTxPayload('userA', 'usera@gmail.com', 'ACTIVATION'))
  );

  // Pending withdrawal succeeds
  await assertSucceeds(
    setDoc(
      doc(userADb, 'transactions', 'tx_wd'),
      validPendingTxPayload('userA', 'usera@gmail.com', 'WITHDRAWAL', {
        amount: 500,
        commissionAmount: 0,
        title: 'Withdrawal Request'
      })
    )
  );

  // Creating pre-approved SUCCESS deposit by user must FAIL
  await assertFails(
    setDoc(
      doc(userADb, 'transactions', 'tx_hack'),
      validPendingTxPayload('userA', 'usera@gmail.com', 'DEPOSIT', { status: 'SUCCESS' })
    )
  );

  // User approving own transaction must FAIL
  await assertFails(
    updateDoc(doc(userADb, 'transactions', 'tx1'), {
      status: 'SUCCESS',
      updatedAt: serverTimestamp()
    })
  );

  // Unscoped transaction list by regular user must FAIL; scoped list succeeds
  await assertFails(getDocs(collection(userADb, 'transactions')));
  await assertSucceeds(
    getDocs(query(collection(userADb, 'transactions'), where('userId', '==', 'userA')))
  );
});

test('5. Email spoofing test: unverified admin email cannot access admin vault or list all users', async () => {
  const spoofedAdminDb = testEnv.authenticatedContext('attacker', {
    email: ADMIN_EMAIL,
    email_verified: false
  }).firestore(DB_ID);

  await assertFails(getDoc(doc(spoofedAdminDb, 'admin_config', 'ADMIN_SINGLETON')));
  await assertFails(getDocs(collection(spoofedAdminDb, 'users')));
  await assertFails(getDocs(collection(spoofedAdminDb, 'transactions')));
});

test('6. Verified Admin can list all users/transactions, manage vault, and approve deposits', async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const bypass = ctx.firestore(DB_ID);
    await setDoc(doc(bypass, 'users', 'userA'), validUserPayload('userA', 'usera@gmail.com'));
    await setDoc(doc(bypass, 'transactions', 'tx1'), validPendingTxPayload('userA', 'usera@gmail.com'));
  });

  const adminDb = testEnv.authenticatedContext('admin-uid', {
    email: ADMIN_EMAIL,
    email_verified: true
  }).firestore(DB_ID);

  await assertSucceeds(
    setDoc(doc(adminDb, 'admin_config', 'ADMIN_SINGLETON'), {
      adminPasskey: 'JIOADMIN2026',
      commissionPoolBalance: 200000,
      commissionRatePercent: 15,
      activationAmount: 5000,
      merchantUpiId: 'vishalkumar90@upi',
      telegramHandle: '@Sainishaab9076',
      updatedAt: serverTimestamp()
    })
  );

  await assertSucceeds(getDocs(collection(adminDb, 'users')));
  await assertSucceeds(getDocs(collection(adminDb, 'transactions')));
  await assertSucceeds(
    updateDoc(doc(adminDb, 'transactions', 'tx1'), {
      status: 'SUCCESS',
      updatedAt: serverTimestamp()
    })
  );
  await assertSucceeds(
    updateDoc(doc(adminDb, 'users', 'userA'), {
      status: 'ACTIVE',
      walletBalance: 5750,
      commissionEarned: 750,
      totalDeposited: 5000,
      updatedAt: serverTimestamp()
    })
  );
});
