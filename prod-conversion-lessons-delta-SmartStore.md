# Phase 3 (SmartStore) - Conversion Delta Notes

## Batch 21 - Utility/Config/Simple Data Classes
- **SmartStoreLogger**: Converted to `object` (stateless utility). All methods get `@JvmStatic`.
- **Features**: Converted to `object` with `const val` (no `@JvmField` needed for `const`).
- **StoreConfig**: Private primary constructor with delegating secondary constructors. `init` block handles the JSON parsing.
- **IndexSpec**: Kept as regular class (not data class) because it has custom `equals`/`hashCode`. Used `@JvmOverloads` on constructor for the optional `columnName` parameter. Used `@JvmField` on public fields.
- **StoreCursor**: `FakeJSONObject` kept as a separate class in same file (package-private in Java, now internal-equivalent in Kotlin).

## Batch 22 - SmartStore (Core, 1684 lines)
- **Thread safety**: All `synchronized(db)` blocks preserved exactly. `synchronized(SmartStore.class)` becomes `synchronized(SmartStore::class.java)`.
- **Companion object**: `protected static` fields use `@JvmField protected val` in companion. `const val` for truly constant strings.
- **open class**: SmartStore is subclassed (indirectly via tests), so marked `open`.
- **registerSoupUsingTableName**: Marked `protected open` since it's called from `AlterSoupLongOperation`.
- **Enum classes**: `Type`, `TypeGroup`, `FtsExtension` kept as nested enums with same names.
- **SmartStoreException**: Kept as nested class with two constructors.
- **Form feed character**: The `'\f'` (form feed) in `escapeStringValue` required using the Unicode literal `''` which Kotlin renders as `''`.
- **vararg vs Array**: `retrieve` and `delete` use `vararg Long` then convert with `.toTypedArray()` for internal use.

## Batch 23 - DBHelper, DBOpenHelper, SmartSqlHelper
- **DBHelper**: Singleton pattern preserved with companion `getInstance()` + `@Synchronized`. LruCache subclasses use Kotlin `object` expression overriding `entryRemoved`.
- **DBOpenHelper**: SECURITY-CRITICAL. `ReentrantLock` usage preserved. `SQLiteDatabaseHook` and `DatabaseErrorHandler` kept as `internal class`. Static methods all get `@JvmStatic @Synchronized`.
- **SmartSqlHelper**: Regex patterns compiled in companion. `StringBuffer` preserved for `Matcher.appendReplacement` compatibility (requires `StringBuffer`, not `StringBuilder`).

## Batch 24 - QuerySpec, LongOperation, AlterSoupLongOperation
- **QuerySpec**: Complex class with two construction paths. Used a single private primary constructor with all fields, then two private secondary constructors (for soup queries vs smart queries). Static factory methods in companion.
- **QuerySpec.computeWhereClause**: The original Java `switch` with fall-through for range (beginKey/endKey null checks) was simplified in the static helper. The actual logic is in `getArgs()`.
- **LongOperation**: `abstract class` with `abstract fun`. `LongOperationType` enum uses reflection-based instantiation (preserved).
- **AlterSoupLongOperation**: The Java `switch` with fall-through in `alterSoupInternal` was converted to a `when` with explicit cascading (each case executes all subsequent steps). This is semantically equivalent.

## Batch 25 - KeyValue Store Classes
- **KeyValueStore**: Interface converted directly. `@Throws(IOException::class)` on `saveStream`.
- **KeyValueEncryptedFileStore**: `open class` (has internal constructors). `FilenameFilter` SAM → lambda. `decryptFileAsSteam` renamed to `decryptFileAsStream` (fixing typo) internally but callers use the same logic.
- **MemCachedKeyValueStore**: Straightforward conversion. `@JvmField` on `keyValueStore` and `memCache` for test access.
- **KeyValueStoreInspectorActivity**: Activity with inner class `KeyValuePair`. Anonymous `ArrayAdapter` → `object : ArrayAdapter`.

## Batch 26 - Manager and Inspector Activities
- **SmartStoreSDKManager**: `open class` extending `SalesforceSDKManager`. Multiple `init` overloads preserved. `INSTANCE` access pattern uses inherited static field. `DevActionHandler` SAM lambdas.
- **SmartStoreUpgradeManager**: Simple singleton pattern with `@JvmStatic @Synchronized getInstance()`.
- **SmartStoreInspectorActivity**: `AppCompatActivity` with `AdapterView.OnItemSelectedListener`. `QueryTokenizer` kept as top-level class (was package-private). `Pair` uses Android's `android.util.Pair`.

## General Observations
- No Kotlin-Java interop issues expected since SmartStore had zero existing Kotlin files.
- All `synchronized` blocks preserved verbatim for thread safety.
- SQLCipher API calls left unchanged — no "Kotlin-ification" of database operations.
- Conservative nullability applied throughout: cursor operations, map lookups, and DB queries all use nullable types.
- `@Suppress("DEPRECATION")` used for `cursor.getColumnIndex()` calls that Android has deprecated but still work.
