# Engineering Workflow & Contributing Guide: Threshold

## 1. Issue-Driven Development (IDD) & 1:1 Contract

Every non-trivial code modification must be tied directly to a GitHub Issue:

1. **Check or Create the Issue:**
   - Must have a clear context, architectural impact assessment, and acceptance criteria.
2. **Assign & Branch:**
   ```bash
   git checkout -b feat/12-conflict-resolution-engine
   ```
3. **Write Failing Tests First (TDD):**
   - Unit tests covering the issue requirements must be committed or drafted first.
4. **Implement Cleanly:**
   - Adhere to `AGENTS.md` and `ARCHITECTURE.md`.
5. **Commit with Scope & Issue Association:**
   ```bash
   git commit -m "feat(core): implement 4-tier conflict resolution engine (#12)"
   ```
6. **Open PR with Template:**
   - Fill out `.github/PULL_REQUEST_TEMPLATE.md`.
   - Ensure all CI gates pass before merge.
   - Enforce **Squash and Merge** to maintain a clean linear history.

---

## 2. Git Branch Naming Convention

| Type | Pattern | Example |
| :--- | :--- | :--- |
| Feature | `feat/<issue>-<short-description>` | `feat/14-rotary-haptic-detent` |
| Fix | `fix/<issue>-<short-description>` | `fix/22-dialer-whitelist-leak` |
| Refactor | `refactor/<issue>-<short-description>` | `refactor/30-search-zero-alloc` |
| Testing | `test/<issue>-<short-description>` | `test/35-paparazzi-insets` |

---

## 3. Scope Discipline ("Three-Strike Rule")

If during task implementation you discover:
1. Missing prerequisite abstractions,
2. An opportunity to add an unrelated feature, or
3. Unrelated refactoring needs,

**DO NOT** bloat the active branch. Create a new GitHub Issue for the discovered item and stick strictly to the active issue's acceptance criteria.
