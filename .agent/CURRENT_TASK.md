# Active Task: CI Sticky PR Comment with Test Summary & Memory Metrics

- **Issue:** [#19 ci: post sticky PR comment with test summary and memory metrics](https://github.com/garanj/threshold/issues/19)
- **Status:** Complete / Ready for Review
- **Branch:** `feat/19-pr-test-summary-comment`

---

## Objectives
1. [x] **Update `.github/workflows/ci.yml` Permissions**:
   - Granted `pull-requests: write` and `issues: write` on the PR commenting job.
2. [x] **Scorecard Aggregator**:
   - Aggregated JVM test counts, Roborazzi golden screenshot checks, emulator functional test counts, and PSS memory metrics (< 30 MB ceiling) into outputs.
3. [x] **Sticky Comment in PR Discussion Thread**:
   - Implemented native `gh api` comment updater using unique HTML marker (`<!-- threshold-ci-summary -->`).
   - Ensures zero spam by updating a single designated comment in place across pushes.
   - Protected with `continue-on-error: true`.
4. [x] **Verification**:
   - Validated YAML syntax with `yaml.safe_load`.
   - Verified spotless code formatting and unit tests pass.
