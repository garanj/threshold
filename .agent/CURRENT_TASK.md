# Active Task: Zero-Allocation Fuzzy Search & Reverse Search Friction (`:core`)

- **Issue:** [#11 feat(core): implement zero-allocation fuzzy search and reverse search friction](https://github.com/garanj/threshold/issues/11)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** In Progress
- **Branch:** `feat/11-fuzzy-search`

---

## Objectives
1. [x] Implement `FuzzyMatcher` in `com.garan.threshold.core.search`:
   - Fast, zero-allocation match evaluation between query and target strings.
   - Unicode decomposition / diacritic stripping and case normalization (`café` -> `cafe`, `Über` -> `uber`).
   - Multi-tier scoring: Exact match (100) > Prefix (80) > Word boundary / Acronym (60) > Contiguous substring (40) > Subsequence (20).
2. [x] Implement `SearchItem` and `SearchEngine` in `com.garan.threshold.core.search`:
   - `SearchItem.App(val component: ComponentKey, val label: String, val accessState: AppAccessState)`
   - `SearchItem.Action(val id: String, val label: String)`
   - Querying with `:` or `,` prefix filters strictly to actions/tools.
   - Reverse search friction: Guarded apps strictly prohibited from auto-launch on partial prefix match.
3. [x] Exhaustive unit tests in `core/src/test/kotlin/com/garan/threshold/core/search/`:
   - `FuzzyMatcherTest`: exact matches, prefixes, acronyms, substrings, fuzzy subsequences, diacritic handling, performance benchmark (100k checks < 300ms).
   - `SearchEngineTest`: integration with `IntentGatekeeper`, ranking order, action prefixing, and reverse search friction behavior.
4. [x] Invariant verifications:
   - Zero `android.*` imports in `:core` (verified via `ArchitectureBoundaryTest`).
   - Pass `./gradlew test spotlessCheck`.
