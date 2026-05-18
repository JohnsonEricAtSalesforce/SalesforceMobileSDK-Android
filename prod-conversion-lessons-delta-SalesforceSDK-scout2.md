# Delta Lessons — SalesforceSDK Scout 2 (Batch 07)

**Date:** 2026-05-18
**Batch:** 07 (config/app utility files)
**Files converted:** 6

## New Patterns Discovered

### Pattern: `object` for constants-only classes
- `Features.java` (all `public static final String` fields, no methods, no state) converts to Kotlin `object` with `const val`.
- `const val` in an `object` compiles to `public static final` fields on the class — no `@JvmField` needed (it's redundant on `const val`).
- Existing Kotlin imports like `import com.salesforce.androidsdk.app.Features.FEATURE_X` work directly with object members.
- Java callers access via `Features.FEATURE_X` unchanged.

### Pattern: Abstract class hierarchy with subclassing
- `AbstractPrefsManager` is `abstract` (implies open) — no `open` keyword needed.
- `AdminSettingsManager` needs explicit `open` because `LegacyAdminSettingsManager.kt` extends it.
- `AdminPermsManager` is NOT subclassed, so no `open` needed.
- Override functions of abstract/open functions are implicitly open — subclasses can override without extra annotation.

### Pattern: Singleton with static factory (not thread-safe)
- `RuntimeConfig` uses a non-thread-safe singleton pattern with `INSTANCE` variable.
- Converted to companion object with `@Volatile` on INSTANCE and `@JvmStatic` on `getRuntimeConfig()`.
- Kept the existing non-synchronized pattern to match original behavior exactly.

### Pattern: `final` class with `Comparable`
- `SdkVersion` is `final` in Java — Kotlin classes are final by default, so no annotation needed.
- `Comparable<SdkVersion>` interface: `compareTo` parameter is non-null in Kotlin.
- Removed the `null` check from `compareTo` (was `if (o == null) return -1`) since Kotlin enforces non-null at call site.
- Used `@JvmField` on constructor properties to preserve direct field access for Java callers.
- Used `@JvmOverloads` on constructor to allow calling without `isDev` parameter.

### Pattern: Kotlin `String.split()` vs Java `String.split()`
- Java `String.split("\\.")` uses regex.
- Kotlin `String.split(".")` splits by literal string (not regex).
- Kotlin `String.split(Regex("\\."))` for regex splitting.
- In `SdkVersion.parseFromString`, used literal `"."` since that's the actual delimiter.

### Pattern: `@JvmField` on companion `const val` is redundant
- `const val` in companion object already compiles to `public static final` on enclosing class.
- `@JvmField` can still be used without error but adds no value for `const val`.
- Decision: Keep `@JvmField` on companion `const val` in `AdminPermsManager`/`AdminSettingsManager` per rule 15 (explicit documentation of Java interop intent), but omit from `object` members where `const val` suffices.

## Issues / Observations for Parallel Track Agents

1. **Pre-existing build failure in SalesforceAnalytics**: `SalesforceLogger.kt` has a platform declaration clash (`logLevel` property setter vs `setLogLevel()` function). This blocks full compilation verification of SalesforceSDK. Must be fixed before track agents can validate.

2. **LoginServerManager.java** (Batch 08) uses `static import` of `RuntimeConfig.ConfigKey.AppServiceHosts` and `RuntimeConfig.getRuntimeConfig`. These work correctly with the companion object pattern (Java static imports of `@JvmStatic` companions work).

3. **BootConfig.java** references `RuntimeConfig.ConfigKey` and `RuntimeConfig.getRuntimeConfig()` — same static import pattern, works with companion.

4. **`dir.listFiles()` null safety**: The original Java code in `AbstractPrefsManager.resetAll()` did not null-check `listFiles()`. Kotlin conversion adds proper null safety since `File.listFiles()` returns `Array<File>?`.

## Rules Applied
- Rule 2: `open` on `AdminSettingsManager` for subclassing
- Rule 8: `const val` in companion for static final constants
- Rule 9: `enum class ConfigKey` for Java enum
- Rule 11: `object Features` for stateless utility class
- Rule 14: `@JvmStatic` on `RuntimeConfig.getRuntimeConfig`
- Rule 15: `@JvmField` on companion constants (`FILENAME_ROOT`)
- Rule 17: `@Throws(IllegalArgumentException::class)` on `SdkVersion` constructor and `parseFromString`
- Rule 22: ternary → if/else expressions, instanceof → is, .class → ::class.java
