# Production Kotlin Conversion Lessons — SalesforceSDK Track D (Batches 18-20)

## Delta Notes from This Track

### Pattern: Kotlin 2.0 (K2) Companion Import Resolution

In Kotlin 2.0+ (K2 compiler), importing companion object functions requires the explicit `.Companion.` path qualifier when importing for unqualified use:

```kotlin
// Works: Using qualified access (no import needed in same package)
UserAccountManager.getInstance()

// K2 compatible import form:
import com.salesforce.androidsdk.accounts.UserAccountManager.Companion.getInstance

// K1/Java static import form (does NOT work in K2 for companion functions):
import com.salesforce.androidsdk.accounts.UserAccountManager.getInstance
```

**Exception**: `const val` in companion objects ARE importable without `.Companion.` because they compile to static fields on the outer class.

**Action Required**: `UserAccountManagerExtension.kt` line 30 needs import updated to `UserAccountManager.Companion.getInstance`.

### Pattern: Property vs Function for Existing Kotlin Callers

When converting Java getters to Kotlin, decide between property and function based on existing callers:
- If all existing Kotlin callers use property syntax (`.authenticatedUsers`), use `val`
- If any existing Kotlin caller uses explicit getter syntax (`.getAuthenticatedUsers()`), that caller needs updating to property syntax after conversion
- Java callers are unaffected (properties generate getters in bytecode)

**Affected file**: `ScreenLockActivity.kt` line 378 uses `.getAuthenticatedUsers()` — needs changing to `.authenticatedUsers`.

### Pattern: Kotlin-defined Property Access on Previously-Java Classes

When `RestClient.okHttpClient` was defined in Java, other Java code could call `getOkHttpClient()`. Now that it's defined as a Kotlin property with `@get:JvmName("getOkHttpClient")`:
- Kotlin callers must use property syntax: `restClient.okHttpClient`
- Java callers can still use `restClient.getOkHttpClient()`
- The null check `if (restClient.okHttpClient == null)` is dead code since the property is non-null

### Pattern: SalesforceSDKManager Static Accessors in Kotlin

When calling `SalesforceSDKManager` companion members from Kotlin:
- `getEncryptionKey()` → `SalesforceSDKManager.encryptionKey` (companion `val`)
- `getAiltnAppName()` → `SalesforceSDKManager.ailtnAppName` (companion `var`)
- `isDevSupportEnabled()` → `SalesforceSDKManager.getInstance().isDevSupportEnabled()` (instance function, NOT a property)

### Pattern: analyticsPublishingType() — Function vs Property Naming Clash

`SalesforceAnalyticsManager.analyticsPublishingType()` was a Java method. Existing Kotlin callers call it as a function. Converting to a property would create a naming clash AND break callers. Solution: keep as function pair (`analyticsPublishingType()` / `setAnalyticsPublishingType()`) with a private backing field.

### Pattern: BiometricAuthenticationManager/ScreenLockManager `.enabled`

These managers use Kotlin property `enabled` with `@get:JvmName("isEnabled")`:
- Kotlin callers: `.enabled`
- Java callers: `.isEnabled()`

### Pattern: OkHttp RequestBody.create → String.toRequestBody()

Modern OkHttp Kotlin extensions:
```kotlin
// Old Java-style (deprecated):
RequestBody.create(body.toString(), MEDIA_TYPE_JSON)

// New Kotlin extension:
body.toString().toRequestBody(MEDIA_TYPE_JSON)
```

### Pattern: Okio.buffer() → Sink.buffer()

```kotlin
// Old:
Okio.buffer(GzipSink(sink))

// New (extension function):
GzipSink(sink).buffer()
```

### Pattern: EventStoreManager.iterateAllEvents() Returns Nullable Elements

`iterateAllEvents()` returns `Iterable<InstrumentationEvent?>` (nullable elements). Use `.filterNotNull()` when assigning to `Iterable<InstrumentationEvent>`.

### Pattern: UserAccount Properties Are Nullable

Most `UserAccount` properties (`userId`, `orgId`, `accountName`, etc.) are `String?`. When passing to methods expecting `String`, use `?: ""` or `?: return` as appropriate.

## Security Decisions — UserAccountManager

1. **Encryption key access**: Changed from `SalesforceSDKManager.getEncryptionKey()` to `SalesforceSDKManager.encryptionKey` (same underlying implementation, just Kotlin property access syntax)
2. **Token refresh synchronization**: Maintained `@Synchronized` annotation on `refreshToken()` method — same thread-safety guarantee
3. **Account password storage**: Unchanged — refresh token stored via `AccountManager.setPassword()` with encryption
4. **Auth bundle construction**: `buildAuthBundle()` remains private, encrypts all sensitive values before storing in AccountManager
5. **Null safety for credentials**: Preserved same null-check patterns from Java; no force-unwrapping of sensitive tokens
6. **Singleton pattern**: Kept lazy initialization with null check — not thread-safe (same as Java original)

## Files Converted

| Batch | File | Lines | Notes |
|-------|------|-------|-------|
| 18 | UserAccountManager.kt | 765 | SECURITY-CRITICAL. open class + companion singleton |
| 18 | SalesforceAnalyticsManager.kt | 514 | Per-account singleton pattern with companion factory |
| 19 | AILTNPublisher.kt | 172 | Implements AnalyticsPublisher interface |
| 19 | EventBuilderHelper.kt | 155 | object (stateless utility) with @JvmStatic |
| 19 | AnalyticsPublisher.kt | 45 | interface |
| 20 | SalesforceActivity.kt | 87 | abstract class (open for subclassing) |
| 20 | SalesforceActivityDelegate.kt | 141 | open class, handles lifecycle |
| 20 | SalesforceActivityInterface.kt | 53 | interface |
| 20 | SalesforceExpandableListActivity.kt | 72 | abstract class |
| 20 | SalesforceListActivity.kt | 78 | abstract class, @Deprecated |
| 20 | SalesforceServerRadioButton.kt | 104 | open class, custom View |
