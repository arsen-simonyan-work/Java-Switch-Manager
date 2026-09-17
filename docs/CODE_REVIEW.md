# Code review — final pass

This document records the separate review pass performed after the first complete implementation.
The review covered domain/platform separation, destructive file/environment changes, privilege boundaries,
process execution, path handling, packaging and CI.

## Findings fixed

1. **Process pipe deadlock risk** — `CommandRunner` originally waited for the child process before draining stdout/stderr. A verbose child could fill the pipe and block forever. Output is now drained concurrently and timeout cleanup is bounded.
2. **Unsafe malformed shell marker handling** — an opening Java Switch Manager marker without a closing marker could have caused the rest of `.bashrc`/`.profile`/zsh config to be dropped. Only complete managed blocks are removed now; incomplete content is preserved.
3. **Incomplete shell escaping** — `$` and backticks in a Java home path were not escaped inside shell double quotes. Backslash, quote, dollar and backtick are all escaped now.
4. **Linux `/usr/bin/java` false JAVA_HOME** — deriving the home before resolving a launcher symlink could identify `/usr` as a JDK. Java executable symlinks are now resolved first.
5. **POSIX permission regression** — atomic config replacement could change the original file mode. Existing POSIX permissions are now copied to the replacement and restored on rollback.
6. **Linux `/etc/environment` rollback edge case** — rollback could create `/etc/environment` even if the file did not exist before the operation. The script records existence and removes a newly-created file during rollback.
7. **Unregistered `update-alternatives` target** — a JDK discovered outside `update-alternatives` could be selected and fail late. The privileged transaction now validates that the selected `bin/java` is registered before switching and rolls back on failure.
8. **Windows UAC script path quoting** — the elevated PowerShell script path is now explicitly quoted as one `Start-Process` argument line, including project/user temp paths containing spaces.
9. **Windows machine change transaction boundary** — environment-change broadcast is now inside the machine update `try` block, so a failure cannot report the operation as failed while leaving the machine variables partially committed.
10. **Case-sensitive path collapsing** — discovery/UI previously lower-cased all paths. Linux/macOS now retain path case; case-insensitive comparison is limited to Windows UI matching.
11. **macOS active Java reporting** — active home now prefers the executable resolved from the current PATH, then inherited `JAVA_HOME`, then `/usr/libexec/java_home` default.
12. **Runtime module risk in native packages** — hand-selected jlink modules could omit a runtime dependency. Native packaging now uses `includeAllModules = true` for the first release, favoring correctness over installer size.
13. **Implicit direct coroutine dependency** — code directly imports `kotlinx.coroutines`; the dependency is now declared explicitly instead of relying on a transitive Compose dependency.
14. **Release workflow supply-chain simplification** — the extra third-party GitHub Release action was removed; the release job now uses GitHub's preinstalled `gh` CLI and repository token.
15. **UI lambda/control-flow cleanup** — apply handlers no longer depend on an implicit function-call label, and platform-aware path comparison is explicit.

## Verification performed

- Core/domain/platform/settings Kotlin sources compile successfully with the local Kotlin compiler.
- A standalone review harness exercised OS detection, Java `release` parsing, shell block idempotency and malformed-block safety, shell escaping, Windows Path rewriting, executable-symlink normalization, POSIX mode preservation/rollback, backup creation, and a >1 MB child-process output case. Result: `REVIEW_HARNESS_OK`.
- `gradlew` passes `bash -n` syntax validation.
- PNG runtime icon and package PNG were checked as RGBA with a real alpha channel; ICO and ICNS package assets were generated from the supplied icon.
- Source scan found no unfinished implementation markers or retained Python/Tkinter implementation code.

## Environment limitation

A complete `./gradlew test` / Compose package build could not be executed in the review sandbox because command-line processes have no DNS access to Gradle/Maven repositories on a clean cache. The bootstrap correctly failed at dependency/distribution download rather than bypassing checksum validation. The repository includes a three-OS GitHub Actions build matrix that runs tests and native packaging in a networked build environment.
