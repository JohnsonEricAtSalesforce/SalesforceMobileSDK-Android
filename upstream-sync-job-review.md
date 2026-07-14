# Upstream Sync Job — Self-Review Findings & Remediation Tracker

**Reviewed:** 2026-07-05 · **Subject:** `upstream-sync-job.md` (v3)
**Branch:** `feature/java-to-kotlin-test-migration`
**Method:** Spec claims verified against the live repo (git remotes, `gh` auth/visibility, `origin/dev` merge shapes, marker/cron state).

## Verdict

The **design** is sound — Category A–F protocol, PR-boundary processing unit, `.java.bak` invariant, and operator-review-only reporting should all be kept. The **plumbing** is what fails: detection is pointed at the personal fork instead of `forcedotcom`, it never fetches before counting, and it assumes a uniform merge shape that `dev` does not use. If the cron were activated as written today, it would silently report "no new PRs" forever while drift keeps growing.

## Findings (ranked)

| ID | Sev | Title | Status |
|----|-----|-------|--------|
| P1 | 🔴 | Detection queries the wrong repo (`origin` = personal fork, not `forcedotcom`); no `upstream` remote exists | **DONE 2026-07-05** (spec edits only; remote add deferred to P5) |
| P2 | 🟠 | "94 commits behind" is stale (local `origin/dev` last fetched 2026-06-18) and counts raw commits, not PRs | **DONE 2026-07-05** (spec + memory + regenerated backlog table) |
| P3 | 🟠 | Mixed merge strategies on `dev` (squash, merge-commit, rebase, direct push) break the `<merge>^1..` diff derivation and PR-vs-standalone detection | **DONE 2026-07-14** |
| P4 | 🟡 | Upstream rename/delete PRs (e.g. #2930 `HybridApp.java`→`MainApplication.kt`) have no A–F home; `.java.bak` invariant breaks on deletes. (Original "collides with P7" claim corrected — see notes.) | **DONE 2026-07-14** (spec: new Category G + invariant; #2930 port recorded, not applied) |
| P5 | 🟡 | No marker file, no `upstream` remote, cron never activated; first run needs a multi-step bootstrap, not a one-line cron create | **DONE 2026-07-14** (spec documents bootstrap; not executed — activation is operator's call) |
| P6 | 🟢 | Spec hygiene: durable-vs-7-day-expiry contradiction; stale dated tables; no changelog | **DONE 2026-07-14** |
| R7 | 🟠 | Analysis output isn't durable — spec persists only the marker (a cursor), so the costly commit→PR mapping + A–G classification is recomputed every firing and lost on context loss | **DONE 2026-07-14** (spec: ledger format + bootstrap/poller wiring; ledger not generated — activation is operator's call) |

| R8 | 🟠 | JSON ledger not operator-reviewable; commit-history mapping needs human eyes + a visible migration-status view | **DONE 2026-07-14** (spec: rendered status view; live pre-map of 55 units + status view generated as durable inputs) |

> **Naming note:** review findings on `upstream-sync-job.md` use **P1–P6** (original 2026-07-05 review) and **R7+** for findings added later. The **R** prefix on new items avoids collision with the *test-migration* P7/P8 (Hybrid `bootconfig.json` / React Hermes blockers), which are unrelated. R7/R8 are review findings, not test blockers.

## Evidence captured during review

- `git rev-list --left-right --count HEAD...origin/dev` → `26 ahead / 94 behind`, but `origin/dev` tip `03e67d741` was last fetched **2026-06-18**.
- `gh pr list --state merged --base dev` (defaults to `origin`) → **`[]`**. The fork has only ~4 PRs (#1–#4).
- Real PRs (#2932, #2930, #2929…) live on `forcedotcom/SalesforceMobileSDK-Android`; `gh pr view 2932 --repo forcedotcom/…` returns them.
- `git remote -v` → only `origin` = `github.com/JohnsonEricAtSalesforce/SalesforceMobileSDK-Android.git`. **No `forcedotcom`/`upstream` remote.**
- `dev` history mixes squash-merges (`#2932`, single commit), `Merge pull request` commits (#2929/#2930), and direct pushes (`5dfed7ce9`, `f1d405a9d`).
- No `.claude/upstream-sync-marker`; no cron registered. `git merge-base HEAD origin/dev` resolves to `11a4a433b…` (but against the fork).
- iOS counterpart spec exists at `…-iOS.Migration-Pass.2/.claude/upstream-sync-job.md` (cross-ref is valid).

## P1 — applied changes (2026-07-05, Option B: spec edits only)

Added a **Remote Contract** section to `upstream-sync-job.md` (before Job Configuration): `origin` = fork = push target; `upstream` = `forcedotcom` = read-only detection source; defines `UPSTREAM_REPO` var and the "name the remote explicitly, never rely on default" invariant. Retargeted every detection command:
- Fetch → `git fetch upstream dev`; Detect fallback → `git merge-base HEAD upstream/dev`; standalone log → `..upstream/dev`.
- All `gh pr list/view/diff` → `--repo "$UPSTREAM_REPO"` (verified: no `gh` command lacks it).
- `gh pr list` limit raised 50→200 with a comment that the marker-date jq filter is the real window bound (silent-truncation guard; full PR-vs-commit semantics deferred to P2).
- Fixed `.java.bak`/marker/silent-behavior prose that said `origin/dev` HEAD → `upstream/dev`.
- **Deferred intentionally:** lines 7, 11, 293 (dated "61 behind `origin/dev`" prose) → corrected under P2/P6.
- **Not applied (Option B):** the `git remote add upstream …` bootstrap → lands in P5.

## P2 — applied changes (2026-07-05)

- Added **"Measuring the Backlog"** section to `upstream-sync-job.md`: PRs-not-commits rule; mandatory `git fetch upstream dev`; marker-date-derived `gh pr list` count; separate direct-push count; `--limit 200` truncation contract.
- Replaced stale **"Current State (2026-06-08)"** block: removed hardcoded "61 behind"; now points to the on-demand command (+ dated 2026-07-05 snapshot: ~48 PRs since marker `11a4a433b`).
- **Regenerated the "Current Backlog" table** from live `gh pr list` data (48 real PRs, provisional A–F classification from titles). Flagged #2930 (Hybrid→P4) and #2951 (ASA crypto→security escalation).
- **Corrected the durable memory** `project_upstream_drift_status.md`: "~94 commits" → "~48 PRs since 2026-05-15", added the remote contract and PRs-not-commits methodology, plus an in-progress note pointing at this tracker.
- Verified no residual stale `origin/dev`/commit-count prose except the intentional invariant comment and the explanatory "why the old numbers were wrong" note.

## P3 — applied changes (2026-07-14)

Empirical finding refined the original review claim: `^1..` works for squash (single-parent, own diff = net) AND merge-commit (first-parent exclusion) — but **rebase-merge** (repo allows all three strategies) silently truncates multi-commit PRs to just the final replayed commit, and **merge strategy is indeterminable from commit shape** (squash/rebase-tip/direct-push all present as single-parent). `gh pr diff` is unconditionally correct (verified: #2932 squash → 551 lines, #2929 merge → 21 lines).

- **Deleted the `git diff <merge>^1..` recipe** from the diff-derivation block; `gh pr diff <number> --repo "$UPSTREAM_REPO"` is now PRIMARY, `git diff <commit>^..` kept only as fallback for genuine standalone pushes. Added a "Why not `^1..`?" warning documenting the three failure modes.
- **Fixed P2's Rule 3 direct-push count (option A):** replaced the `grep -v 'Merge pull request'` filter — which mis-classifies squash/rebase PRs as standalone and double-counts them — with a per-commit `gh api .../commits/<sha>/pulls` association check (0 PRs = genuine standalone).
- **Fixed standalone detection in step 3** with the same association check.
- **Cross-referenced Category B Step 2** to the authoritative derivation.
- Verified no executable `^1..` or bare-`grep` recipe survives (only the intentional warnings remain).

## P4 — applied changes (2026-07-14)

Reading the actual #2930 diff corrected the original review claim: #2930 is a **rename+rewrite** (`HybridApp.java` deleted, `MainApplication.kt` added, two sample manifests repointed `HybridApp`→`MainApplication`), and it **does NOT touch `bootconfig.json`** — so it neither fixes nor worsens the deferred P7 blocker. The review's "collides with P7" framing was over-stated; the only real linkage is "same module, natural moment to also do P7." The genuine problem is structural: our branch already holds `HybridApp.kt` + `HybridApp.java.bak`, and a delete/rename has **no Java diff to translate**, so no A–F category fit and the `.java.bak` invariant breaks (orphaned `.bak` pointing at a deleted upstream file).

Spec changes (general mechanism, per operator's choice):
- **Added Category G** (rename/delete/restructure) to the classification table + a "classify by dominant action" note; B/D assumed the upstream file still exists.
- **Added "Category G Protocol"** section: determine shape (pure delete / rename / rename-to-kt) → mirror on `.kt` + update all references (manifests, imports) → **delete orphaned `.java.bak`** → compile + escalate on public/manifest names.
- **Added a `.java.bak` deletion invariant:** "a `.java.bak` must never outlive its upstream Java source"; noted archaeological access via `git show` survives the delete.
- **Extended Mixed-Category PRs** to include a G step and explicit `.bak` deletion.

Recorded #2930 port plan (NOT applied — public-surface change, needs human review):
- Rename `HybridApp.kt`→`MainApplication.kt`; `open class HybridApp`→`class MainApplication` (note: upstream's is not `open`; our migration made it `open` — decide whether to preserve).
- Delete `HybridApp.java.bak`; update both sample manifests `HybridApp`→`MainApplication`; `:libs:SalesforceHybrid:assembleDebug`.
- Classification: Category G (+ manifest edits). **Escalation: public Application class name + manifest change.**

**P7 decoupled:** remains its own deferred blocker (missing `www/bootconfig.json`); #2930 does not address it. React removal #2904/#2906 is the same G shape and should reuse this protocol.

## P5 — applied changes (2026-07-14)

Verified live: no `upstream` remote, no `.claude/upstream-sync-marker`, activation section was a bare 4-line cron create. As written, the first cron firing would error on `git fetch upstream dev` (unknown remote). Also noted `.claude/skills/` IS tracked, so the marker must be ignored file-specifically, not by ignoring `.claude/`.

- **Rewrote "To Activate This Job"** into 3 steps: (1) interactive one-time **Bootstrap** — idempotent `git remote add upstream` + `set-url --push upstream DISABLED` guard, `git fetch upstream dev`, git-ignore the marker (`.gitignore` append, idempotent), create marker only if absent (never clobber in-progress backlog), and a **detection smoke-test** that must return a non-empty count before proceeding; (2) **Bootstrap preconditions** the cron asserts every firing (remote exists → forcedotcom, push URL DISABLED, marker exists) failing loud not silent; (3) **cron creation** as a separate operator-approved action, gated on the smoke-test passing.
- **Marker section:** documented git-ignored / per-clone / never-committed; creation moved to bootstrap Step 1d.
- **Decisions (operator-approved):** marker git-ignored; bootstrap manages the `.gitignore` entry.
- **NOT executed:** no `git remote add`, no marker created, no cron scheduled — activation is the operator's explicit call (durable cron per CLAUDE.md needs go-ahead). This iteration documents the bootstrap only.

## P6 — applied changes (2026-07-14)

- **Resolved the durable-vs-expiry contradiction:** rewrote both Job Configuration lines to state they are independent mechanisms (durable = survives restarts; auto-expire = 7-day safety valve requiring deliberate re-creation).
- **Bumped v3 → v4** with a top-of-file changelog summarizing P1–P6 and pointing at this tracker (the single best durability improvement — tells the next reader the spec was audited and where the rationale lives).
- **Labeled the example report block** "Illustrative example (historical PRs) — not a live queue" so it can't be misread as live state.
- **Confirmed remaining dated content is legitimately dated** (labeled 2026-07-05 snapshots + the "why old numbers were wrong" note), not stale bugs.
- **Final sweep:** every surviving `origin/dev` / `^1..` / "61/94 commits" hit is an intentional warning or the changelog — no live footguns.

## R7 — applied changes (2026-07-14)

Operator raised that with ~48 PRs and non-trivial commit→contribution mapping, the analysis is the dominant cost — and asked whether the plan makes that analysis durable before work begins. It did not: the spec persisted only the marker (a cursor); Step 7 said "reports only," so the commit→PR mapping + A–G classification was recomputed every firing and lost on context loss.

- **Added "Backlog Ledger" section** to the spec: `.claude/upstream-sync-ledger.json` (git-ignored, per-clone), one entry per unit with pr/mergeSha/mergeStrategy/commits/files/category/categoryConfidence/status/escalation/notes. Immutable identity (computed once); only category/status/escalation/notes edited later.
- **Bootstrap Step 1f** — "Build the ledger" is now the explicit *analyze-before-work gate*; porting cannot begin until it exists.
- **Step 6** — report is now a *view* of the ledger; poller upserts new units (append-only, never recompute existing).
- **Preconditions** gained (d) ledger-exists; **Step 3 cron prompt** and Operator Commands updated; **Sync Marker** cross-links marker(cursor) vs ledger(map); 1c git-ignores both files.
- **Version bumped v4 → v5** with changelog entry.
- Chose JSON (programmatic upsert/status parsing) and named it **R7** to avoid collision with the test-migration P7/P8 blockers.

## R8 — applied changes (2026-07-14)

Operator asked for an operator-reviewable format for the ledger + commit-history mapping, saved as part of the ledger, showing migration status. Then asked to actually pre-map the history now as a durable job input.

Spec (v5→v6):
- **Added "Operator-reviewable status view"** to the Backlog Ledger section: `.claude/upstream-sync-status.md`, a Markdown rendering **derived from the JSON** (never hand-edited; JSON wins on conflict; re-rendered on every write). Layout: provenance header, summary + progress bar, ⚠️ Escalations pulled to top, then chronological Units table with an "Orig commits → contribution" column that makes squash/merge/rebase collapsing visible. Grouping = chronological; single combined table (operator's choices).
- Wired render into bootstrap 1f + Step 6 upsert; added status file to 1c git-ignore and precondition (d); bumped version + changelog.
- **Fixed a real bug found while running it:** the 1c `.gitignore` append had no trailing-newline guard (it concatenated onto `keep.xml`). Added a guard to the spec and repaired the live `.gitignore`.

Durable pre-map (executed now, before activation):
- Pulled all **55 backlog PRs** (#2887→#2964) via `gh`, enriched each with constituent commits + files + merge-parent count (parallelized, resumable per-PR files).
- Classified provisionally (path-heuristic): A=14, B=15, C=2, D=2, E=2, F=19, G=1. Strategy: 29 merge / 26 single.
- **17 escalations** each with a concrete reason (auth/crypto source, manifest, build system) — e.g. #2930 (G, manifest), #2904 (E), #2913/#2916/#2936/#2958/#2960 (B, OAuth/SQLCipher), #2918/#2951.
- Wrote `.claude/upstream-sync-ledger.json` (55 units) + rendered `.claude/upstream-sync-status.md` (92 lines). Both git-ignored, verified untracked. #2904 visibly maps **17 commits → 1 contribution** — the reviewable history map the operator asked for.
- Spec Step 1f now says: if a pre-mapped ledger exists, validate + preserve edits, don't rebuild.

**Caveats on the pre-map:** categories are PROVISIONAL (title/path heuristic, not diff-confirmed); `single` strategy conflates squash/rebase/standalone (disambiguate via `gh pr diff` when porting); built via `gh` before the `upstream` remote exists, so commit SHAs are upstream's, not yet fetched locally.

## Status: ALL P1–P6 + R7 + R8 COMPLETE (2026-07-14)

The spec (`upstream-sync-job.md` **v6**) is internally consistent and ready, and a **durable pre-mapped ledger + reviewable status view now exist** as activation inputs (`.claude/upstream-sync-ledger.json`, `.claude/upstream-sync-status.md`; 55 units, marker `11a4a433b`). Still **not** activated — bootstrap (which will now *validate* the existing ledger, not rebuild) + cron creation remain the operator's explicit call. The recorded-but-unapplied #2930 Category-G port is pending human review as a public-surface change.

**Operator's next natural step:** review `.claude/upstream-sync-status.md` — especially the 17 escalations — and confirm/correct the provisional categories before porting begins.

## Activation progress (2026-07-14)

- **Working tree cleaned** — commits `bc2a150af` (12 .java deletions) + `595643aa9` (docs + gitignore). Tree clean; branch 28 ahead of `dev`.
- **Bootstrapped (Steps 1a–1f)** — `upstream` remote added (push DISABLED), `upstream/dev` fetched (tip #2964), marker `11a4a433b` created (== `git merge-base HEAD upstream/dev`, matches pre-map). Smoke-test passed; ledger validated (55 units, not rebuilt). No cron — operator-driven first pass.
  - The raw smoke-test count is 57 vs the ledger's 55: #2884 (its merge commit **is** the marker) and #2885 are same-second boundary ancestors, correctly excluded. Not drift.

### Escalation walkthrough — COMPLETE (2026-07-14)
Operator chose option (a); all 17 escalations were reviewed against their file lists and **APPROVED**. Recorded durably in the ledger as `escalationApproved:true` + `escalationApprovedAt:"2026-07-14"` on each escalated unit; status view now shows a "Review" column (✅ approved). Renderer copied to a durable path `.claude/upstream-sync-render.jq` (was `/tmp`-only; git-ignored).

**Category corrections surfaced during review** (provisional → recommended; NOT yet written to ledger `category` fields — categories stay provisional until diff-confirmed at port time):
- #2894 F→**C + likely SKIP** (mooted by #2904 React removal)
- #2917 F→**C** (catalog pin); #2929 D→**C/config** (manifest edit, not new file)
- #2913 B→**mixed A+B**; #2958 B→**mixed B+D** (new `OAuthErrorCode.kt`); #2960 B→**C+B** (dep bump + 1 Java test)
- #2918 (A) & #2951 (A): source is Kotlin-on-Kotlin, but **build+policy** (#2918 minSdk — needs team sign-off) / **security** (#2951 ASA — high priority) escalations dominate
- #2956 A holds, but `sf__strings.xml` = **localization escalation** too
- Root signal for B-vs-A: OAuth source (`OAuth2.java`, `AuthenticatorService.java`, `ClientManager.java`) is still `.java` upstream ⇒ our branch has `.kt`+`.java.bak` ⇒ upstream Java diff must be **translated** (B). Already-`.kt` upstream files ⇒ near-cherry-pick (A, subject to drift).

**ORDERING INVARIANT added to spec** (`upstream-sync-job.md` → Processing Unit section): chronological/ledger merge order (unit 1→N) is **mandatory**; reordering by risk/ease manufactures phantom conflicts in both directions (later-first = no base to land on; earlier-after-restructure = stale conflict). Only permitted deviation: provably-disjoint file sets. A skip decision and a pre-run go/no-go gate are **not** reorders. (This corrects my own earlier "port easy ones first" suggestion, which had inverted units 1,2 behind 4,21 and would have broken the TOML-catalog dependency chain.)

### Strategic gates — BOTH APPROVED (2026-07-14)
- **#2904 (unit 11) — Remove SalesforceReact: GO.** Adopt upstream's extraction to SalesforceMobileSDK-ReactNative. Ledger: `gateDecision:"go"`. Consequence: **#2894 marked `skipped`** (RN version bump moot once the lib is removed). Also simplifies our deferred-React (P8/Hermes) problem. NB when porting: touches `settings.gradle.kts` + `install.sh`/`install.vbs` — permitted here by this explicit approval.
- **#2918 (unit 22) — Bump minSdk to 31: GO.** Team sign-off given. Ledger: `gateDecision:"go"`.

Ledger status now: 54 pending / 1 skipped (#2894). Both gate decisions + the skip are recorded in-ledger with notes and re-rendered into the status view.

### ▶️ PORTING IN PROGRESS (autonomous mode) — post-compaction, through #2904 (2026-07-14)

**Progress: 11 of 55 processed — 10 applied, 1 skipped, 44 pending. Marker: `f9517493e` (#2904). Tree clean; 40 commits ahead of dev.**

Applied (each a separate commit, upstream-attributed):
- #2887 (C) Dokka v2 — `ba8801b10`
- #2888 (F) RTR UI tests — `23a0bcbbd`
- #2891 (C) TOML catalog — `3b8740463`
- #2892 (A) token-migration silent-failure fix, hand-applied (companion-import drift) — `0ec6b0d3a`
- #2900 (B) form-urlencoded notif body (first Java→Kotlin translation) — `cebf7e06f`
- #2903 (B mixed) unit-test-timeout fixes: shard JSON + new Biometric test + 2 .kt edits applied clean, AuthConfigUtilTest .java diff hand-translated onto .kt (HandlerThread, 30s timeouts) — `75680a969`
- #2901 (F) AuthFlowTester UI tests; nullability fix `HttpAccess.DEFAULT!!`/`refreshToken!!` — `04dc89e7d`
- #2893 (F) SalesforceReact docs — **UN-SKIPPED CORRECTION** — `a097dc381`
- #2894 (F) RN 0.79.3→0.81.5 catalog bump — **UN-SKIPPED CORRECTION** — `44b24421f`
- #2904 (E) **Remove SalesforceReact** (46 upstream deletes → 69 on our branch incl .java.bak; 9 mods; gate-approved build/install edits) — `f57065e7b`

Skipped (verified valid):
- #2890 empty merge-from-master (0 files / 0 additions / 0 deletions — re-confirmed).

**⚠️ SKIP-AUDIT FINDING (2026-07-14): 2 of my 3 earlier content-based skips were WRONG and have been corrected.**
- **#2893** — earlier skip claimed "#2904 deletes these 4 docs → net-zero." FALSE: #2904 *modifies* (not deletes) `docs/salesforcereact/*.md` (adds "moved to ReactNative repo" banner); all 4 persist at upstream tip. Skipping left drift AND left #2904's doc edits with no base. Un-skipped + ported ahead of #2904 (`a097dc381`).
- **#2894** — earlier skip claimed "mooted by #2904 (RN removal)." FALSE: #2904 does not touch `libs.versions.toml`, and `react-android = 0.81.5` persists at tip (SalesforceReactActivity/SDKManager only moved repos; the catalog entry stays). Un-skipped + applied (`44b24421f`). This also clears old carried-forward note 1.
- **Lesson:** content-based skip reasoning that depends on a *later* unit's behavior must be verified against that unit's actual `gh pr diff` AND upstream tip *before* skipping — not assumed. Both old carried-forward notes are now void.

**RESUME AT: unit 12 = #2905 (F) "Add PR Review Skill" (merged 2026-05-28).** Then #2906 (E — "Remove remaining SalesforceReact references"), #2907 (A), onward. NOTE for #2906: React trees are already fully gone on our branch as of #2904; #2906's remaining-reference cleanup may be partial no-ops — verify against its actual diff.

### ▶️ UPDATE — through #2913 (2026-07-14, post-compaction session)

**Progress: 19 of 55 processed — 18 applied, 1 skipped, 36 pending. Marker: `20ddbd717` (#2913). Tree clean; 49 commits ahead of dev.**

Additional applied since last update:
- #2905 (F) PR Review Skill (.prizm + .claude/skills symlink) — `6a91c3b0d`
- #2906 (E) remaining SalesforceReact refs (4 CI/config + 2 READMEs) — `8d0555f16` — **CI-config approved for React cluster**
- #2907 (A) Simulate Welcome Discovery test seam — `4c8ad35ad` — **ESCALATION (login UI + sf__strings), operator-approved.** New user-facing string `sf__login_options_save_and_login`.
- #2908 (A) postValue crash fix — `5de9d6c93`
- #2910 (F) CI security hardening (4 workflows) — `c0bfe3197` — **ESCALATION (CI config), operator-approved THIS-UNIT-ONLY**
- #2912 (B) test_login_domain https prefix (TestCredentials.kt) — `83a93f656`
- #2909 (A) transient Welcome Discovery My Domain — `1bce0655e` — **ESCALATION (login UI), login-UI cluster approved**
- #2913 (B) app attestation → Web Server Flow — `2fef69159` — **ESCALATION (OAuth flow + new public API), operator-approved.** FLAG API REVIEW: new public `SalesforceSDKManager.initNative()` + `@JvmOverloads` ctor overload.

**APPROVAL POLICY (operator, 2026-07-14):**
- **React-cluster CI-config edits:** APPROVED for the run (#2904/#2906 and similar React-consequence CI).
- **Non-React CI-config edits:** per-unit approval required (approved #2910 only).
- **Login-UI / Welcome-Discovery flow changes:** APPROVED for the run — BUT still individually flag OAuth-token-exchange / credential-storage / crypto changes (not mere login navigation). Flagged & approved #2913 under this.

**RELEASE-NOTES / LOCALIZATION / API-REVIEW backlog to surface at run end:**
- New user-facing localized string: `sf__login_options_save_and_login` ("Save and Login") — #2907.
- New public API: `SalesforceSDKManager.initNative(context, mainActivity, googleCloudProjectId)` + `@JvmOverloads` ctor overload — #2913.

**Escalation-flag heuristic caveat:** ledger `escalation` flag is unreliable — #2907 was `escalation:false` but hit login-UI + localization triggers. CONTINUE inspecting every unit's actual diff for CLAUDE.md triggers regardless of the ledger flag.

**RESUME AT: unit 20 = next pending after #2913 (check `jq '.units[]|select(.status=="pending")' | head`).** Same per-unit loop + techniques as above.

### ▶️ UPDATE — through #2916 (2026-07-14, post-compaction session, cont.)

**Progress: 23 of 55 processed — 22 applied, 1 skipped, 32 pending. Marker: `699f7119a` (#2916). Tree clean; 54 commits ahead of dev. BOTH strategic gates landed (#2904 React removal, #2918 minSdk→31).**

Additional applied since last update:
- #2915 (F) AuthFlowTester README — `c1b7d9417`
- #2917 (F) mockk pin 1.14.9→1.14.5 (build-system escalation, pre-approved) — `71d8620bd`
- #2918 (A) **minSdk 28→31** (36 files, GATE GO w/ team sign-off) — `f4014f495`
- #2916 (B) **token-refresh error handling** (3 auth .kt files, credential path) — `cf2682c80` — **ESCALATION (OAuth/credential), operator-approved "port with extra care"; self-reviewed control flow. FLAG SECURITY REVIEW.**

**#2916 translation specifics (highest-risk unit so far):** client_blocked/client_blocked_retry error constants + CLIENT_BLOCKED LogoutReason (OAuth2.kt); refreshStaleToken now THROWS instead of returning null; getNewAuthToken terminal-vs-retriable branching (Kotlin has no multi-catch → typed if/else in one catch); revoke-intent error extras; kept upstream's defensive null-guard via @Suppress(SENSELESS_COMPARISON). ClientManagerMockTest merged 3-way (kept our reflection-based private refreshToken access). All compiled; NOT run on device.

**RELEASE-NOTES / API-REVIEW / SECURITY backlog (cumulative, surface at run end):**
- New localized string `sf__login_options_save_and_login` — #2907.
- New public API `SalesforceSDKManager.initNative()` + ctor overload — #2913.
- minSdk raised to 31 (breaking for API 28-30 consumers) — #2918 (gate-approved).
- SECURITY REVIEW: token-refresh credential path rewrite — #2916.

**RESUME AT: unit 24 = next pending after #2916.** Same per-unit loop. Reminder: tests compiled but NOT executed on device this run (no Firebase/emulator); flag that in the final PR.

**Per-unit loop (proven this session):** `gh pr diff <n> --repo "$UPSTREAM_REPO"` → save to /tmp → `git apply --check` (clean = branch matches upstream base; reject = drift, read rejects + hand-apply) → for Cat B, translate the `.java` upstream diff onto our `.kt` (watch Kotlin-nullability: our migrated types are `T?` where Java was a platform type) → compile affected lib (`:libs:<lib>:compileDebugSources` + `compileDebugAndroidTestSources`; use ONLINE gradle — offline fails on uncached deps, not a code error) → commit ONE unit with upstream attribution → advance marker to unit's `mergeSha` → set ledger status=applied + localCommit + re-render status view. Escalations all pre-approved: proceed, don't stop to ask.

## Working protocol

We work through P1→P6 in iterations, together. For each item: re-think → propose → operator approves → apply edit to `upstream-sync-job.md` → mark done here. This tracker is the durable record; the session task list mirrors live status.
