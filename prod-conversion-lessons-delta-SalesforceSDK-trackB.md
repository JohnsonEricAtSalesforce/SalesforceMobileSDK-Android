# Production Kotlin Conversion Lessons Delta - SalesforceSDK Track B (Batches 08-11)

## New Patterns Discovered

### 1. Open class + open val for overridable Java-style getters
When an existing Kotlin consumer uses `object : SomeClass() { override fun getX() = ... }` to override what was a Java getter, the converted class must use `open val x` (not a function) since Kotlin auto-generates `getX()` from `open val x`. However, direct `override fun getX()` syntax won't work from Kotlin callers — they must use `override val x`. This creates a conflict if there are two caller conventions. Resolution: use `open val` and flag the non-property-style callers for follow-up migration.

### 2. `protected` is NOT valid in Kotlin `object` declarations
Java `protected static` fields/methods in utility classes (converted to `object`) must become `internal` in Kotlin, not `protected`. The `object` keyword creates a singleton — it has no subclasses, so `protected` is meaningless.

### 3. @get:JvmName property access from Kotlin
When `SalesforceSDKManager` defines:
```kotlin
@get:JvmName("shouldUseHybridAuthentication")
var useHybridAuthentication = true
```
From Kotlin, access the property name (`useHybridAuthentication`), not the JVM name. The JVM name is only for Java callers.

### 4. Nullable returns from KeyStore operations
`KeyStoreWrapper.getRSAPrivateKey()` and `getRSAPublicKey()` return nullable. All callers performing crypto operations must null-check before passing to `Encryptor.encryptWithRSA()` / `decryptWithRSA()` which expect non-null keys.

### 5. Map type covariance at interface boundaries
Kotlin's `Map<K, out V>` is covariant on V but invariant on K. When an interface declares `fun onPushMessageReceived(data: Map<String?, String?>?)` and you have `Map<String, String>`, you cannot assign directly. Use `@Suppress("UNCHECKED_CAST")` for safe upcasts at platform boundaries.

### 6. MutableLiveData from companion RuntimeConfig.getRuntimeConfig
Static imports from companion objects: use `import com.salesforce.androidsdk.config.RuntimeConfig.Companion.getRuntimeConfig` (not `RuntimeConfig.getRuntimeConfig`).

### 7. `!!` for essential OAuth fields
`UserAccount.clientIdForRefresh` and `UserAccount.refreshToken` are nullable in the Kotlin API but are logically required for token refresh. Use `!!` with early-exit guard (`?: return`) on the parent account.

### 8. Android Service classes need `open` for Kotlin subclassing
`AuthenticatorService` must be `open class` since `LegacyAuthenticatorService` extends it. Similarly, `BootConfig` needs `open` if anonymous objects extend it in preview/test code.

## Security Decisions

1. **OAuth2.kt**: All security-critical flows (token refresh, token revoke, code exchange) preserved exactly. Attestation logic preserved without Uri.encode per original design comment.
2. **KeyStoreWrapper.kt**: All crypto operations preserved exactly. StrongBox disable comment preserved.
3. **SalesforceKeyGenerator.kt**: RSA key migration logic (LEGACY -> MSDK keypair) preserved exactly with proper null guards added.
4. **PushNotificationDecryptor.kt**: Decryption chain preserved exactly. RSA multi-cipher node decryption followed by AES decryption.
5. **HttpAccess.kt**: TLS 1.2 enforcement, connection specs, and user agent interceptor preserved.

## Files Converted

| Batch | File | Lines | Notes |
|-------|------|-------|-------|
| 08 | BootConfig.kt | ~260 | open class, open val properties, backing fields |
| 08 | LoginServerManager.kt | ~470 | SharedPreferences, LiveData, XML parsing |
| 09 | AuthenticatorService.kt | ~180 | AndroidManifest service, AccountAuthenticator |
| 09 | LegacyAuthenticatorService.kt | ~35 | @Deprecated, extends AuthenticatorService |
| 09 | HttpAccess.kt | ~170 | SECURITY: OkHttp TLS config, singleton |
| 10 | OAuth2.kt | ~700 | SECURITY: OAuth flows, token management |
| 11 | KeyStoreWrapper.kt | ~230 | SECURITY: Android KeyStore crypto |
| 11 | SalesforceKeyGenerator.kt | ~200 | SECURITY: Key generation, RSA encryption |
| 11 | PushNotificationDecryptor.kt | ~100 | SECURITY: Push payload decryption |
| 11 | SFDCFcmListenerService.kt | ~55 | AndroidManifest FCM service |
