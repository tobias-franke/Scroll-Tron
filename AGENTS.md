# Agent Guidelines & Workflow Rules

## Git & PR Workflow
1. **Never branch from local `main` without fetching**:
   - Always run `git fetch origin main` first.
   - Create new branches directly from `origin/main`:
     ```bash
     git checkout -b <branch-name> origin/main
     ```

2. **Pre-PR Freshness Verification Gate**:
   - Before opening a pull request or pushing for review, verify whether `origin/main` has advanced:
     ```bash
     git fetch origin main
     git log HEAD..origin/main --oneline
     ```
   - If there are incoming commits, rebase immediately:
     ```bash
     git rebase origin/main
     ```
   - Re-run local verification (e.g. `./gradlew check`) to confirm no newly merged commits conflict or cause regressions.
   - Only push and create the pull request once the branch is confirmed up to date with `origin/main`.
