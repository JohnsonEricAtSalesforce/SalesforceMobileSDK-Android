# Test Suite Java → Kotlin Conversion Plan — v2 Recommendations

**Context:** This document recommends changes to the v1 test conversion plan based on:
1. Lessons from executing the production conversion (126 files, 7 phases)
2. The v2 production plan recommendations (independent review incorporated)
3. Additional constraints specific to test conversion (no production code modification, individual test execution, deferred test protocol)

---

## Definition of Done

The test conversion is COMPLETE when:
1. All 95 `.java` test files are converted to `.kt` (with `.java.bak` preserved)
2. `./gradlew assembleDebugAndroidTest` passes for all 6 modules (clean compilation)
3. Full `connectedAndroidTest` run per module completes — only `@Ignore`-annotated deferred tests may fail
4. Deferred test log (`deferred-tests.md`) is delivered to operator with count and classification summary
5. All commits are on the `feature/java-to-kotlin-test-migration` branch
6. Operator has reviewed and accepted the deferred test summary (deferred tests are fixed in a follow-up commit, NOT a blocker for merge)

**Verification commands (run at end):**
```bash
find libs/test -name "*.java" -not -path "*/build/*" | wc -l          # expect 0
find libs/test -name "*.java.bak" -not -path "*/build/*" | wc -l      # expect 95
./gradlew assembleDebugAndroidTest                                      # expect BUILD SUCCESSFUL
grep -r "@Ignore" libs/test --include="*.kt" -l | wc -l               # deferred count
```

---

## Key Differences: Test Conversion vs Production Conversion

| Dimension | Production (completed) | Test (this plan) |
|-----------|----------------------|-----------------|
| Risk of API breakage | High (public SDK) | None (internal tests) |
| JVM interop annotations needed | Yes (@JvmStatic, @JvmField, etc.) | No (tests are end-consumers, not consumed by others) |
| Existing Kotlin callers to worry about | 125 files (caused 425 build errors) | 54 files (but they don't call the Java tests — they're peers) |
| Build verification | `assembleDebug` only | `assembleDebugAndroidTest` + actual test execution |
| "Correctness" definition | Compiles | Compiles AND tests pass |
| Production code modification | Allowed (it's what we're converting) | **FORBIDDEN** (see safeguard below) |
| Available reference material | Original .java.bak audit artifacts | Same — production .java.bak files show original behavior |

---

## Drift Prevention Mechanisms

The production conversion experienced drift: agents in later batches forgot rules, applied inconsistent nullability decisions, and in one case modified files outside scope. These mechanisms are MANDATORY — they are not guidelines, they are mechanical procedures.

### Agent Re-Anchor Protocol (orchestrator-driven)

Agents cannot reliably "re-read" rules in their own context window — outputting a confirmation line doesn't cause re-reading, it's a ritual. Instead, **the orchestrator drives re-anchoring** by structuring agent prompts and mid-session injections.

**For solo agents (Phases 1, 2, 4, 5):**
The orchestrator includes the full rule set in the initial agent prompt (per the Agent Prompt Construction Template). The solo agent handles all batches within its phase autonomously — the orchestrator does NOT inject mid-session SendMessages between batches (it has no visibility into batch transitions within a running agent).

Instead, the agent's initial prompt includes this instruction: "Before starting each batch, output a one-line marker: `--- BATCH NN START ---`. This serves as a self-anchor point. After this marker, re-state the scope fence for this batch (files to create/rename) before beginning conversion work."

This is a structural prompt pattern — the act of outputting the file list forces the agent to retrieve it from its context, reducing drift in later batches.

**For parallel agents (Phase 3):**
Each parallel agent receives its complete batch list and rules upfront. Since they run independently without mid-session orchestrator messages, the full rule set MUST be in their initial prompt. The Agent Prompt Construction Template (below) ensures this.

### Orchestrator Heartbeat Checklist (executed at EVERY agent handoff)

The orchestrator executes this numbered checklist mechanically at every handoff point (sending and receiving). It is not prose to "remember" — it is a copy-paste procedure.

**When SENDING work to an agent (Handoff 1):**
```
□ 1. Constructed scope fence from batch tracker (list files, count)
□ 2. Included in agent prompt: scope fence, seed rules, nullability rules, production safeguard, delta notes
□ 3. For duplicate utility files (TestForceApp, MainActivity, JSONTestHelper): included reference Kotlin from first conversion as golden template
□ 4. For Phase 3 parallel agents: included batch 02 delta notes for utility file consistency
□ 5. Recorded handoff timestamp in timing table
```

**When RECEIVING work from an agent (Handoff 2):**
```
□ 1. Ran: git diff --name-only > /tmp/changed_files.txt
□ 2. Verified: every file in changed_files.txt is within agent's scope fence
□ 3. Verified: NO file in libs/*/src/ (production) was modified (unless operator-approved Classification B)
□ 4. Verified: .kt file count matches expected batch file count
□ 5. Verified: .java.bak file count matches expected
□ 6. Checked for pending Classification B proposals to batch for operator
□ 7. Recorded handoff timestamp in timing table
□ 8. If Phase 3 parallel: deferred all checks until BOTH agents return, then ran steps 1-7 for each
```

**Drift detection:** If the orchestrator catches itself skipping a step, it must re-run the full checklist from step 1. If an agent's output shows a scope fence violation, the orchestrator must investigate before proceeding — not dismiss it.

### Duplicate Utility File Consistency Enforcement

`TestForceApp.java`, `MainActivity.java`, and `JSONTestHelper.java` exist as independent copies in SmartStoreTest (batch 02), SalesforceSDKTest (batch 10), and MobileSyncTest (batch 17).

**Enforcement mechanism:**

1. After batch 02 completes, the orchestrator captures the Kotlin output for these three files (stripping the package declaration line) as **golden reference templates**.

2. The orchestrator includes these templates verbatim in the prompt for any agent handling batch 10 or batch 17, with the instruction: "Produce identical Kotlin for these utility files (only the package declaration differs)."

3. After batches 10 and 17 complete, the orchestrator runs:
   ```bash
   # Compare ignoring package line
   diff <(tail -n +2 libs/test/SmartStoreTest/src/.../TestForceApp.kt) \
        <(tail -n +2 libs/test/SalesforceSDKTest/src/.../TestForceApp.kt)
   ```
   If diff output is non-empty, flag for review — the agent drifted.

### Agent Prompt Construction Template

The orchestrator MUST include these materials in every conversion agent's prompt (no omissions):

```
Agent prompt materials checklist:
1. Scope fence (files to create, rename, not modify)
2. Seed Conversion Rules (from "Revised Seed Conversion Rules" section — full text)
3. Nullability rules (Section 6 — full text)
4. Production code safeguard + failure classification protocol (Section 4 + Section 5 — full text)
5. Cumulative delta notes from all prior batches in this phase
6. Production conversion lessons for corresponding library (read-only reference)
7. Deferred test log (current state — for context on what's already deferred)
8. Golden reference templates for duplicate utility files (if applicable to this agent's batches)
9. Re-anchor protocol instructions (copied above)
```

If any material is unavailable (e.g., no prior delta notes for the first batch), the orchestrator explicitly states "N/A — first batch in phase" rather than silently omitting it.

### Expedited Mid-Phase Approval for Cascading Failures

If >3 Classification B failures trace to the SAME production change within a single phase, the agent requests an expedited mid-phase approval for that single fix before continuing. This prevents cascading `@Ignore` pollution where every subsequent test fails for the same reason.

**Protocol:**
1. Agent identifies that 3+ test methods are blocked by the same production issue (e.g., `SmartStore.query()` returning `JSONArray?` instead of `JSONArray`). This may be 3+ methods in a single file (large test classes) or across files.
2. Agent presents a single expedited proposal to the operator (same format as the Cascading Failure Protocol, but mid-phase rather than waiting for boundary)
3. If operator approves: agent applies the production fix, then writes the affected test methods WITHOUT `@Ignore` (or removes `@Ignore` if already written). At the boundary, re-runs the test class(es) containing these methods to confirm they pass.
4. If operator denies: agent writes all affected test methods with `@Ignore` and continues
5. Agent records the mid-phase approval in delta notes

**Single-file batch note (e.g., batch 07, SmartStoreTest.java at 1638 lines):** When converting a large test file, the agent may discover all failures from the same production issue simultaneously (during the boundary test run, not incrementally). In this case, the agent identifies the pattern in the test output, classifies it as Classification B, and requests expedited approval — then edits the already-written .kt file to remove any `@Ignore` annotations after approval.

### Classification Ambiguity Resolution

When the boundary between Classification B and Classification C is ambiguous, apply this decision tree:

| Scenario | Classification | Rationale |
|----------|---------------|-----------|
| Production class needs `open` for test to subclass it | **B** — propose `open` | The original Java class was implicitly open; this is a mechanical omission |
| Test uses reflection to access private Java fields now Kotlin properties | **C** — fix test | Test is using non-public API; rewrite to use public API |
| Production method returns `T?` but the `.java.bak` shows it never returned null | **B** — propose `T` | Nullable annotation was overly conservative during production conversion |
| Production method returns `T?` and the `.java.bak` shows it CAN return null in edge cases | **A** — defer | The test exposed a real behavioral difference; needs deeper investigation |
| Test relies on Java checked exception in method signature | **C** — fix test | Kotlin doesn't have checked exceptions; test should catch normally |
| Production method visibility changed from `protected` (Java, accessible from same-package test) to `internal` (Kotlin, accessible within module — but test is in same module via androidTest) | **Neither** — should still compile | `internal` is visible to androidTest in the same module. If it doesn't compile, investigate the source set configuration |

---

## Recommended Structural Changes (from v1 test plan)

### 1. Drop Scout Phases — Not Needed for Tests

**Rationale (from v2 production plan, Recommendation #3):** Scouts provided marginal value in the production conversion. For tests, the value is even lower because:
- Test conversion patterns are simpler (no JVM interop annotations)
- The production conversion lessons already document all API signature changes
- Tests don't have cross-cutting callers that need discovery

**Change:** Phase 3 (SalesforceSDKTest) should use 2 parallel agents directly, not 4 sequential scouts + 3 parallel tracks. The 49 existing Kotlin test files in SalesforceSDKTest are *peers* (not callers) — they won't break when Java tests are converted.

**Recommended Phase 3 topology:**
- Agent 1: Batches 10–13 (12 files — app infra, REST tests)
- Agent 2: Batches 14–16 (11 files — auth, security, account tests)
- Both agents write their `.kt` files and rename `.java` → `.java.bak` in parallel. **Neither agent runs a build independently** — all 23 test files share one `androidTest` source set, so compilation requires all files present. The orchestrator runs the single compile + test pass after both agents complete.
- **Git staging:** Parallel agents do NOT run `git add` — only the orchestrator stages files after both agents finish. The per-batch `git add` rule (see "Incremental Staging") applies only to solo-agent phases.
- **Build errors in Phase 3:** If compilation fails after both agents complete, the orchestrator spawns a single repair agent with the full error list and both agents' delta notes. The repair agent has scope to modify any file in the SalesforceSDKTest source set. This is the only "boundary agent" in the plan.

---

### 2. Combine Phases 5–6 Into One Phase (with Isolation Caveat)

**Rationale (from v2 production plan, Recommendation #3):** SalesforceHybridTest (10 files) and SalesforceReactTest (13 files) are both small, non-security-critical, and share similar patterns (framework bridge testing). Combining them into one phase with one agent saves an operator gate and an agent spawn.

**Change:** Single agent handles batches 22–25 sequentially, building each module before moving to the next.

**Isolation caveat:** If the Cordova environment works but React Native does not (or vice versa), the agent should:
1. Complete conversion and testing for the working module
2. Complete semantic conversion (writing .kt files) for the blocked module
3. Stop at the blocked module's test execution and request operator help
4. Do NOT let one module's environment failure block the other's completion

---

### 3. Replace Full Module Test Runs with Individual Test Class Execution

**Rationale (user requirement):** The full workspace suite of instrumented tests is prohibitively expensive to run. Running `./gradlew :libs:MODULE:connectedAndroidTest` executes ALL tests in that module — too slow for iterative feedback during conversion.

**Change — granular test execution strategy:**

**During conversion (per-batch — compile only, no device needed):**
The agent does NOT run tests after each batch. It runs the compile check only:
```bash
./gradlew :libs:MODULE:assembleDebugAndroidTest
```
This catches type mismatches, import errors, and API signature problems immediately — without requiring a device. Tests run only at the boundary.

**At library boundary (selective device tests):**
Run 3-5 converted test classes individually to verify runtime behavior:
```bash
# Example: run a single converted test class
./gradlew :libs:SmartStore:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.smartstore.store.SmartStoreTest
```

**At library boundary (confidence check):**
Run 3-5 representative test classes covering different areas, not the full module:
```bash
# Example for SmartStore boundary
./gradlew :libs:SmartStore:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.smartstore.store.SmartStoreTest,com.salesforce.androidsdk.smartstore.store.DBOpenHelperTest,com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStoreTest
```

**At post-conversion (final verification):**
Run the full module suite ONCE per module, sequentially. This is the only full run:
```bash
./gradlew :libs:SalesforceAnalytics:connectedAndroidTest
./gradlew :libs:SmartStore:connectedAndroidTest
# ... etc
```

**Escalation:** If a single-class test run fails, the agent investigates and fixes. If the final full-module run reveals failures NOT seen in individual runs, those are deferred to operator review (likely test-ordering dependencies or shared state issues).

**Exception — JavaScript bridge tests (Phase 5, SalesforceHybridTest):**
The `JSTestCase`-based tests (`ForceJSTest`, `SmartStoreJSTest`, `MobileSyncJSTest`, etc.) use a suite-level initialization pattern: a single Activity launch runs ALL JavaScript tests and caches results. Individual `@Test` methods then assert on cached results. **These tests CANNOT be verified method-by-method.** They must be run as complete classes:
```bash
# Correct — runs entire class (triggers suite-level JS execution)
./gradlew :libs:SalesforceHybrid:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.phonegap.ForceJSTest
```
Do NOT attempt to run individual test methods from JS-based test classes.

**Distinguishing network failures from conversion bugs:**
Many tests (RestClientTest, OAuth2Test, SyncManagerTest, etc.) make real network calls to Salesforce APIs. When a test fails, check whether the failure is a conversion bug or an environment issue:

1. **Network/credential indicator:** Look for these exception types in the stack trace:
   - `java.net.UnknownHostException` / `java.net.ConnectException` → network down
   - `com.salesforce.androidsdk.rest.RestClient$AuthTokenRevokedException` → expired token
   - `HTTP 401` / `INVALID_SESSION_ID` → expired credentials
   - `java.net.SocketTimeoutException` → transient network issue

2. **If network/credential failure detected:**
   - Do NOT classify as a conversion bug
   - Report to operator: "Test `X` failed with network/credential error — not a conversion issue"
   - Retry once after a brief pause. If it fails again, skip and continue.
   - The operator may need to refresh `shared/test/test_credentials.json`

3. **If assertion failure or NPE:** This is likely a real conversion issue — proceed with the `.java.bak` diagnostic protocol.

---

### 4. Production Code Modification Safeguard

**Constraint:** Test conversion must NEVER modify production source code to make tests pass. This is a hard rule with no exceptions during autonomous execution.

**Implementation:**

**Scope fence addition — every agent receives:**
```
### Files you must NOT modify (without operator approval)
- Any .kt file in libs/*/src/ (production source) — see "Cascading Failure Protocol" for exception
- Any .java.bak file anywhere
- Any build.gradle.kts
- Any AndroidManifest.xml
- Any file in libs/*/res/ or libs/*/assets/
```

**If a test fails and the root cause is in production code, classify the failure:**

#### Classification A: Logic bug in production conversion
The production Kotlin code behaves *semantically differently* from the original Java (different control flow, different algorithm, missing functionality). This is a real bug.
- The agent STOPS fixing that test
- Records the failure in the **Deferred Test Log** (see below)
- Marks the test with `@Ignore("Deferred: production logic bug — see deferred-tests.md entry #N")`
- Continues converting remaining tests

#### Classification B: Cascading conversion artifact (non-logic)
The production Kotlin code is *semantically equivalent* to the original Java, but a non-logic conversion artifact (nullability declaration, visibility modifier, return type annotation, missing `open` modifier) causes tests to fail. These are the most common category — they accounted for ~425 errors during the production conversion.

**Examples of non-logic changes:**
- Return type changed from platform type to explicit nullable (`String` → `String?`) when the method never actually returns null
- Method visibility narrowed (`public` in Java → `internal` in Kotlin) when tests need access
- Property missing `open` modifier needed for test mocking/subclassing
- Class missing `open` modifier that tests need to subclass for mocking/testing

**Cascading Failure Protocol:**
1. The agent identifies the production file and specific change causing the cascade
2. The agent compares with the `.java.bak` to confirm the original Java behavior
3. The agent proposes a **minimal, non-logic fix** — only changing the type annotation, visibility, or modifier (never the algorithm or control flow)
4. The agent presents the proposed fix to the operator for review:
   ```
   CASCADING FAILURE — Operator approval requested
   
   Test: SmartStoreTest#test_given_encryptedSoup_when_query_then_decrypts
   Production file: libs/SmartStore/src/.../store/SmartStore.kt:342
   Original Java (SmartStore.java.bak:355): public JSONArray query(...)  // never returns null
   Current Kotlin: fun query(...): JSONArray?  // declared nullable but never returns null in practice
   
   Proposed fix: Change return type from JSONArray? to JSONArray
   Scope: Non-logic (type annotation only, no behavioral change)
   Impact: N callers also benefit (grep count: N)
   
   Approve / Deny / Defer
   ```
5. **If operator approves:** Agent applies the fix to production code and removes the blocking test failure. Records the change in delta notes.
6. **If operator denies or defers:** Agent marks the test `@Ignore` and logs it in the Deferred Test Log as normal.

**Async workflow — do not block on individual approvals:**
The agent does NOT stop and wait for operator approval on each Classification B failure (exception: the Expedited Mid-Phase Approval override applies when >3 failures trace to the same production change — see "Drift Prevention Mechanisms" section). For non-expedited cases:
1. When a Classification B failure is identified, the agent marks the test `@Ignore` temporarily and records the proposed fix
2. The agent continues converting remaining tests
3. At the library boundary, ALL Classification B proposals are batched and presented to the operator together:
   ```
   CASCADING FAILURES — Batch approval requested (N proposals)
   
   Proposal 1: SmartStore.kt:342 — change query() return from JSONArray? to JSONArray
   Proposal 2: SmartStore.kt:508 — change upsert() return from Long? to Long
   Proposal 3: OAuth2.kt:189 — change refreshAuthToken visibility from internal to public
   ...
   
   Approve all / Approve individually / Deny all
   ```
4. Operator reviews all proposals at once. Can approve all, deny all, or cherry-pick.
5. Agent applies approved fixes, removes `@Ignore` from unblocked tests, re-runs ALL converted test classes that import from the modified production file (not just the previously-ignored tests — the production fix could affect other tests too).
6. Denied proposals remain as deferred tests.

**Batch grouping heuristic:** If >3 Classification B failures target the same production file, present them together with the note: "These may indicate the production file's nullability/visibility strategy needs a broader revision rather than piecemeal fixes."

**Key principle:** The agent never modifies production code autonomously. Every production change requires explicit operator approval presented with the comparison against `.java.bak`.

#### Classification C: Pre-existing test fragility
The test relied on Java-specific behavior that doesn't exist in Kotlin (field initialization order, platform type lenience, etc.). Fix the test — no production change needed.

---

**Deferred Test Log format (`deferred-tests.md`):**
```markdown
# Deferred Tests — Require Operator Review

## Entry N — Classification A (production logic bug)
- **Test class:** com.salesforce.androidsdk.smartstore.store.SmartStoreTest
- **Test method:** test_given_encryptedSoup_when_query_then_decrypts
- **Failure:** NullPointerException at SmartStore.kt:342
- **Root cause hypothesis:** Production `SmartStore.query()` returns nullable where original Java returned non-null platform type. The test expects non-null.
- **Fix required in:** `libs/SmartStore/src/.../store/SmartStore.kt` (production code)
- **Reference:** Compare `SmartStore.java.bak:342` with `SmartStore.kt:342`
- **Severity:** Test-only (production callers may also be affected)
- **Phase for resolution:** Post-conversion production review

## Entry M — Classification B (cascading artifact, operator denied/deferred)
- **Test class:** com.salesforce.androidsdk.rest.RestClientTest
- **Test method:** test_given_validToken_when_sendRequest_then_succeeds
- **Failure:** Cannot access 'refreshAuthToken': it is internal
- **Production file:** libs/SalesforceSDK/src/.../auth/OAuth2.kt:189
- **Original Java:** `protected static` (accessible from same-package test)
- **Current Kotlin:** `internal` (accessible within module, but test is in different module)
- **Proposed fix was:** Change `internal` to `public` on `refreshAuthToken`
- **Operator decision:** Denied — security-sensitive method should not be public
- **Resolution:** Test rewritten to use public API path instead
```

---

### 5. `.java.bak` Reference Strategy for Diagnosing Failures

**Constraint (user requirement):** The v2 test plan must be aware that original Java sources are present as `.java.bak` for reference.

**Implementation — agent instructions:**

When a test fails after conversion, the agent follows this diagnostic sequence:

1. **Rule out environment issues first:** Check for network/credential failures (see "Distinguishing network failures from conversion bugs" in Section 3). If it's a `401`/`UnknownHostException`/`SocketTimeoutException`, it's not a conversion problem.

2. **Check the converted test:** Compare the test's `.java.bak` with the `.kt` conversion. Is the Kotlin test calling the production API correctly? If the test is wrong, fix it (Classification C territory — test bug).

3. **Check the production API via `.java.bak` comparison:**
   ```bash
   diff libs/MODULE/src/.../ClassName.java.bak libs/MODULE/src/.../ClassName.kt
   ```
   Does the production Kotlin behave differently from the original Java?

4. **Apply the Classification from Section 4:** The failure is one of A (production logic bug → defer), B (cascading non-logic artifact → propose fix to operator), or C (test fragility → fix test). Section 4 defines these classifications, the Cascading Failure Protocol, and the async batch-approval workflow. Do NOT re-derive the classification logic here — follow Section 4.

5. **Record in delta notes:** Which `.java.bak` files were consulted, which classification was applied, and what the outcome was.

---

### 6. Nullability Strategy for Tests — Stricter Than Production

**Rationale:** Tests are not consumed by external code. We can be more aggressive with Kotlin idioms.

**Rules:**
- Test method parameters: **non-null by default** unless testing null behavior explicitly
- `setUp()` fields: use `lateinit var` (not nullable) for fields always initialized in `@Before`
- Assertion results: use non-null assertions directly — if a value is null when it shouldn't be, the test SHOULD fail with a clear NPE
- Do NOT use `!!` to paper over nullable returns from production code — if production returns null unexpectedly, that's a deferred production bug (see #4)

**Example:**
```kotlin
// WRONG — masks a production nullability issue with no diagnostic info
val result = smartStore.query(querySpec)!!

// RIGHT — test fails clearly with context about what was null
val result = assertNotNull(smartStore.query(querySpec), "SmartStore.query() returned null for spec: $querySpec")
```

**Note:** Prefer `assertNotNull()` over `?: fail()` — the assertion framework provides better stack trace integration and failure reporting than manual `fail()` calls.

---

### 7. Operator Gates — Reduce to 2 Mandatory

**Rationale (from v2 production plan, Recommendation #5):** Tests are lower-risk than production code. Most gates can be informational.

| Gate | Type | When |
|------|------|------|
| After Phase 2 (SmartStoreTest) | **Mandatory** | SmartStore tests exercise encrypted storage — the most complex test suite. Review test pass rate and any deferred tests. |
| After Phase 3 (SalesforceSDKTest) | **Mandatory** | Largest phase, 49 existing Kotlin test peers, most likely to surface production bugs via OAuth/REST/auth tests. |
| After Phases 1, 4, 5-6 | Informational | Auto-proceed unless: (a) >5 Classification A deferred tests accumulated, or (b) >20 compilation errors persisted after fix attempts, or (c) any test crash (process death). Log report to delta notes and timing table; proceed without waiting. Operator reviews async and may request a pause at any time — if paused, orchestrator finishes the current batch but does not start the next phase. |

---

### 8. Build Verification — Compile First, Test Selectively

**Problem with v1 approach:** The v1 plan says "Build then Test" at each boundary. But `connectedAndroidTest` for a full module can take 10-30+ minutes on device. Running this after each of 6 phases is prohibitively expensive.

**Recommended boundary protocol:**

1. **Compile test APK** (fast, ~30 seconds): `./gradlew :libs:MODULE:assembleDebugAndroidTest`
   - If this fails → fix compilation errors (same-agent, inline)
   - This catches: import errors, type mismatches, API signature changes

2. **Run converted tests individually** (medium, ~2-5 min): Run only the tests that were just converted in this phase:
   ```bash
   ./gradlew :libs:MODULE:connectedAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.MODULE.ConvertedTest1,com.salesforce.androidsdk.MODULE.ConvertedTest2
   ```
   - If tests fail → diagnose per the `.java.bak` reference protocol (#5)
   - Fix test bugs. Defer production bugs.

3. **Verify existing Kotlin tests still compile** (fast): Only relevant for SalesforceSDKTest (49 files) and MobileSyncTest (3 files). The existing tests are peers — they should not be affected by converting other tests. But verify with the compile step.

4. **Full module test run** — only at post-conversion final verification, not during phases.

---

### 9. Incremental Staging, Crash Recovery, and Session Resume

**Same as v2 production plan, Recommendation #7:** After each batch, `git add` the new files. This preserves work in the staging area if a session terminates.

### Agent Crash Recovery Protocol

An agent may crash, time out, or stop responding mid-phase. The orchestrator must detect this and continue the work without re-doing completed batches.

**Detection:** The orchestrator detects a crash when:
- The Agent tool call returns an error or timeout
- The agent returns a partial result (mentions completing some batches but not all)
- The agent session becomes unreachable for `SendMessage`

**Assessment — determine what was completed:**

The orchestrator runs this diagnostic sequence (mechanical, not judgment):

```bash
# 1. What files changed since last commit? (staged + unstaged)
git diff --name-only HEAD -- libs/test/LIBRARYTest/
git diff --name-only --cached -- libs/test/LIBRARYTest/

# 2. What .kt files exist (converted)?
find libs/test/LIBRARYTest -name "*.kt" -not -path "*/build/*" | sort

# 3. What .java files remain unconverted?
find libs/test/LIBRARYTest -name "*.java" -not -path "*/build/*" | sort

# 4. What .java.bak files exist (successfully renamed)?
find libs/test/LIBRARYTest -name "*.java.bak" -not -path "*/build/*" | sort

# 5. Check if delta notes were written
cat test-conversion-lessons-delta-LIBRARYTest.md 2>/dev/null | tail -20
```

**Recovery decision tree:**

| Agent completed... | Orchestrator action |
|--------------------|---------------------|
| All assigned batches (all .kt exist, all .java renamed) | Proceed to boundary build. No continuation agent needed. |
| Some batches fully (N .kt + .java.bak pairs) but not all | Spawn continuation agent for remaining batches only. See below. |
| Partial batch (some .kt written, but matching .java NOT yet renamed) | Revert the partial batch: delete incomplete .kt files, restore any .java.bak → .java. Spawn continuation agent starting from the incomplete batch. |
| Nothing (no new files on disk) | Spawn a fresh agent for all assigned batches. |

**Spawning a continuation agent:**

The orchestrator constructs the continuation agent's prompt with:
1. A **reduced scope fence** — only the files that remain unconverted (exclude already-completed files)
2. The **delta notes** written by the crashed agent (if any) — these contain decisions made before the crash
3. The same materials as a fresh agent (rules, safeguards, golden templates) — per the Agent Prompt Construction Template
4. An explicit statement: "A prior agent converted batches NN–MM successfully. You are continuing from batch MM+1. Do not re-convert already-completed files."

The continuation agent executes the Re-Anchor Protocol before its first batch (reading the crashed agent's delta notes as "most recent batch" context).

**Phase 3 parallel crash handling:**

If one parallel agent crashes but the other completes:
1. Wait for the surviving agent to complete (do not interrupt it)
2. Assess what the crashed agent completed (per assessment steps above)
3. Spawn a continuation agent for the crashed agent's remaining work
4. Once the continuation agent completes, proceed to the orchestrator boundary build (both agents' files now present)

If BOTH parallel agents crash:
1. Assess each independently
2. Spawn continuation agents sequentially (not parallel — reduce risk of double-crash)
3. After both complete, proceed to boundary build

**If a continuation agent also crashes:** Stop and escalate to operator. Do not spawn a third agent for the same work — a repeated crash indicates a systemic issue (context window overflow, infrastructure failure, or a file that causes agent malfunction). The operator investigates and either resolves the root cause or completes the remaining batch manually.

**Git state after crash:**

- Completed batches have their .kt files staged (`git add` per batch)
- Incomplete batches may have unstaged .kt files or partially-renamed .java files
- The orchestrator must `git checkout -- <file>` to restore any partially-renamed files before spawning the continuation agent
- Already-staged files are safe — `git stash` will preserve them if a full reset is needed

**Resume protocol — credential revalidation:**
When resuming after a session break (especially if >4 hours have elapsed), re-validate test credentials before running any tests:
```bash
# Quick credential check — run a test that makes an authenticated API call
./gradlew :libs:SalesforceSDK:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.rest.RestClientTest \
  -Pandroid.testInstrumentationRunnerArguments.method=testGetVersions
```
Choose a test that actually hits the Salesforce REST API (not a pure-crypto or offline test — those pass regardless of credential state).

If this fails with `401`/`INVALID_SESSION_ID`/`AuthTokenRevokedException`:
- Report to operator: "Test credentials appear expired. Please refresh `shared/test/test_credentials.json` and confirm."
- Do NOT proceed with test execution until credentials are validated.
- Semantic conversion (writing .kt files, renaming .java) can continue — it doesn't require a device.

---

### 10. Simplified Lessons/Artifact Strategy

**Rationale (from v2 production plan, Recommendations #9 and #11):** The pattern registry was never consumed. The manifest was overhead. Simplify.

**Artifacts per phase:**
- Delta notes file: `test-conversion-lessons-delta-LIBRARYTest.md` (one per library, append per batch)
- Deferred test log: `deferred-tests.md` (single file, append as issues found)
- Intent log: appended to delta notes (1 line per file — which patterns applied)

**Eliminated:**
- Test pattern registry (the production lessons + production .java.bak are sufficient reference)
- Completion manifests (replaced by git diff + build status)
- Per-scout and per-track delta files (use the single library delta file)

---

## Revised Phase Structure

| Phase | Module | Files | Batches | Agent Topology | Gate |
|-------|--------|-------|---------|----------------|------|
| 1 | SalesforceAnalyticsTest | 5 | 1 (01) | Solo agent + build + test | Informational |
| 2 | SmartStoreTest | 22 | 8 (02–09) | Solo agent + build + test | **Mandatory** |
| 3 | SalesforceSDKTest | 23 | 7 (10–16) | 2 parallel agents + boundary build + test | **Mandatory** |
| 4 | MobileSyncTest | 22 | 5 (17–21) | Solo agent + build + test | Informational |
| 5 | SalesforceHybridTest + SalesforceReactTest | 23 | 4 (22–25) | Solo agent, sequential modules + build + test per module (see **Framework Environment Note** below) | Informational |
| **Total** | | **95** | **25** | **6 conversion agents + orchestrator** | **2 mandatory** |

**Batch compositions** (which files are in which batch) are unchanged from the v1 test plan (`test-kotlin-conversion-plan.md`, "Batch Progress Tracker" section). This v2 document changes phase structure, agent topology, and protocols only — not file assignments.

**IMPORTANT — this v2 document is NOT self-contained.** The orchestrator must read BOTH documents:
- `test-kotlin-conversion-plan.md` (v1): defines batch file assignments, conflict maps, baseline test infrastructure paths
- `test-kotlin-conversion-plan-v2.md` (this file): defines protocols, safeguards, phase structure, and agent topology

Before beginning execution, the orchestrator reads the v1 plan's Batch Progress Tracker to obtain file assignments per batch, then applies v2's protocols to those assignments.

**Test module isolation:** Each module's `androidTest` source set depends only on its own production module (via `androidTestImplementation`), not on other modules' test source sets. SmartStoreTest does not reference SalesforceSDKTest code, MobileSyncTest does not reference SmartStoreTest code, etc. If this assumption is violated, the compile step will surface it immediately as an unresolved import.

### Framework Environment Note — Phase 5 (Cordova & React Native Tests)

SalesforceHybridTest depends on Apache Cordova and SalesforceReactTest depends on React Native. These frameworks may require environment setup beyond the standard Android build toolchain:

**Potential dependencies:**
- Node.js + npm/yarn (for React Native test bundles, Cordova plugin tooling)
- Cordova CLI (`npm install -g cordova`)
- React Native CLI or Metro bundler
- NPM dependencies in test project directories (`npm install` in relevant paths)
- Pre-built JavaScript test bundles (may need generation before test APK build)

**Agent protocol for Phase 5:**

1. **Attempt the test APK build first.** If `./gradlew :libs:SalesforceHybrid:assembleDebugAndroidTest` or `./gradlew :libs:SalesforceReact:assembleDebugAndroidTest` fails with errors indicating missing framework dependencies (e.g., missing JS bundles, node_modules not found, Cordova platform not added), proceed to step 2.

2. **Check for setup scripts.** Look for `package.json`, `install.sh`, or setup instructions in the test directories:
   ```bash
   ls libs/test/SalesforceHybridTest/package.json
   ls libs/test/SalesforceReactTest/package.json
   find libs/test/SalesforceHybridTest -name "install*" -o -name "setup*"
   find libs/test/SalesforceReactTest -name "install*" -o -name "setup*"
   ```

3. **If setup scripts exist and are non-destructive (e.g., `npm install`):** Run them and retry the build.

4. **If setup requires interactive steps, global tool installation, or is unclear:** **STOP and request operator assistance.** Report:
   - Which module's test build failed
   - The specific error message indicating the missing dependency
   - What setup appears to be needed (based on error message and any README/docs found)
   - What the agent has already tried

   The operator will either:
   - Provide the setup commands to run
   - Perform the setup themselves and tell the agent to retry
   - Skip the module's tests (mark as deferred with reason "environment setup required")

5. **If tests compile but fail at runtime due to missing JS bundles or framework initialization:** Same protocol — stop and request operator help. Do NOT attempt to generate JS bundles autonomously (build toolchain differences, version sensitivity).

**Key principle:** The agent should never install global CLI tools (`npm install -g`, `npx`, version managers) without explicit operator approval. These affect the developer's machine state beyond the repository.

---

## Revised Seed Conversion Rules (Changes from v1)

### Removed:
- All JVM interop annotation rules (tests don't need @JvmStatic, @JvmField, @Throws — they're not called by Java code)
- Scout/pattern-registry references

### Added:
- **Rule: Use `assertNotNull()` instead of `!!` for production API returns.** If production returns nullable, the test should fail descriptively via the assertion framework, not crash with a bare NPE. For inline use within expressions, `?: fail("context")` is acceptable: `val id = result.getId() ?: fail("getId() was null")`.
- **Rule: Use `lateinit var` for `@Before`-initialized fields.** Not `var foo: Type? = null` with `!!` in every test method.
- **Rule: Never `@Suppress` a null-safety warning on a production API call.** If the production API is nullable and the test expects non-null, this is a signal to investigate (possible production bug → defer).
- **Rule: Consult `.java.bak` before assuming a test failure is a test bug.** The original Java behavior is the ground truth. If the Kotlin production code behaves differently, that's a production issue to defer.
- **Rule: Preserve all `@RunWith` annotations.** Convert `.class` to `::class` (e.g., `@RunWith(AndroidJUnit4::class)`). Do not change test runners or introduce new test frameworks during conversion. For parameterized tests, preserve the existing JUnit4 `@RunWith(Parameterized::class)` pattern.

### Preserved:
- All structural rules (class hierarchy, test method signatures, setUp/tearDown)
- All language-forced changes (casts, arrays, assertions)
- Existing Kotlin test file rules (leave as-is, naming conflict resolution)

### Additional guidance:

**Test base classes must be converted before subclasses:**
Abstract test base classes (`SmartStoreTestCase`, `ManagerTestCase`, `SyncManagerTestCase`, `ParentChildrenSyncTestCase`, `JSTestCase`, `ReactTestCase`) are placed in earlier batches than their subclasses by design. When converting a base class:
- Add the `open` modifier (Kotlin classes are final by default; test subclasses need to extend them)
- Mark `open` on any methods that subclasses override (check the Java `@Override` annotations in subclass `.java.bak` files)
- If a base class and subclass are in the same batch, convert the base class first
- After converting a base class, unconverted Java subclasses in later batches must still compile. Since Kotlin classes compile to standard JVM bytecode, Java subclasses can extend them — but only if the class and overridden methods are `open`.

**Duplicate utility files across modules:**
`TestForceApp.java`, `MainActivity.java`, and `JSONTestHelper.java` exist as independent copies in SmartStoreTest (batch 02), SalesforceSDKTest (batch 10), and MobileSyncTest (batch 17). These are separate files in separate source sets — they do NOT share code. However, the agent converting batch 10 or 17 should produce consistent Kotlin for these utilities matching what was done in batch 02 (the first conversion). Check delta notes from the earlier batch before converting the later copy.

**Existing Kotlin test utilities referenced by Java test code being converted:**
`SyncUpdateCallbackQueue.kt` (MobileSyncTest, existing Kotlin) is used by `SyncManagerTestCase.java` (batch 17). When converting `SyncManagerTestCase.java` to Kotlin, check how `SyncUpdateCallbackQueue.kt` exposes its API — the Java test may have been using it via Java interop patterns (e.g., `queue.getFirst()`) that should become idiomatic Kotlin (e.g., `queue.first()`) in the converted test. The existing Kotlin utility file itself must NOT be modified.

---

## Deferred Test Protocol — Full Specification

### When to Defer

A test is deferred when ALL of the following are true:
1. The test fails after correct conversion to Kotlin
2. The failure root cause is in production code (not the test code)
3. Fixing the failure requires modifying a file in `libs/*/src/` (production source)

### How to Defer

```kotlin
@Ignore("Deferred: production bug — see deferred-tests.md entry #N")
@Test
fun test_given_encryptedSoup_when_query_then_decrypts() {
    // Original test body preserved for reference
}
```

### Deferred Test Log Entry

Append to `deferred-tests.md`:
```markdown
## Entry N — [Phase P, Batch B]
- **Test:** `ModuleTest/ClassName#methodName`
- **Failure:** NPE at `SmartStore.kt:342` — query() returned null
- **Production file:** `libs/SmartStore/src/.../store/SmartStore.kt:342`
- **Original Java:** `SmartStore.java.bak:355` — returned empty JSONArray, never null
- **Diagnosis:** Return type changed from non-null platform type to nullable during conversion
- **Recommended fix (optional):** Change return from `JSONArray?` to `JSONArray`
```
The minimum required fields are: Test, Failure, Production file, Original Java, Diagnosis. Recommended fix and severity/impact analysis are optional — the operator performs detailed triage at the review gate.

### Post-Conversion Deferred Test Review

After all phases complete, the operator receives a summary:
```markdown
# Deferred Tests Summary
- Total tests deferred: N
- By severity: P1: X, P2: Y, P3: Z
- By production file: (grouped list)
- Recommended action: Review and batch-fix in a follow-up commit on the production branch
```

**Severity scale for deferred tests:**
- **P1:** Multiple tests (3+) blocked by the same production issue, OR the issue affects production runtime behavior (not just tests)
- **P2:** Single test blocked by a production issue isolated to that test's usage pattern
- **P3:** Test-only cosmetic issue (e.g., test relied on specific exception message text that changed)

---

## Escalation Thresholds (Revised)

| Condition | Action |
|-----------|--------|
| >5 deferred tests (Classification A) in a single phase | **Mandatory gate** — likely a systematic production logic bug |
| >10 cascading failures (Classification B) from the same production file | **Stop** — the production file likely needs a broader fix, not piecemeal approvals. Present all 10+ to operator as a batch. |
| >20 compilation errors after first fix | Stop — likely a systematic production API change not accounted for (generics, nullability, renamed members) |
| >10 test failures from the same production class | Stop — likely a production conversion bug (not individual test issues) |
| Any test crash (process death, not assertion failure) | Stop — investigate before continuing |
| Agent modifies a file in `libs/*/src/` | **Immediate violation** — revert and re-run with corrected scope fence |
| Hybrid/React test build fails with framework dependency error | **Stop and request operator help** — do not install global tools autonomously |

---

## Pre-Flight Validation (Revised)

1. **Production conversion complete** — verify all batches `[✓]` in production plan
2. **Production builds green** — `./gradlew assembleDebug` (full project, ~22s based on v1)
3. **Feature branch created** — from the production conversion branch
4. **Dry-run test build** — `./gradlew :libs:SalesforceAnalytics:assembleDebugAndroidTest` (validates test build infrastructure)
5. **Device available** — `adb devices` shows at least one connected device
6. **Baseline test verification (SELECTIVE, not full)** — run 1 representative test class from each module to confirm the test infrastructure works:
   ```bash
   ./gradlew :libs:SalesforceAnalytics:connectedAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.analytics.security.EncryptorTest
   ```
   Do NOT run the full suite as baseline — too expensive. The production plan's assertion is: "Java tests compile and run against Kotlin production code via JVM interop." One representative test per module verifies this.
7. **Production artifacts available** — `prod-conversion-lessons-SalesforceAnalytics.md` and related files exist
8. **Framework environment check (Hybrid/React)** — Verify that the Cordova and React Native test modules can at least compile their test APKs. If they fail due to missing dependencies, note this for operator resolution before Phase 5 begins (does not block Phases 1–4):
   ```bash
   ./gradlew :libs:SalesforceHybrid:assembleDebugAndroidTest 2>&1 | tail -5
   ./gradlew :libs:SalesforceReact:assembleDebugAndroidTest 2>&1 | tail -5
   ```
   If either fails: record the error. The operator will resolve framework setup before Phase 5. Phases 1–4 proceed independently.
9. **Permissions configured** — `.claude/settings.json` allows: file read/write/edit across repo, Bash commands (`./gradlew`, `find`, `grep`, `mv`, `git`, `ls`, `wc`, `diff`, `adb`)

---

## What's Different from v1 Test Plan

| Aspect | v1 Test Plan | v2 Test Plan |
|--------|-------------|-------------|
| Scout phases | 4 sequential scouts for SalesforceSDKTest | None — direct parallel agents |
| Operator gates | 6 (one per phase) | 2 mandatory + 3 informational |
| Test execution | Full module suite at each boundary | Individual class runs during phases; full suite only at post-conversion |
| Production code modification | "Fix the test" (ambiguous) | **Prohibited without operator approval**; non-logic cascading fixes may be proposed via Cascading Failure Protocol; logic bugs always deferred |
| `.java.bak` usage | Mentioned as audit trail | Active diagnostic tool with protocol |
| Pattern registry | Separate test-conversion-patterns.md | Eliminated — use production lessons + delta notes |
| Failure classification | Not addressed | 3-tier: test bug / production bug / pre-existing fragility |
| Nullability approach | Conservative nullable | Strict non-null for test fields; `assertNotNull()` for production API calls |
| Agent topology (Phase 3) | 4 scouts + 3 tracks + boundary = 8 agents | 2 parallel + boundary = 3 agents |
| Crash recovery | Not addressed | Incremental git staging per batch + automated continuation agent protocol with diagnostic assessment, reduced scope fence, and delta note inheritance |
| Artifact count | 20 files | ~8-10 files |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Production cascading artifact surfaces during test conversion | High (based on v1: 100+ `!!` added to production callers) | Medium — tests fail until resolved | Cascading Failure Protocol: agent proposes non-logic fix to operator; `.java.bak` comparison proves equivalence; if denied, deferred |
| Production logic bug surfaces during test conversion | Low-Medium | High — indicates shipped bug | Always deferred via Classification A; operator reviews at gate; never fixed autonomously |
| Test depends on Java-specific behavior (field init order, platform types) | Medium | Low — fix in test | Agent has full context to recognize and fix |
| Full test suite reveals ordering-dependent tests not caught by individual runs | Medium | Low — cosmetic test fix | Run full suite at post-conversion only; fix ordering issues then |
| Agent accidentally modifies production code | Low (scope fence is explicit) | High — could mask bugs | Hard constraint in scope fence; orchestrator verifies `git diff --name-only` against allowed list |
| Existing Kotlin test files break after conversion | Very Low (they're peers, not callers) | Low | Compile check includes all files in test source set |
| Deferred test count becomes unmanageable (>20) | Low | High — signals production bugs | Escalation threshold at 5 per phase; mandatory gate triggers |
| Cordova/React Native environment not set up | Medium | Medium — Phase 5 blocked | Pre-flight check identifies early; operator resolves before Phase 5; Phases 1–4 unaffected |
