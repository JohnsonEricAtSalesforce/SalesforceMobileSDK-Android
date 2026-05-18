# Delta Notes — SalesforceSDK Track A (Batches 05-06)

## Conversion Summary

| Batch | File | Pattern | Notes |
|-------|------|---------|-------|
| 05 | JSONObjectHelper.kt | `object` with `@JvmStatic` | Stateless utility, generic methods with `@Suppress("UNCHECKED_CAST")` |
| 05 | EventsObservable.kt | `class` (not object, uses inheritance) | Extends `Observable<EventsObserver>`, singleton via companion |
| 05 | ManagedFilesHelper.kt | `object` with `@JvmStatic` | Stateless file utility, SAM lambda for FilenameFilter |
| 05 | AuthConfigUtil.kt | `object` with `@JvmStatic` | Inner class `MyDomainAuthConfig` retained as nested class |
| 06 | EventsObserver.kt | `interface` | Simple SAM interface |
| 06 | EventsListenerQueue.kt | `class` | Implements EventsObserver, abstract inner class `BlockForEvent` |
| 06 | TestCredentials.kt | `object` with `@JvmField`/`@JvmStatic` | All fields nullable `String?` since assigned at init time |
| 06 | BroadcastListenerQueue.kt | `class` extends `BroadcastReceiver` | Simple queue wrapper |

## Key Patterns Discovered

### 1. HttpAccess.DEFAULT is nullable in Kotlin conversion
The `HttpAccess.kt` declares `DEFAULT` as `HttpAccess?`. The Java version was effectively non-null after init. Required `!!` assertion when using the elvis operator fallback.

### 2. HttpAccess property access vs function call
`HttpAccess.kt` has `private var okHttpClient` + public `fun getOkHttpClient()`. In Kotlin, `httpAccess.okHttpClient` tries to access the private property, not the getter function. Must use `httpAccess.getOkHttpClient()` explicitly.

### 3. Kotlin-to-Kotlin generic type inference
When `JSONObjectHelper` was Java, Kotlin callers could infer generic types from assignment context (e.g., `val x: List<JSONObject> = JSONObjectHelper.toList(arr)`). After conversion to Kotlin object, callers must specify type explicitly: `JSONObjectHelper.toList<JSONObject>(arr)`. This affects `BatchResponse.kt`, `CollectionResponse.kt`, `CompositeResponse.kt` (other track's files).

### 4. EventsObservable cannot be `object`
Although it's a singleton pattern, `EventsObservable` extends `Observable<EventsObserver>` (Android framework class). Kotlin `object` declarations do support inheritance, but since it uses `mObservers` (protected field from Observable), it remains a `class` with companion object providing `get()`.

### 5. const val vs @JvmField val for public constants
String constants that are compile-time can use `const val` directly in an `object` — no `@JvmField` annotation needed. This is more idiomatic and produces identical bytecode for Java callers.

### 6. Batch 06 files are in `util/test/` subdirectory
The scope spec listed paths as `libs/SalesforceSDK/src/com/salesforce/androidsdk/util/EventsListenerQueue.kt` but actual location is `libs/SalesforceSDK/src/com/salesforce/androidsdk/util/test/EventsListenerQueue.kt`.

## Issues / Risks

- **Cross-track dependency**: `BatchResponse.kt`, `CollectionResponse.kt`, `CompositeResponse.kt` (from another batch) call `JSONObjectHelper.toList()` and `JSONObjectHelper.toMap()` without explicit type parameters. These callers need to add explicit type params (e.g., `toList<JSONObject>(...)`) now that `JSONObjectHelper` is Kotlin. This is NOT a regression in Java callers — only affects Kotlin-to-Kotlin calls.
- **Pre-existing build failures**: The SalesforceSDK module has many pre-existing Kotlin compilation errors from other conversion tracks (Redeclarations, unresolved references, type mismatches). None are caused by this batch.
