# Delta Lessons — SalesforceSDK Scout 3 (Batch 12)

## New Patterns Discovered

### 1. Java `isX()` to Kotlin property for Kotlin callers
When a Java class has `public boolean isSuccess()`, Kotlin callers can access it as `obj.isSuccess` (property syntax). When converting to Kotlin, if other Kotlin code already uses property syntax, the method must become a `val isSuccess: Boolean` property (with custom getter if computed), NOT a `fun isSuccess()`. A Kotlin property named `isSuccess` of type `Boolean` generates `isSuccess()` in JVM bytecode, maintaining Java caller compatibility.

### 2. `object` for pure-constant/utility classes with `@JvmStatic`
`ApiVersionStrings` converted to `object` with `const val` for compile-time constants and `@JvmStatic` on all public methods. `const val` compiles to `public static final` fields automatically — no `@JvmField` needed for `const`.

### 3. Mutable `@VisibleForTesting` fields
`VERSION_NUMBER_TEST` was originally `public static String = null` (mutable, nullable). Converted to `@JvmField var VERSION_NUMBER_TEST: String? = null` in the object. The `@JvmField` allows Java and Kotlin callers to access it directly as `ApiVersionStrings.VERSION_NUMBER_TEST` without getter/setter.

### 4. Subclass constructors calling parent with computed arguments
`BatchRequest` and `CompositeRequest` extend `RestRequest`. The parent constructor call requires a `JSONObject` computed from the subclass's parameters. Pattern: use a companion `private fun` to compute the JSON, called in the super-constructor delegation. The `companion object` function is callable from the constructor delegation expression.

### 5. JSONObjectHelper generic type erasure in Kotlin
`JSONObjectHelper.<JSONObject>toList(...)` in Java becomes just `JSONObjectHelper.toList<JSONObject>(...)` in Kotlin, or simply inferred as `JSONObjectHelper.toList(...)` when the target variable's type provides inference.

### 6. Response classes with init blocks
Response classes that parse JSON in constructors are best represented as Kotlin classes with `init {}` blocks rather than data classes, since they contain parsing logic and mutable collections.

### 7. DateFormat static initialization
Java `static {}` initializer blocks for DateFormat become property initialization in companion object using `.apply {}` for fluent configuration.

## Rules Applied
- Rule 2: `open` not needed — these classes are not subclassed
- Rule 5: Nullability — `relayToken: String?`, `id: String?`, `VERSION_NUMBER_TEST: String?`, `systemModstamp: Date?`
- Rule 8: `const val` for string constants in companion objects
- Rule 14: `@JvmStatic` on companion methods in `ApiVersionStrings`
- Rule 15: `@JvmField` on public fields that Java code accesses directly
- Rule 17: `@Throws(JSONException::class)` on constructors and methods
- Rule 22: for-each → for-in, instanceof/cast not applicable here

## Issues / Observations
- `PrimingRecordsResponse.TIMESTAMP_FORMAT` is a `DateFormat` (non-thread-safe) exposed as a public static field. This is a pre-existing thread-safety concern carried forward from Java.
- `CompositeRequest.computeCompositeRequestJson` was package-private (`public static`) in Java; kept as `@JvmStatic fun` in companion for same visibility.
- Pre-existing build errors in the repo (from other batch conversions: `getRuntimeConfig`, `setLogLevel`, `isManagedApp`) are unrelated to batch 12.
