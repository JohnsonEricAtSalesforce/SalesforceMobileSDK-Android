# Upstream Sync Poller — Job Specification (v6)

> **v6 (2026-07-14):** Added the **operator-reviewable status view** (`.claude/upstream-sync-status.md`) — a Markdown rendering derived from the ledger JSON (never hand-edited, re-rendered on every write) that displays the original-commit→contribution mapping and per-unit migration status, chronological with escalations pulled to the top. (Finding R8.)
>
> **v5 (2026-07-14):** Added the **Backlog Ledger** (`.claude/upstream-sync-ledger.json`) — the commit→PR mapping + A–G classification is now analyzed once at bootstrap and persisted, not recomputed every firing or lost on context loss. Marker = cursor, ledger = map. (Finding R7.)
>
> **v4 (2026-07-14):** Corrected detection to target `forcedotcom` via a read-only `upstream` remote (was pointed at the personal fork); backlog measured in PRs not commits with fetch-first discipline; `gh pr diff` made the authoritative diff derivation (removed the `^1..` footgun); added Category G for rename/delete PRs; replaced the one-line activation with an explicit bootstrap; resolved the durable-vs-expiry ambiguity. Full P1–P6 + R7 rationale and the audit trail live in `upstream-sync-job-review.md`.

## Context

This repository (`SalesforceMobileSDK-Android`) underwent a major migration from Java to Kotlin on the feature branch `feature/java-to-kotlin-test-migration`. The migration converted 126 production Java files and 95 test Java files to Kotlin. Original Java files are preserved as `.java.bak` in the same directories.

Meanwhile, `upstream/dev` (`forcedotcom`) continues to receive commits in the mixed Java/Kotlin codebase. Those commits cannot be merged or rebased — they must be **semantically translated** into the pure-Kotlin branch.

### Current State

- **Feature branch:** `feature/java-to-kotlin-test-migration` (26 commits ahead of `dev`)
- **Backlog:** measured on demand — see "Measuring the Backlog" below. **Do not hardcode a number here**; it goes stale immediately. (As of 2026-07-05: ~48 merged PRs behind `upstream/dev` since marker `11a4a433b`.)
- **Push remote (`origin`):** `https://github.com/JohnsonEricAtSalesforce/SalesforceMobileSDK-Android.git` (personal fork; lagging mirror)
- **Detection remote (`upstream`):** `https://github.com/forcedotcom/SalesforceMobileSDK-Android.git` (source of truth)
- **Default branch:** `dev`
- **Migration strategy:** Original `.java` files renamed to `.java.bak` (invisible to Gradle); `.kt` replacements in same packages
- **Key constraint:** Upstream `dev` still has files like `OAuth2.java`, `RestRequest.java` etc. that this branch has as `.kt`

---

## Remote Contract (READ THIS FIRST)

**`origin`** = the personal fork (`github.com/JohnsonEricAtSalesforce/SalesforceMobileSDK-Android`) — this is the **push** target for the migration branch. It is a *lagging mirror* of upstream, not the source of truth.

**`upstream`** = `forcedotcom/SalesforceMobileSDK-Android` — the **read-only** source of truth for detection. All upstream PRs (#29xx) live here.

**Invariant:** every `gh`/`git` detection command names `upstream` / `forcedotcom` **explicitly** — never rely on the ambient default remote, which resolves to `origin` (the fork) and will silently return nothing. The `upstream` remote is added once during first-run bootstrap (see "To Activate This Job").

Throughout this spec:
```bash
UPSTREAM_REPO="forcedotcom/SalesforceMobileSDK-Android"   # for gh --repo
# git baseline ref is always upstream/dev (never origin/dev)
```

---

## Measuring the Backlog

**The backlog is counted in PRs (processing units), never in commits.** A squash-merge is one commit but one unit; a `Merge pull request` PR spans several commits but is still one unit; a direct push is its own unit. Raw commit counts conflate all three and inflate the figure — the old "61 / 94 commits behind" numbers were wrong for exactly this reason (and were measured against a stale, fork-side ref).

**Rule 1 — always fetch first.** Never count against a cached ref:
```bash
git fetch upstream dev            # MANDATORY before any count
```

**Rule 2 — count merged PRs since the marker** (the actual unit of work):
```bash
MARKER=$(cat .claude/upstream-sync-marker 2>/dev/null || git merge-base HEAD upstream/dev)
MARKER_DATE=$(git show -s --format=%cI "$MARKER")
gh pr list --repo "$UPSTREAM_REPO" --state merged --base dev --limit 200 \
  --json number,mergedAt --jq "[.[] | select(.mergedAt > \"$MARKER_DATE\")] | length"
```

**Rule 3 — add genuine direct pushes** (real units that never appear in the PR list).

⚠️ A `grep -v 'Merge pull request'` filter is **not** sufficient: squash-merged and rebase-merged PRs do NOT contain the text "Merge pull request", yet they are PRs (usually carrying a `(#NNNN)` suffix). Filtering only on that string mis-classifies them as standalone and double-counts them against the PR list. A first-parent commit is a **true standalone only if it has no associated PR**:
```bash
for sha in $(git log --first-parent --format=%H "$MARKER..upstream/dev"); do
  # 0 associated PRs => genuine direct push; anything >0 already counted by Rule 2
  [ "$(gh api "repos/$UPSTREAM_REPO/commits/$sha/pulls" --jq 'length')" = "0" ] && echo "$sha"
done | wc -l
```
Report as: "**N merged PRs + M genuine direct-push commits** since marker `<hash>` (`<date>`)".

**Truncation contract:** `--limit 200` caps *fetched* rows before the jq date filter runs. If the count ever approaches 200, raise the limit — otherwise the backlog is silently truncated.

---

## Backlog Ledger (analysis is persisted, not recomputed)

The expensive part of this job is **analysis**, not application: mapping which `dev` commits collapse into each contribution (squash/rebase/merge), resolving the strategy, listing files, and assigning a Category. That analysis is **stable once a PR is merged** — it never changes. Recomputing it on every 4-hour firing (or after a session compaction) is pure waste and risks losing hours of classification work.

**Therefore the analysis is persisted to a durable ledger before any porting begins.** The marker and the ledger are complementary:
- **Marker** (`.claude/upstream-sync-marker`) = the **cursor** — how far we have fully processed.
- **Ledger** (`.claude/upstream-sync-ledger.json`) = the **map** — what every unit *is*, plus its per-unit status.

Both are git-ignored, per-clone operator state, created at bootstrap.

### Ledger format — `.claude/upstream-sync-ledger.json`

```json
{
  "generatedAt": "<ISO8601>",
  "marker": "<commit hash the ledger was built from>",
  "upstreamRepo": "forcedotcom/SalesforceMobileSDK-Android",
  "units": [
    {
      "pr": 2913,                          // null for a genuine standalone push
      "title": "…",
      "mergeSha": "74fff3b6b",
      "mergeStrategy": "merge",            // squash | merge | rebase | standalone
      "mergedAt": "2026-06-03",
      "commits": ["abc1234", "def5678"],   // constituent dev commits — the mapping
      "files": ["libs/.../OAuth2.java"],
      "category": "B",                      // A–G
      "categoryConfidence": "provisional",  // provisional (title-only) | enriched
      "status": "pending",                  // pending|analyzed|drafted|applied|skipped
      "escalation": false,                  // true → human review (public API, manifest, crypto)
      "notes": ""
    }
  ]
}
```

### Rules

1. **Immutability of identity.** Once a PR is merged, its `pr`/`mergeSha`/`commits`/`files`/`mergeStrategy` never change. These are computed **once** and never recomputed. Only `category` (provisional→enriched), `status`, `escalation`, and `notes` are ever edited on an existing entry.
2. **Built at bootstrap** (activation Step 1f) — the full backlog is analyzed once and written before porting starts. This is the "analyze before work" gate.
3. **Incremental thereafter.** Each poller firing appends only units newer than the marker (bounded exactly as in "Measuring the Backlog"). Existing entries are never re-derived.
4. **The report is a view of the ledger**, not a throwaway (see Step 6). A compacted or restarted session reads the ledger to resume — it does not re-run the analysis.
5. **`commits` uses the P3 resolution** (`gh api .../commits/<sha>/pulls`) so squash/rebase/merge/standalone all map correctly.

### Operator-reviewable status view — `.claude/upstream-sync-status.md`

The JSON is authoritative but not reviewable; the commit→contribution mapping is exactly what needs human eyes. A **rendered Markdown view is derived from the JSON** — never hand-edited — and regenerated on every ledger write (bootstrap build + each upsert). If the two ever disagree, **the JSON wins and the view is re-rendered.**

Layout: a provenance header (generated-at + marker + repo), a summary line with status counts + a progress bar, an **⚠️ Escalations** section pulled to the top, then the **Units table in chronological (merge-order)** form. The mapping column makes the squash/rebase/merge collapsing visible — N original `dev` commits → one contribution:

```markdown
# Upstream Sync — Migration Status
_Generated <ISO8601> from ledger @ marker <hash> · upstream forcedotcom/…_

**N units** · ✅ A applied · 📝 D drafted · 🔍 Z analyzed · ⏳ P pending · ⏭️ S skipped
**⚠️ E need human review** (escalation) · Progress: ████████░░░░ NN%

## ⚠️ Escalations (review before porting)
| Unit | Title | Cat | Why flagged | Status |
|------|-------|-----|-------------|--------|
| #2930 | HybridApp→MainApplication.kt | G | public Application class + manifest | ⏳ pending |

## Units (chronological)
| # | PR | Title | Strategy | Cat | Orig commits → contribution | Status |
|---|----|----|----------|-----|------------------------------|--------|
| 1 | #2887 | Dokka v2 | squash | C | `a1b2c3d` → #2887 | ✅ applied |
| 2 | #2913 | app attestation flow | merge | B🔒 | `abc1234`,`def5678` → #2913 | 📝 drafted |
```
Status glyphs: ✅ applied · 📝 drafted · 🔍 analyzed · ⏳ pending · ⏭️ skipped. `🔒` on the category marks an escalation inline. Standalone pushes show `(no PR)` in the mapping column.

---

## Job Configuration

**Schedule:** Every 4 hours on weekdays  
**Cron:** `17 */4 * * 1-5` (minute 17 to avoid contention)  
**Durable:** Yes — survives session/process restarts (cron state persists).  
**Auto-expires:** 7 days — safety valve so a forgotten poller stops itself.  
Durability and expiry are **independent** mechanisms: the job persists across restarts *until* the 7-day expiry, then must be deliberately re-created (a re-confirmation that the sync is still wanted).

---

## Processing Unit: One PR Merge (strict boundary)

Each merge commit to `dev` (or squash-merge) is exactly one processing unit. No consolidation across PRs.

- **Merged PR** → process the net diff of that PR. Internal commits within the PR branch are irrelevant — only the final merged result matters.
- **Standalone commit** (direct push to dev, no merge commit) → one processing unit.
- **Multiple related PRs** (e.g., #2904 React extraction + #2906 remove references) → separate processing units, processed in chronological order. Even if conceptually related, each is translated, compiled, and committed independently.

**Why:** Each PR merge to `dev` represents a deployable state. The feature branch must also be deployable after each ported unit. Consolidation destroys traceability.

**Derivation of the diff to translate:**
```bash
# PRIMARY — for ANY PR (correct for squash, merge-commit, AND rebase-merge):
gh pr diff <number> --repo "$UPSTREAM_REPO" -- libs/

# FALLBACK — ONLY for a genuine standalone direct push with no associated PR:
git diff <commit>^..<commit> -- libs/
```

> **Why not `git diff <merge>^1..<merge>`?** That recipe is a footgun and has been removed:
> - Merge strategy (squash / merge / rebase) **cannot be determined from a commit's shape** — squash commits, rebase-merge tips, and direct pushes all present as single-parent commits.
> - `forcedotcom` allows all three strategies (`allow_squash_merge`, `allow_merge_commit`, `allow_rebase_merge` all true).
> - A **rebase-merge** replays a PR's N commits onto `dev` with no merge commit; `merge_commit_sha` points to the last replayed commit, so `^1..` silently captures **only that final commit** and drops the other N−1 — a partial diff that looks complete.
>
> `gh pr diff <number>` computes the true `base...head` net diff regardless of how the PR landed. Always use it for anything with a PR number.

---

## What It Does Each Firing

1. **Fetch** — `git fetch upstream dev`

2. **Detect** — Read `.claude/upstream-sync-marker` for last-processed commit. If absent, use `git merge-base HEAD upstream/dev`.

3. **Identify PRs** — Use GitHub CLI to find merged PRs since the marker:
   ```bash
   # Get merged PRs to dev, ordered by merge date.
   # --limit 200: gh caps results at the limit and truncates SILENTLY; the
   # marker-date jq filter is what actually bounds the window, so keep the
   # limit safely above the expected backlog.
   gh pr list --repo "$UPSTREAM_REPO" --state merged --base dev \
     --json number,title,mergedAt,mergeCommit \
     --limit 200 --jq '.[] | select(.mergedAt > "<marker-date>")'
   ```
   For standalone commits (no PR), walk the first-parent log and keep only commits
   with **no associated PR** (squash/rebase PRs carry a `(#NNNN)` suffix but no
   "Merge pull request" text — do not treat them as standalone; see Measuring the
   Backlog, Rule 3):
   ```bash
   for sha in $(git log --first-parent --format=%H <marker>..upstream/dev); do
     [ "$(gh api "repos/$UPSTREAM_REPO/commits/$sha/pulls" --jq 'length')" = "0" ] && echo "$sha"
   done
   ```

4. **Enrich each PR** — For each identified PR:
   ```bash
   # PR metadata: description, motivation, linked work items
   gh pr view <number> --repo "$UPSTREAM_REPO" --json title,body,author,labels,comments,files

   # Clean net diff (what actually landed on dev) — authoritative for any merge
   # strategy; never use git ^1.. (see "Derivation of the diff to translate")
   gh pr diff <number> --repo "$UPSTREAM_REPO"
   ```

5. **Classify** each PR:

   | Cat | Criteria | Agent Action |
   |-----|----------|--------------|
   | **A** | Only touches `.kt` files present on both branches | Pre-attempt cherry-pick; report result |
   | **B** | Touches `.java` files that this branch has as `.kt` | **Draft translation** (see below) |
   | **C** | Build/config (`build.gradle.kts`, TOML, settings) | Flag for operator; note conflicts |
   | **D** | Adds new files not on feature branch | Flag; note if `.java` needs conversion |
   | **E** | SalesforceReact removal/restructuring | Flag as bulk decision |
   | **F** | Non-libs only | Pre-attempt cherry-pick; report result |
   | **G** | Upstream **renames or deletes** a file this branch holds as `.kt` (rename/restructure, not logic change) | Mirror the rename/delete on the `.kt`; delete the orphaned `.java.bak`; update all references (manifests, imports). **No semantic translation.** See "Category G Protocol" below. |

   > **Classification is by the dominant action, not file count.** A PR that renames one file and edits another is split into its G part and its A/B part and handled as a mixed-category unit (see "Mixed-Category PRs"). Category **G exists because a delete/rename has no Java diff to translate** — the old B/D categories assumed the upstream file still exists.

6. **Report** — Present the queue with prepared artifacts.

   *Illustrative example (historical PRs) — not a live queue:*
   ```
   UPSTREAM SYNC REPORT — 3 new PRs since <marker>

   [1] PR #2913 — @W-22699717: Use web server flow when app attestation configured
       Author: Johnson.Eric | Merged: 2026-06-03 | Cat B (HIGH)
       Intent: When app attestation is enabled, force web server auth flow
       Files requiring translation: OAuth2.java (+14 lines)
       Files cherry-pickable: SalesforceSDKManager.kt, LoginViewModel.kt
       Translation: DRAFTED — review below

   [2] PR #2909 — Do not add My Domain from Welcome Discovery
       Author: wmathurin | Merged: 2026-05-28 | Cat A (LOW)
       Intent: Prevent Welcome Discovery from writing to My Domain list
       All files exist as .kt on both branches
       Cherry-pick: CLEAN — ready to apply

   [3] Standalone 7ef093b50 — Fix crash: use postValue in resetFrontDoorBridgeUrl
       Author: wmathurin | Merged: 2026-05-27 | Cat A (LOW)
       Intent: Avoid main-thread crash by using postValue instead of setValue
       Cherry-pick: CLEAN — ready to apply
   ```

   The report is a **rendered view of the ledger** (see "Backlog Ledger"), not a throwaway. Before rendering, the poller **upserts** any units newer than the marker into `.claude/upstream-sync-ledger.json` (append new; never recompute existing), then **re-renders `.claude/upstream-sync-status.md`** from the updated JSON. These two files (plus the marker, on operator confirmation) are the only files the poller writes.

7. **No source/working-tree changes.** The poller writes only its own state (`.claude/upstream-sync-ledger.json`) and never touches repo source or the marker. Operator approves before any porting is applied; the marker advances only on operator confirmation.

---

## Category B Translation Protocol

For each Category B PR, the agent prepares:

**Step 1: Extract the semantic intent**
- Read the PR description (`gh pr view <number> --repo "$UPSTREAM_REPO" --json body`)
- Read PR review comments for context (`gh pr view <number> --repo "$UPSTREAM_REPO" --json comments`)
- Read the net diff (`gh pr diff <number> --repo "$UPSTREAM_REPO"`)
- Summarize: what changed, why, and any trade-offs discussed in review

**Step 2: Derive the diff to translate** (authoritative derivation — see "Derivation of the diff to translate"; never `git ^1..`)
```bash
# Net diff of the merged PR — correct for squash, merge-commit, and rebase-merge
gh pr diff <number> --repo "$UPSTREAM_REPO" -- libs/
```
Only the portions touching `.java` files that this branch has as `.kt` need translation. Other files in the PR (`.kt` files, tests, config) may cherry-pick cleanly.

**Step 3: Locate the Kotlin equivalent**
- Find the corresponding code in the `.kt` file on the feature branch
- Identify the exact location where the change applies

**Step 4: Draft the Kotlin translation**
- Write the equivalent change in idiomatic Kotlin
- Preserve the upstream PR's semantics exactly — no improvements, no refactoring, no unrelated changes
- Use PR description and review comments to inform ambiguous decisions

**Step 5: Update `.java.bak`**
- Update `.java.bak` to the Java file's state at this PR's merge commit (not `upstream/dev` HEAD)
- This is the state the file was in when this specific PR landed
- Maintains invariant: `.java.bak` reflects exactly the Java that `.kt` should be equivalent to

**Step 6: Present for operator review**
```
TRANSLATION DRAFT — PR #2913 (@W-22699717: App attestation flow)

PR DESCRIPTION:
  "When app attestation is configured on the External Client App,
  use web server flow instead of user-agent flow for OAuth."

UPSTREAM INTENT:
  Adds isAppAttestationConfigured() check to OAuth flow selection.
  If attestation is active, forces GrantType.WEB_SERVER.

JAVA DIFF (from `gh pr diff 2913 --repo "$UPSTREAM_REPO"`, OAuth2.java portion):
  + if (oauthConfig.isAppAttestationConfigured()) {
  +     return grantType == GrantType.WEB_SERVER;
  + }

PROPOSED KOTLIN (OAuth2.kt, line ~342):
  + if (oauthConfig.isAppAttestationConfigured()) {
  +     return grantType == GrantType.WEB_SERVER
  + }

.java.bak UPDATE: OAuth2.java.bak → state at merge commit 74fff3b6b^1

COMPILE CHECK: ./gradlew :libs:SalesforceSDK:assembleDebug

Approve / Edit / Skip
```

---

## Category G Protocol (rename / delete / restructure)

Category G applies when upstream **removes** a `.java` file this branch holds as `.kt` — either an outright delete or a rename to a new name. There is **no Java diff to translate**; the work is to mirror the structural change and keep the `.java.bak` bookkeeping honest.

**Step 1 — Determine the shape:**
- **Pure delete:** upstream removes `Foo.java`, adds nothing equivalent → delete our `Foo.kt` and `Foo.java.bak`, remove references.
- **Rename (same logic, new name):** upstream deletes `Foo.java` and adds `Bar.<kt|java>` with equivalent body → rename our `Foo.kt` → `Bar.kt` (adjust class name), delete `Foo.java.bak`, update references.
- **Rename where upstream's replacement is already `.kt`:** no conversion needed — just align our file's name/class and drop the stale `.java.bak`.

**Step 2 — Mirror on the feature branch:**
- Rename/delete the `.kt` file; update the class/type name to match upstream.
- Update **all references**: sample-app `AndroidManifest.xml` `android:name`, imports, DI registrations, docs.

**Step 3 — Fix `.java.bak` bookkeeping:**
- **Delete the orphaned `.java.bak`.** Upstream no longer has that Java source, so retaining it violates the invariant (a `.java.bak` must never outlive its upstream Java — see `.java.bak` Update Strategy).
- If renamed and the replacement is still Java upstream, create `Bar.java.bak` from the new upstream file; if the replacement is `.kt` upstream, no `.bak` exists (correct).

**Step 4 — Verify & flag:**
- `./gradlew :libs:MODULE:assembleDebug`.
- **Escalate if the rename touches a public class name, an Application/Activity referenced in a manifest, or any published API surface** — per repo rules these are human-review items, not auto-applied.

---

## Mixed-Category PRs

A single PR may contain Category A files (`.kt` on both branches), Category B files (`.java` needing translation), and/or Category G files (renamed/deleted upstream). These are handled together as one processing unit:

1. Cherry-pick the Category A portions (may require conflict resolution on `.kt` files modified by both the migration and the PR)
2. Translate the Category B portions
3. Apply the Category G renames/deletes (mirror on `.kt`, update references)
4. Update `.java.bak` files — including **deleting** any orphaned by Category G
5. Compile-verify the entire module
6. Commit as one unit

---

## `.java.bak` Update Strategy

**Invariant:** At all times, `File.java.bak` represents the upstream Java that `File.kt` is semantically equivalent to.

**Sequencing:** When multiple PRs touch the same Java file, `.java.bak` is updated incrementally at each PR boundary (chronological order). It is never jumped to `upstream/dev` HEAD — it advances one PR at a time.

**Verification:** After applying a translation, comparing the conceptual logic in `.java.bak` against `.kt` should reveal no semantic divergence (only syntax differences inherent to Java vs Kotlin).

**Deletion/rename (Category G):** A `.java.bak` must **never outlive its upstream Java source**. When upstream deletes `Foo.java`, delete `Foo.java.bak` (and the `.kt`, or rename it if the PR is a rename). Leaving an orphaned `.bak` pointing at a file no longer on `upstream/dev` silently breaks the invariant — the poller would keep treating a deleted file as if it still needed tracking.

**Archaeological access:** The original migration-time Java is always available via `git show <migration-commit>:path/to/File.java` — this holds even after a Category G delete, so dropping the `.java.bak` loses nothing.

---

## Verification After Each Applied PR

1. **Compile** — `./gradlew :libs:MODULE:assembleDebug` (mandatory)
2. **Test compile** — If the PR included test changes: `./gradlew :libs:MODULE:assembleDebugAndroidTest`
3. **Semantic equivalence** — Operator confirms the Kotlin diff expresses the same logic as the Java diff (informed by PR description and review comments)
4. **`.java.bak` alignment** — Updated `.java.bak` reflects the upstream Java at this PR's merge

---

## Commit Format

One commit per PR applied:
```
Port PR #NNNN: <PR title>

Upstream-PR: #NNNN (<PR URL>)
Upstream-merge: <merge commit hash>
Translated: OAuth2.java→OAuth2.kt (semantic translation)
Cherry-picked: SalesforceSDKManager.kt, LoginViewModel.kt
.java.bak updated: OAuth2.java.bak
```

---

## Sync Marker

File: `.claude/upstream-sync-marker` (**git-ignored** — per-clone operator state, never committed; added to `.gitignore` during bootstrap Step 1c)
Contents: merge commit hash of the last PR the operator has fully processed.

- Created during bootstrap (activation Step 1d) using `git merge-base HEAD upstream/dev`
- Updated only after operator confirms a PR is fully processed (translated + compiled + committed)
- The poller reads it but never writes it automatically
- **Marker vs ledger:** the marker is the *cursor* (how far processed); `.claude/upstream-sync-ledger.json` is the *map* (what every unit is + per-unit status). See "Backlog Ledger".

---

## Operator Commands (manual, not part of the cron)

After reviewing a report, the operator would:
- Ask Claude to translate specific Category B PRs (includes `.java.bak` update + `.kt` translation)
- Ask Claude to cherry-pick Category A/F PRs directly
- Ask Claude to handle Category C PRs (build/config changes)
- Ask Claude to apply Category G renames/deletes (mirror `.kt`, drop orphaned `.java.bak`)
- Tell Claude to advance the sync marker after processing (and set that unit's ledger `status` to `applied`)

---

## Silent Behavior

If `upstream/dev` has no new merged PRs since the marker:
> "Upstream sync: no new PRs since `<marker>` (last checked <timestamp>)"

And does NOT interrupt workflow.

---

## Efficiency Summary

| Operator action | Time | Frequency |
|---|---|---|
| Read poller report | ~30 seconds | Every 4 hours (usually "no new PRs") |
| Approve Category A/F cherry-pick | ~10 seconds | Per clean cherry-pick PR |
| Review Category B translation draft | ~2-5 minutes (read intent + verify Kotlin diff) | Per PR with Java→Kotlin translation |
| Handle Category C/D/E | Variable | Rare |

The agent does all research, PR context gathering, drafting, and preparation. The operator's role is **review and approval** — never authoring translations or reading raw diffs.

---

## Current Backlog (snapshot 2026-07-05)

**~48 merged PRs** to `upstream/dev` since marker `11a4a433b` (2026-05-15). Regenerate with the "Measuring the Backlog" command — the breakdown below is a **provisional** classification from PR titles only; final Category assignment happens during per-PR enrichment.

| Category | Count | Representative PRs |
|---|---|---|
| A (pure Kotlin) | ~15 PRs | Flaky-test stabilization (#2922, #2926), UI tests (#2888, #2901, #2907), My Domain (#2909), feature flags (#2937, #2953), MainApplication.kt (#2930) |
| B (Java→Kotlin translation) | ~11 PRs | OAuth2 attestation (#2913), Notification content-type (#2900), token migration (#2892), token-refresh handling (#2916), code-exchange handling (#2932), serialize refresh (#2931), instanceUrl refresh (#2936), ASA encryption (#2951) |
| C (build/config) | ~5 PRs | TOML deps (#2891), Dokka v2 (#2887), minSdk→31 (#2918), mockk pin (#2917), RN 0.81.5 catalog (#2894) |
| D (new files) | overlaps A/B | New test classes and docs land within the PRs above |
| E (React removal) | ~2 PRs | #2904 (extraction), #2906 (cleanup); React docs (#2893) |
| F (non-libs: docs/CI) | ~13 PRs | Doc sweeps (#2919, #2924, #2927, #2934), CI runner/schedule (#2910, #2939, #2941, #2942, #2950, #2952), PR-review skill (#2905) |

**Notable for downstream items:** #2930 (`MainApplication.kt`, hybrid) intersects deferred P7 — see review tracker P4. #2951 (ASA "sensitive data unencrypted") touches storage/crypto — likely an escalation trigger under the repo's security rules.

---

## Relationship to iOS Sync Job

An identical job spec exists at:
`/Users/johnson.eric/Salesforce/Repositories/SalesforceMobileSDK-iOS.Migration-Pass.2/.claude/upstream-sync-job.md`

Differences:
- iOS cron at minute :23 (this job at :17)
- iOS uses ObjC de-reference strategy (files stay at original paths, removed from Xcode project) instead of `.java.bak` rename
- iOS has additional concerns: `.pbxproj` conflicts, header file visibility, Podspec exclusion patterns
- Same PR-boundary processing unit, same classification categories, same sync marker mechanism

Both jobs report independently. Cross-platform feature parity is the operator's responsibility.

---

## To Activate This Job

Activation is **not** a one-line cron create. Two prerequisites do not exist by default and every command in this spec depends on them: the `upstream` remote and the `.claude/upstream-sync-marker`. Run the bootstrap **once, interactively**, verify detection works, and only then create the cron.

### Step 1 — Bootstrap (run ONCE, interactively)

```bash
UPSTREAM_REPO="forcedotcom/SalesforceMobileSDK-Android"

# 1a. Add the read-only upstream remote (idempotent) and disable pushing to it.
#     origin stays the push target (the fork); upstream is detection-only.
git remote get-url upstream >/dev/null 2>&1 || \
  git remote add upstream "https://github.com/${UPSTREAM_REPO}.git"
git remote set-url --push upstream DISABLED     # guard: never push to forcedotcom

# 1b. Fetch the source of truth.
git fetch upstream dev

# 1c. Marker + ledger + status view are per-clone operator state — git-ignore them (do NOT commit).
#     .claude/skills/ is tracked, so ignore the specific files, not all of .claude/.
#     Guard against a .gitignore with no trailing newline (else entries concatenate onto the last line).
[ -s .gitignore ] && [ -n "$(tail -c1 .gitignore)" ] && echo >> .gitignore
for f in '.claude/upstream-sync-marker' '.claude/upstream-sync-ledger.json' '.claude/upstream-sync-status.md'; do
  grep -qxF "$f" .gitignore 2>/dev/null || echo "$f" >> .gitignore
done

# 1d. Create the marker ONLY if absent (never clobber an in-progress backlog).
if [ ! -f .claude/upstream-sync-marker ]; then
  mkdir -p .claude
  git merge-base HEAD upstream/dev > .claude/upstream-sync-marker
  echo "Marker initialized: $(cat .claude/upstream-sync-marker)"
else
  echo "Marker already exists: $(cat .claude/upstream-sync-marker) — left untouched"
fi

# 1e. Smoke-test the corrected detection — expect a non-empty count, NOT [].
MARKER_DATE=$(git show -s --format=%cI "$(cat .claude/upstream-sync-marker)")
gh pr list --repo "$UPSTREAM_REPO" --state merged --base dev --limit 200 \
  --json number,mergedAt --jq "[.[] | select(.mergedAt > \"$MARKER_DATE\")] | length"
```

If step 1e prints `0` or `[]`, **stop** — detection is misconfigured (wrong remote, bad marker date, or `gh` auth). Do not create the cron until it returns a real backlog count.

**Step 1f — Build the ledger (the "analyze before work" gate).** Only after 1e passes. Run the full analysis pass **once** over the backlog and write `.claude/upstream-sync-ledger.json` (format + rules in "Backlog Ledger"): for each unit resolve `mergeStrategy` and constituent `commits` (via the P3 `gh api .../commits/<sha>/pulls` resolution), list `files`, assign a provisional `category`, and flag `escalation` for public-surface/manifest/crypto changes. Then **render `.claude/upstream-sync-status.md`** from the JSON (see "Operator-reviewable status view"). **Porting does not begin until this ledger exists** — it is the durable map every later step (and every restarted session) reads from. This is a one-time analysis run and is the operator's explicit call, like cron creation.

> **A pre-mapped ledger already exists** (built 2026-07-14 during spec review, 55 units through PR #2964, marker `11a4a433b`). If `.claude/upstream-sync-ledger.json` is present at bootstrap, **do not rebuild it** — validate it (marker matches, unit count sane), refresh only if the marker moved, and preserve any `status`/`category`/`notes` already edited. The pre-map was done via `gh` before the `upstream` remote existed; strategy is recorded as `merge` (parents>1) vs `single` (squash/rebase/standalone — disambiguate via diff when porting).

### Step 2 — Bootstrap preconditions (asserted every cron firing)

The cron prompt verifies these before detecting, and fails **loud** (reports "not bootstrapped — run activation Step 1") rather than silently doing nothing:
- (a) `upstream` remote exists and points at `forcedotcom/SalesforceMobileSDK-Android`;
- (b) `upstream` push URL is `DISABLED`;
- (c) `.claude/upstream-sync-marker` exists;
- (d) `.claude/upstream-sync-ledger.json` and `.claude/upstream-sync-status.md` exist (built in Step 1f) — if absent, the analysis gate was skipped; report "not bootstrapped" and stop.

### Step 3 — Create the cron (separate, operator-approved action)

Only after Steps 1e **and 1f** pass (detection works and the ledger exists). Creating a durable cron is an explicit operator decision — not part of the bootstrap script:
- `cron: "17 */4 * * 1-5"`
- `durable: true`
- Prompt implements the preconditions check (Step 2, incl. ledger) → fetch → detect → identify PRs via `gh` → classify → **upsert new units into the ledger** → report.
