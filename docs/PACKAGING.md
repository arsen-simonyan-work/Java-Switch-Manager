# Packaging and releases

## Local packaging

Run on the target operating system:

```bash
./gradlew test
./gradlew packageDistributionForCurrentOS
```

Outputs are under `build/compose/binaries`.

Configured formats:
- Linux: `.deb`
- Windows: `.msi`
- macOS: `.dmg`

## macOS signing

The project intentionally does not contain signing identities or notarization credentials.
For public distribution, configure Apple Developer signing/notarization in CI before publishing the DMG.

## Windows

`jpackage` requires WiX for MSI creation. The GitHub Actions workflow installs WiX on the Windows runner.

## Release workflow

Push a tag matching the `VERSION` value, for example:

```bash
git tag 1.0.0
git push origin 1.0.0
```

The workflow verifies the tag, runs tests and builds packages on all three operating systems before creating a GitHub release.
