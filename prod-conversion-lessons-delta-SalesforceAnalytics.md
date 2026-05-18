# Production Kotlin Conversion - Delta Notes (SalesforceAnalytics)

## Batch 01: Logger + Util

### SalesforceLogger.kt
- Marked class as `open` since FileLogger subclass pattern exists in other libraries.
- Used `@JvmStatic` on all companion object methods that were public static in Java.
- Preserved the `ConcurrentHashMap` for thread safety.
- The `redact()` method calls `LogRedactorKt.redactSensitiveData()` via direct Kotlin extension syntax (`message.redactSensitiveData()`).
- `Level` enum: exposed `severity` as a public property (was accessed via `.severity` field in Java).
- Interaction with existing `SalesforceLogReceiver.kt` and `SalesforceLogReceiverFactory.kt` interfaces preserved cleanly.

### FileLogger.kt
- `@Throws(IOException::class)` on constructor for Java interop.
- `@JvmStatic` + `@Synchronized` on `resetFileLoggerPrefs` companion method.
- `commit()` preserved (not `apply()`) as the original Java used commit for file logger prefs.

### SalesforceAnalyticsLogger.kt
- Converted to Kotlin `object` (singleton) since all methods were static and no instance state.
- All public methods annotated with `@JvmStatic` for Java callers.
- `context` parameter is nullable (`Context?`) since callers pass `null` in several places.

### WatchableStream.kt
- Simple interface conversion. No JVM interop annotations needed.

## Batch 02: Model Classes

### InstrumentationEvent.kt
- Used `internal` visibility on the primary constructor to match Java package-private constructor.
- Public secondary constructor from JSONObject preserved for deserialization.
- All `public static final String` fields use `@JvmField` in companion object.
- Properties are `val` (read-only) matching the Java getter-only pattern.
- Preserved nullable types for optional fields (attributes, sessionId, senderContext, etc.).

### InstrumentationEventBuilder.kt
- `getInstance` factory method uses `@JvmStatic`.
- `@Throws(EventBuilderException::class)` on `buildEvent()`.
- `@Suppress("DEPRECATION")` for `ConnectivityManager.getActiveNetworkInfo()` usage (matches original Java behavior).
- `EventBuilderException` is a nested class with `serialVersionUID`.

### DeviceAppAttributes.kt
- Two constructors: one taking individual parameters, one taking JSONObject.
- All properties are `val` (immutable after construction).
- Nullable types throughout since JSON deserialization can yield empty strings.

## Batch 03: Security, Manager, Store, Transform

### Encryptor.kt (SECURITY-CRITICAL)
- Converted to Kotlin `object` (all methods static, no state).
- All public methods annotated with `@JvmStatic`.
- `@Throws` annotations on crypto methods that throw checked exceptions.
- Thread safety preserved: no mutable state exists (all methods are pure functions).
- `CipherMode` enum uses a `fullName` property (was package-private field in Java).
- All byte array operations, System.arraycopy, and cipher initialization logic preserved exactly.
- No synchronized blocks existed in the original (confirmed stateless utility).

### AnalyticsManager.kt
- `globalSequenceId` uses `@get:Synchronized` and `@set:Synchronized`.
- `deviceAppAttributes` and `eventStoreManager` are public `val` properties.
- Constructor initializes Paper and creates EventStoreManager.

### EventStoreManager.kt
- `countEvents` is `Int?` (null = not yet calculated), matches Java's Integer null pattern.
- `@Synchronized` on `enableLogging` and `isLoggingEnabled`.
- Book initialization in constructor body.

### Transform.kt
- Simple interface. No JVM annotations needed.

### AILTNTransform.kt
- Implements `Transform` interface.
- All string constants are `private const val` in companion object.
- Nullable `JSONObject?` patterns preserved for error-case null assignment.

## General Notes
- No existing .kt files were modified (LogRedactor.kt, SalesforceLogReceiver.kt, SalesforceLogReceiverFactory.kt left untouched).
- No build.gradle.kts files were modified.
- All Salesforce copyright headers preserved with original years.
