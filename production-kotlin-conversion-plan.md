# Production Java → Kotlin Conversion Plan

**Date:** 2026-05-17
**Branch:** feature/java-to-kotlin-production-migration
**Goal:** Convert all 126 production Java files (.java) to idiomatic Kotlin
**Total files to convert:** 126 .java files — 117 library files + 9 sample app files
**Total Java lines:** ~31,200 lines across 126 .java files
**Existing Kotlin files:** 125 production .kt files already exist and must be preserved/integrated
**Audit goal:** Retain all original .java files on disk (renamed to `.java.bak`) as unreferenced audit artifacts for post-conversion verification

---

## How to Execute This Plan

### First run
Provide this file to Claude with the instruction:

> "Execute the Java→Kotlin production conversion plan in production-kotlin-conversion-plan.md"

### Resuming after session termination
Provide this file to Claude with the instruction:

> "Resume the Java→Kotlin production conversion from the plan in production-kotlin-conversion-plan.md"

Claude should:
1. Read this plan to understand the approach and batch structure
2. Check the **Batch Progress Tracker** below to find the last completed batch
3. Read the most recent lessons-learned file(s) to load accumulated knowledge:
   - Latest **cumulative** lessons file: `prod-conversion-lessons-LIBRARY.md` (for last completed library)
   - Latest **delta** file: `prod-conversion-lessons-delta-LIBRARY.md` (for in-progress library, if any)
   - **Pattern registry**: `prod-conversion-patterns.md` (machine-readable verified patterns — always read this)
   - **Phase 2 variant:** If resuming mid-Phase 2, read all scout and track-specific delta files that exist (`prod-conversion-lessons-delta-SalesforceSDK-scoutX.md` and `trackA.md` through `trackD.md`). Check which scouts/tracks have completed (all batches `[✓]`) vs. which are in-progress or not started.
4. If the orchestrator is restarting and the batch tracker shows all batches `[✓]` for the current library but no library boundary timestamp exists, re-run the **orchestrator verification checklist** before proceeding to the boundary.
5. Resume from the next incomplete batch (or next incomplete track, for Phase 2)

---

## Autonomous Execution Model

### Orchestrator role definition
The orchestrator is the top-level Claude session. It manages workflow, spawns agents, and verifies work. It **never** performs conversion work itself.

**The orchestrator DOES:**
- Run pre-flight validation (build, test, environment checks — these are verification, not conversion)
- Construct scope fences for each agent from the batch tracker
- Spawn sub-agents with complete prompts (plan context, lessons, scope fence, conflict map entries, **rule addenda**, **accuracy briefing**, **pattern registry**)
- Receive agent completion manifests
- Run the orchestrator verification checklist (mechanical file/scope/annotation checks)
- Record timestamps in the Execution Timing table
- Compute **accuracy briefing** from build/test results (arithmetic, not judgment) and include in next agent's prompt
- Include **rule addenda** (drafted by the boundary agent) in the next agent's prompt
- Include the **pattern registry** file in every agent's prompt materials
- Provide all track delta files to the Phase 2 boundary agent (the boundary agent merges them into the cumulative lessons file as part of its authoring work — the orchestrator does not author lessons content)
- Generate the operator review report (compiling data from agent manifests, verification results, and build/test output)
- Manage operator gates (present report, wait for decision, relay adjustments)
- Manage git commits at library boundaries

**The orchestrator DOES NOT:**
- Convert any Java file to Kotlin
- Fix build errors
- Edit `build.gradle.kts` or any source file
- Write or modify any `.kt` file
- Apply seed conversion rules, JVM interop annotations, or conflict map decisions
- Make judgment calls about conversion patterns — these belong to agents

**Bright line:** If a task requires reading a Java `.java` file and producing Kotlin code, it is agent work. If a task requires running a command and checking its output against an expected value, it is orchestrator work.

### Handoff protocol at each phase

The orchestrator and agents interact at three handoff points per library. The protocol is explicit so neither side drifts into the other's work.

**Handoff 1 — Orchestrator → Agent (start of phase):**
The orchestrator constructs a prompt containing:
- The agent's assigned batches (from the batch tracker)
- The scope fence (files to create, files to rename, files to not touch)
- The cumulative lessons file from the prior library (or nothing for Phase 1)
- The **pattern registry** (`prod-conversion-patterns.md`) — the agent greps this for verified patterns
- The **rule addenda** from prior phases (or nothing for Phase 1) — these override or extend seed rules
- The **accuracy briefing** from prior phases (or nothing for Phase 1) — focuses attention on high-risk patterns
- The conflict map entries relevant to this library
- The instruction to return a completion manifest when semantic conversion is done

The orchestrator spawns the agent and waits.

**Handoff 2 — Agent → Orchestrator → Agent (after semantic conversion, before library boundary):**
The agent completes all batch conversions and returns its completion manifest. It **pauses** — it does not start the library boundary work yet.

**Implementation note:** This is a single agent session with a manifest checkpoint. The agent returns from the `Agent` tool call with the manifest. The orchestrator inspects the result, runs verification via `Bash`, then sends a `SendMessage` to the same agent to resume. The agent retains its full conversion context when it resumes for the library boundary — this is critical for efficient build error repair.

The orchestrator:
1. Receives the manifest
2. Runs the verification checklist (see "Orchestrator verification checklist")
3. If checks pass → tells the agent to proceed to the library boundary (build, test, fix)
4. If checks fail → sends the specific failures back to the agent with instructions to fix **only** the verification issues. The agent fixes, returns an updated manifest, and the orchestrator re-verifies.

**Orchestrator restart recovery:** If the orchestrator session dies between receiving the manifest and telling the agent to proceed, the agent's work is on disk but verification hasn't been recorded. On restart: if the batch tracker shows all batches `[✓]` for the current library but no library boundary timestamp exists, the orchestrator re-runs the verification checklist before spawning a new agent for the boundary work (since the original agent session is lost).

**Handoff 3 — Agent → Orchestrator (after library boundary):**
The agent completes the library boundary (build, test, fix, lessons, self-review, commit) and returns its final report including:
- Build/test results
- The operator review report
- The cumulative lessons file

The orchestrator:
1. Compiles the operator review report (adding its own verification results section)
2. Presents the report at the operator gate
3. Waits for operator decision
4. On "proceed" — spawns the next phase's agent
5. On "adjust" — incorporates operator adjustments into the next agent's prompt
6. On "stop" — halts and reports to operator

### Phase 2 handoff (parallel tracks)

Phase 2 modifies the handoff protocol for parallel execution:

**Handoff 1 (parallel):** The orchestrator spawns 4 agents simultaneously, each with its own scope fence and track assignment. All 4 receive the same cumulative lessons from Phase 1.

**Handoff 2 (parallel, converging):** The orchestrator waits for all 4 agents to return their manifests. It then:
1. Runs the verification checklist for each track independently
2. Runs the cross-track isolation check (no file overlap between tracks)
3. If all pass → spawns a **single boundary agent** to handle the library boundary
4. If any fail → sends failures back to the specific track agent for repair

**Boundary agent for Phase 2:** The orchestrator spawns a **fresh boundary agent** with:
- The merged delta notes from all 4 tracks
- The cumulative lessons from Phase 1
- The full conflict map for SalesforceSDK
- Instructions to perform only the library boundary steps (Loop 2 steps 1–14)
- A scope fence that includes `build.gradle.kts` and all converted `.kt` files (for build error repair)

**Handoff 3 (Phase 2):** Same as the standard protocol — the boundary agent returns, the orchestrator compiles the report, presents at Operator Gate 2.

### Agent-per-library architecture (Phases 1, 3–7)
For these phases, the orchestrator spawns one sub-agent per library phase, **sequentially**. The same agent handles both semantic conversion and the library boundary — but with a verification pause between the two.

**Semantic conversion phase (agent works autonomously):**
1. Reads this plan (batch structure, seed rules)
2. Reads the latest `prod-conversion-lessons-LIBRARY.md` (cumulative, for last completed library — if exists)
3. Reads `prod-conversion-lessons-delta-LIBRARY.md` for the in-progress library (if exists)
4. **Reads 2–3 existing Kotlin files** from the current library (or a sibling library if none exist) to calibrate idiom style
5. **Receives its scope fence** from the orchestrator
6. **Records timestamp** in the Execution Timing table
7. Converts all batches in its assigned library, following the **re-anchor step** before each batch (see Loop 1 step 0)
8. Appends delta notes to `prod-conversion-lessons-delta-LIBRARY.md` after each batch
9. **Returns its completion manifest** to the orchestrator and **pauses**

**Verification pause (orchestrator runs checks — see Handoff 2):**
The orchestrator runs the verification checklist. If checks pass, it tells the agent to continue. If checks fail, it sends specific issues back to the agent for repair.

**Library boundary phase (agent resumes after verification passes):**
10. **Records timestamp** (`Phase N — library boundary started`)
11. Performs all library boundary steps (Loop 2 steps 1–14)
12. **Records timestamp** (`Phase N — library boundary complete`)
13. Returns final report to orchestrator and **stops**

### Agent architecture for Phase 2 (scouts + parallel tracks + boundary agent)
Phase 2 uses a three-step structure: scout, parallel, boundary.

1. The orchestrator spawns **4 sequential scout agents** (one per track domain), each converting a single batch: 04, 07, 12, 17
2. The orchestrator collects scout discoveries, updates the pattern registry, drafts scout addenda
3. The orchestrator spawns **4 parallel track agents** (Tracks A–D), each with its own scope fence covering the remaining batches (05–06, 08–11, 13–16, 18–20). Each receives the scout addenda + updated registry.
4. Each track agent does semantic conversion only (steps 1–9 above) and returns a manifest
5. The orchestrator verifies all 4 manifests + cross-track isolation
6. The orchestrator spawns a **fresh boundary agent** to handle the library boundary
7. The boundary agent performs Loop 2 steps 1–14 and returns the final report

### Autonomous within libraries, gated between libraries
Batch-level conversion within a library runs **unattended** — all semantic conversion, delta notes, and progress tracking proceed without pause. This is ~95% of the working time.

**Operator review gates** occur at **7 points only** — one per library boundary, after the build pass completes and the lessons file is written. The operator reviews the report and decides: **proceed**, **adjust** (change strategy for next library), or **stop** (investigate a systemic issue).

Lessons files and progress tracker updates serve as **recovery checkpoints**. If a session is terminated mid-run, the plan can be resumed from the last completed batch.

### Agent scope fence
Each sub-agent receives an explicit scope fence in its prompt — a list of files it **may** create, rename, or modify and a list it **must not** touch.

The orchestrator constructs the scope fence from the batch tracker:

```
## Scope Fence — Phase N / Track X

### Files you MUST create (one .kt per .java)
- libs/LIBRARY/src/.../ClassName.kt  (from ClassName.java, batch NN)
- ...

### Files you MUST rename (original Java → .java.bak)
- libs/LIBRARY/src/.../ClassName.java → ClassName.java.bak  (batch NN)
- ...

### Files you may MODIFY (plan file, delta notes only)
- production-kotlin-conversion-plan.md  (batch tracker updates only)
- prod-conversion-lessons-delta-LIBRARY[-trackX].md  (append delta notes)

### Files you must NOT modify
- Any .kt file listed as "Leave as-is" or "Pure Kotlin" in the conflict map
- Any file in batches not assigned to you
- Any build.gradle.kts (modified at the library boundary, not during batch conversion)
```

### Agent completion manifest
Each sub-agent must return a **structured manifest** (not prose) when it completes.

```
# Agent Completion Manifest — Phase N [Track X] (LIBRARY)

## Files converted
| Batch | Java file | Kotlin file written | Lines (Java → Kotlin) | Conflict map consulted | Legacy suffix applied |
|-------|-----------|--------------------|-----------------------|----------------------|----------------------|
| NN | ClassName.java | ClassName.kt | NNN → NNN | Yes/N/A | Yes/No |

## Rules applied
- Rule 14 (@JvmStatic): Applied to N files
- Rule 15 (@JvmField): Applied to N files
- Rule 16 (@JvmOverloads): Applied to N files
- Rule 17 (@Throws): Applied to N files: (list)
- Rule 22 (existing Kotlin untouched): N files in scope, all untouched

## Files NOT touched (per rules 22, 23)
- (list each existing Kotlin file that was in scope but correctly left alone)

## Scope fence violations
- None / (list any files modified outside scope, with justification)

## Delta notes
- File: prod-conversion-lessons-delta-LIBRARY[-trackX].md
- Batches covered: NN, NN+1, ...
- New patterns discovered: N
- Pitfalls encountered: N

## Batch tracker updates
- Batches marked [✓]: NN, NN+1, ...

## Issues requiring orchestrator attention
- None / (list any unresolved issues)
```

### Orchestrator verification checklist
After each sub-agent returns its manifest, the orchestrator runs these checks **before** proceeding to the library boundary build. These are mechanical — no judgment calls.

**Step 0 — Capture changed file list once:**
Run `git diff --name-only > /tmp/conversion_changed_files.txt` and reuse this file for all checks below.

**File existence (per manifest):**
1. For each row in the manifest's "Files converted" table, verify the `.kt` file exists on disk: `test -f libs/.../ClassName.kt`
2. Verify the file is non-empty: `wc -l libs/.../ClassName.kt` should be > 0
3. Verify the corresponding `.java.bak` exists: `test -f libs/.../ClassName.java.bak`

**Scope compliance (from changed file list):**
4. Verify every file in `/tmp/conversion_changed_files.txt` appears in the agent's scope fence. Flag any file outside scope.
5. For parallel tracks (Phase 2): collect changed file lists from all 4 tracks and verify **zero overlap**.

**Conflict map compliance (from changed file list):**
6. Verify no "Leave as-is" Kotlin file appears in the changed file list.

**JVM interop annotation check:**
7. For **every** converted `.kt` file that was a public class in Java, verify that appropriate JVM interop annotations are present. Run: `grep -rL "@JvmStatic\|@JvmField\|@JvmOverloads\|@Throws\|companion object" libs/LIBRARY/src/**/*.kt` filtered to only converted files. Files with no JVM annotations AND no companion object may need review. This is the highest-risk drift for a public SDK.

**Delta notes and tracker:**
8. Verify delta notes file exists and is non-empty
9. Verify batch tracker shows `[✓]` for all assigned batches

**Manifest consistency:**
10. Verify the count of "Files converted" rows matches the expected file count for the assigned batches

If **any check fails**, the orchestrator must investigate before proceeding.

### Operator review report (generated at each library boundary)
After each library boundary's build+test cycle, the agent generates a concise report:

```
# Operator Review — Phase N (LIBRARY)

## Orchestrator Verification Results
- All verification checks passed: yes/no
- File existence: N/N confirmed
- Scope fence violations: N (list, or "none")
- Cross-track overlap (Phase 2 only): none / (list)
- JVM interop spot check: N/N files had correct annotations
- Issues found and resolved: (list, or "none")

## Conversion Scope
- Java files converted: N
- Kotlin files written: N
- Original .java files renamed to .java.bak: N
- Existing Kotlin files in this library: N
  - Left as-is (compiled without changes): N
  - Required updates after base class conversion: N (list)

## Build Results
- First-pass build errors: N
- Errors after repair: N (of 3 max attempts)
- Error categories: (list top categories)

## build.gradle.kts Changes
- Changes made: (list any kotlin plugin additions, source set updates)
- Flagged for review: yes/no

## Security-Critical Files
- Files flagged for human review: (list — see escalation rules)

## Self-Review Summary
- Conversion accuracy: N% of files compiled without errors on first pass
- Key lessons learned: (1–3 bullet points)
- Recommended strategy adjustments for next library: (if any)

## Decision Required
- [ ] **Proceed** to Phase N+1
- [ ] **Adjust**
- [ ] **Stop** (investigate before continuing)
```

### Stopping conditions (immediate, within a library)
Claude stops **immediately** and reports to the operator if:
- An escalation threshold is hit (see below)
- An unrecoverable error is encountered (e.g., Gradle project corruption, missing source files)
- Permission prompts blocked autonomous operation

### Escalation thresholds

**Build error thresholds:**
- **>20 build errors** after the first repair attempt → likely a wrong JVM interop strategy or fundamental type mapping error. Stop and report.
- **>10 build errors of the same category** after the first repair → systematic pattern that needs a strategy change.

**Security-critical file escalation:**
The following files are **always flagged for operator review** in the library boundary report:
- `OAuth2.java` → `OAuth2.kt`
- `UserAccountManager.java` → `UserAccountManager.kt`
- `UserAccount.java` → `UserAccount.kt`
- `KeyStoreWrapper.java` → `KeyStoreWrapper.kt`
- `SalesforceKeyGenerator.java` → `SalesforceKeyGenerator.kt`
- `PushNotificationDecryptor.java` → `PushNotificationDecryptor.kt`
- `RestClient.java` → `RestClient.kt`
- `ClientManager.java` → `ClientManager.kt`
- `HttpAccess.java` → `HttpAccess.kt`
- `AuthenticatorService.java` → `AuthenticatorService.kt`
- `Encryptor.java` → `Encryptor.kt` (SalesforceAnalytics)
- `SmartStore.java` → `SmartStore.kt`
- `DBOpenHelper.java` → `DBOpenHelper.kt`

These files handle OAuth2 flows, token storage, credential handling, keystore access, encryption, and encrypted storage — the CLAUDE.md escalation categories.

### Permission requirements
Before starting execution, ensure `.claude/settings.json` has permissions for:
- File read/write/edit across the repo
- Bash commands: `./gradlew`, `find`, `grep`, `mv`, `git`, `ls`, `wc`
- These should be configured before the first run

---

## Critical Context: Production Code Differences from Test Migration

This plan converts **production** Java — fundamentally different from a test-only migration:

### Public API surface
- Production files define the SDK's **public API**. Every public class, interface, method, field, and constant is potentially consumed by external Java developers.
- Kotlin conversions must preserve the Java-visible API surface using `@JvmStatic`, `@JvmField`, `@JvmOverloads`, `@Throws`, and `@JvmName` annotations so that existing Java consumers (including test targets and external apps) continue to compile.
- Android SDK classes registered in `AndroidManifest.xml` (Activities, Services, BroadcastReceivers) must keep the same fully-qualified class name.

### Coexistence with existing Kotlin files
- 125 production `.kt` files already exist and must be preserved. Some are pure Kotlin additions; others extend or reference Java classes being converted.
- When converting a Java class that has Kotlin callers, the Kotlin callers should continue to work automatically through JVM interop. Verify at library boundaries.
- There is only one same-class name overlap in production: `Features.java` exists in two libraries (SalesforceSDK, SmartStore) — but these are in different packages (`com.salesforce.androidsdk.app` vs `com.salesforce.androidsdk.smartstore.app`), so no conflict. (MobileSync's `Features` is already Kotlin.)

### Dependency ordering is critical
- Libraries depend on each other: `SalesforceAnalytics` → `SalesforceSDK` → `SmartStore` → `MobileSync` → `SalesforceHybrid` / `SalesforceReact`. Each upstream library must build successfully before downstream conversions begin.
- Within a library, base classes and interfaces must be converted before their subclasses/implementors.

### Java/Kotlin interop within a library during conversion
- Java and Kotlin source files coexist seamlessly within the same Gradle module — Gradle compiles both and they can reference each other freely.
- However, once a `.java` file is renamed to `.java.bak` and replaced by `.kt`, the class is now Kotlin. Other Java files in the same module that reference it will still work (Kotlin compiles to JVM bytecode identical in visibility to Java).
- The semantic conversion renames `.java` → `.java.bak` and writes `.kt` per-file during batch work. Builds only happen at library boundaries after ALL files in the library are converted.

### Tests are the behavioral contract
- Existing tests (both Java and Kotlin) must continue to pass after each library conversion. Java test code can call Kotlin production code seamlessly through JVM interop.
- Test failures indicate a conversion bug that must be fixed in the production Kotlin code.

---

## Original File Retention for Audit

### Goal
Every original `.java` file is **kept on disk** alongside its Kotlin replacement by renaming its extension to `.java.bak`. Gradle only compiles files with `.java` and `.kt` extensions, so `.java.bak` files are automatically excluded from compilation. This creates a side-by-side audit trail: for any converted `.kt` file, the original Java source is in the same directory for comparison.

### Why rename extension (not move to an archive directory)
- **Trivial diffing:** `diff ClassName.java.bak ClassName.kt` or any side-by-side tool works immediately — no path mapping needed.
- **Git history preserved:** `git log --follow ClassName.java` still works since the file was renamed, not deleted. Git detects renames with high similarity.
- **No directory structure to create or maintain:** No `_originals/` mirror tree that rots if paths change.
- **Automatic build exclusion:** Gradle ignores `.java.bak` files — no `exclude` patterns needed in `build.gradle.kts`.
- **Easy bulk cleanup later:** `find libs -name "*.java.bak" -delete` removes them all.

### Why rename is necessary
Gradle auto-discovers source files by extension from the configured source directories. Having both `Foo.java` and `Foo.kt` defining the same class in the same package would cause a **duplicate class definition** build error. Renaming the extension is the simplest solution that keeps files alongside their replacements.

### Implementation during semantic conversion
At each file conversion during batch work:
1. **Read** `ClassName.java`
2. **Write** `ClassName.kt` (the converted Kotlin file)
3. **Rename** `ClassName.java` → `ClassName.java.bak`

This is done per-file, not at the library boundary. The library boundary then just builds and tests the fully converted module.

### Post-audit cleanup
After a future verification pass confirms all conversions are accurate:
1. Delete all `.java.bak` files: `find libs -name "*.java.bak" -delete`
2. Commit the removal as a separate, clearly-labeled commit

---

## Module-Isolated Gradle Builds

Full builds for this repository are **very time-consuming**. All Gradle commands in this plan target **individual modules** rather than the entire project.

### Build commands (per module)
```bash
# Build a single library (debug variant)
./gradlew :libs:SalesforceAnalytics:assembleDebug
```

### Why module isolation matters
- `./gradlew assembleDebug` builds ALL modules (~20 min+). `:libs:SalesforceAnalytics:assembleDebug` builds only that module and its dependencies (~2–5 min).
- **No downstream compile checks.** Each downstream module will be semantically converted in order. The converting agent reads actual upstream Kotlin signatures, so API changes are absorbed during each module's own conversion.
- **No test runs during this plan.** Tests are Java and will be converted by the separate test plan. Running them now would surface JVM interop issues that the test conversion handles naturally by writing idiomatic Kotlin test code.

### Exception: post-conversion full build
The only full-project build in this plan is the final post-conversion verification:
```bash
./gradlew assembleDebug
```

---

## Pre-flight Validation

Before starting batch 01, the orchestrator must verify the environment is in a known-good state.

1. **Git working tree is clean** — `git status` shows no uncommitted changes (except this plan file and any lessons files from prior runs).
2. **Create the feature branch** — `git checkout -b feature/java-to-kotlin-production-migration` (or verify it already exists and is checked out).
3. **Submodule dependencies are present** — verify `install.sh` has been run: `test -d external`. If missing, run `./install.sh` first.
4. **All libraries build clean (isolated)** — build each library module sequentially:
   ```bash
   ./gradlew :libs:SalesforceAnalytics:assembleDebug
   ./gradlew :libs:SalesforceSDK:assembleDebug
   ./gradlew :libs:SmartStore:assembleDebug
   ./gradlew :libs:MobileSync:assembleDebug
   ./gradlew :libs:SalesforceHybrid:assembleDebug
   ./gradlew :libs:SalesforceReact:assembleDebug
   ```
   If any library doesn't build, the conversion cannot proceed — document and stop.
5. **Kotlin plugin availability** — verify each library's `build.gradle.kts` includes the `kotlin-android` plugin. All 6 libraries already have this plugin configured. Confirm with: `grep -l "kotlin-android" libs/*/build.gradle.kts` (expect 6 matches).
6. **Permissions are configured** — verify `.claude/settings.json` has the required permissions.

Record the pre-flight results in the Execution Timing table.

---

## Commit Strategy

Intermediate commits happen at each library boundary, after the operator approves at the gate.

### Commit points
- **After pre-flight validation:** Commit the plan file. Message: `"Add production Java→Kotlin conversion plan"`
- **Before each operator gate (after build pass):**
  ```
  Convert LIBRARY production Java to Kotlin (Phase N/7)

  - N files converted to Kotlin
  - Original .java renamed to .java.bak for audit
  - N existing Kotlin files verified compatible
  - [build.gradle.kts changes: ...] (if any)
  - [Security-critical files: ...] (if any)
  ```
- **After post-conversion validation:** `"Verify clean build after production Java→Kotlin conversion"`

### Rollback
If a library conversion needs to be reverted:
1. Rename `.java.bak` → `.java` for all files in that library
2. Delete the `.kt` replacements
3. A single `git revert` of the library commit accomplishes this

---

## Approach: Incremental Semantic Conversion with Two Learning Loops

### Large file isolation rule
Files over 1,000 lines get their own batch or are paired with at most 1–2 small files. The large files isolated this way are:
- `SmartStore.java` (1,684 lines) — batch 22 (solo)
- `UserAccount.java` (1,106 lines) — batch 17 (solo)
- `RestRequest.java` (1,071 lines) — batch 14 (solo)
- `OAuth2.java` (1,034 lines) — batch 10 (solo)
- `RestClient.java` (916 lines) — batch 15 (solo)

### Uncertain API verification rule
When converting a method and the correct Kotlin signature is ambiguous, **do not guess**. Instead:
1. Check how existing Kotlin code (in the same or downstream modules) calls the method
2. Check for `@NonNull`/`@Nullable` annotations in the Java source to determine optionality
3. Run `grep -rn "methodName" libs/LIBRARY/ --include="*.kt"` to see how existing Kotlin code calls the method
4. Verify JVM interop requirements — will existing Java callers (tests, other Java files in downstream libraries, external consumers) still work?

### JVM interop annotation strategy
Since this is a **public SDK consumed by external Java developers**, Kotlin code must maintain Java-callable APIs:
- **Companion object methods** that were `static` in Java must be annotated `@JvmStatic`
- **Public fields** that were accessed directly in Java should use `@JvmField` to suppress getter/setter generation
- **Functions with default parameters** that are called from Java need `@JvmOverloads`
- **Functions that throw checked exceptions** need `@Throws(ExceptionType::class)` — Java callers expect checked exceptions
- **Property/method names** that conflict with Kotlin keywords or would produce different JVM signatures need `@JvmName`
- **Interfaces with default implementations** need `@JvmDefault` or must be compiled with `-Xjvm-default=all` (check existing project configuration)
- **Enum values** using `entries` property — maintain `values()` for Java compatibility

### Loop 1: Semantic batch learning (per batch)
Files are converted in batches of ~3–6 files. For each batch:

**Before converting (re-anchor step):**
0. **Re-read rules** — Before each batch, re-read the conflict map entries for files in this batch and the latest delta notes from prior batches in this library. For **batch 3+** within a library (or any batch within a Phase 2 track), also re-read: (a) the structural rules (1–13), (b) the JVM interop rules (14–18), (c) the language-forced change rules (19–21). Drift increases with context length; this step is the primary defense.

**Convert:**
1. **Read** each Java file in the batch
2. **Check** for existing Kotlin files that interact with the Java class. Consult the **conflict map**.
3. **Convert** semantically to idiomatic Kotlin, applying all known patterns. When uncertain about an API mapping, verify against existing Kotlin code via grep before writing.
4. **Write** the `.kt` replacement file. **Rename** the `.java` → `.java.bak`. Only write files listed in the agent's scope fence.

**Record:**
5. **Append** delta notes to `prod-conversion-lessons-delta-LIBRARY.md`
6. **Update** this plan's Batch Progress Tracker (mark batch `[✓]`)
7. **Log** a status summary: files converted, cumulative progress, key observations, permission gaps
8. **Continue** immediately to the next batch — no pause

### Loop 2: Build, test, and learn (per library boundary)
After all batches in a library are semantically converted:

**Verification gate (orchestrator runs this — agent is paused):**
0. Orchestrator receives completion manifest, runs verification checklist.
0a. If checks fail → sends failures back to agent.
0b. If all pass → tells agent to proceed.

**Library boundary build:**
1. **Verify `build.gradle.kts`** has the `kotlin-android` plugin (expected: already present in all 6 libraries).
2. **Build** the library module in isolation: `./gradlew :libs:LIBRARY:assembleDebug`
3. **Verify existing Kotlin files** — if any existing Kotlin files fail to compile after the base class conversion, fix them minimally. Record each file that needed changes vs. compiled as-is.
4. **Assess build errors against escalation thresholds**
5. **Fix** compilation errors (up to 3 repair attempts)
6. **Update pattern registry** (`prod-conversion-patterns.md`)
7. **Write** the cumulative lessons file: `prod-conversion-lessons-LIBRARY.md`
8. **Draft rule addenda** for the next phase
9. **Perform plan self-review**
10. **Generate operator review report**
11. **Commit** the library conversion (see "Commit strategy")

**What is NOT done at library boundaries:**
- **No downstream compile checks.** Each downstream module will absorb API changes during its own semantic conversion — the converting agent greps upstream `.kt` files for current signatures.
- **No test runs.** Tests remain Java until the separate test plan converts them. JVM interop issues surface and are fixed there.

### Lessons-learned file strategy

**Within a library:** Each batch appends **delta-only** notes to `prod-conversion-lessons-delta-LIBRARY.md`.

**At library boundaries:** Agent writes a **cumulative lessons file** `prod-conversion-lessons-LIBRARY.md`.

**Cumulative lessons file format** (`prod-conversion-lessons-LIBRARY.md`):
```
# Production Conversion Lessons — Through LIBRARY

## Cumulative API Migration Patterns
## JVM Interop Patterns
## Conversion Pitfalls Discovered
## Kotlin Idiom Preferences
## Compiler-Discovered Corrections
## Existing Kotlin Integration Notes
## Gradle Build Notes
## Permission Gaps
```

### Pattern registry — machine-readable knowledge base

File: `prod-conversion-patterns.md`

```markdown
# Pattern Registry

| # | Java Pattern | Kotlin Pattern | Rule | Discovered | Verified | Notes |
|---|-------------|---------------|------|-----------|----------|-------|
| 1 | `synchronized(lock) { ... }` | `synchronized(lock) { ... }` | 26 | P1 B01 | P1 build | Kotlin has same syntax |
| 2 | `public static final String FOO = "bar"` | `const val FOO = "bar"` (in companion) | 8 | P1 B01 | P1 build | Use @JvmField if Java callers access directly |
```

### Rule injection — orchestrator augments agent prompts

Same structure as the lessons-learned approach: rule addenda accumulate across phases, with corrections and new patterns.

### Accuracy briefing — quantitative feedback per phase

Same structure: orchestrator computes accuracy metrics and includes in next agent's prompt.

---

## Seed Conversion Rules

### Structural rules
1. **Imports:** Remove wildcard imports; Kotlin uses explicit imports. Drop `import android.` duplicates that Kotlin provides through stdlib.
2. **Class structure:** `public class Foo extends Bar implements Baz` → `class Foo : Bar(), Baz`. Add `open` modifier if the class has subclasses within the SDK.
3. **Interfaces:** `public interface Foo` → `interface Foo`. Mark methods `fun` (no `public` needed — Kotlin default is public).
4. **Inner classes:** `static class Inner` → `class Inner` (nested by default in Kotlin). Non-static `class Inner` → `inner class Inner`.
5. **Properties and nullability:** 
   - Fields with `@NonNull` → non-optional property
   - Fields with `@Nullable` → optional property (`?`)
   - Fields without annotation → check usage; default to nullable if uncertain
   - `private` fields with getters/setters → Kotlin properties with appropriate visibility
6. **Methods:** Direct translation; drop `public` (Kotlin default), add `override` for overrides.
7. **Error handling → Kotlin idioms:** `try/catch` → `try/catch` (same syntax). `throws` declaration → `@Throws(ExceptionType::class)` for Java callers.
8. **Constants:** `public static final String FOO = "..."` → `companion object { const val FOO = "..." }` with `@JvmField` or `@JvmStatic` where needed for Java compatibility.
9. **Enums:** `public enum Foo { A, B, C }` → `enum class Foo { A, B, C }`. If referenced from Java, ensure `values()` remains available.
10. **Callbacks and listeners → Lambdas/SAM:** Functional interfaces remain as SAM interfaces. Anonymous inner classes implementing them → lambda syntax where applicable.
11. **Singletons:** `private static Foo instance; public static Foo getInstance()` → `companion object { @JvmStatic val instance: Foo by lazy { Foo() } }` or `object Foo` where appropriate.
12. **Constructor patterns:** Multiple constructors → primary constructor with `@JvmOverloads` and default parameters, or secondary constructors where Java callers depend on specific signatures.
13. **`// region` / `// endregion`:** Preserve as Kotlin supports the same markers.

### JVM interop rules (backward compatibility)
14. **`@JvmStatic`:** All `companion object` methods that were `public static` in Java must be annotated `@JvmStatic` so Java callers can use `Foo.method()` instead of `Foo.Companion.method()`.
15. **`@JvmField`:** Public fields that were accessed as `Foo.FIELD` in Java should be annotated `@JvmField` on the companion object property. Without it, Java sees a getter method instead of a field.
16. **`@JvmOverloads`:** Methods with default parameter values that are called from Java must have `@JvmOverloads` to generate overloaded versions for each default.
17. **`@Throws`:** Any function that throws a checked exception must be annotated `@Throws(ExceptionType::class)`. Java callers expect to catch checked exceptions — without this annotation, the exception is unchecked from Java's perspective.
18. **`@JvmName`:** Use when the Kotlin property name or method signature would produce a different name in JVM bytecode than the original Java name. Example: a property `val isEnabled: Boolean` generates `isEnabled()` in bytecode, but Java callers may expect `getIsEnabled()` — use `@get:JvmName("isEnabled")` if needed.

### Language-forced changes (beyond mechanical translation)
19. **Static initializer blocks:** Java `static { ... }` → Kotlin `companion object { init { ... } }` or `init { }` for instance initializers. For lazy one-time initialization, use `by lazy { }`.
20. **Checked exceptions:** Kotlin does not have checked exceptions. Methods with `throws` clauses must add `@Throws` annotations for Java callers, but the Kotlin code itself does not declare checked exceptions.
21. **Parcelable:** No current Java production files implement `Parcelable`, so this rule is unlikely to apply. If any class is discovered to implement `Parcelable`, the `kotlin-parcelize` plugin is available in SalesforceSDK's `build.gradle.kts`. Only use `@Parcelize` for internal classes — public `Parcelable` classes consumed by external apps should keep manual implementations to avoid breaking serialization compatibility.
22. **Java-specific patterns:**
    - `instanceof` → `is`
    - `(Type) cast` → `as Type` or `as? Type`
    - `.class` → `::class.java` (for Java Class references) or `::class` (for KClass)
    - `String.format()` → string templates `"${var}"` where cleaner, but preserve `String.format()` for complex formatting
    - `for (Type item : collection)` → `for (item in collection)`
    - Ternary `condition ? a : b` → `if (condition) a else b`

### Existing Kotlin integration rules
23. **Existing Kotlin files — leave as-is:** Do not modify or merge content from converted Java files into existing Kotlin files. The existing Kotlin files should continue to work against the converted classes. Only update if a build error occurs at the library boundary.
24. **Pure-Kotlin files — do not touch:** Existing Kotlin files that have no Java counterpart are left completely untouched.
25. **Preserve file organization:** New Kotlin files are placed in the exact same directory as the original Java file they replace.

### Pre-conversion conflict map

**SalesforceAnalytics:**
| Existing Kotlin file | Relationship to Java | Action |
|---|---|---|
| `LogRedactor.kt` | Standalone utility — no Java counterpart | Leave as-is. |
| `SalesforceLogReceiver.kt` | Interface — no Java counterpart | Leave as-is. |
| `SalesforceLogReceiverFactory.kt` | References `SalesforceLogger` (being converted) | Leave as-is. Verify at boundary. |

**SalesforceSDK:**
78 existing Kotlin files interact with 57 Java files being converted. Key interactions:
| Existing Kotlin file | Relationship to Java | Action |
|---|---|---|
| `SalesforceSDKManager.kt` | References UserAccountManager, OAuth2, RestClient (all being converted) | Leave as-is. Verify at boundary. |
| `AuthenticationUtilities.kt` | References OAuth2.java | Leave as-is. Verify at boundary. |
| `UserAccountBuilder.kt` | Creates UserAccount instances | Leave as-is. Verify at boundary. |
| `UserAccountManagerExtension.kt` | Extension functions on UserAccountManager | Leave as-is. Verify at boundary. |
| `OAuthConfig.kt` | Used by OAuth2 flow | Leave as-is. |
| `PushMessaging.kt`, `PushService.kt` | Reference OAuth2 and push classes | Leave as-is. Verify at boundary. |
| All UI `.kt` files (LoginActivity, etc.) | Reference Java Activity base classes | Leave as-is. Verify at boundary. |
| All other 66 Kotlin files | Various references to Java classes | Leave as-is. Verify at boundary. |

**SmartStore:** No existing Kotlin files.

**MobileSync:**
42 existing Kotlin files alongside 1 Java file being converted.
| Existing Kotlin file | Relationship to Java | Action |
|---|---|---|
| `SyncManager.kt`, all target `.kt` files, all util `.kt` files | Reference `MobileSyncSDKManager.java` (being converted) | Leave as-is. Verify at boundary. |

**SalesforceHybrid:**
| Existing Kotlin file | Relationship to Java | Action |
|---|---|---|
| `SalesforceDroidGapActivity.kt` | Extends/uses Java plugin classes | Leave as-is. Verify at boundary. |
| `SalesforceWebViewCookieManager.kt` | Standalone utility | Leave as-is. |

**SalesforceReact:** No existing Kotlin files.

### AndroidManifest-registered components
The following Java files being converted are declared in `AndroidManifest.xml` files. Their fully-qualified class names **must not change** — Android resolves components by name at runtime.

| File | Batch | Manifest Registration |
|------|-------|----------------------|
| `AuthenticatorService.java` | 09 | Service in `libs/SalesforceSDK/AndroidManifest.xml` |
| `LegacyAuthenticatorService.java` | 09 | Service in `libs/SalesforceSDK/AndroidManifest.xml` |
| `SFDCFcmListenerService.java` | 11 | Service in `libs/SalesforceSDK/AndroidManifest.xml` |
| `SmartStoreInspectorActivity.java` | 26 | Activity in `libs/SmartStore/AndroidManifest.xml` |
| `KeyValueStoreInspectorActivity.java` | 25 | Activity in `libs/SmartStore/AndroidManifest.xml` |

Since Kotlin compiles to identical JVM bytecode class names, no manifest changes should be needed. The orchestrator verification checklist should confirm that the package name and class name in the converted `.kt` file match the manifest entry exactly.

### Pre-existing test conflict (SalesforceSDK)
`libs/test/SalesforceSDKTest/src/com/salesforce/androidsdk/auth/LoginServerManagerTest.java` and `LoginServerManagerTest.kt` both exist in the same package. This is a pre-existing naming conflict in the test target (not introduced by this conversion). The test plan handles resolution of this conflict during test conversion.

### ProGuard/R8 consumer rules
Three libraries have `consumer-rules.pro` files that reference class names:
- `libs/SalesforceSDK/consumer-rules.pro` — references `PushService`, `Transform`, `AnalyticsPublisher`
- `libs/SmartStore/consumer-rules.pro` — references `LongOperation`
- `libs/MobileSync/consumer-rules.pro` — references `SyncUpTarget`, `SyncDownTarget`

Since Kotlin compiles to identical JVM class names, these rules should continue to work without modification. Verify during post-conversion that no ProGuard/R8 errors appear in release builds.

### Library-specific rules
26. **SalesforceAnalytics:** Logger + analytics model + transform + encryption. Model classes use `JSONObject` serialization for persistence. `Encryptor.java` is security-critical — handles key generation, encryption/decryption. Preserve thread safety of existing synchronized blocks.
27. **SalesforceSDK:** Largest library (57 Java files, ~13K lines). OAuth, identity, REST, user accounts, login UI, push notifications. `OAuth2.java` (1,034 lines) and `UserAccountManager.java` (792 lines) are security-critical. 78 existing Kotlin files — most should compile against converted classes without changes. **Note:** This library uses `kotlin-serialization` plugin — do NOT convert existing `JSONObject` serialization to `@Serializable`/kotlinx-serialization during this conversion. Keep the existing serialization approach; modernization is a separate effort.
28. **SmartStore:** Heavy SQLCipher interaction via Android's `SQLiteDatabase` API (using `net.zetetic:sqlcipher-android`). `SmartStore.java` (1,684 lines) is the core — database access patterns must preserve thread safety. Uses `synchronized` blocks extensively.
29. **MobileSync:** Nearly fully Kotlin already — only `MobileSyncSDKManager.java` (187 lines) remains. Trivial conversion.
30. **SalesforceHybrid:** Depends on Cordova framework (Java). Plugin classes extend `CordovaPlugin` (Java) — converted Kotlin must maintain Java-compatible overrides. `SmartStorePlugin.java` (712 lines) is the largest.
31. **SalesforceReact:** Depends on React Native framework (Java). Bridge classes extend React Native base classes — same JVM interop requirements as SalesforceHybrid.

---

## Execution Timing

| Milestone | Timestamp | Wall-Clock Elapsed |
|-----------|-----------|-------------------|
| Plan execution started | | |
| Pre-flight validation complete | | |
| Phase 1 (SalesforceAnalytics) — all batches converted | | |
| Phase 1 — library boundary complete | | |
| 🔶 Operator Gate 1 — report generated, awaiting review | | |
| 🔶 Operator Gate 1 — approved, proceeding | | |
| Phase 2 (SalesforceSDK) — conversion started | | |
| Phase 2 — library boundary complete | | |
| 🔶 Operator Gate 2 — report generated, awaiting review | | |
| 🔶 Operator Gate 2 — approved, proceeding | | |
| Phase 3 (SmartStore) — conversion started | | |
| Phase 3 — library boundary complete | | |
| 🔶 Operator Gate 3 — report generated, awaiting review | | |
| 🔶 Operator Gate 3 — approved, proceeding | | |
| Phase 4 (MobileSync) — conversion started | | |
| Phase 4 — library boundary complete | | |
| 🔶 Operator Gate 4 — report generated, awaiting review | | |
| 🔶 Operator Gate 4 — approved, proceeding | | |
| Phase 5 (SalesforceHybrid) — conversion started | | |
| Phase 5 — library boundary complete | | |
| 🔶 Operator Gate 5 — report generated, awaiting review | | |
| 🔶 Operator Gate 5 — approved, proceeding | | |
| Phase 6 (SalesforceReact) — conversion started | | |
| Phase 6 — library boundary complete | | |
| 🔶 Operator Gate 6 — report generated, awaiting review | | |
| 🔶 Operator Gate 6 — approved, proceeding | | |
| Phase 7 (Sample Apps) — conversion started | | |
| Phase 7 — app boundary complete | | |
| 🔶 Operator Gate 7 — report generated, awaiting review | | |
| 🔶 Operator Gate 7 — approved, proceeding | | |
| Post-conversion (clean build) started | | |
| Plan execution finished | | |
| **Total wall-clock time** | | |
| **Total operator wait time** | | |

| Build Command | Duration |
|---------------|----------|
| Pre-flight baseline build (per-module) | |
| Phase 1 build | |
| Phase 2 build | |
| Phase 3 build | |
| Phase 4 build | |
| Phase 5 build | |
| Phase 6 build | |
| Phase 7 build (sample apps) | |
| Post-conversion clean build (all modules) | |
| **Total build idle time** | |

## Unanticipated Issues Log

| # | Phase | Batch/Step | Issue | Resolution | Time Spent |
|---|-------|-----------|-------|------------|------------|
| | | | | | |

---

## Batch Progress Tracker

126 production .java files across 36 batches.
6 library boundaries + 1 sample app boundary → 7 phase build passes (+ 1 post-conversion full build).

Status key: `[ ]` = pending, `[→]` = in progress, `[✓]` = complete

### Phase 1: SalesforceAnalytics (12 .java files → 3 batches, 1 build)
Existing Kotlin: 3 files (LogRedactor.kt, SalesforceLogReceiver.kt, SalesforceLogReceiverFactory.kt)

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 01 | `SalesforceLogger.java` (531), `FileLogger.java` (288), `SalesforceAnalyticsLogger.java` (222), `WatchableStream.java` (38) | ~1,079 | [✓] |
| 02 | `InstrumentationEvent.java` (433), `InstrumentationEventBuilder.java` (322), `DeviceAppAttributes.java` (214) | ~969 | [✓] |
| 03 | `Encryptor.java` (637), `AnalyticsManager.java` (106), `EventStoreManager.java` (297), `AILTNTransform.java` (195), `Transform.java` (47) | ~1,282 | [✓] |

**Library boundary after batch 03:**
- Verify `build.gradle.kts` has `kotlin-android` plugin (expected: already present)
- Build (isolated): `./gradlew :libs:SalesforceAnalytics:assembleDebug`
- Verify 3 existing Kotlin files compile as-is
- Lessons files: `prod-conversion-lessons-delta-SalesforceAnalytics.md`, `prod-conversion-lessons-SalesforceAnalytics.md`
- Security-critical files: `Encryptor.java` (encryption utilities)
- **🔶 OPERATOR GATE 1**

### Phase 2: SalesforceSDK (57 .java files → 17 batches, 1 build)
Existing Kotlin: 78 files across accounts/, analytics/, app/, auth/, config/, push/, rest/, security/, ui/, util/

#### Phase 2 parallel execution model

The 17 batches are organized into 4 parallel tracks for semantic conversion:

| Track | Sub-phases | Batches | Files | Description |
|-------|-----------|---------|-------|-------------|
| A | 2a (Utilities) | 04–06 | 13 | SDK logger, JSON helpers, test utilities |
| B | 2b–2c (Config/Auth/Security) | 07–11 | 16 | Config, auth, OAuth2, keystore, push |
| C | 2d (REST) | 12–16 | 16 | REST types, requests, client, files |
| D | 2e–2f (Accounts/Analytics/UI) | 17–20 | 12 | User accounts, analytics, activity classes |

**Scout batches (sequential):** 04, 07, 12, 17
**Parallel batches:** 05–06, 08–11, 13–16, 18–20

**Cross-track type references:** SalesforceSDK's Java files have cross-track import relationships (e.g., `RestClient` imports `UserAccount`, `OAuth2`; `ClientManager` imports `AuthenticatorService`). This is acceptable because semantic conversion is read-then-write — agents do not compile during batch work. All `.java` → `.java.bak` renames and `.kt` writes complete before the single library boundary build compiles everything together.

**Fallback:** If the operator prefers serial execution, all 4 tracks can be run sequentially. The parallel model is the default but can be overridden at Operator Gate 1.

#### Sub-phase 2a: Utilities — **Track A**

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 04 | `SalesforceSDKLogger.java` (160), `UriFragmentParser.java` (82), `MapUtil.java` (146), `ResourceReaderHelper.java` (111), `UserSwitchReceiver.java` (52) | ~551 | [ ] |
| 05 | `JSONObjectHelper.java` (206), `EventsObservable.java` (96), `ManagedFilesHelper.java` (111), `AuthConfigUtil.java` (232) | ~645 | [ ] |
| 06 | `EventsListenerQueue.java` (129), `TestCredentials.java` (111), `BroadcastListenerQueue.java` (69), `EventsObserver.java` (37) | ~346 | [ ] |

#### Sub-phase 2b: Config/App — **Track B**

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 07 | `AbstractPrefsManager.java` (175), `AdminPermsManager.java` (46), `AdminSettingsManager.java` (46), `RuntimeConfig.java` (223), `Features.java` (48), `SdkVersion.java` (166) | ~704 | [ ] |
| 08 | `BootConfig.java` (388), `LoginServerManager.java` (797) | ~1,185 | [ ] |

#### Sub-phase 2c: Auth/Security — **Track B** (continued)

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 09 | `AuthenticatorService.java` (208), `LegacyAuthenticatorService.java` (35), `HttpAccess.java` (210) | ~453 | [ ] |
| 10 | `OAuth2.java` (1,034) — solo large file, **SECURITY CRITICAL** | ~1,034 | [ ] |
| 11 | `KeyStoreWrapper.java` (313), `SalesforceKeyGenerator.java` (238), `PushNotificationDecryptor.java` (133), `SFDCFcmListenerService.java` (74) | ~758 | [ ] |

#### Sub-phase 2d: REST — **Track C**

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 12 | `ApiVersionStrings.java` (78), `BatchRequest.java` (95), `BatchResponse.java` (50), `CompositeRequest.java` (92), `CompositeResponse.java` (90), `CollectionResponse.java` (97), `PrimingRecordsResponse.java` (139) | ~641 | [ ] |
| 13 | `FileRequests.java` (215), `ConnectUriBuilder.java` (117), `ApiRequests.java` (75), `RenditionType.java` (44), `RestResponse.java` (256) | ~707 | [ ] |
| 14 | `RestRequest.java` (1,071) — solo large file | ~1,071 | [ ] |
| 15 | `RestClient.java` (916) — solo large file, **SECURITY CRITICAL** | ~916 | [ ] |
| 16 | `ClientManager.java` (547), `SalesforceSDKUpgradeManager.java` (351) | ~898 | [ ] |

#### Sub-phase 2e: Accounts/Analytics — **Track D**

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 17 | `UserAccount.java` (1,106) — solo large file, **SECURITY CRITICAL** | ~1,106 | [ ] |
| 18 | `UserAccountManager.java` (792), `SalesforceAnalyticsManager.java` (545) | ~1,337 | [ ] |

#### Sub-phase 2f: Analytics/UI — **Track D** (continued)

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 19 | `AILTNPublisher.java` (190), `EventBuilderHelper.java` (151), `AnalyticsPublisher.java` (45) | ~386 | [ ] |
| 20 | `SalesforceActivity.java` (99), `SalesforceActivityDelegate.java` (157), `SalesforceActivityInterface.java` (55), `SalesforceExpandableListActivity.java` (84), `SalesforceListActivity.java` (90), `SalesforceServerRadioButton.java` (122) | ~607 | [ ] |

**Library boundary after batch 20:**
- Verify `build.gradle.kts` has `kotlin-android` plugin (expected: already present)
- Build (isolated): `./gradlew :libs:SalesforceSDK:assembleDebug`
- Verify 78 existing Kotlin files compile as-is
- Lessons files
- Security-critical files: `OAuth2.java`, `UserAccountManager.java`, `UserAccount.java`, `KeyStoreWrapper.java`, `SalesforceKeyGenerator.java`, `PushNotificationDecryptor.java`, `RestClient.java`, `ClientManager.java`, `HttpAccess.java`, `AuthenticatorService.java`
- **🔶 OPERATOR GATE 2** — generate review report (with detailed security-critical file review)

### Phase 3: SmartStore (19 .java files → 6 batches, 1 build)
Existing Kotlin: 0 source files (but `kotlin-android` plugin is already present in `build.gradle.kts`)

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 21 | `SmartStoreLogger.java` (160), `Features.java` (38), `StoreConfig.java` (139), `IndexSpec.java` (184), `StoreCursor.java` (129) | ~650 | [ ] |
| 22 | `SmartStore.java` (1,684) — solo large file, **SECURITY CRITICAL** (encrypted storage) | ~1,684 | [ ] |
| 23 | `DBHelper.java` (613), `DBOpenHelper.java` (426), `SmartSqlHelper.java` (211) | ~1,250 | [ ] |
| 24 | `QuerySpec.java` (545), `AlterSoupLongOperation.java` (539), `LongOperation.java` (74) | ~1,158 | [ ] |
| 25 | `KeyValueEncryptedFileStore.java` (514), `KeyValueStore.java` (57), `MemCachedKeyValueStore.java` (153), `KeyValueStoreInspectorActivity.java` (222) | ~946 | [ ] |
| 26 | `SmartStoreSDKManager.java` (691), `SmartStoreUpgradeManager.java` (88), `SmartStoreInspectorActivity.java` (507) | ~1,286 | [ ] |

**Library boundary after batch 26:**
- Verify `build.gradle.kts` has `kotlin-android` plugin (expected: already present)
- Build (isolated): `./gradlew :libs:SmartStore:assembleDebug`
- Lessons files
- Security-critical files: `SmartStore.java`, `DBOpenHelper.java` (encrypted database management)
- **🔶 OPERATOR GATE 3**

### Phase 4: MobileSync (1 .java file → 1 batch, 1 build)
Existing Kotlin: 42 files — library is **nearly fully Kotlin**. Only `MobileSyncSDKManager.java` remains.

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 27 | `MobileSyncSDKManager.java` (187) | ~187 | [ ] |

**Library boundary after batch 27:**
- Build (isolated): `./gradlew :libs:MobileSync:assembleDebug`
- Verify 42 existing Kotlin files compile as-is
- Lessons files
- Security-critical files: none
- **🔶 OPERATOR GATE 4**

### Phase 5: SalesforceHybrid (18 .java files → 5 batches, 1 build)
Existing Kotlin: 2 files (SalesforceDroidGapActivity.kt, SalesforceWebViewCookieManager.kt)

**Note:** Plugin classes extend Cordova's `CordovaPlugin` (Java). Converted Kotlin must maintain compatible method overrides.

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 28 | `ForcePlugin.java` (100), `PluginConstants.java` (69), `JavaScriptPluginVersion.java` (121), `TestRunnerPlugin.java` (135) | ~425 | [ ] |
| 29 | `SmartStorePlugin.java` (712) — solo large file | ~712 | [ ] |
| 30 | `SalesforceNetworkPlugin.java` (335), `SalesforceOAuthPlugin.java` (128), `SFAccountManagerPlugin.java` (176), `SDKInfoPlugin.java` (215), `MobileSyncPlugin.java` (338) | ~1,192 | [ ] |
| 31 | `SalesforceWebView.java` (57), `SalesforceWebViewClient.java` (149), `SalesforceWebViewClientHelper.java` (188), `SalesforceWebViewEngine.java` (85) | ~479 | [ ] |
| 32 | `HybridApp.java` (49), `SalesforceHybridSDKManager.java` (214), `SalesforceHybridUpgradeManager.java` (56), `SalesforceHybridLogger.java` (160) | ~479 | [ ] |

**Library boundary after batch 32:**
- Build (isolated): `./gradlew :libs:SalesforceHybrid:assembleDebug`
- Verify 2 existing Kotlin files compile as-is
- Lessons files
- Security-critical files: none
- **🔶 OPERATOR GATE 5**

### Phase 6: SalesforceReact (10 .java files → 2 batches, 1 build)
Existing Kotlin: 0 source files (but `kotlin-android` plugin is already present in `build.gradle.kts`)

**Note:** Bridge classes extend React Native base classes (Java). Converted Kotlin must maintain compatible method overrides.

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 33 | `SalesforceReactSDKManager.java` (183), `SalesforceReactUpgradeManager.java` (56), `SalesforceReactLogger.java` (160), `SalesforceReactActivity.java` (388), `SalesforceReactActivityDelegate.java` (61) | ~848 | [ ] |
| 34 | `SmartStoreReactBridge.java` (713), `ReactBridgeHelper.java` (179), `SalesforceNetReactBridge.java` (280), `MobileSyncReactBridge.java` (272), `SalesforceOauthReactBridge.java` (91) | ~1,535 | [ ] |

**Library boundary after batch 34:**
- Verify `build.gradle.kts` has `kotlin-android` plugin (expected: already present)
- Build (isolated): `./gradlew :libs:SalesforceReact:assembleDebug`
- Lessons files
- Security-critical files: none
- **🔶 OPERATOR GATE 6**

### Phase 7: Sample Apps (9 .java files → 2 batches, 1 build)

Sample apps with Java code: RestExplorer (1 file), AppConfigurator (6 files), ConfiguredApp (2 files).
Other sample apps (AuthFlowTester) are already Kotlin.

| Batch | Files | Lines | Status |
|-------|-------|-------|--------|
| 35 | `ExplorerActivity.java` (857 — RestExplorer) | ~857 | [ ] |
| 36 | AppConfigurator: `AppConfiguratorAdminReceiver.java` (64), `AppConfiguratorState.java` (182), `ConfigureAppFragment.java` (186), `EnableProfileActivity.java` (62), `MainActivity.java` (71), `SetupProfileFragment.java` (115); ConfiguredApp: `ConfiguredApp.java` (52), `MainActivity.java` (103) | ~835 | [ ] |

**App boundary after batch 36:**
- Build RestExplorer: `./gradlew :native:NativeSampleApps:RestExplorer:assembleDebug`
- Build AppConfigurator: `./gradlew :native:NativeSampleApps:AppConfigurator:assembleDebug`
- Build ConfiguredApp: `./gradlew :native:NativeSampleApps:ConfiguredApp:assembleDebug`
- No test suite (sample apps rely on manual testing)
- Commit: `"Convert sample apps Java to Kotlin (Phase 7)"`
- **🔶 OPERATOR GATE 7**

---

## Learning Flow Diagram

```
Batches 01–03 (semantic, autonomous)
  → deltas → prod-conversion-lessons-delta-SalesforceAnalytics.md
  ↓
Library boundary: build + test + self-review → prod-conversion-lessons-SalesforceAnalytics.md
  ↓
🔶 OPERATOR GATE 1 — review report → proceed / adjust / stop
  ↓
Phase 2 — scout-then-parallel:
  Scout batches (sequential): 04, 07, 12, 17 → scout addenda + pattern registry update
  ↓
  Parallel tracks (remaining batches):
    Track A (05–06) → prod-conversion-lessons-delta-SalesforceSDK-trackA.md
    Track B (08–11) → prod-conversion-lessons-delta-SalesforceSDK-trackB.md
    Track C (13–16) → prod-conversion-lessons-delta-SalesforceSDK-trackC.md
    Track D (18–20) → prod-conversion-lessons-delta-SalesforceSDK-trackD.md
  ↓ (all 4 tracks complete)
Merge deltas → library boundary: build + test + self-review (+ security-critical file review)
  → prod-conversion-lessons-SalesforceSDK.md
  ↓
🔶 OPERATOR GATE 2 — review report (security focus) → proceed / adjust / stop
  ↓
Batches 21–26 (semantic, autonomous)
  → deltas → prod-conversion-lessons-delta-SmartStore.md
  ↓
Library boundary → prod-conversion-lessons-SmartStore.md
  ↓
🔶 OPERATOR GATE 3
  ↓
Batch 27 (trivial — 1 file)
  → deltas → prod-conversion-lessons-delta-MobileSync.md
  ↓
Library boundary → prod-conversion-lessons-MobileSync.md
  ↓
🔶 OPERATOR GATE 4
  ↓
Batches 28–32 (semantic, autonomous)
  → deltas → prod-conversion-lessons-delta-SalesforceHybrid.md
  ↓
Library boundary → prod-conversion-lessons-SalesforceHybrid.md
  ↓
🔶 OPERATOR GATE 5
  ↓
Batches 33–34 (semantic, autonomous)
  → deltas → prod-conversion-lessons-delta-SalesforceReact.md
  ↓
Library boundary → prod-conversion-lessons-SalesforceReact.md
  ↓
🔶 OPERATOR GATE 6
  ↓
Batches 35–36 (Phase 7 — Sample Apps)
  → prod-conversion-lessons-delta-SampleApps.md
  ↓
App boundary: build (no tests) → commit
  ↓
🔶 OPERATOR GATE 7
  ↓
Post-conversion: clean build all, commit
```

---

## Post-Conversion Steps

1. **Clean build all modules** sequentially:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Verify audit artifacts** — confirm all original `.java` files are renamed to `.java.bak` and no `.java` production files remain (except in external dependencies):
   ```bash
   find libs -name "*.java" -not -path "*/test/*" -not -path "*/build/*" | wc -l  # should be 0
   find libs -name "*.java.bak" -not -path "*/build/*" | wc -l  # should be 117
   find native -name "*.java.bak" | wc -l  # should be 9
   ```
3. **Verify Gradle build files** — ensure all `build.gradle.kts` files have `kotlin-android` plugin where needed
4. **Verify ProGuard/R8 consumer rules** — build a release variant (`./gradlew assembleRelease`) and confirm no missing class warnings from rules referencing `PushService`, `Transform`, `AnalyticsPublisher`, `LongOperation`, `SyncUpTarget`, `SyncDownTarget`
5. **Verify AndroidManifest-registered components** — confirm that all Services and Activities listed in the "AndroidManifest-registered components" section retain their exact fully-qualified class name in the converted `.kt` files
6. **Final commit** — `"Verify clean build after production Java→Kotlin conversion"`
7. **Push** to `feature/java-to-kotlin-production-migration`
8. **(Future)** After a separate verification pass confirms conversion accuracy, remove `.java.bak` files in a dedicated cleanup commit

**Note:** Tests are NOT run during this plan. The separate test conversion plan handles converting Java tests to Kotlin and verifying they pass against the converted production code.

---

## File Counts

| Phase | Library | Java .java Files | Existing .kt | Batches | Build Pass |
|-------|---------|-----------------|--------------|---------|------------|
| 1 | SalesforceAnalytics | 12 | 3 | 3 (01–03) | After batch 03 |
| 2 | SalesforceSDK | 57 | 78 | 17 (04–20) | After batch 20 |
| 3 | SmartStore | 19 | 0 | 6 (21–26) | After batch 26 |
| 4 | MobileSync | 1 | 42 | 1 (27) | After batch 27 |
| 5 | SalesforceHybrid | 18 | 2 | 5 (28–32) | After batch 32 |
| 6 | SalesforceReact | 10 | 0 | 2 (33–34) | After batch 34 |
| 7 | Sample Apps | 9 | varies | 2 (35–36) | After batch 36 |
| **Total** | | **126** | **125** | **36 batches** | **8 builds** |

## Lessons Files Summary

| Type | Count | Naming Convention | Purpose |
|------|-------|-------------------|---------|
| Cumulative (per library) | 7 | `prod-conversion-lessons-LIBRARY.md` | Full knowledge through this library |
| Delta (Phases 1, 3–7) | 6 | `prod-conversion-lessons-delta-LIBRARY.md` | Batch-level observations |
| Delta (Phase 2 scouts) | 4 | `prod-conversion-lessons-delta-SalesforceSDK-scoutX.md` | Scout batch observations |
| Delta (Phase 2 tracks) | 4 | `prod-conversion-lessons-delta-SalesforceSDK-trackX.md` | Per-track observations |
| Pattern registry | 1 | `prod-conversion-patterns.md` | Machine-readable verified patterns |
| **Total** | **22** | | |
