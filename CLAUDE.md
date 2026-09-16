# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Kotlin Multiplatform nonogram puzzle app targeting Android, Web (JS/Wasm), and iOS (currently commented out). Users
solve nonogram puzzles, track progress, and optionally sync via Google sign-in with Firebase.

## Build & Run

```bash
# Android — dev/prod are product flavors (Firebase environments), so every variant is qualified
./gradlew :androidApp:assembleDevDebug

# Web (Wasm — faster, modern browsers) — dev by default, -Pnonogram.env=prod for the prod project
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Web (JS — broader compatibility)
./gradlew :webApp:jsBrowserDevelopmentRun

# Web production bundles (against prod Firebase)
./gradlew :webApp:wasmJsBrowserDistribution :webApp:jsBrowserDistribution -Pnonogram.env=prod

# iOS: open iosApp/ in Xcode
```

## Tests

```bash
# Android host tests (uses SQLite JVM driver, no emulator needed)
./gradlew :shared:testAndroidHostTest

# Common tests only
./gradlew :shared:wasmJsTest
./gradlew :shared:jsTest

# iOS
./gradlew :shared:iosSimulatorArm64Test
```

Tests live in `shared/src/commonTest/` (pure-logic tests) and `shared/src/androidHostTest/` (tests that need a
SQLDelight driver — uses `TestDatabaseFactory` with the SQLite JVM driver).

## Documenting changes

Don't over-document via code comments. When a change is architecturally significant (a new subsystem, a cross-cutting
refactor, a platform port), explain the design in a `.md` file under `docs/` instead — see
`docs/web-architecture.md` for the shape this should take. Code comments stay minimal (one line, only for genuinely
non-obvious *why*).

## Architecture

All shared code lives in `shared/src/commonMain/`, with platform-specific code in `shared/src/androidMain/` and
`shared/src/webMain/` (shared by the `jsMain`/`wasmJsMain` source sets — see `docs/web-architecture.md`). Platform apps
(`androidApp/`, `webApp/`, `iosApp/`) are thin shells that initialize Koin DI and host the Compose UI.

### Layers

- **`AppSDK`** — facade over the database. All data access goes through here; every method is `suspend` (SQLDelight
  `generateAsync` is on so the web worker driver can be async). Builds its `Database` lazily behind a mutex on first use
  so the constructor itself stays synchronous for DI. It also **owns the dispatcher**: every method hops once through
  `dbDispatcher` (`cache/DatabaseFactory.kt`; `Dispatchers.IO` on Android where the driver blocks,
  `EmptyCoroutineContext` on web where `Dispatchers.Default` *is* the main thread), driver creation included. Callers
  just `suspend` — do not wrap an `AppSDK` call in a `withContext` of your own. The one thing in a ViewModel that still
  earns its own `Dispatchers.Default` is `GenViewModel`'s Solver run.
- **`cache/Database`** — internal class wrapping SQLDelight-generated `NonogramDb`. Maps DB rows to domain types. Not
  accessed directly outside `AppSDK`.
- **`cache/DatabaseFactory`** — `suspend fun createDriver(): SqlDriver`, implemented per platform:
  `AndroidDatabaseFactory`
  (androidMain, `Schema.synchronous()`), `WebDatabaseFactory` (webMain, OPFS worker driver + explicit
  `PRAGMA user_version` migration — see `docs/web-architecture.md`), `TestDatabaseFactory` (androidHostTest, in-memory
  JDBC).
- **`auth/AuthRepository`** — manages auth state (`GUEST` / `SIGNED_IN`), the **user key**, and the onboarding flag via
  `multiplatform-settings`. `initialize()`/`linkFirebaseUser()`/`signOut()` are suspend (they call the suspend
  `AppSDK`). Onboarding flag and per-UID sync cursors survive sign-out untouched.

  **The user key** (`currentUserUid: StateFlow<String?>`, persisted as `current_user_uid`) is the app's *only* identity:
  the Firebase uid once signed in, `"local:<random>"` while a guest. It keys both `Nonogram.authorUid` and
  `UserProgress.userUid`, so the local tables mirror Firestore's own `users/{uid}/progress/{nonogramId}` layout.
  `currentFirebaseUid` is the same value projected to null while a guest, for the calls that must
  reach Firestore. The generator is open to guests, hence the local form; guests never push, so it cannot leak.
  `linkFirebaseUser` migrates the guest's data onto the uid (`reassignAuthor` + `mergeProgressInto`, then drops the
  emptied guest row); a key that is already a real uid is never migrated, so signing into a second account cannot take
  the first one's data. `signOut()` mints a fresh guest key and leaves the signed-in row intact, so signing back in
  restores that account's puzzles and progress.
- **`settings/SettingsRepository`** — holds the app's persisted preferences via `multiplatform-settings`: the selected
  `ColorTheme` (key `color_theme`, stores the enum name) and `showNames` (key `show_all_nonogram_names`, default
  `true` — the Settings screen's "Always show names" switch, read by `MenuViewModel`). Same shape as `AuthRepository`:
  reads synchronously in the constructor (no `initialize()` needed), exposes a `StateFlow` per preference, writes
  through on set. See **AppTheme** below.
- **`sync/SyncService`** — interface for syncing *both* progress and the shared `nonograms` collection (push/pull/merge;
  `mergeRemoteNonograms` is the shared merge policy), the author's `deleteNonogram` (the `DELETED` tombstone — see
  **Data Model**), plus the publish-review calls (`requestPublish`,
  `fetchModerationGate`, `isAdmin`, `pullPendingReviews`, `decideReview`. The review queue is a plain `List<Nonogram>`:
  the author rides along in `authorUid`, so there is no wrapper type; pure streak/ban helpers live in
  `sync/Moderation.kt`). `sync/FirebaseAndroidSyncService` (androidMain)
  implements it with `dev.gitlive:firebase-firestore`; web binds `sync/FirebaseWebSyncService` (webMain), built on
  hand-written Kotlin externals to the Firebase JS SDK in `firebase/` (see `docs/web-architecture.md` for the externals
  pattern and the auth-session gate). Same Firestore shape on both platforms, so data interoperates.

  **The platform services are adapters, not logic.** Anything two implementations would otherwise write twice lives in
  commonMain beside `SyncService`, because every duplicated piece had already drifted once: `FirestoreSchema.kt`
  (`Paths` / `Fields` — every collection path and field name, mirrored a second time by the web externals' property
  names and a third by the console-managed Firestore rules), `RemoteProgress.kt` (`mergeRemoteProgress` / `applyRemoteProgress` /
  `uploadAllProgress`, the mirror of `mergeRemoteNonograms`) and `NonogramDocument.kt` (the wire document plus
  `toNonogram(onSkip)` — decoding, `isWellFormedGrid`, and the enum fallbacks that let an older writer's document
  read on a newer client; `encodeSolution` is the write side). A platform contributes a fetch (`fetchProgress`,
  `parseNonograms` + its snapshot → `NonogramDocument` adapter), the document writes, and its error handling:
  `gated(uid, label, fallback) { }` on web (session check + catch), `logged(label, fallback) { }` on Android (catch).
  Put new merge or mapping rules in commonMain and let both platforms call them.
- **`classes/` board + game** — the interactive grid (clues, tiles, pan/zoom, drag-to-draw) is a self-contained Compose
  engine: `Board`/`BoardTransform` (one Canvas for all tiles + a layer-transform pan/zoom model),
  `Game` (win check), `Tile`/`TileState`, `ClueProgress` (which clues the player has certainly drawn,
  struck out in the game screen's gutters), and `RezoomButton` — fit-to-screen, floating over the board's top-left
  on a translucent scrim and shown only while the board is zoomed in (`BoardTransformState.canReset`).
  Performance-critical and gesture-heavy — see `docs/board-rendering.md`.
- **Desktop widths** — `MAX_CONTENT_WIDTH = 1000.dp` lives in `AppTheme.kt` alongside the palettes, applied as
  `Modifier.widthIn(max = …)` ahead of any `fillMax*` and centred by the screen root's `horizontalAlignment`. App bars
  stay full-bleed with capped content, the Board is deliberately exempt, and `NonogramGrid` picks its column count from
  the available width.
- **`filter/`** — the combined filter/sorter behind both list screens: a pure `FilterSortState` model plus the
  `FilterMenuButton` dropdown hosted in the `TopAppBar`'s `navigationContent` slot. Rows are data — a sortable
  `FilterAttribute` with checkable values, or a standalone `FilterToggle` (the "Personal"
  own-puzzles switch) — so adding one is a list entry in `NonogramFilters.forUser(authorUid)` (the menu; a function
  rather than a constant because ownership is user-scoped) or `NonogramFilters.GENERATOR` (the generator list:
  one `STATUS` attribute over `CardStatus`, whose `Nonogram.cardStatus` mapping lives beside the enum). A row's
  `label` is also its id. `FilterSortState` also carries the two filters the dropdown's closing "More" row opens
  `FilterScreen` for (`FilterRoute(generator)`, a flag so `App.kt` binds the right ViewModel):
  `query`, a case-insensitive name search that drops unnamed puzzles while non-blank, and `sizeRange`, a bound on
  `Nonogram.longerSide` edited through text fields and a `RangeSlider` (`withMinSize`/`withMaxSize` keep the ends
  ordered and inside `FULL_SIZE_RANGE`). The screen applies every edit live, so back just pops. With no sort chosen
  the menu lists own puzzles first and the generator lists latest-updated first.
- **`tutorial/`** — the first-run hint overlay: `TutorialStep` (an enum whose declaration order is priority order,
  carrying the copy), `TutorialRepository` (one `tutorial_seen_<STEP>` boolean per step, device-wide so it survives
  sign-out), `TutorialController` + `Modifier.tutorialAnchor(step)`
  (an anchor registry, since screens own their own app bars), and `TutorialHost`, a `Box` wrapped around the `NavHost`
  in `App.kt` that draws the scrim, the spotlight hole and the blob. Nothing is ever placed over the highlighted
  control — Compose hit-testing stops at the topmost sibling, so the anchor itself listens on `PointerEventPass.Initial`
  without consuming.
- **`classes/Solver`** — line-logic solver; run via `Nonogram.isValid` to check a puzzle is uniquely solvable (gates
  publishing). **User-owned and actively changing — do not document its internals or modify it.**
- **`screens/GoogleSignInSection`** — `expect`/`actual` composable for the Google sign-in button, both actuals built on
  kmpauth's `GoogleSignInButton` + a `SignInState`. Android uses `rememberGoogleAuthState`, which exchanges the
  credential for a Firebase session through kmpauth's own Firebase backend (auto-registered from `kmpauth-firebase`) and
  hands back a `KMPAuthUser`. Web uses `rememberGoogleSignInState` — credential only — and does the exchange itself via
  `FirebaseWeb.signInWithGoogle`, because the web sync gate reads the Firebase JS SDK's auth state (see
  `docs/web-architecture.md`). Both skip `KMPAuthUserCancelledException` rather than logging a dismissed prompt.
- **ViewModels** (`screens/viewModel/`) — Compose state holders using `mutableStateOf`. `GameViewModel` manages the tile
  board and save/sync. `GenViewModel` drives the generator (draw/resize/save + validation). `MenuViewModel` holds the
  nonogram list and progress preview map; it loads via `AppSDK.getVisibleNonograms(uid)` — approved puzzles plus
  whatever the current user key owns — so a signed-out user stops seeing the previous account's puzzles (the rows stay
  in the DB, they are just filtered out). `AuthViewModel` orchestrates login flow and **all remote sync** —
  `syncAll` pulls progress + public + owned nonograms in one pass (separate public/owned cursors read via
  `AuthRepository`) and then refreshes the admin flag and publish ban (`isAdmin` / `publishBanned` StateFlows),
  `retryOwnNonograms` re-runs just the owned stream for the generator's retry button. The **public** stream runs first,
  before the `currentFirebaseUid` gate, so guests pull public puzzles too — approved puzzles are
  readable unauthenticated (enforced by the Firestore rules), while progress, owned puzzles and the admin/moderation reads all
  need a session and stay behind the gate. `AdminViewModel` drives the admin review queue (one pending request at a
  time, buffered a batch at a time). All depend on the suspend `AppSDK`/`SyncService` from inside
  `viewModelScope.launch` — but always via `launchGuarded` (`screens/viewModel/LaunchGuarded.kt`), never
  `viewModelScope.launch` directly: an uncaught throwable in a plain launch reaches the default handler and kills the
  process on Android. It rethrows `CancellationException` and routes everything else to `onError`; UI flags that gate a
  screen (`isLoading`, `_signInComplete`) are released in a `finally` inside the block, so a failed read can never
  leave a spinner up forever.

  **A missing uid** goes through `String?.orMissing` (`screens/viewModel/SignedIn.kt`) rather than a bare
  `?: return`, so a guard is never silent. Its default handler just logs, which is what a background pass wants;
  the four sites where the user is waiting on the result override it to set the screen's error state instead
  (`saveError`, `publishError`, `AdminViewModel.error`). The two uid sources are not the same condition — a null
  `currentUserUid` is an anomaly (auth not initialized), a null `currentFirebaseUid` is just a guest.

  **When sync runs.** `syncAll` fires once from `AppContent`'s app-start `LaunchedEffect`, and after that only when the
  user pull-to-refreshes the menu (`MenuScreen`'s
  `PullToRefreshBox` → `MenuViewModel.refresh`). Entering `MenuRoute` does **not** sync — it calls
  `MenuViewModel.reload()`, a silent local-DB re-read with no spinner, so puzzles just authored in the generator still
  appear. `MenuViewModel` therefore has two flags: `isLoading` (full-screen spinner, cold start and sign-in/sign-out
  only, via `loadAll()`) and `isRefreshing` (the pull-to-refresh indicator).

### Navigation

Type-safe navigation via `navigation-compose` with `@Serializable` route objects in `navigation/Routes.kt`. Routes:
`LoginRoute`, `MenuRoute`, `GameRoute(nonogramId)`, `SettingsRoute`, `AdminRoute`, plus the generator routes
`GenListRoute`,
`GenConfRoute(editing)`, `GeneratorRoute` (the board editor), plus dialog routes (`PlayDialogRoute`, `WinDialogRoute`,
`LeaveDialogRoute`).

**Generator flow.** `GenConfRoute` is *linked to a specific `GeneratorRoute`* via its `editing` flag:

- **New:** `GenList` → `+New` (`startNew()`) → `GenConf(editing=false)` → *Generate* (`setNonogram`) → `GenScreen`.
  GenConf's
  "Done" navigates forward to `GeneratorRoute` (`popUpTo(GenListRoute)`); back cancels to the list.
- **Edit:** `GenList` → card (`loadForEdit`) → `GenScreen`. The top-left wrench opens `GenConf(editing=true)` pre-filled
  with the current dims; *Save* applies `resizeNonogram` (preserves overlapping cells, keeps the puzzle id) **and
  persists via
  `onSave`**, *Back* discards — both `popBackStack()` to the same `GenScreen`.

**Save-state guard.** `GenViewModel.isDirty` tracks unsaved edits (set on tile edit/`resize`, cleared on load/save).
Leaving
`GenScreen` toward the list — via the swap button *or* system/predictive back (`NavigationBackHandler` +
`rememberNavigationEventState` from `androidx.navigationevent:navigationevent-compose`, the modern non-deprecated back
API — NOT the deprecated `androidx.compose.ui.backhandler.BackHandler`) — routes through `attemptLeave`:
if dirty it shows `GenSaveConfirmDialog` (Save / Don't save / cancel-by-dismiss) before navigating; otherwise it goes
straight to `GenListRoute`. The board's bottom app-bar Save icon saves in place and is enabled only for a new or dirty
puzzle; the leave dialog's Save action remains save-and-exit.

`TopAppBar` navigation icon: GENERATOR mode shows the `build` wrench (used as the "config" affordance in `GenScreen`);
pass `backArrow = true` to force a plain back arrow (used in `GenConf`).

`navigation/BottomToolBar.kt` is the board's bottom bar (GameScreen + GenScreen). Its buttons sit in **three groups** —
drawing, history, board, in that order — each a `ToolGroup` of adjacent items, the separation coming from the computed
gap the parent `Row` spaces them by rather than any divider or container. The **drawing** group is one button per
`DrawMode` (Fill / Cross / Erase), the active one highlighted; there is no cycling tool button and no Toggle mode —
every mode writes its state idempotently. The **history** group is undo/redo. The **board** group is the
**lock/unlock** toggle (locked = one-finger drag draws, unlocked = drag pans — see `docs/board-rendering.md`), the
(GenScreen) **Save** icon (enabled only for a new or dirty puzzle) and the **Check** icon (a magnifying glass, on both
screens, and it fits the board back on screen as well as marking cells — mistakes in GameScreen, cells the Solver
cannot pin down in GenScreen, where the icon itself is tinted green/red with the verdict). Rezoom is *not* here — it floats over the
board's own top-left (see `classes/` above), where it can be hidden whenever the board is already fitted.

A `ToolGroup` with a `title` labels the cluster as a whole and its buttons carry no labels of their own — that is what
the drawing and history groups use ("Pencil", "History"), since their buttons are options of one setting rather than
separate actions. The board group passes no title, so each of its buttons labels itself. Both shapes come out the same
height (pill + one text line), which is what keeps every icon on one line.

Both screens render 7 buttons, which does not fit a phone at a fixed width, so `BoxWithConstraints` sizes them: the
icon-only buttons take a fixed `ICON_ITEM_WIDTH`, the labelled ones split what is left and ellipsize rather than
overflow, and the group gap absorbs the remainder (clamped, with the row centred) — **the gaps are subtracted before
the items are sized**, because handing the items the full width leaves the arrangement no slack and the grouping
silently disappears. Icons come from the hand-built `icons/` package of `ImageVector`s.

### DI (Koin)

- `di/AppModule.kt` — common singletons: `AppSDK`, `Settings`, `AuthRepository`, `SettingsRepository`, plus all
  ViewModel registrations via `viewModelOf` (koin-core-viewmodel, shared across platforms).
- `di/AndroidModule.kt` — platform bindings: `DatabaseFactory` → `AndroidDatabaseFactory`, `SyncService` →
  `FirebaseAndroidSyncService`.
- `di/WebModule.kt` (webMain) — platform bindings: `DatabaseFactory` → `WebDatabaseFactory`, `SyncService` →
  `FirebaseWebSyncService`.

### Data Model

- **`Nonogram`** — `id`, `difficulty` (enum: EASY/MEDIUM/HARD/HARDCORE), `solution` (List<List<Int>> stored as JSON),
  `name: String?`, `authorUid` (the user key — see `auth/AuthRepository`; the same string as the Firestore
  `authorUid` field, so no local↔remote translation is needed), `updatedAt`, `publishStatus` (enum:
  NONE/PENDING/DENIED/UNLISTED/APPROVED/VALID/DELETED, stored as its ordinal in the DB column `status`, as its name in
  the Firestore field `publishStatus`). **`publishStatus` also carries the Solver verdict**, because the two axes are not
  independent: publication may only be requested from `VALID`, so every state past `NONE` was reached by passing the
  Solver and `NONE` is the only state a puzzle that is not uniquely solvable can be in. That is what lets
  `GenListScreen` read its status dot off the column (`isKnownValid get() = publishStatus != NONE`) instead of
  re-solving every puzzle it shows; `GenViewModel.afterSave` is the one place the stored status is derived from a fresh
  verdict. `VALID` and `DELETED` are declared **last** so the ordinals of the existing entries do not move — an older
  build reads them, and any unknown ordinal or name, back as `NONE` (`util/PublishStatusConversions.kt`). `DELETED` is a
  **Firestore-only tombstone**: `SyncService.deleteNonogram` writes it when the author deletes a puzzle (never a hard
  delete, so a stale copy on another of the author's devices cannot push it back to life), and `mergeRemoteNonograms`
  removes the local row when it pulls one — delete wins regardless of timestamps — so the DB never stores it and no
  query has to filter it out. Other players who already pulled a public copy keep it, the same way `UNLISTED` does not
  propagate: the public pull only ever fetches `APPROVED` docs. Visibility is derived, not
  stored: `isPublic get() = publishStatus == APPROVED`, and the author's on/off switch moves an approved puzzle between
  APPROVED and UNLISTED. Computes `rowClues`/`colClues` on the fly, and `isValid` lazily via the `Solver`. Name helpers live
  alongside: `MAX_NONOGRAM_NAME_LENGTH` (30), `normalizeNonogramName()`, `UNNAMED_NONOGRAM_TITLE`, and
  `nonogramNameProblem()` — the client-side name gate (emoji/symbols, plus the substring blocklist in
  `classes/NameBlocklist.kt`), returning the message to show or null; a blank name is always fine. The shared
  `NameField` (`screens/GenConfScreen.kt`) renders it as the field's error, and `GenViewModel.requestPublish` and
  `AdminViewModel.accept` refuse a name it rejects. Ownership is
  `isOwned(uid)`, which never matches the blank `authorUid` seeded puzzles carry. Grid shape lives here too:
  `MIN_NONOGRAM_SIDE` (5) / `MAX_NONOGRAM_SIDE` (60), clamped in `GenViewModel.setNonogram`/`resizeNonogram` and shown
  in `GenConfScreen`'s size fields, plus `isRectangularGrid()` / `isWellFormedGrid()`. Both sync services reject a
  grid failing `isWellFormedGrid()` in `parseNonograms` (ragged grids crash `colClues`), and the DB mapper falls back
  to an empty solution for one already stored — keep `MAX_NONOGRAM_SIDE` in step with the 20 000-character cap
  the Firestore rules put on the encoded `solution`.
- **`Tile`** — mutable Compose state. Cycles: NONE → FILLED → CROSSED → NONE.
- Grids are serialized as `List<List<Int>>` (JSON, `classes/SolutionCodec.kt`), but in **two different
  encodings**, and `Tile.kt` names them apart. A puzzle `solution` is 0/1 — `toSolutionInts()`, which collapses
  `CROSSED` to 0; that is what the win check (`GameScreen`) and the generator (`GenViewModel`) want, and
  `computeLineClues` would misread anything else. A **saved board** (`UserProgress.boardState`, and the same
  string in Firestore) is 0/1/2 — `toProgressInts()` / `progressIntToTileState()`, where 2 is a cross, because
  crosses are the solver's own working-out and have to survive leaving the puzzle. `progressIntToTileState`
  falls back to `NONE`, so the 0/1 rows written before crosses were persisted still load unchanged. The layers
  in between (codec, DB column, both `SyncService`s) never inspect the values.
- **`cache/SeedPuzzles.kt`** — the built-in puzzles, **generated**: `./gradlew :seedTool:run` rewrites it from every
  `APPROVED` puzzle in the *dev* Firestore project, so it is a projection with no state of its own and each seed's id
  is its dev document id. Each solution is stored as a `0`/`1` row string (`SeedPuzzle.rows`, one row per line,
  decoded by `String.toBinaryGrid()` in `classes/SolutionCodec.kt`) rather than nested `listOf`s, so the file's
  static initializer holds one string constant per puzzle instead of one vararg element per cell — that is what
  kept it under the JVM method-size limit. `AppInitializer.initializeApp` calls `AppSDK.seedIfEmpty()` once at startup, before
  `AuthRepository.initialize()` and so before any ViewModel reads the table; the guard is a `SELECT count(*)`, since
  an empty table is the first launch (and the one web storage eviction leaves behind). See `docs/seeding.md` — do not
  hand-edit the file.

### SQLDelight

Schema: `shared/src/commonMain/sqldelight/com/trainpaths/nonogram/cache/database.sq`
Migrations: none. The pre-release history (`1.sqm`–`3.sqm`) was collapsed before v1 — it had drifted from
`database.sq` (`2.sqm` added `authorId INTEGER`, the schema declares `authorUid TEXT`), and with no install base the
honest fix was to delete it. `database.sq` is therefore schema version 1 and every install is a fresh `create()`. Any
schema change from here on needs a numbered `.sqm` beside it. Database name: `NonogramDb`, package:
`com.trainpaths.nonogram.cache`

Tables: `NonogramData`, `User` (`uid` TEXT PK → display name), `UserProgress` (composite PK: userUid + nonogramId).

`generateAsync` is enabled, so all generated query/transaction code is `suspend`. Sync drivers (Android, JVM tests)
adapt via `NonogramDb.Schema.synchronous()`; the web worker driver uses the async API directly. See
`docs/web-architecture.md` for the per-platform schema init/migration story.

### AppTheme

Defined in `AppTheme.kt`. `ColorTheme` is an enum of up to 5 Material 3 `lightColorScheme`s, built by the private
`colorScheme(...)` factory. `ColorTheme.DEFAULT = FOREST` reproduces the original single hardcoded scheme. Current
entries: `FOREST`, `MIDNIGHT`, `PLUM` (dark), `PAPER`, `FROST` (light).

Per-theme roles (vary):

- `primary` — background, TopAppBar, main surface
- `onPrimary` — the accent; content on primary (e.g. app-bar icons/text, card accent blocks)
- `secondary` / `onSecondary` — BottomToolBar container/content, unfocused text-field state
- `outline` — the near-white/white card panel color (`NonogramGrid`/`GenListScreen` cards)
- `onBackground` — text drawn directly on `background` (white on dark themes, near-black on light)
- `background` — always set equal to `primary`, never passed independently

Frozen roles (identical across every theme — difficulty semantics, always drawn on white cards):

- `tertiary` `#D7B400` gold — MEDIUM difficulty, beat badges
- `onTertiary` `#6DB85C` green — EASY difficulty
- `tertiaryFixed` `#CE0C0C` red — HARD difficulty

`surface` and `error` are never overridden (M3 light defaults) — don't put per-theme content on `surface`, use
`outline` instead. `AppTheme(theme: ColorTheme, content: ...)` requires the theme explicitly (no default), so every call
site must supply one. The active theme lives in `settings/SettingsRepository`/`SettingsViewModel` and is threaded as a
required `App(..., settingsViewModel)` param.

`App()` itself owns the single `AppTheme` call site and the auth-init gate: it reads `settingsViewModel.theme` and
`authViewModel.authState`, wraps everything in `AppTheme(theme)`, and renders `LoadingScreen()` while
`authState == INITIALIZING` instead of the real content (`AppContent`, a private composable with the NavHost). The two
platform entry points (`MainActivity.kt`, `webApp/main.kt`) therefore just call `App(...)` unconditionally — no
`if`/`else`, no `AppTheme` call of their own. `menuViewModelFactory`/`genViewModelFactory` are passed as
`@Composable () -> VM` (matching the existing `gameViewModelFactory` idiom), **not** resolved instances — this is
deliberate, not simplifiable: it keeps `MenuViewModel`/`GenViewModel` construction inside the non-`INITIALIZING`
branch, since `MenuViewModel.init { loadAll() }` needs `AuthRepository.currentUserUid` to already be set.

Use `MaterialTheme.colorScheme.*`, never hardcode hex — outside `AppTheme.kt` itself, where each theme's palette is
defined.

### Firebase / Auth

- Google sign-in via `kmpauth` (`io.github.mirzemehdi:kmpauth-google/firebase`) — `kmpauth-firebase` stays an
  androidMain-only dependency: its wasmJs actual cannot do browser-flow sign-in (gitlive has no wasm target), and
  `webMain` is shared by js+wasmJs, so web exchanges the credential itself. `kmpauth-google`/`kmpauth-uihelper` are
  commonMain (both publish js+wasmJs).
- **Two environments, both committed.** `nonogram-trainpaths` is **prod**, `nonogram-ba791` is **dev**; they share
  nothing (separate uids, puzzles, admin rosters, rules). Android selects one with a product flavor in the `env`
  dimension, reading `androidApp/src/{dev,prod}/google-services.json` — so the tasks are `assembleDevDebug`,
  `bundleProdRelease` and friends, never a bare `assembleDebug`. The `dev` flavor carries
  `applicationIdSuffix = ".dev"` (and its own `app_name` in `androidApp/src/dev/res`), so it installs as
  `com.trainpaths.nonogram.dev` alongside the prod build rather than colliding with it — which means
  `com.trainpaths.nonogram.dev` is registered as its **own Android app** in the dev Firebase project, with the
  debug SHA-1 on that app; `namespace` stays unsuffixed. Web selects one with the Gradle property
  `nonogram.env` (`dev` by default in `gradle.properties`, `-Pnonogram.env=prod` to switch), which picks the
  source directory holding `FirebaseWebConfig.kt`: `webApp/src/dev` or `webApp/src/prod`, one line
  of `kotlin.srcDir` in `webApp/build.gradle.kts`. The two files declare the same object, so callers never see
  the switch — but a new constant has to be added to both. Both are public-by-design client config; the only
  gitignored secrets are `keystore.properties` / `*.jks`. 
- **App Check** — **Android only.** Play Integrity in `prod`, the debug provider in `dev`, installed in
  `MainApplication.onCreate` *before* `startKoin` (Koin builds `FirebaseAndroidSyncService`, which touches
  Firestore) via `installAppCheck()`, which has one copy **per flavor** (`androidApp/src/{dev,prod}/`), with the
  provider artifacts scoped `devImplementation` / `prodImplementation` to match. Provider therefore tracks the
  Firebase project, not debuggability, which encodes the project's rule — **debug against dev, build for
  prod**. `devDebug` is the variant to develop in; `prodRelease` is what ships; `devRelease` is only the local
  R8 smoke test. `prodDebug` is not used: once prod App Check is enforced it cannot reach Firestore, since
  Play Integrity cannot attest a sideloaded APK. **Web has no App Check** — reCAPTCHA v3 no longer has a free
  tier, so the provider, its externals and `RECAPTCHA_SITE_KEY` were removed; `FirebaseWeb.initialize` just
  builds the app and hands back auth + Firestore. Anything web-facing must therefore stay unenforced in the
  Firebase console.
- `AppInitializer.onApplicationStart()` calls `KMPAuth.initialize { google(serverId = …) }` with a web client ID.
  Android passes `R.string.default_web_client_id` (generated from the flavor's `google-services.json`); web passes
  `FirebaseWebConfig.GOOGLE_WEB_CLIENT_ID`. The two must be the same OAuth web client within an environment, or the
  platforms mint different Firebase users.
- Firestore paths: `users/{firebaseUid}/progress/{nonogramId}` (progress), `nonograms/{id}` (puzzles — own + public per
  their `publishStatus` field), `users/{firebaseUid}` (`denialStreak` /
  `publishBanned`) and `admins/{firebaseUid}` (admin roster). Puzzles are pulled incrementally by
  `AuthViewModel.syncAll` in **two independent streams** — public and owned — each with its own `updatedAt` cursor
  persisted via `AuthRepository`
  (`getLast{Public,Owned}NonogramSyncTimestamp`). The owned cursor is per-uid; the **public** cursor is device-wide (no
  uid suffix), since every user on the device — guests included — sees the same approved set.
  `pullPublicNonogramsSince` therefore takes a nullable uid: null is a guest's unauthenticated pull, and
  `mergeRemoteNonograms` (`sync/SyncService.kt`) then merges without ever pushing back. Merge policy is remote newer →
  upsert; local newer & locally authored → push back. On both platforms — Android via
  `dev.gitlive:firebase-firestore` (androidMain), web via hand-written Firebase JS SDK externals (webMain), both
  isolated behind `sync/SyncService`; the web impl gates every call on `sessionMatches` *except* the public pull, which
  must work signed out. Security rules are **not** checked in — they are maintained per project in the Firebase
  console (the last tracked copy is `git show fix/admin-panel:firestore.rules`). They are what actually enforces
  publish moderation, and the `nonograms` read rule deliberately allows unauthenticated reads of `APPROVED` docs; a
  rule change has to be applied to dev and prod separately. Two composite indexes are needed on `nonograms` —
  `(publishStatus, updatedAt)` (public pull + review queue) and `(authorUid, updatedAt)` (owned pull) — likewise
  configured in the console, not checked in.
- `auth/PlatformAuth.kt` declares `expect suspend fun firebaseSignOut()`, ending the platform Firebase session —
  `dev.gitlive.firebase.auth.auth.signOut()` on Android, `FirebaseWeb.signOut()` (a new `firebase/auth` `signOut`
  external) on web. `AuthViewModel.signOut()` calls it before `AuthRepository.signOut()`, swallowing failures so local
  sign-out still proceeds if the platform call errors.

## Current State

The generator is implemented end-to-end: `GenListScreen` lists the signed-in user's puzzles, `GenConfScreen` sets the
grid size, `GenScreen` is the tile-drawing board, all driven by the shared `GenViewModel`. Users can create new puzzles
and edit existing ones (with non-destructive resize). See **Navigation → Generator flow** above. On save, `GenViewModel`
runs `Nonogram.isValid` (the `Solver`) to check the puzzle is uniquely solvable — validation is *advisory* (the puzzle
still saves if it fails or the check throws) and only gates whether the author may *request* publication. That is the
**only** place the Solver's verdict is *persisted* — into `publishStatus` (see **Data Model**), so the list screen and
the config screen both read it rather than recomputing it. `GenViewModel.checkBoard`, behind `GenScreen`'s bottom-bar
**Check** button, runs the Solver a second time but purely for the author's benefit: it outlines the cells line logic
cannot pin down and moves `validationState` only, writing nothing (see `docs/board-rendering.md`). Publishing
itself is admin-moderated: the generator's config screen offers a "Request publish" button, an admin accepts or denies
in `AdminScreen` (reachable from Settings), and five denials in a row ban a user from requesting. Editing a puzzle that
is currently public un-publishes it, so every save path first confirms via `PublicEditConfirmDialog`. A request is also
refused when the grid is already spoken for — `AppSDK.hasPublishConflict` (the `selectPublishConflictId`
query) matches the saved `solution` against any `APPROVED` puzzle and against the author's own `UNLISTED`/`PENDING`
copies, and `requestPublish` reports it through `publishError` before anything reaches Firestore. It first runs
`SyncService.syncPublicNonograms` (the cursor-advancing public pull, shared with `AuthViewModel.syncAll`) so a puzzle
approved on another device is in the local DB before the comparison; a failed pull still checks what is already local.
The match is exact — mirrored, rotated or padded grids are different puzzles. **Difficulty is the reviewer's call**:
`GenViewModel` authors every puzzle as `EASY` (no selector), and `AdminScreen`'s four difficulty buttons — defaulting to
`MEDIUM` — decide what `SyncService.decideReview` writes alongside `APPROVED`, which every other device picks up on its
next public pull. **So is the name**: `AdminScreen` shows it in an editable `NameField`, and an approval writes
`AdminViewModel.name` (normalized, name-gate checked) to the `name` field the same way — a denial leaves the
author's name alone. An author deletes a puzzle from the same config screen (`DeleteConfirmDialog` → `GenViewModel.deleteNonogram`):
a signed-in author tombstones the Firestore doc first and keeps the local row if that is refused, a guest just drops
the local row (`AppSDK.deleteNonogram`, progress rows included). The Firestore rules must let the author write
`publishStatus = DELETED` — on update from any prior state, and on create (a puzzle whose push never landed).

Web (js + wasmJs) has persistent OPFS storage plus Google sign-in and Firestore sync via hand-written Firebase JS SDK
externals in `shared/src/webMain` (no gitlive — it doesn't publish wasmJs; see `docs/web-architecture.md` for the
externals pattern, the kmpauth One Tap / token-client caveat, and the auth-session restore gate).
