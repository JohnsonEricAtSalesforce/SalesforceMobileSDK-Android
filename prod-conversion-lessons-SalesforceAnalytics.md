# Production Conversion Lessons — Through SalesforceAnalytics

## Cumulative API Migration Patterns
- Java `static` members → Kotlin `companion object` with `@JvmStatic`/`@JvmField` for Java callers
- Interface conversions need no JVM interop annotations (interfaces are naturally compatible)
- `object` declarations work well for stateless utility classes (e.g., Encryptor)

## JVM Interop Patterns
- `@JvmStatic` on all public companion methods that were `public static` in Java
- `@JvmField` on companion constants that Java callers access as fields
- `@Throws(Exception::class)` on any function that throws checked exceptions callable from Java
- `@JvmOverloads` for constructors/functions with default parameters

## Conversion Pitfalls Discovered
1. **PaperDB type inference**: `book.write(key, value)` fails when `value` is `String?` — PaperDB requires non-null. Solution: null-check before write, or use explicit type parameter `book.write<String>(key, value)` only if value is proven non-null.
2. **Nullable assignment to non-null var**: When a var is initialized from a non-null expression but later assigned `null` in a catch block, declare the type as nullable from the start: `var x: Type? = nonNullInit()`.
3. **`encrypt()` returns nullable**: The Encryptor methods return `String?` — callers must handle null before passing to APIs that expect non-null.

## Kotlin Idiom Preferences
- Use `object` for stateless utility classes (no instance state, all methods are pure functions)
- Use `open class` only when the class is subclassed within the SDK
- Preserve `@Suppress("DEPRECATION")` when wrapping deprecated Android APIs that must stay for compatibility
- Keep `synchronized` annotations (`@get:Synchronized`, `@set:Synchronized`) for thread-safe property access

## Compiler-Discovered Corrections
- EventStoreManager: PaperDB `write()` needs non-null value — added null check on encrypt result
- EventStoreManager: PaperDB `read()` needs explicit type parameter
- AILTNTransform: `var logLine` must be declared `JSONObject?` since catch block assigns null

## Existing Kotlin Integration Notes
- 3 existing Kotlin files (LogRedactor.kt, SalesforceLogReceiver.kt, SalesforceLogReceiverFactory.kt) compiled as-is without any changes
- SalesforceLogReceiverFactory references SalesforceLogger — compatible after conversion
- SalesforceLogReceiver uses SalesforceLogger.Level enum — compatible after conversion

## Gradle Build Notes
- `kotlin-android` plugin already present in `build.gradle.kts`
- No build.gradle.kts changes needed
- Language version 2.0 deprecation warning present (pre-existing, not introduced by conversion)

## Permission Gaps
- None encountered
