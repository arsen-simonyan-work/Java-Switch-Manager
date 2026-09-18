# Packaging and releases

## Local packaging

Run on the target operating system:

```bash
./gradlew test
./gradlew packageDistributionForCurrentOS
```

Outputs are under `build/compose/binaries`.

Configured formats:
- Linux x64: `.deb`
- Windows x64: `.exe`
- macOS Apple Silicon: `.dmg`

The Linux DEB desktop entry uses `Name=Java Switch Manager` and
`StartupWMClass=JavaSwitchManager`. The DEB packaging tasks update these fields
after jpackage generates the package, including release builds.
The `JavaSwitchManager` launcher initializes AWT before Compose so the actual
X11 window class matches the desktop entry. The package also includes the desktop
entry in `/usr/share/applications`, PNG icons in the hicolor theme, and AppStream
metadata in `/usr/share/metainfo`. Installation and removal refresh desktop and
icon caches when the corresponding utilities are available. Local DEB previews
may still use a generic package icon, depending on the Ubuntu installer version.
`generateAppIcons` creates window icons using progressive downsampling. The Linux
icon theme installs only the 512×512 PNG, matching STB Update Verifier's packaging,
so the desktop scales the large image to its menu and Dock sizes. Smaller PNGs
are embedded only in the application for AWT window icons.

## macOS signing

The project intentionally does not contain signing identities or notarization credentials.
For public distribution, configure Apple Developer signing/notarization in CI before publishing the DMG. Unsigned builds can still be produced by GitHub Actions, but Gatekeeper may warn or block them on another Mac.

## Windows

`jpackage` requires WiX for EXE installer creation. The pinned `windows-2025` GitHub runner already includes WiX Toolset 3.14, and the workflow verifies that `candle.exe` and `light.exe` are available before packaging. The generated EXE is unsigned unless a Windows code-signing certificate is added to CI, so SmartScreen may warn users.

## GitHub Actions runners

Release packaging is pinned to:
- `ubuntu-24.04` for the Linux x64 DEB
- `windows-2025` for the Windows x64 EXE
- `macos-15` for the Apple Silicon DMG

All jobs use JDK 21 and the repository Gradle wrapper.

## Release workflow

The workflow can be started manually with `workflow_dispatch` to verify package creation without publishing a release.

To publish a GitHub Release, push a tag matching the `VERSION` value. Both `1.0.0` and `v1.0.0` tag styles are accepted for `VERSION=1.0.0`.

```bash
git tag 1.0.0
git push origin 1.0.0
```

For a tagged build the workflow verifies the version, runs tests, builds all three native packages, stages them with platform-specific names, uploads Actions artifacts, and then creates or updates the GitHub Release.

Expected release assets for version `1.0.0`:

```text
JavaSwitchManager-1.0.0-windows-x64.exe
JavaSwitchManager-1.0.0-linux-x64.deb
JavaSwitchManager-1.0.0-macos-arm64.dmg
```
