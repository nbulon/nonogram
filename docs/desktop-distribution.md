# Desktop distribution (jpackage + GitHub Releases)

`desktopApp` is packaged by Compose Desktop's jpackage integration and published as a GitHub Release on every push to
`main`. The web app links straight at those assets, so a browser visitor is one click away from the native build.

## What is produced

| Platform          | Format | Runner                           | Asset name                 |
|-------------------|--------|----------------------------------|----------------------------|
| Windows 10/11 x64 | `.msi` | `windows-latest` (GitHub-hosted) | `Nonogram-windows-x64.msi` |
| Debian/Ubuntu x64 | `.deb` | `ubuntu-latest` (GitHub-hosted)  | `Nonogram-linux-x64.deb`   |

**jpackage only ever produces the host OS's format**, which is the whole reason this is a matrix and not one job. The
repo's two self-hosted runners (`dev`, `docker-self`) are Linux, so the `.msi` has to come from somewhere else —
`nbulon/nonogram` is a public repository, so GitHub-hosted minutes are free and that is the cheapest way to get a
Windows box. Neither job carries the memory clamping `deploy-web.yml` needs; GitHub's runners are not the shared,
memory-tight machine the web build is tuned for.

macOS is deliberately **not** built. Adding it is one matrix row (`macos-latest`, `dmg`) plus a `Mac` branch in the web
button's user-agent check, but an unsigned `.dmg` is hard-blocked by Gatekeeper on recent macOS — it is worth pairing
with a Developer ID rather than shipping something that cannot be opened.

## The asset-name contract

Asset names carry **no version**. That is what makes

```
https://github.com/nbulon/nonogram/releases/latest/download/Nonogram-windows-x64.msi
```

a permanent URL: `releases/latest/` resolves to whichever release was published with `--latest`, and the file inside it
always has the same name. `shared/src/webMain/.../screens/DesktopDownloadButton.web.kt` hardcodes those two URLs and is
never rebuilt when a new installer ships — **rename an asset in the workflow and you must rename it there too.**

The release itself *is* versioned (`desktop-v1.0.<run#>`), so there is a history to roll back to; only the file names
inside are stable. One caveat: `--latest` is repo-wide. If Android releases are ever tagged in this repo they would take
over `releases/latest/`, and the web URLs would have to move to a fixed `desktop-latest` tag instead.

## Versioning

`packageVersion` comes from `-Pnonogram.desktopVersion`, defaulting to `1.0.0` for local builds — the same
`providers.gradleProperty(...).getOrElse(...)` idiom `nonogram.env` uses. CI passes `1.0.<github.run_number>`. MSI's
ProductVersion is `major.minor.build` with major/minor ≤ 255 and build ≤ 65535, so that scheme is valid until run 65535
and then needs a rethink.

## Two environments, two installed apps

`-Pnonogram.env` picks the Firebase project the same way it does for web, and on desktop it also decides the **package
identity**:

|                       | prod         | dev              |
|-----------------------|--------------|------------------|
| `packageName`         | `nonogram`   | `nonogram-dev`   |
| Windows `upgradeUuid` | `8fd365e4-…` | `3e781c5d-…`     |
| Windows `menuGroup`   | `Nonogram`   | `Nonogram (dev)` |
| `DATA_DIR_NAME`       | `Nonogram`   | `Nonogram-dev`   |

This is the desktop counterpart of Android's `applicationIdSuffix = ".dev"`: a dev build installs *beside* a prod one
rather than replacing it. The data directories were already separate; without the name split the two installers were the
same application as far as Windows and apt were concerned.

The flag is **not** implied by the release build type. `packageReleaseDistributionForCurrentOS` is "release" only in the
minification sense, and the source set is wired at configuration time — before Gradle knows which task is being run — so
`-Pnonogram.env=prod` has to be passed explicitly, exactly as `deploy-web.yml` does for the web bundle.
`gradle.properties` defaults it to `dev`, which is the safe default for a local run.

## Generating an `upgradeUuid`

`upgradeUuid` is the MSI's *upgrade code*: the identity Windows uses to recognise that a newer installer supersedes an
older one. It is per `packageName`, so each environment needs its own, and once an installer carrying one has been
handed to anyone it must never change.

1. Generate a random UUID (v4). Any of these work:
   ```bash
   uuidgen                                      # Linux / macOS
   python3 -c "import uuid; print(uuid.uuid4())"
   ```
   ```powershell
   [guid]::NewGuid()                            # Windows PowerShell
   ```
2. Paste it into the matching branch of `upgradeUuid` in `desktopApp/build.gradle.kts`. Case does not matter; the
   canonical `8-4-4-4-12` hyphenated form does.
3. Commit it. It is a constant, not a secret: every MSI carries its own upgrade code in the clear, so anyone who has an
   installer can read it back out (`msiinfo`, Orca, or the `Installer\UpgradeCodes` registry key after install). It
   identifies the product; it authenticates nothing. Same category as the Firebase API key and app id already committed
   in `FirebaseConfig.desktop.kt` — public by design, with the real enforcement elsewhere.
4. Never reuse one across two `packageName`s, and never regenerate one for an existing product.

**If it does change**, the next MSI stops being an upgrade: Windows installs it alongside the old version, both show up
in *Apps & features*, and the two share a Start-menu group and install directory. Recovering means uninstalling the old
copy by hand on every machine that has it.

## Signing: there is none

Both builds are unsigned, which is a deliberate cost decision, not an oversight:

- **Windows** — SmartScreen shows "Windows protected your PC" on first run; the user has to click *More info → Run
  anyway*. `perUserInstall = true` at least avoids the admin/UAC prompt on top of it. Removing the warning needs a real
  certificate: Azure Trusted Signing (cheap, but needs a verified organisation or a three-year individual history) or an
  OV/EV certificate on a hardware token (which does not live comfortably in CI).
- **Linux** — no equivalent problem. The `.deb` installs unremarkably.
- **macOS** — would need the $99/yr Apple Developer Program. Compose Desktop has the hooks already
  (`macOS { signing { … }; notarization { … } }`), so it is a secrets-and-config change, not a restructuring.

The release notes tell users what to expect, and the workflow is shaped so that adding certificates later touches only
the Gradle `nativeDistributions` block and GitHub secrets.

## The JDK the installer carries

`desktopApp` pins `jvmToolchain(21)`, matching `gradle/gradle-daemon-jvm.properties` and both CI workflows. Without it
the jlink'd runtime image is built from whatever JDK happens to run Gradle, so the installer differs between a
developer's machine and CI — and on a JDK 24+ host `sqlite-jdbc`'s `System::load` trips the JEP 472 restricted-native
-access warning ("Restricted methods will be blocked in a future release").

Pinning a compile toolchain means Gradle has to be able to *find* a JDK 21 locally — `JAVA_HOME`, SDKMAN/jenv/asdf, an
OS standard location, or `~/.gradle/jdks` (where `gradle-daemon-jvm.properties` provisions its own Amazon 21). No
toolchain download repository is configured, deliberately: a machine without a matching JDK fails loudly with *"No
matching toolchains found for requested specification: {languageVersion=21}"* rather than quietly downloading one. The
fix on such a machine is to install a JDK 21, or to add
`org.gradle.toolchains.foojay-resolver-convention` to `settings.gradle.kts` (toolchain resolvers are a settings-level
API, so that is the only place it can go). `./gradlew -q javaToolchains` lists what Gradle can currently see.

If the toolchain ever moves to 24 or later, that warning comes back and the fix is
`jvmArgs += "--enable-native-access=ALL-UNNAMED"`. Do **not** add it while pinned to 21: the option does not exist
before JDK 22 and the JVM refuses to start on an unrecognised flag.

## Icons

Three icon files, two different jobs, and they must not share a directory:

- `desktopApp/src/desktopMain/composeResources/drawable/icon.png` — a Compose resource (`Res.drawable.icon`, the window
  icon) *and* the Linux jpackage icon.
- `desktopApp/icons/icon.ico`, `desktopApp/icons/icon.icns` — jpackage only, deliberately outside `composeResources`.

Compose Resources groups every file under `composeResources/` by its qualifier-stripped base name, so an `icon.png`,
`icon.ico` and `icon.icns` sitting together all collapse into the single id `drawable:icon`. Generation still succeeds;
`painterResource(Res.drawable.icon)` then throws `IllegalStateException: Resource with ID='drawable:icon'
has more than one file` at startup. jpackage's `iconFile` takes a plain `File`, so keeping the other two formats out of
the resource tree costs nothing.

## Building locally

```bash
# Current OS only, prod Firebase project
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS -Pnonogram.env=prod

# ...with a version stamp, the way CI does it
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS -Pnonogram.env=prod -Pnonogram.desktopVersion=1.0.99
```

Output lands in `desktopApp/build/compose/binaries/main-release/{msi,deb,dmg}/`. The `release` build type is the one CI
uses; its ProGuard is disabled (Firestore's grpc stack is not worth the keep rules), so it differs from the plain
`packageDistributionForCurrentOS` only in output directory.

## The download button

`DesktopDownloadButton` is an `expect`/`actual` composable following the `GoogleSignInSection` pattern: the
`jvmSharedMain` actual is empty (Android and desktop are already native), and the `webMain` actual owns the user-agent
sniff, the release URLs and the click. It returns nothing where there is no build to offer — phones, and macOS — so
common code only decides *where* it sits.

`App.kt` places it in a `Box` wrapped around the `NavHost`, aligned `BottomEnd`. That `Box` is the app root and is
deliberately not width-capped, so on a window wider than `MAX_CONTENT_WIDTH` the button lands in the gutter beside the
content rather than on top of it. It is hidden on `GameRoute` and `GeneratorRoute`, where `BottomToolBar` owns that
corner, and on any dialog destination, which is modal over whatever screen opened it.

The click sets `window.location.href`. GitHub serves release assets with `Content-Disposition: attachment`, so the
browser downloads the file and the single-page app is not navigated away from.
