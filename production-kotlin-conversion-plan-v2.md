# Production Java → Kotlin Conversion Plan — v2 (Recommended Improvements)

**Context:** This document captures lessons from executing v1 of the production conversion plan and recommends structural improvements for a v2 plan. Applicable to: the test conversion plan for this repo, or any future large-scale Java→Kotlin migration of a public Android SDK.

**Independent review incorporated:** An independent reviewer identified contradictions, backfire risks, and gaps in the initial v2 draft. Those findings are integrated below.

---

## Executive Summary of v1 Performance

- **Outcome:** 126 files converted, full build passes, 7 clean commits
- **Total boundary build errors fixed:** ~425 (4 + 254 + 71 + 48 + misc)
- **Error source:** 90%+ were in *existing Kotlin files* reacting to newly explicit nullability/property changes — not in the *converted* files themselves
- **Biggest time sink:** Phase 2 boundary fix (254 errors in 78 existing Kotlin files)
- **Unanticipated issues:** 6 (all resolved, most were JVM interop clashes)
- **Key gap identified post-completion:** No runtime behavioral verification exists — compilation success does not prove semantic correctness

---

## Consolidated Recommendations (10)

### 1. Unified Caller-Aware Conversion Strategy

**Problem (from 3 separate v1 issues):** The dominant cost was boundary build errors in existing Kotlin callers (~425 total). Three factors contributed: (a) agents couldn't predict which callers would break, (b) agents were forbidden from fixing callers during batch work, (c) a separate boundary agent lacked conversion context.

**Recommendation — a single integrated approach:**

1. **Pre-analyze:** Before converting any file, the orchestrator runs:
   ```bash
   for java_file in $(find libs/MODULE/src -name "*.java" -not -path "*/test/*"); do
     class_name=$(basename "$java_file" .java)
     grep -rl "$class_name" libs/MODULE/src --include="*.kt" | sort -u
   done
   ```
   Output: a "caller impact map" listing which existing `.kt` files reference each class being converted.

2. **Fix inline:** The conversion agent receives the caller impact map and is permitted to make three types of fixes in existing `.kt` files *during batch work*:
   - Import path changes (`Foo.bar` → `Foo.Companion.bar`)
   - Function-to-property syntax (`obj.getFoo()` → `obj.foo`)
   - Non-null assertions on platform types that became explicit nullable
   - No logic changes, refactoring, or unrelated fixes.

3. **Same-agent build:** After completing all batches, the same agent runs `./gradlew :libs:MODULE:assembleDebug` and fixes any residual errors. It has full context of every conversion decision.

**Projected impact:** 70-80% reduction in boundary errors (from ~425 to ~85-125). Eliminates the separate boundary agent entirely.

**Interaction note:** Steps 1-3 are complementary, not alternatives. Pre-analysis informs inline fixing; same-agent build catches anything missed. The residual errors are genuinely unpredictable cases (generic type inference, covariance issues, framework edge cases).

---

### 2. Nullability Strategy — Prove It, Don't Assume It

**Problem:** v1 defaulted to nullable (`String?`) for unannotated Java fields. This produced ~100+ `!!` assertions in callers. But the naive alternative — "default non-null" — is dangerous for a public SDK because:
- JSON deserialization can bypass constructors
- Reflection-based instantiation (Android framework, ProGuard-kept classes) can produce partially-initialized objects
- A server-side API change could introduce null where none existed historically
- `lateinit var` throws `UninitializedPropertyAccessException` which is harder to diagnose than NPE

**Recommendation — evidence-based nullability with three tiers:**

| Tier | Condition | Kotlin Type | Example |
|------|-----------|-------------|---------|
| **Non-null** | Assigned in primary constructor from non-null source, OR `const val`, OR has `@NonNull` annotation | `val foo: String` | `val TAG = "MyClass"` |
| **Lateinit** | Assigned in a guaranteed lifecycle method (e.g., `onCreate`, `init {}`) AND never accessed before assignment | `lateinit var foo: String` | Activity-scoped fields set in `onCreate` |
| **Nullable** | Any JSON-deserialized field, any field assignable from external input, any field without constructor guarantee, OR has `@Nullable` annotation | `val foo: String?` | `val communityUrl: String?` |

**Critical guardrail:** For a public SDK, when in doubt, prefer nullable. A caller handling null is inconvenient. A crash from a non-null assertion is a P1 bug. The cost of `!!` in callers is cosmetic; the cost of an NPE in production is reputational.

**Decision record:** Each converted file should have a one-line comment at the top of the PR noting the nullability tier decisions for its public API fields. This is reviewable context, not permanent code comments.

---

### 3. Simplify Agent Topology — Eliminate Scouts, Merge Phases

**Problem (consolidates v1 recommendations #1, #7, #10, #11):** v1 used 9 agent spawns for Phase 2 alone (4 scouts + 4 tracks + 1 boundary). The scouts discovered nothing the Phase 1 lessons didn't already cover. The pattern registry was never read by any agent. Phases 4-6 (29 files) were run as 3 separate phases with 3 operator gates, but none had security-critical decisions.

**Recommendation:**

**For small libraries (<20 files):** Single agent, end-to-end (conversion + build + fix). No scouts, no parallelism.

**For large libraries (>20 files):** 2-4 domain-aligned parallel agents. Each agent:
- Receives the cumulative lessons from prior phases (no scout needed — Phase 1 IS the scout)
- Handles its domain end-to-end including the build attempt
- The orchestrator does a final full-module build after all agents complete

**Phase grouping:**
- Phase 1: SalesforceAnalytics (12 files) — solo agent, establishes patterns
- Phase 2: SalesforceSDK (57 files) — 3-4 parallel agents by domain
- Phase 3: SmartStore (19 files) — solo agent
- Phase 4: MobileSync + SalesforceHybrid + SalesforceReact (29 files) — solo agent, sequential modules. One operator gate.
- Phase 5: Sample Apps (9 files) — solo agent

**Eliminates:** Scout agents, pattern registry file, 2 operator gates, ~30% of agent spawns.

**Caveat on combining Phases 4-6:** MobileSync (1 file, 42 existing Kotlin callers, 48 boundary errors in v1) has very different error characteristics than SalesforceHybrid (18 files, 2 callers) or SalesforceReact (10 files, 0 callers). The combined agent should handle MobileSync first and build it before proceeding to Hybrid/React, since MobileSync's errors are entirely in existing files and require different cognitive load than converting Cordova plugins.

---

### 4. Dry-Run Build Before Conversion (Subsumes JVM Target Check)

**Problem:** Phase 3 (SmartStore) failed at build time with a JVM target mismatch because the module had never compiled Kotlin sources before. This wasted ~15 minutes of investigation. A trivial pre-check would have caught it.

**Recommendation:** Before converting any files in a module, create a trivial `.kt` file and build:

```bash
# Pre-conversion infrastructure check
echo "internal class KotlinCompilationTest" > libs/MODULE/src/com/salesforce/androidsdk/MODULE/KotlinCompilationTest.kt
./gradlew :libs:MODULE:compileDebugKotlin
rm libs/MODULE/src/com/salesforce/androidsdk/MODULE/KotlinCompilationTest.kt
```

This validates in one step:
- Kotlin plugin configuration
- JVM target alignment (Java ↔ Kotlin)
- Source set discovery
- Dependency resolution

**When it fails:** Fix the build infrastructure (e.g., add `jvmTarget` to root `build.gradle.kts`) before starting conversion. This is a pre-flight step, not a mid-conversion surprise.

---

### 5. Operator Gates — Mandatory for Security, Informational Otherwise

**Problem:** v1 had 7 operator gates. Only Gates 1 and 2 provided value (Phase 1 established patterns, Phase 2 had 10 security-critical files). Gates 3-7 were all "proceed" without meaningful review. Neither gate ever triggered "adjust" or "stop."

**Recommendation:**

| Gate Type | When | Action |
|-----------|------|--------|
| **Mandatory** | After libraries with security-critical files (SalesforceAnalytics for Encryptor, SalesforceSDK for OAuth/crypto/keystore, SmartStore for encrypted storage) | Full operator review required — present report, wait for decision |
| **Informational** | After non-security libraries (MobileSync, Hybrid, React, Sample Apps) | Auto-proceed unless error count exceeds adjusted threshold. Log the report for async review. |

**Adjusted threshold formula:** `base_per_file × converted_files + caller_factor × existing_kt_files`
- `base_per_file` = 3 (typical errors from a converted file)
- `caller_factor` = 3 (typical errors per existing Kotlin caller)
- Phase 2 adjusted: `3×57 + 3×78 = 171+234 = 405` → 254 actual is well under threshold
- Phase 4 adjusted: `3×1 + 3×42 = 3+126 = 129` → 48 actual is well under threshold

**Reduces:** Human wait time from 7 pauses to 2-3, while preserving review where it matters.

---

### 6. Report Simplification — Kill the Manifest, Keep the Intent Log

**Problem:** The structured completion manifest (per-file tables, rule counts) was overhead that duplicated `git diff --stat`. But dropping it entirely loses the record of *which JVM interop decisions were made* — a silent API degradation (missing `@JvmStatic`) would pass the build but degrade the Java consumer experience.

**Recommendation:** Replace the manifest with a lightweight **intent log** appended to the delta notes file:

```markdown
## JVM Interop Decisions (Batch NN)
- FooClass.kt: companion object with @JvmStatic on 5 public methods, @JvmField on 3 constants
- BarClass.kt: no companion (no statics in original), @Throws on 2 methods
- BazClass.kt: object declaration (stateless utility), all methods @JvmStatic
```

This is ~1 line per file (not a full table), captures intent, and is greppable for review.

**Scope compliance:** The orchestrator still runs `git diff --name-only` post-build to verify no files outside scope were modified. This is a mechanical check, not agent-authored prose.

---

### 7. Incremental Staging for Crash Recovery

**Problem:** If a session terminates mid-phase, all batch work since the last commit is unstaged and vulnerable to loss. The `.java.bak` renames + `.kt` writes are not atomic.

**Recommendation:** After each batch, `git add` the new files (stage but don't commit):
```bash
git add libs/MODULE/src/.../Batch04File.kt libs/MODULE/src/.../Batch04File.java.bak
```

This means:
- Session crash → `git stash` or `git status` shows progress
- Resuming → agent sees staged files and knows which batches completed
- Final commit still groups the entire library as one logical unit

---

### 8. Runtime Verification Strategy (NEW — Critical Gap in v1)

**Problem:** Neither v1 nor the initial v2 draft addresses runtime correctness. "Compiles = correct" is insufficient for a public SDK. Specific risks:
- `when` expressions missing branches (compiles with warning, silently drops cases at runtime)
- `object` singleton initialization timing differs from Java lazy `getInstance()`
- Property initialization order in Kotlin vs Java field assignment order
- Default parameter values that differ from the overloaded Java constructors they replace
- `companion object { init {} }` vs Java's `static {}` execution timing

**Recommendation — three levels:**

1. **Smoke test (during conversion):** After each library boundary build succeeds, launch the sample app (RestExplorer) against a test org and verify: login flow, REST query, logout. This catches catastrophic initialization-order bugs.

2. **Structural verification (automated, during conversion):** `grep -rn "when\b" libs/MODULE/src --include="*.kt" | grep -v "else"` to find non-exhaustive `when` statements. Flag for review.

3. **Full behavioral verification (after conversion):** The test conversion plan. Explicitly acknowledged as the primary runtime safety net. The production plan document should cross-reference it: "Runtime correctness is NOT verified by this plan. See `test-kotlin-conversion-plan.md` for the behavioral verification strategy."

---

### 9. API Documentation Preservation (NEW — Gap Identified by Reviewer)

**Problem:** When Java files with Javadoc are converted to Kotlin, documentation format must change (Javadoc → KDoc). The Salesforce Mobile SDK publishes Javadoc at `https://forcedotcom.github.io/SalesforceMobileSDK-Android/`. If documentation is lost or malformed, external developers lose API reference.

**Recommendation:**
- Conversion agents should preserve all Javadoc as KDoc (same content, Kotlin syntax: `/** ... */` with `@param`, `@return`, `@throws`)
- Add a post-conversion check: `./gradlew :libs:MODULE:dokkaHtml` (if available) or verify that KDoc is present on all `public`/`protected` members that had Javadoc in the original
- Add to the pre-flight validation: verify that the project's doc generation pipeline works with Kotlin source (Dokka vs Javadoc)

---

### 10. `.java.bak` Strategy — Acknowledge the Debt

**Problem:** The 126 `.java.bak` files are useful during verification but create long-term costs: repository bloat (~31K lines of dead code), polluted `grep` results, confusion for new contributors.

**Recommendation — time-boxed retention:**
- Retain `.java.bak` files until the test conversion plan completes AND passes
- Set a concrete cleanup deadline (e.g., "remove within 2 weeks of test plan completion")
- Add `.java.bak` to the project's `.gitignore` AFTER the cleanup commit (prevents accidental re-addition)
- Consider: for the test conversion plan, use a git branch comparison (`diff` between the pre-conversion commit and post-conversion commit) instead of on-disk `.java.bak` files. This gives the same audit capability without repository pollution.

---

## What Worked Well in v1 (Honest Assessment)

### Preserve unchanged:
1. **Dependency-ordered phases** — Zero cross-library build issues. Non-negotiable for any codebase with module dependencies.
2. **Module-isolated builds** — Only building the affected module saved enormous time. The final full build was a formality.
3. **Per-batch delta notes** — Incrementally accumulated, short, focused. More useful than structured manifests.
4. **Security-critical file flagging** — Forced explicit attention on OAuth2, KeyStoreWrapper, Encryptor, SmartStore.
5. **Large file isolation** — Solo batches for 1000+ line files (SmartStore, OAuth2, RestRequest) prevented context overflow.

### Preserve with caveats:
6. **`.java.bak` audit trail** — Useful during conversion and verification. But has a shelf life. See Recommendation #10 for cleanup strategy.
7. **Parallel tracks for Phase 2** — Saved ~35-40 minutes of wall time. BUT: the boundary agent that followed cost ~25 minutes because it lacked conversion context. Net savings: ~15 minutes. Under v2's unified strategy (Recommendation #1), agents fix callers inline, making the boundary agent unnecessary — so the net savings of parallelism is the full ~40 minutes.

### Revise:
8. **Seed conversion rules** — The structural and JVM interop rules (1-4, 6-18) were solid and rarely deviated from. The nullability heuristic (Rule 5: "default nullable if uncertain") produced poor outcomes — ~100+ unnecessary `!!` assertions. See Recommendation #2 for the revised strategy.

### Acknowledge as overhead:
9. **Scout phases** — Discovered nothing that Phase 1 lessons didn't cover. Drop.
10. **Pattern registry** — Never consumed by any downstream agent. Drop.
11. **Operator Gates 3-7** — All "proceed" without meaningful review. No gate triggered "adjust" or "stop." Reduce to 2-3 mandatory gates.

---

## Risks of v2 Recommendations

| Recommendation | Risk | Mitigation |
|---------------|------|------------|
| #1 (agents fix callers inline) | Agent modifies a file incorrectly, breaking unrelated functionality | Restrict to 3 specific fix categories (imports, property syntax, `!!`). No logic changes. |
| #2 (revised nullability) | Non-null declaration on a field that actually receives null at runtime (deserialization, reflection) | The "prove it" tier system. When in doubt, nullable. Document the decision per file. |
| #3 (combine phases 4-6) | Agent context overflow from handling 3 libraries sequentially | Build each module before starting the next. Bail if errors exceed threshold. |
| #5 (informational gates) | A non-security issue slips through without human review | The adjusted threshold catches anomalies. All reports are logged for async review. Security files are always mandatory-gated. |
| #8 (runtime verification) | Sample app smoke test passes but a subtle behavioral difference exists | Acknowledged. The test conversion plan is the full mitigation. The smoke test catches only catastrophic failures. |

---

## Summary: v1 vs Projected v2 Topology

| Metric | v1 Actual | v2 Projected | Change |
|--------|-----------|-------------|--------|
| Total agent spawns | ~18 | ~8-10 | -45% |
| Operator gates (human waits) | 7 | 2-3 | -60% |
| Boundary build errors | ~425 | ~85-125 | -70% |
| Separate artifact files | 22 | 7-9 | -60% |
| Plan document length | ~1,100 lines | ~500-600 lines | -45% |
| Pre-flight checks | 6 | 7 (add dry-run build) | +1 |
| Runtime verification | None | Smoke test + cross-reference | New |
| API doc verification | None | KDoc check | New |
