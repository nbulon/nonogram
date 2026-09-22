# Web architecture (js + wasmJs)

## Targets

`shared` and `webApp` both build `js` and `wasmJs`. Kotlin's default source set hierarchy creates a shared
`webMain` (dependsOn'd by both `jsMain` and `wasmJsMain`), so almost all web-specific code lives in `webMain`
and only the two lines that construct the platform `Worker` live in `jsMain`/`wasmJsMain`. **wasmJs is the only deployed
target** (faster startup, smaller output). `js` is still built and still tested (`tests.yml` runs
`:shared:jsBrowserTest`), so it stays a working fallback for browsers without wasm-GC — it is simply not shipped today;
see **Deploy** for why.

## Data layer: suspend all the way down

SQLDelight's web worker driver is asynchronous, so `generateAsync.set(true)` is on for the whole schema. This made
`Database`, `AppSDK`, `AuthRepository.initialize()/linkFirebaseUser()`, and `AppInitializer.initializeAuth()`
suspend functions. `AppSDK` builds its `Database` lazily behind a mutex on first use, so the constructor itself stays
synchronous (Koin still does `single { AppSDK(get()) }` without change) and the actual driver/schema creation happens
off the first suspend call.

Each platform owns schema creation differently:

| Platform   | Driver                                                      | Who creates/migrates the schema                                                                                                    |
|------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Android    | `AndroidSqliteDriver(NonogramDb.Schema.synchronous(), ...)` | the driver, internally, via its create/upgrade callbacks (unchanged behavior)                                                      |
| Test (JVM) | `JdbcSqliteDriver` (in-memory)                              | `TestDatabaseFactory` calls `Schema.synchronous().create(driver)` explicitly                                                       |
| Desktop    | `JdbcSqliteDriver` (file)                                   | the driver, from the schema handed to its constructor (`DesktopDatabaseFactory`)                                                   |
| Web        | `WebWorkerDriver` (OPFS-backed)                             | `WebDatabaseFactory` reads `PRAGMA user_version`, then `awaitCreate`/`awaitMigrate` explicitly, then writes the new `user_version` |

Web needs explicit version tracking because OPFS storage persists across page loads and app deploys — unlike sql.js
in-memory setups, a returning user's on-disk schema may be older than the current `NonogramDb.Schema.version`.

Because `AuthRepository.initialize()` is now async, `MenuViewModel`'s `init { loadAll() }` could otherwise read
`currentUserUid` before it's set. Both `MainActivity` (Android) and `main.kt` (web) gate the app behind
`LoadingScreen()` until `AuthViewModel.authState != INITIALIZING`, so ViewModels are only constructed once auth has
resolved.

## One thread, and what that costs

js and wasmJs have a single thread, and it is the one that draws. `Dispatchers.Default` is that thread,
`Dispatchers.IO` does not exist, and `dbDispatcher` is `EmptyCoroutineContext` for the same reason — there is nowhere to
hop *to*. A `withContext(Dispatchers.Default)` written for Android's benefit buys web nothing, and anything genuinely
CPU-bound (the Solver, most visibly) freezes the UI for as long as it runs. The app deliberately runs no workers of its
own beyond the database one; what follows is how it stays usable without them.

- **Nothing may pin a spinner.** `kotlinx.coroutines.await` on a JS `Promise` cannot cancel the promise, and the
  Firestore JS SDK does not promise to settle one: an offline `setDoc` queues until the client is back online, a
  `getDocs` retries with backoff. So `AuthViewModel` bounds every remote pass with `withTimeoutOrNull(SYNC_TIMEOUT)`
  (`syncAllNow` is the awaitable form the menu uses), releases its `onComplete` callbacks under
  `NonCancellable + Dispatchers.Main` (a plain `withContext` in a `finally` rethrows on a cancelled job and skips the
  callback), and `MenuViewModel.refresh(sync)` sets and clears `isRefreshing` inside one coroutine rather than trusting
  a callback from another ViewModel to arrive. For the timeout to unwind, `gated` (web) and `logged`
  (Android) rethrow `CancellationException` instead of swallowing it into a fallback. `AppSDK` likewise times out driver
  creation, which happens under a mutex every other database call waits on.
- **A wheel scroll never lets go.** Compose dispatches mouse-wheel scrolling through nested scroll as
  `NestedScrollSource.UserInput`, the same as a finger drag, but does not fling afterwards (the default fling behaviour
  is skipped for the wheel). Anything that pairs `onPostScroll` with a release in `onPreFling` —
  `PullToRefreshBox`, whose indicator otherwise sticks at whatever distance a wheel-up at the top of the list pulled it
  to — must therefore be gated to touch; `MenuScreen`'s `pullToRefreshByTouchOnly` swallows the overflow unless a
  non-mouse pointer is down.
- **Image decode is the browser's.** `scan/ImageDecode.kt` is `expect`: Compose's `decodeToImageBitmap` +
  `readPixels` is synchronous and would decode a 12 MP photo on the UI thread. The web actual hands the picked
  `File` — already a `Blob`, held as `PickedImage` so the bytes never enter Kotlin — to an `<img>`, lets the browser
  decode it off-thread, and `drawImage`s it into a canvas *already sized to `WORKING_SIDE`*, so only the finished
  256-pixel-wide image crosses back into Kotlin. The resample is therefore the browser's rather than
  `LumaAccumulator`'s box filter, so a web scan is not bit-identical to an Android one; both feed the same interactive
  threshold.
- **Don't decode a grid you will not draw.** Every `solution` and `boardState` is JSON parsed on the UI thread by
  `Database`'s row mappers; `selectProgressForUser` selects progress columns only for exactly this reason.

## Persistence: OPFS via a custom worker

SQLDelight's documented web worker setup uses `sql.js`, which is in-memory only — a reload wipes all data. Instead,
`webApp/src/webMain/resources/sqlite.worker.js` is a small custom worker (per SQLDelight's
[custom worker protocol](https://github.com/sqldelight/sqldelight/blob/master/docs/js_sqlite/custom_worker.md))
built on the official `@sqlite.org/sqlite-wasm` package, using the `opfs-sahpool` VFS:

- No COOP/COEP headers required (unlike the plain OPFS VFS), so the Gradle dev server needs no special config.
- Requires a secure context (`https://` or `localhost`) — both the dev server and any real deployment satisfy this.
- The pool holds an **exclusive lock**: a second tab open at the same time gets no database access. Acceptable for v1;
  would need a `SharedWorker` or a leader-election scheme to fix.
- `webApp/webpack.config.d/sqlite-wasm.js` copies `sqlite3.js`/`sqlite3.wasm` from the npm package next to the compiled
  bundle so the worker's `importScripts("sqlite3.js")` resolves.

`WebDatabaseFactory` (`shared/src/webMain/.../cache/WebDatabaseFactory.kt`) drives the worker through the same
`WebWorkerDriver` SQLDelight uses for its own sql.js reference worker — only the worker script differs.

## Web sign-in + sync (milestone 2)

Neither `kmpauth-firebase` nor `dev.gitlive:firebase-firestore` publish a `wasmJs` variant, so web sign-in/sync is built
without gitlive: `kmpauth-google` (publishes js+wasmJs, commonMain dep) obtains the Google token in the browser, and
**hand-written Kotlin externals to the Firebase JS SDK** (`npm("firebase", ...)` in `shared`'s
`webMain`) do the credential exchange and Firestore I/O. Both v1 seams are now filled:

- **`sync/SyncService`** — web binds `sync/FirebaseWebSyncService` (webMain), which mirrors the androidMain
  `FirebaseJvmSyncService` method-for-method against the same Firestore shape, so Android and web sync interoperate. Two
  collections: progress (`users/{uid}/progress/{nonogramId}`, fields `boardState: String?` +
  `updatedAt: number`) and the shared `nonograms/{id}` puzzle collection (own + public). Neither service owns any
  *policy*: the paths and field names (`sync/FirestoreSchema.kt`), the progress merge (`sync/RemoteProgress.kt`) and the
  document → `Nonogram` mapping (`sync/NonogramDocument.kt`) all live in commonMain. A platform supplies the fetch, the
  document write and its own log tag — nothing else, because everything else drifted the last time it was duplicated.
- **`screens/GoogleSignInSection`** — the web actual drives kmpauth's `rememberGoogleSignInState` from a
  `GoogleSignInButton`, exchanges the Google token via `FirebaseWeb.signInWithGoogle`, and feeds the resulting Firebase
  `uid`/`displayName` into the unchanged common login flow. Deliberately the *credential-only* state, not
  `rememberGoogleAuthState`: kmpauth's own web backend is a Firebase Auth REST engine, which would never populate the JS
  SDK's auth state that the session gate below reads.

### The externals pattern (first in this repo)

All bindings live once in `shared/src/webMain/kotlin/.../firebase/` and compile for **both** js and wasmJs (supported
since Kotlin 2.2.20). The rules that make that work:

- Only `JsAny`-family types in external signatures; every `external interface` extends `JsAny`. No `Long`
  (Firestore numbers are doubles — `updatedAt` crosses the boundary as `Double`, `.toLong()` on read).
- `@file:JsModule("firebase/auth")` etc. at file level; `@OptIn(ExperimentalWasmJsInterop::class)` per file.
- `@JsModule`-only externals can't link under UMD, so **both `shared` and `webApp` set `js { useEsModules() }`**
  (wasmJs is ESM anyway; webpack bundles either).
- `js(...)` is unavailable in a shared source set. Plain JS objects (Firebase config, Firestore write payloads)
  are built via a global `external object JSON { fun parse(...) }` + kotlinx-serialization `buildJsonObject`.
- `Promise<T : JsAny?>.await()` comes from kotlinx-coroutines ≥ 1.11, which ships it in its shared web fragment.
- Statics like `GoogleAuthProvider.credential(...)` are bound as an `external object`.
- `QuerySnapshot` is consumed via `.empty` / `.forEach(callback)` instead of `.docs`, sidestepping the js-vs-wasm
  `JsArray` API divergence.

`firebase/Firebase.web.kt` is the facade: everything outside the `firebase` package (sync service, sign-in UI,
`webApp/main.kt`) talks only to it, so if shared externals ever regress the bindings can move per-target without
touching callers. `initialize` is just config → `initializeApp` → `getAuth`/`getFirestore`; no platform uses App Check
(see `CLAUDE.md`).

The config it is called with is environment-specific: `FirebaseConfig.web.kt` exists twice, in
`webApp/src/dev` and `webApp/src/prod`, and `webApp/build.gradle.kts` puts one of them on
`webMain`'s source path via `-Pnonogram.env` (`dev` by default). Only the selected one is indexed by the IDE, and a
constant added to one must be added to the other; see `docs/prod-firebase-setup.md`.

### Auth/session details

- **kmpauth token caveat:** GIS splits the two tokens across two flows. kmpauth-google (since 3.0) runs *One Tap*
  first, which yields a real `GoogleUser.idToken` and no `accessToken`; only if that is suppressed or dismissed does it
  fall back to the OAuth *token client*, which yields an `accessToken` and possibly no ID token. So either field can
  come back empty and the sign-in actual passes both (blank-filtered) to
  `GoogleAuthProvider.credential(idToken?, accessToken?)` — Firebase accepts either. kmpauth injects the GIS script
  itself; `index.html` needs no change.
- **Session restore gate:** on page reload the app trusts the local SQL `User.firebaseUid`, but the Firebase JS session
  restores asynchronously from indexedDB. Every Firestore op in `FirebaseWebSyncService` first awaits
  `auth.authStateReady()` and verifies the live uid matches — otherwise it logs and no-ops instead of hitting a
  guaranteed permission-denied. That is `gated(uid, label, fallback) { }`, which wraps the check *and* the best-effort
  catch around every override, so a new method cannot silently skip the gate. The one exception is
  `pullPublicNonogramsSince`: approved docs are readable signed out, so it calls `awaitSessionSettled()` — the same
  await, deliberately without the comparison — and keeps its own catch.
- **Config:** `webApp/.../FirebaseConfig.web.kt` holds committed constants (Firebase web config + the Google web OAuth
  client id). These are public-by-design — they ship in every JS bundle; security comes from Firestore rules and the
  OAuth **Authorized JavaScript origins** allowlist (each dev/prod origin must be listed there; note js and wasmJs dev
  servers on different ports are different origins with separate indexedDB sessions).
  `main.kt` calls `FirebaseWeb.initialize(...)` and `AppInitializer.onApplicationStart(clientId)` before Koin.

## Deploy (GitHub Actions + Docker over SSH)

`.github/workflows/deploy-web.yml` builds the prod web bundles on the self-hosted runner and deploys them to the webhost
as an nginx container. Every push to `main` deploys; `workflow_dispatch` reruns it by hand.

### Design

The web app is static files, so the container is `nginx-unprivileged:alpine` plus one directory:

| URL path | Gradle task                         | Copied from                                     |
|----------|-------------------------------------|-------------------------------------------------|
| `/`      | `:webApp:wasmJsBrowserDistribution` | `webApp/build/dist/wasmJs/productionExecutable` |

**Only wasmJs is deployed.** The js bundle used to be served from `/js/` as a manual fallback, and building both is what
the runner's memory budget could not sustain, so it was dropped. The js target is still compiled and tested — restoring
the fallback is a second `jsBrowserDistribution` step plus a `COPY` in the `Dockerfile`, not new code.

Two build steps, in two places:

1. **Gradle runs on the runner**, in *two separate invocations*. It already has the JDK / Android SDK / Gradle setup
   `tests.yml` uses; a multi-stage Docker build would re-download all of that on every run. The task is always
   `-Pnonogram.env=prod`, so the bundle carries `webApp/src/prod/.../FirebaseConfig.web.kt`.

   The split — `compileProductionExecutableKotlinWasmJs`, then `wasmJsBrowserDistribution` — exists so the compile JVM
   has exited before the native `wasm-opt` runs, rather than the two sitting on the runner's memory at once. It is also
   why the two steps get different heaps: 1380 MB with 300 MB metaspace and a 128 MB code cache for the Kotlin compile
   (serial GC that hands heap back, compiler in-process), then 640 MB with 256 MB metaspace for the bundling step,
   alongside a 580 MB `NODE_OPTIONS` heap for webpack. Both run `--no-daemon --no-parallel
   --max-workers=1`, because the `gradle.properties` defaults (4 GB Gradle JVM, 3 GB Kotlin daemon, daemons that outlive
   the job) are sized for a dev machine, not a shared self-hosted runner.

   Each step also writes `1000` to its own `/proc/self/oom_score_adj`: if the machine does run out of memory, the
   kernel's OOM killer takes the build first instead of whatever else the runner is hosting. A failed build is cheaper
   than a killed neighbour. On an out-of-memory failure raise `-Xmx` first (a Kotlin compile
   `OutOfMemoryError`), `NODE_OPTIONS` second (a webpack "heap out of memory").
2. **The image is built on the host.** The deploy step sets `DOCKER_HOST=ssh://<user>@<host>` and runs
   `docker compose -f webApp/compose.yml up -d --build`. The Docker CLI streams the build context to the host's daemon
   over SSH, the image is built and started there, and nothing is pushed to a registry. `webApp/.dockerignore` is a
   whitelist, so the context is just the two conf files and the wasmJs dist folder (source maps excluded). Because
   that whitelist un-ignores the **whole** dist tree and the runner checks out with `clean: false`, the deploy purges
   `webApp/build/dist` before compiling — otherwise a file left there by an earlier run would be published at the
   site's root.

The files: `webApp/Dockerfile`, `webApp/nginx.conf`, `webApp/security-headers.conf`, `webApp/compose.yml`,
`webApp/.dockerignore`. **The `.dockerignore` is a whitelist — a new file next to the `Dockerfile` is invisible to the
build until it is un-ignored there.**

### Rootless, and why the port is 8080

The base image is `nginxinc/nginx-unprivileged`, not `nginx`. Stock nginx runs its master process as root purely to
bind port 80; a static file server has no other need for it. The unprivileged image runs as uid 101 and listens on
8080 instead, which lets `compose.yml` drop every capability (`cap_drop: [ALL]` — not even `NET_BIND_SERVICE`), set
`no-new-privileges`, and mount the root filesystem `read_only`. Read-only works because that image's `nginx.conf`
writes its pid to `/tmp`; the only writable mounts are `tmpfs` at `/tmp` and `/var/cache/nginx`.

Only the *container* port moved. `WEB_PORT` still controls the host side and the publish is still loopback-only, so
the reverse proxy is unaffected.

`image:` carries `${IMAGE_TAG:-latest}`, and the workflow passes the commit sha. That is the rollback handle:
`IMAGE_TAG=<older-sha> docker compose --project-name nonogram-web -f webApp/compose.yml up -d` on the host. Tagged
images survive the deploy's `docker image prune -f` (it removes dangling images only), so they accumulate — that is
the trade for being able to roll back.

### Headers

`nginx.conf` serves the content-hashed `*.wasm` chunks as `immutable` and everything else — whose names are stable
across deploys — as `no-cache`. `application/wasm` comes from nginx's stock `mime.types`.

The security headers live in their own `security-headers.conf`, `include`d **inside each `location` block** rather
than once at `server` level. That is not style: nginx's `add_header` *replaces* the inherited set rather than merging
into it, so a `location` that sets its own `Cache-Control` silently drops every header declared in the parent. Any
header added later has to go in that file, and any new `location` has to `include` it.

COOP is `same-origin-allow-popups`, not `same-origin`: Google sign-in is a popup that reports back through
`window.opener`, and full isolation severs it. COEP is still unset — the `opfs-sahpool` VFS was chosen precisely so
none is needed.

HSTS is deliberately absent here. This server speaks plain HTTP on loopback; `Strict-Transport-Security` belongs on
the TLS-terminating reverse proxy, which is also what makes the site a secure context for OPFS.

**The CSP ships as `Content-Security-Policy-Report-Only` and must be verified before it is enforced.** `index.html`
has no inline `<script>`, so a real policy is achievable — but four allowances are load-bearing and each one fails a
different feature, silently, on a path a smoke test will not reach:

| Allowance | Needed by | Fails without it |
|---|---|---|
| `'wasm-unsafe-eval'` in `script-src` | the wasmJs module is compiled at runtime | the app never boots |
| `https://accounts.google.com` in `script-src`, `style-src`, `frame-src` | kmpauth injects the GSI script *and* its stylesheet, and frames it | sign-in |
| `blob:` in `img-src` and `worker-src` | `ImageDecode.web.kt`'s `createObjectURL`; the sqlite-wasm OPFS worker | image scanner, database |
| `https://*.googleapis.com` in `connect-src` | Firebase auth + Firestore transport | all sync |

To promote it: deploy, exercise all four paths on the live site with the DevTools console open, widen the policy to
cover anything reported, then rename the header to `Content-Security-Policy` and deploy again. `style-src
'unsafe-inline'` is a standing concession — Compose's canvas host and the GSI button both set inline styles.

`compose.yml` publishes the container on `127.0.0.1:${WEB_PORT:-8080}` only. TLS is the host's reverse proxy's job —
OPFS needs a secure context, so the site must be reached over `https://`.

### One-time setup

#### Runner machine

- Docker CLI (with the compose plugin) installed; the runner's user can run `docker`.
- SSH key for `<user>@<host>` in the runner user's `~/.ssh`, and the host's key already in `known_hosts`
  (`ssh <user>@<host> docker info` must work non-interactively as the runner's user).

#### Webhost

- Docker Engine + compose plugin; `<user>` is in the `docker` group.
- Reverse proxy rule: `https://<domain>` → `http://127.0.0.1:8080` (or whatever `WEB_PORT` is set to). If the proxy runs
  in Docker on the same host, replace the `ports` entry in `compose.yml` with a shared external network instead — and
  target the container on **8080**, not 80 (see **Rootless** above).
- The proxy is also where `Strict-Transport-Security` belongs; the container serves plain HTTP and sets every other
  security header itself.

#### GitHub repository → Settings → Secrets and variables → Actions → Variables

| Variable     | Value                                              |
|--------------|----------------------------------------------------|
| `SSH_TARGET` | `<user>@<host>` (required)                         |
| `WEB_PORT`   | host port to publish on (optional, default `8080`) |

#### Firebase / Google console (prod project `nonogram-trainpaths`)

- Google Cloud → OAuth client `GOOGLE_WEB_CLIENT_ID` → **Authorized JavaScript origins**: add `https://<domain>`.
- Firebase Authentication → Settings → **Authorized domains**: add `<domain>`.

Without both, the site loads but Google sign-in fails.

#### Firebase console hardening (prod project `nonogram-trainpaths`)

The bundle ships the project's API key and config, which is public by design, and web has no App Check — so anyone can
script Firebase calls with it. The Firestore rules (managed in the console, not in the repo) are the only thing that
enforces anything; the rest is limiting blast radius:

- Google Cloud → APIs & Services → Credentials → the web API key: **Application restrictions** = HTTP referrers
  (`https://<domain>/*`, `https://nonogram-trainpaths.firebaseapp.com/*`), **API restrictions** = Identity Toolkit API,
  Token Service API, Cloud Firestore API.
- Firebase Authentication → Sign-in method: only **Google** enabled (no email/password, no anonymous — either would let
  a stranger mint accounts and write docs with the key alone).
- Google Cloud → Billing → a budget alert on the project, since unauthenticated-key abuse shows up as a bill first.
- The rules should type- and size-check every writable field (`solution`, `name`, `boardState`, `updatedAt`), not just
  ownership and status transitions — the client-side caps in `classes/Nonogram.kt` are UX, not enforcement.

### Verifying a deploy

On the host:

```bash
docker ps --filter name=nonogram-web
curl -I  http://127.0.0.1:8080/          # 200
curl -sI http://127.0.0.1:8080/sqlite3.wasm | grep -i content-type   # application/wasm
```

In a browser: `https://<domain>` loads, sign-in works, and a puzzle in progress survives a reload (OPFS persisted).

### Alternatives not taken

- **Registry (GHCR) + `docker compose pull` on the host** — needs a token on the host and a public/private package; the
  SSH Docker context needs neither. Switch to it if the image ever needs to be deployed to more than one host.
- **Re-deploying the js bundle with a feature-detect fallback** — a script in `index.html` could redirect a browser
  without wasm-GC to a `/js/` build. That means restoring the second `jsBrowserDistribution` step, which is exactly the
  memory the runner did not have. Worth revisiting if the runner grows.
