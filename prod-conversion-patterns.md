# Pattern Registry

| # | Java Pattern | Kotlin Pattern | Rule | Discovered | Verified | Notes |
|---|-------------|---------------|------|-----------|----------|-------|
| 1 | `public static final String FOO = "bar"` | `companion object { @JvmField const val FOO = "bar" }` | 8,15 | P1 B01 | P1 build | Use @JvmField for Java field access |
| 2 | `synchronized(lock) { ... }` | `synchronized(lock) { ... }` | 26 | P1 B03 | P1 build | Kotlin has same syntax |
| 3 | `private static Foo instance` + `getInstance()` | `companion object { @JvmStatic fun getInstance() }` or `object Foo` | 11,14 | P1 B01 | P1 build | Use object for stateless; companion+lazy for stateful |
| 4 | PaperDB `book.write(key, value)` | `book.write(key, value)` with non-null value | - | P1 B03 | P1 build | PaperDB requires non-null; null-check encrypt results |
| 5 | `var x = nonNullExpr()` then `x = null` in catch | `var x: Type? = nonNullExpr()` | - | P1 B03 | P1 build | Declare nullable upfront if null-assigned later |
