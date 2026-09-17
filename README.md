# Java Switch Manager 2

Java Switch Manager 2 is a Kotlin/JVM + Compose Desktop rewrite of the original Python/Linux utility.
The UI and domain logic are shared, while Java discovery and switching behavior is implemented per operating system.

## Supported systems

- Windows 10/11 x64
- macOS 13+ on Apple Silicon
- Linux x64 desktop distributions compatible with Ubuntu 20.04+ runtime requirements

## What it does

### Linux
- Discovers system-registered Java installations through `update-alternatives`.
- Can manage `~/.bashrc` and `~/.profile` through an idempotent Java Switch Manager block.
- Can update `/etc/environment` and `update-alternatives` using the system PolicyKit prompt (`pkexec`).
- Does not collect or pass a sudo password itself.

### macOS
- Discovers system-registered JDKs through `/usr/libexec/java_home`.
- Can manage `~/.zshrc` and `~/.zprofile`.
- Never attempts to replace `/usr/bin/java`.

### Windows
- Discovers system-registered JDKs from JavaSoft registry keys.
- Can set user `JAVA_HOME` and user `Path` without elevation. Windows composes the effective Path from system and user scopes, so an earlier Java entry in the system Path can still win; in that case use the System Path option with UAC.
- Can set machine `JAVA_HOME` and machine `Path` through a normal Windows UAC prompt.
- Path switching prepends the selected JDK and removes only Java-bin entries that can be identified safely.

## Safety model

Selecting a JDK does **not** modify the system. The app first builds a change plan, shows the operations, and requires an explicit Apply action.
User shell-file changes are written atomically and get timestamped backups. If a later privileged operation fails, user-file changes are rolled back.
Privileged Linux changes run as one transactional shell script with rollback of `/etc/environment` and the previous `update-alternatives` value.

Changes to environment variables normally affect **new terminals/processes**. Existing terminals keep their inherited environment.

## Development

Requirements:
- JDK 21 recommended (packaging requires JDK 17+)
- Internet access on the first Gradle bootstrap/build (dependencies are then cached by Gradle)

Run:

```bash
./gradlew run
```

Tests:

```bash
./gradlew test
```

Build an application image:

```bash
./gradlew createDistributable
```

Build the native installer for the current operating system:

```bash
./gradlew packageDistributionForCurrentOS
```

Native packages must be built on their target OS. GitHub Actions contains a Windows/macOS/Linux build matrix and produces:

- Windows x64: `.exe`
- Linux x64: `.deb`
- macOS Apple Silicon: `.dmg`

See [`docs/PACKAGING.md`](docs/PACKAGING.md) for release/tag details and signing notes.

## Architecture

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). The final implementation review is recorded in [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md).

## Version

The application version is stored in `VERSION` and is also used by the window title and native packaging.
