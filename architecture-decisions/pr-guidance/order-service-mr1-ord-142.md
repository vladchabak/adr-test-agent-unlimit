# PR Guidance — order-service MR !1 · ORD-142

**Branch**: `fix/ORD-142-logging-typos`  
**Architectural assessment**: ✅ No architectural concerns — safe to merge  
**ADR required**: No  

---

## Architectural Note for Reviewers

This MR is cosmetic (log message typos, variable rename) and carries no architectural impact.

However, the MR also includes a **Maven → Gradle build system migration** that was not part of the ORD-142 ticket scope. This is an infrastructure change with its own risk surface (build reproducibility, CI pipeline behaviour, wrapper script availability) and should have had its own MR and ticket.

Since `main` already has Gradle, retroactively splitting is not practical. **Document it explicitly in the MR description** so it appears in the merge commit history and is discoverable when auditing build system changes.

**Remaining gap**: The `gradle-wrapper.properties` is committed but the `gradlew` / `gradlew.bat` scripts are not. Developers cloning fresh will not be able to use `./gradlew`. Either commit the full wrapper or document that the CI image's `gradle` binary is the intended entry point.

---

## Merge Readiness

| Check | Status |
|-------|--------|
| Cross-service impact | None |
| Deployment dependency | None |
| Blockers | None |
| ADR | Not required |
