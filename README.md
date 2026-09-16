### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app: `./gradlew :desktopApp:run` (`-Pnonogram.env=prod` for the prod Firebase project)
- Web app: see the **Web app** section below.

### Web app

- Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- Hosting: pushes to `main` build and deploy the prod bundles via `.github/workflows/deploy-web.yml` — see
  `docs/web-architecture.md` (Deploy section).

Before running the dev browser after npm dependencies have changed (after pulling changes to
`webApp/build.gradle.kts`, or when the build fails with `Lock file was changed`), refresh the yarn lockfiles:

```bash
./gradlew kotlinWasmUpgradeYarnLock kotlinUpgradeYarnLock
```

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:desktopTest`
- Web tests:
    - Wasm target: `./gradlew :shared:wasmJsTest`
    - JS target: `./gradlew :shared:jsTest`

---

google-services.json goes into androidApp