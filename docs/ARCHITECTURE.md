# Architecture

## Decision

This is intentionally a **Kotlin/JVM Compose Desktop** application rather than a Kotlin/Native KMP application.
Windows, macOS and Linux all execute the same desktop JVM binary model and share the entire Compose UI/domain layer.
The platform boundary exists around operating-system integration, not around source-set compilation.

This keeps the project simple while preserving the important form of multiplatform support for this utility.

## Layers

```text
UI (Compose)
  -> AppController
      -> PlatformJavaManager
          -> LinuxJavaManager
          -> MacOsJavaManager
          -> WindowsJavaManager
              -> discovery / config editors / command runner / privilege service
```

### Domain

The domain layer owns:
- `JavaInstallation`
- environment snapshots
- switch target descriptions
- immutable `SwitchPlan`
- apply results

It does not know about Compose.

### Platform boundary

`PlatformJavaManager` exposes:
- JDK discovery
- current environment snapshot
- available switch targets
- change-plan generation
- plan application

The UI never calls `update-alternatives`, `reg.exe`, `java_home`, `pkexec` or PowerShell directly.

### Privileges

The app never asks for an administrator password.
- Linux: PolicyKit (`pkexec`) supplies the desktop authorization UI.
- Windows: PowerShell `Start-Process -Verb RunAs` invokes the standard UAC prompt.
- macOS: current functionality is user-scoped and needs no elevation.

### File updates

Shell startup files are managed using one identifiable block:

```sh
# >>> Java Switch Manager >>>
export JAVA_HOME="..."
case ":$PATH:" in *":$JAVA_HOME/bin:"*) ;; *) export PATH="$JAVA_HOME/bin:$PATH" ;; esac
# <<< Java Switch Manager <<<
```

This makes updates idempotent and avoids rewriting unrelated user configuration.

### Discovery

Installation discovery is intentionally multi-source. Candidates are normalized and then validated by `JavaInstallationInspector`, which checks the executable and reads the JDK/JRE `release` file. Duplicates collapse by normalized Java home.

### Packaging

Compose Desktop native distributions bundle a private runtime image. Java Switch Manager therefore does not rely on the system Java installation it is modifying.
