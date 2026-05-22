# Deferred Follow-Up: SalesforceHybrid & SalesforceReact Test Verification

**Date:** 2026-05-22
**Branch:** `feature/java-to-kotlin-test-migration`
**Context:** The Java→Kotlin test conversion is complete. All test code compiles and builds. Four modules pass all instrumented tests on device. Two modules (SalesforceHybrid, SalesforceReact) are blocked by environment/build configuration issues that prevent on-device test execution.

---

## P7: SalesforceHybrid — Missing `www/bootconfig.json`

### Symptom
```
RuntimeException: Unable to start activity ComponentInfo{.../SalesforceHybridTestActivity}:
  BootConfigException: Failed to open www/bootconfig.json
```

### Root Cause
The Hybrid test app (`SalesforceHybridTestActivity`) extends a Cordova activity that requires `www/bootconfig.json` in the APK's assets directory. The test APK doesn't include this file.

### Fix Required
1. Create `libs/test/SalesforceHybridTest/assets/www/bootconfig.json` with appropriate test configuration (consumer key, callback URL, login server, etc.)
2. Verify the test module's `build.gradle.kts` source set includes this assets directory
3. Rebuild and run: `./gradlew :libs:SalesforceHybrid:connectedAndroidTest`

### Verification Command
```bash
adb -s emulator-5556 install -r libs/SalesforceHybrid/build/outputs/apk/androidTest/debug/SalesforceHybrid-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w \
  com.salesforce.androidsdk.salesforcehybrid.tests/androidx.test.runner.AndroidJUnitRunner
```

### Notes
- The Hybrid tests use a `JSTestCase` base class that runs JavaScript test suites in a WebView
- Tests must be run as full classes (not individual methods) due to suite-level JS initialization
- This is NOT a conversion issue — the same test assets were needed before conversion

---

## P8: SalesforceReact — Missing Hermes Native Library

### Symptom
```
SoLoaderDSONotFoundError: couldn't find DSO to load: libhermes.so
```
Followed by process crash.

### Root Cause
React Native 0.81.5 defaults to the Hermes JavaScript engine. The test APK includes `libjsc.so` (JavaScriptCore) via `androidTestImplementation("org.webkit:android-jsc:+")` but does NOT include `libhermes.so`. When React Native's `ReactInstanceManagerBuilder` tries to load Hermes, it fails with `UnsatisfiedLinkError`, returns a null executor factory, and the React Native bridge crashes.

### Fix Required
Add the Hermes runtime dependency to `libs/SalesforceReact/build.gradle.kts`:

```kotlin
androidTestImplementation("com.facebook.react:hermes-android:0.81.5")
```

Then rebuild and run:
```bash
./gradlew :libs:SalesforceReact:assembleDebugAndroidTest
```

### Alternative Fix
If the team wants to stay on JSC, provide an explicit `JSCExecutorFactory` (if one exists for RN 0.81.5) or downgrade React Native to a version that defaults to JSC. However, Hermes is the recommended engine for RN 0.70+.

### Pre-requisites
- `yarn install` must be run in `libs/SalesforceReact/` (not `npm install`) to resolve `react-native-force` git dependency with test fixtures
- The JS test bundle must be generated (either by the Gradle `buildReactTestBundle` task or manually):
  ```bash
  cd libs/SalesforceReact
  node node_modules/react-native/cli.js bundle \
    --platform android --dev true \
    --entry-file node_modules/react-native-force/test/alltests.js \
    --bundle-output ../test/SalesforceReactTest/assets/index.android.bundle \
    --assets-dest ../test/SalesforceReactTest/assets
  ```

### Verification Command
```bash
adb -s emulator-5556 install -r libs/SalesforceReact/build/outputs/apk/androidTest/debug/SalesforceReact-debug-androidTest.apk
adb -s emulator-5556 shell am instrument -w \
  com.salesforce.androidsdk.salesforcereact.tests/androidx.test.runner.AndroidJUnitRunner
```

### Notes
- The 13 converted Kotlin test files compile cleanly — no code changes needed
- The JS bundle generates correctly with `yarn install` + manual bundle command
- This is NOT a conversion issue — it's a React Native version/build configuration gap
- The tests use a host-activity pattern where each `@Test` method launches a React Native activity that executes one JS test via the bridge

---

## Scope

Both P7 and P8 are **environment/build configuration issues**, not Java→Kotlin conversion bugs. The converted Kotlin test code is correct and compiles. These items require:
- Build system expertise (Cordova assets, React Native Hermes dependency)
- Possible `build.gradle.kts` modifications (outside conversion plan scope)
- Operator/team decision on test infrastructure approach

These should be resolved as a separate task from the migration, ideally by someone familiar with the Hybrid/React Native test infrastructure.
