# Delta Lessons — SalesforceSDK Scout 1 (Batch 04)

## New Patterns Discovered

| # | Java Pattern | Kotlin Pattern | Rule | Notes |
|---|-------------|---------------|------|-------|
| 6 | Java static method with unannotated params called from Kotlin with nullable types | Make params nullable with null guard | 5 | Java's lack of null annotations means callers in Kotlin pass nullable values freely. Must accept nullable to maintain compat. |
| 7 | Logger Throwable overloads called with `Throwable?` from existing Kotlin | Accept `Throwable?` and null-check before delegation | 5,6 | SalesforceSDKLogger is heavily called from existing .kt files that pass nullable Throwable. |
| 8 | `object` with `@JvmStatic` methods | Preserves `ClassName.method()` call sites in both Java and Kotlin | 11,14 | Perfect for stateless utility classes (logger wrappers, parsers, serializers). |
| 9 | Abstract class extending Android framework class | `abstract class Foo : FrameworkClass()` | 2 | `abstract` implies `open` in Kotlin; no need for explicit `open` keyword. |
| 10 | Private constructor utility class | `object` declaration | 11 | Java pattern of `private Foo() { assert false }` converts to Kotlin `object`. |

## Critical Findings for Parallel Agents

### 1. Nullable parameter widening is required for heavily-used utility APIs
When converting a Java static method that had no `@NonNull`/`@Nullable` annotations, and the method is called from existing Kotlin code with nullable parameters, you MUST declare the Kotlin parameter as nullable. Otherwise the existing .kt callers will fail to compile.

**Affected classes this batch**: `SalesforceSDKLogger` (Throwable params), `ResourceReaderHelper` (Context and String params), `UriFragmentParser` (Uri param).

### 2. Logger wrapper Throwable overloads need null-safety handling
The `SalesforceSDKLogger` is called from many places with `Throwable?`. The pattern is:
```kotlin
fun e(tag: String, message: String, e: Throwable?) {
    if (e != null) {
        getLogger().e(tag, message, e)
    } else {
        getLogger().e(tag, message)
    }
}
```

### 3. Pre-existing MobileSync compilation error (NOT caused by batch 04)
`MobileSyncLogger.kt:184` tries to set `logger.logLevel = level` but `SalesforceLogger.logLevel` has `private set` (from Phase 1 analytics conversion). This is a known pre-existing issue that should be addressed separately.

### 4. UriFragmentParser `split()` behavior difference
Kotlin's `String.split()` returns a `List<String>` (not array), and by default uses regex. The conversion uses `split("&")` and `split("=")` which in Kotlin with a String argument uses literal match (via `Regex` under the hood for single-char literals). This preserves behavior.

## Build Verification
- SalesforceSDK: Kotlin compile PASS, Java compile PASS
- SmartStore: Kotlin compile PASS, Java compile PASS
- MobileSync: FAIL (pre-existing, unrelated to batch 04)
- SalesforceHybrid: FAIL (transitive via MobileSync, unrelated)
