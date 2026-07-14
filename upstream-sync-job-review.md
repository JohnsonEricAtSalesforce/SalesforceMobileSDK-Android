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

## Working protocol

We work through P1→P6 in iterations, together. For each item: re-think → propose → operator approves → apply edit to `upstream-sync-job.md` → mark done here. This tracker is the durable record; the session task list mirrors live status.
