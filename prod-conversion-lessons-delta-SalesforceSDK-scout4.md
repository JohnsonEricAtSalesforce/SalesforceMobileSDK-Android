# Delta Lessons — SalesforceSDK Scout 4 (Batch 17: UserAccount.java)

## Files Converted
- `UserAccount.java` (1,106 lines) -> `UserAccount.kt` (989 lines)

## Rules Applied
- Rule 5 (Nullability): All String fields nullable since constructors can leave them unset; `nativeLogin` kept as non-nullable `Boolean` (defaults to `false`) to match builder expectations
- Rule 8 (static final): `const val` in companion object for all public string constants (no `@JvmField` needed — `const` compiles to `public static final` directly)
- Rule 12 (Multiple constructors): Four constructors preserved — internal (parameterized), internal (JSON+appName), public (JSON), internal (Bundle+keys), public (Bundle)
- Rule 14 (@JvmStatic): Not needed — no companion methods
- Rule 22 (instanceof->is): `instanceof` check in `equals()` converted to `is` smart cast

## New Patterns Discovered

### 1. Custom getter names with @get:JvmName
Java methods `getVFDomain()`, `getVFSid()`, `getCSRFToken()` have non-standard capitalization. Kotlin properties generate `getVfDomain()`, `getVfSid()`, `getCsrfToken()` by default. Used `@get:JvmName("getVFDomain")` etc. to preserve the original Java method names for backward compatibility.

### 2. Property vs Function for Kotlin synthetic property compatibility
When converting Java methods like `getUserLevelFilenameSuffix()` that Kotlin callers already access via synthetic property syntax (`account.userLevelFilenameSuffix`), these MUST become Kotlin properties (not functions). The JVM bytecode for a Kotlin `val userLevelFilenameSuffix` generates `getUserLevelFilenameSuffix()` which Java callers can still use.

Conversely, if existing Kotlin callers use function-call syntax (`account.getUserLevelFilenameSuffix()`), they must be updated to property syntax — this is unavoidable.

### 3. Parameterized overloads alongside no-arg properties
Pattern: `val communityLevelFilenameSuffix: String` (property, no args) coexists with `fun getCommunityLevelFilenameSuffix(communityId: String?): String` (function, with param). These have different JVM signatures and don't clash.

### 4. Private backing field for complex getter (refreshToken)
Used `private var _refreshToken: String?` as backing field with a public `val refreshToken: String?` having a custom getter that consults AccountManager. Also exposed `val refreshTokenForPersistence: String?` that returns the raw backing field. This pattern preserves both the smart getter behavior and the raw-field access for persistence code.

### 5. Platform type -> explicit nullable causes downstream type mismatches
When Java fields had no nullability annotations, Kotlin treated them as platform types (`String!`). After conversion to explicit `String?`, downstream Kotlin callers that assumed non-null get compilation errors. These are legitimate null-safety improvements surfacing pre-existing bugs, not regressions.

### 6. `const val` vs `@JvmField val` for string constants
`const val` is preferred for compile-time string constants in companion objects. It generates the same `public static final` bytecode. `@JvmField val` is only needed for non-const runtime values. Note: `@JvmField` CANNOT be combined with `const` (compilation error).

### 7. encryptionKey property access from Kotlin
`SalesforceSDKManager.getEncryptionKey()` in Java becomes `SalesforceSDKManager.encryptionKey` in Kotlin (it's a `@JvmStatic val` property in the companion object).

## Downstream Issues (Not Fixed — Scope Fence)
The following files have type mismatch errors due to nullable properties:
- `AuthenticationUtilities.kt:489,522` — `refreshTokenForPersistence`/`accountName` passed where non-null expected
- `IDPManager.kt:62` — `orgId`/`userId` passed where non-null expected
- `TokenMigrationActivity.kt:134,170` — `instanceServer` passed where non-null expected
- `PickerBottomSheet.kt:425,426` — `displayName`/`communityUrl`/`instanceServer` in non-null context
- `DevSupportInfo.kt:147,155` — `authToken`/`username`/`clientId` in non-null context

These require adding null-safety operators (`!!`, `?:`, or `?: return`) in the calling code.

## One File Modified Outside Scope Fence
- `ScreenLockActivity.kt:382` — Changed `account.getUserLevelFilenameSuffix()` to `account.userLevelFilenameSuffix` (function-call syntax to property syntax, required by conversion)
