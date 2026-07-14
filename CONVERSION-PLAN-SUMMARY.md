# Java → Kotlin Conversion Plan Summary

## How the Android Plans Differ from the iOS Plans

The iOS plans (ObjC → Swift) served as the structural template for these Android plans (Java → Kotlin), but every platform-specific mechanism was replaced. The key differences:

### Build System
| iOS | Android |
|-----|---------|
| Xcode projects (`project.pbxproj`) manually edited at boundaries | Gradle auto-discovers sources by extension — no project file edits needed |
| `xcodebuild build/test` commands | `./gradlew :libs:MODULE:assembleDebug` (module-isolated) |
| CocoaPods `exclude_files` to hide retained originals | `.java.bak` extension invisible to Gradle — zero configuration |
| Umbrella header updates | No equivalent needed |
| Simulator destination management | No equivalent needed (compilation only, no device required) |

### File Retention Strategy
| iOS | Android |
|-----|---------|
| Original `.m`/`.h` files left in place, removed from Xcode project | Original `.java` files renamed to `.java.bak` in same directory |
| Podspec `exclude_files` added to prevent compilation | Gradle ignores `.bak` extension automatically |
| Files remain compilable (just de-referenced) | Files are inert (wrong extension) |

The iOS approach worked because Xcode only compiles explicitly-listed files. Android's Gradle discovers files by extension, so having both `Foo.java` and `Foo.kt` in the same package causes a duplicate class error. Renaming the extension is the minimal solution.

### Interoperability Annotations
| iOS (@objc strategy) | Android (JVM interop strategy) |
|----------------------|-------------------------------|
| `@objc`, `@objcMembers` on all public classes | `@JvmStatic`, `@JvmField`, `@JvmOverloads`, `@Throws` on companion/public members |
| `@objc(OriginalName)` for name preservation | `@JvmName` for bytecode name control |
| ObjC/Swift bridging headers | No equivalent — Java/Kotlin share JVM bytecode seamlessly |

### Boundary Workflow (Production Plan)
| iOS | Android |
|-----|---------|
| Remove `.m`/.h` from Xcode project | Rename `.java` → `.java.bak` (done during batch work, not at boundary) |
| Update umbrella header | N/A |
| Update podspec `exclude_files` | N/A |
| Build module | Build module |
| Build downstream libraries | **Removed** — downstream modules absorb changes during their own conversion |
| Run tests | **Removed** — test plan handles this after test conversion |
| Fix test failures | **Removed** |

The Android production plan boundary is dramatically simpler: **compile the module → fix errors → commit → gate.** This lesson was learned from the iOS execution where downstream effects consumed significant time fixing modules not yet being converted.

### Dependency Graph
| iOS (5 libraries) | Android (6 libraries + 2 framework bridges) |
|-------------------|---------------------------------------------|
| SalesforceSDKCommon → SalesforceAnalytics → SmartStore → MobileSync → SalesforceSDKCore | SalesforceAnalytics → SalesforceSDK → SmartStore → MobileSync → SalesforceHybrid / SalesforceReact |

Android has two additional leaf libraries (SalesforceHybrid, SalesforceReact) that bridge to Cordova and React Native frameworks respectively.

### Phase Structure
| iOS | Android |
|-----|---------|
| 5 library phases + 1 sample app = 6 phases | 6 library phases + 1 sample app = 7 phases |
| Phase 5 (SalesforceSDKCore, 116 files) used parallel tracks | Phase 2 (SalesforceSDK, 57 files) uses parallel tracks |
| 48 batches total | 36 batches (production), 25 batches (test) |
| 198 production + 98 test files | 126 production + 95 test files |

### What Was Removed Entirely
- All `xcodebuild` commands
- All `project.pbxproj` manipulation
- All podspec/CocoaPods configuration
- All umbrella header management
- All bridging header removal
- All simulator destination management
- All `@objc`/`@objcMembers` annotation strategy
- All `NSSecureCoding`, `FMDB` references
- All downstream compile checks (production plan)
- All test runs (production plan)
- All test baseline recording (production plan)
- Connected device/emulator requirement (production plan)

---

## Conversation Summary

### Initial Request
The user asked to rewrite two iOS migration plans (ObjC → Swift) for the Android equivalent repository (Java → Kotlin), preserving operational goals while adapting all platform-specific logic. Key constraints:
1. Original Java files cannot remain parallel with their replacements using the same extension (Gradle would compile both)
2. Renaming extensions to `.java.bak` was proposed and adopted
3. Gradle builds should target individual modules to avoid time-consuming full-project builds

### Research Phase
Comprehensive inventory of the Android repository:
- 117 Java production files across 6 libraries (SalesforceAnalytics: 12, SalesforceSDK: 57, SmartStore: 19, MobileSync: 1, SalesforceHybrid: 18, SalesforceReact: 10)
- 9 Java sample app files
- 125 existing Kotlin production files
- 95 Java test files across 6 test modules
- 54 existing Kotlin test files
- 1 same-directory naming conflict (LoginServerManagerTest.java/.kt in test)
- All 6 libraries already have `kotlin-android` plugin configured

### Plan Writing
Both plans were written in a single pass, adapting the iOS structure to Android while:
- Replacing all iOS build commands with module-isolated Gradle commands
- Replacing `@objc` strategy with JVM interop annotations (`@JvmStatic`, `@JvmField`, etc.)
- Using `.java.bak` rename instead of Xcode project de-referencing
- Adding Android-specific concerns (AndroidManifest components, ProGuard rules, Parcelize)
- Restructuring phases to match the Android dependency graph

### Four Review Cycles
1. **First review** found: iOS residuals (NSSecureCoding, FMDB), file count errors, missing AndroidManifest tracking, incorrect kotlin-android plugin claims, missing downstream checks, base class ordering issues in test plan, phantom file in batch listing
2. **Second review** found: Remaining arithmetic errors in track file counts, build pass count wrong, SmartStore downstream check incomplete, kotlin-serialization not addressed, large file isolation batch numbers wrong in test plan
3. **Third review** confirmed: Both plans clean except one post-conversion find filter issue (production) and one batch count typo (test plan header)
4. **Fourth review** confirmed: Both plans fully consistent, one minor build count convention difference between plans

### Efficiency Refinement
Based on iOS execution experience where downstream module fixes consumed time before those modules' own conversion, the production plan was simplified:
- **Removed:** All downstream compile checks at boundaries
- **Removed:** All test runs at boundaries
- **Removed:** Test baseline recording
- **Removed:** Connected device requirement
- **Result:** Boundary = compile module → fix errors → commit → gate

The test plan retains full build + test at its boundaries since tests are its deliverable.

---

## Promotional Brief

### Salesforce Mobile SDK for Android: Java → Kotlin Production Migration

**What:** Complete conversion of 126 Java production files (~31,200 lines) and 95 Java test files (~27,500 lines) to idiomatic Kotlin across the entire Salesforce Mobile SDK for Android.

**Why:** 
- Kotlin is the language standard for all new Android SDK code (per CLAUDE.md)
- Eliminates the Java/Kotlin boundary that forces JVM interop annotations on new code
- Enables full use of Kotlin coroutines, null safety, sealed classes, and data classes across the SDK
- Aligns with the iOS repository's equivalent Swift migration (completed/in-progress)
- Reduces cognitive load for contributors who currently navigate a mixed-language codebase

**Scope:**
- 6 SDK libraries: SalesforceAnalytics, SalesforceSDK, SmartStore, MobileSync, SalesforceHybrid, SalesforceReact
- 3 sample apps: RestExplorer, AppConfigurator, ConfiguredApp
- 125 existing Kotlin files preserved and verified compatible
- 13 security-critical files (OAuth2, encryption, keystore, token storage) flagged for human review

**Approach:**
- Incremental conversion following the dependency graph (leaf libraries first)
- Module-isolated builds at each boundary — no full-project builds until final verification
- Parallel track execution for the largest library (SalesforceSDK, 57 files, 4 tracks)
- Scout-then-parallel pattern: sequential discovery batches feed learned patterns to parallel workers
- Operator review gates at each library boundary for human oversight
- Original Java files preserved as `.java.bak` for post-conversion audit

**Key Design Decisions:**
1. **No downstream compile checks** — each module absorbs upstream API changes during its own conversion via grep-driven signature discovery
2. **No test runs during production conversion** — tests remain Java until the separate test plan converts them to Kotlin
3. **`.java.bak` retention** — enables trivial `diff` auditing and single-command cleanup when verified
4. **JVM interop annotations preserved** — external Java consumers continue to work unchanged
5. **Two-plan separation** — production conversion is pure compilation; test conversion is behavior verification

**Estimated Structure:**
- Production: 36 batches, 7 operator gates, 8 builds
- Test: 25 batches, 6 operator gates, 7 builds
- Total: 61 batches of semantic conversion work

**Risk Mitigations:**
- Security-critical files (OAuth, keystore, encryption) explicitly flagged for operator review
- AndroidManifest-registered components tracked to prevent class name drift
- ProGuard/R8 consumer rules verified post-conversion
- Escalation thresholds halt execution on systematic build failures (>20 errors or >10 same-category)
- Every operator gate provides proceed/adjust/stop decision points
- Full rollback via `git revert` of any single library commit

**Prerequisites:**
- Clean build of all 6 library modules
- `./install.sh` completed (submodule dependencies present)
- `.claude/settings.json` permissions configured for Gradle and file operations
