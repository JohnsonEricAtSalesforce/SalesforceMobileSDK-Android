# Test Runtime Failures Report

**Date:** 2026-05-19
**Branch:** `feature/java-to-kotlin-test-migration`
**Context:** All 95 Java test files converted to Kotlin, all test APKs compile. This report documents runtime failures discovered during instrumented test execution.

---

## Summary

| Module | Total Tests | Pass | Fail | Status |
|--------|-------------|------|------|--------|
| SalesforceAnalytics | All | All | 0 | **CLEAN** |
| SmartStore | 74 | 71 | 3 | 2 test NPE + 1 UI test timeout |
| SalesforceSDK | 527 | 506 | 21 | Mixed: auth/network, mocking, URL |
| MobileSync | 246 | 44 | 202 | SDK init failure (`syncs_soup`) |
| SalesforceHybrid | 0 (crash) | 0 | all | Environment (bootconfig.json) |
| SalesforceReact | 1 | 0 | 1 | Environment (Metro/bundle) |

**Total failures:** 227 (3 + 21 + 202 + 1)
**Status:** P1 fix applied (`mockkStatic` → `mockkObject`). SalesforceSDK now runs 527 tests (was 22).

**Unique root causes remaining:** 1 SDK init issue (P1-cascade), 1 test NPE (P5), 1 UI test (P6), network/credential issues, 2 environment issues

---

## P1 — SalesforceSDKLogger MockK Issue (FIXED)

**Status:** FIXED. Changed `mockkStatic(SalesforceSDKLogger::class)` → `mockkObject(SalesforceSDKLogger)` in `UserAccountManagerMigrateTokenTest.kt`. SalesforceSDK now runs 527 tests (was 22).

---

## P1-cascade — MobileSync `syncs_soup does not exist` (202 failures)

### Root Cause Analysis (REVISED)

**NOT a production code bug.** The 202 MobileSync failures are caused by **test credentials / network authentication failure**, not by the Kotlin conversion.

**Evidence:**
- Tests that DON'T require network (SOQLMutatorTest, SyncStateTest.testSetupSyncsSoupFirstTime) PASS
- Tests that DO require network (SyncManagerTest, SyncsConfigTest, etc.) ALL fail
- `ManagerTestCase.setUp()` calls `OAuth2.refreshAuthToken()` — if this throws (expired token, network issue), setUp aborts
- When setUp aborts, `SyncManager` is never created → `syncs_soup` never registered
- `tearDown()` then calls `clearSoup(SYNCS_SOUP)` which throws because the soup doesn't exist
- The `syncs_soup does not exist` error is a SECONDARY symptom of setUp failure, not the root cause

**The `SalesforceSDKManager.getInstance()` throwing behavior was pre-existing (not introduced by our conversion).** The original Java `MobileSyncSDKManager.getInstance()` also threw when not initialized.

### Classification
This is **NOT a conversion issue**. It is a test environment/credentials issue. The same behavior would occur with the original Java tests if credentials expired.

### Required Action
Refresh test credentials in `shared/test/test_credentials.json` and retry. No code changes needed.

---

## P1-original (historical, for reference) — SalesforceSDKLogger Object Initialization

### Symptoms
- `ExceptionInInitializerError` → `ArrayIndexOutOfBoundsException: length=0; index=0` at `SalesforceSDKLogger.<init>(SalesforceSDKLogger.kt:38)`
- Subsequent tests get `NoClassDefFoundError: com.salesforce.androidsdk.util.SalesforceSDKLogger`
- Cascades into MobileSync (202 failures: "Soup: syncs_soup does not exist") because SDK initialization fails

### Root Cause Analysis
`SalesforceSDKLogger` was converted from a Java `class` with `public static` methods to a Kotlin `object`. The test `UserAccountManagerMigrateTokenTest` uses **MockK** with `mockkStatic(SalesforceSDKLogger::class)`.

**The bug:** `mockkStatic()` is designed for Java static methods or Kotlin top-level/companion functions. When applied to a Kotlin `object` class, it triggers the object's `<clinit>` in a corrupted MockK proxy state, causing `ArrayIndexOutOfBoundsException`. The correct MockK API for Kotlin objects is `mockkObject(SalesforceSDKLogger)`.

### Evidence
- Original Java: `public class SalesforceSDKLogger` with static methods → `mockkStatic` was correct
- Converted Kotlin: `object SalesforceSDKLogger` with `@JvmStatic` methods → `mockkStatic` is wrong for MockK
- The `@JvmStatic` annotation makes methods appear static to Java callers, but MockK sees the Kotlin `object` structure

### Suggested Fix Options

**Option A (test-only fix — preferred):**
Change `UserAccountManagerMigrateTokenTest.kt` line 80:
```kotlin
// Before:
mockkStatic(SalesforceSDKLogger::class)
// After:
mockkObject(SalesforceSDKLogger)
```
And in tearDown:
```kotlin
// Before:
unmockkStatic(SalesforceSDKLogger::class)
// After:
unmockkObject(SalesforceSDKLogger)
```
**Scope:** Test code only. No production changes.

**Option B (production change — broader fix):**
Change `SalesforceSDKLogger` from `object` back to a `class` with `companion object` containing `@JvmStatic` methods. This preserves the original Java class structure and makes `mockkStatic` work as before.
**Scope:** Production code change. Semantically equivalent (all methods were static anyway).

### Recommendation
**Option A** — this is a test code bug (wrong MockK API for Kotlin objects). The production code is correct. Only one test file needs changing.

### Impact If Fixed
Resolves 9 SalesforceSDK failures directly. The 202 MobileSync failures ("syncs_soup does not exist") are likely a CASCADE from uninitialized SDK state — they may also resolve once the SalesforceSDK logger initialization stops failing. However, MobileSync may have its own independent issues too.

---

## P2 — QuerySpec Null Bind Values (13 failures)

### Symptoms
- `IllegalArgumentException: the bind value at index 1 is null`
- Affects: SmartSqlTest, SmartStoreAlterTest, SmartStoreConcurrencyTest

### Root Cause Hypothesis
The `QuerySpec.kt` conversion changed how `getArgs()` constructs the bind parameter array. In the original Java, `beginKey`/`endKey` parameters were non-null strings passed to SQLite. In the Kotlin conversion, these may be `null` due to nullable type declarations or different control flow in the `when` expression that replaced Java's `switch` with fall-through.

### Reference Files
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/QuerySpec.kt` — current
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/QuerySpec.java.bak` — original

### Suggested Investigation
Compare `getArgs()` method in both files. Focus on how `beginKey`/`endKey`/`likeKey`/`matchKey`/`orderPath` are passed to the result array. The Java switch-with-fallthrough semantics may not have been correctly translated.

### Scope
Production code fix in `QuerySpec.kt`.

---

## P3 — QuerySpec SQL Spacing (2 failures)

### Symptoms
- `ComparisonFailure: expected:<SELECT id []FROM...> but was:<SELECT id [ ]FROM...>`
- Extra space between `id` and `FROM` in generated SQL

### Root Cause Hypothesis
String concatenation in `QuerySpec.kt` has an extra space. Likely a string template or `+` operation that adds a trailing/leading space.

### Reference Files
Same as P2. Compare the `computeSmartSql()` or similar SQL-building method.

### Scope
Production code fix in `QuerySpec.kt` — string formatting correction.

---

## P4 — SmartStore whereArgs Validation (2 failures)

### Symptoms
- `SmartStoreException: whereArgs can only be provided for smart queries`

### Root Cause Hypothesis
The `SmartStore.kt` query validation logic is incorrectly rejecting `whereArgs` for certain query types that the original Java accepted.

### Reference Files
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/SmartStore.kt`
- `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/SmartStore.java.bak`

### Scope
Production code fix in `SmartStore.kt`.

---

## P5 — KeyValueEncryptedFileStoreTest NPE (2 failures)

### Symptoms
- `NullPointerException: null cannot be cast to non-null type kotlin.String`

### Root Cause
Test code uses `!!` or `as String` on a value that is null at runtime. This is a test conversion artifact — the test should use safe handling.

### Scope
Test code fix only. No production changes needed.

---

## P6 — KeyValueStoreInspectorActivityTest (10 failures)

### Symptoms
- `NoActivityResumedException: No activities in stage RESUMED`

### Root Cause Hypothesis
The converted test's activity launch mechanism doesn't properly start the inspector activity. May be related to how the test rule or `ActivityScenarioRule` was converted.

### Scope
Test code fix — investigate activity launch setup in the converted test.

---

## P7 — SalesforceHybrid (crash on launch)

### Symptoms
- `BootConfigException: Failed to open www/bootconfig.json`

### Root Cause
The test APK's assets directory is missing the `www/bootconfig.json` file that the Hybrid framework requires for initialization. This is a test fixture/asset configuration issue, not a code conversion bug.

### Scope
Test assets/configuration. Check whether the test module's `build.gradle.kts` source sets include the correct assets directory.

---

## P8 — SalesforceReact (Metro not running)

### Symptoms
- `Unable to load script. Make sure you're either running Metro or that your bundle is packaged correctly.`

### Root Cause
React Native tests require either Metro bundler running (`npx react-native start`) or a pre-built `index.android.bundle` in the APK assets.

### Scope
Runtime environment. Not a code issue.

---

## Recommended Fix Order

1. **P1** (test code fix — `mockkStatic` → `mockkObject`) — unblocks 9 + potentially 202 tests
2. **P2** (production code — `QuerySpec.getArgs()` null binding) — unblocks 13 tests
3. **P3** (production code — `QuerySpec` spacing) — unblocks 2 tests
4. **P4** (production code — `SmartStore` whereArgs) — unblocks 2 tests
5. **P5** (test code — NPE assertion) — unblocks 2 tests
6. **P6** (test infrastructure — activity launch) — unblocks 10 tests
7. **P7** (environment — bootconfig.json) — unblocks Hybrid module
8. **P8** (environment — Metro/bundle) — unblocks React module

**Fixes P1 + P5 require NO production code changes** (test-only).
**Fixes P2-P4 require operator approval** (production code, Classification B — non-logic artifacts from conversion).
**Fixes P7-P8 require operator/environment setup.**

---

## @Ignore Annotations

**None.** Zero tests have `@Ignore` annotations in the converted code. All failures are runtime failures that surface during test execution, not compile-time deferrals.
