# Image scanner

Image → black/white grid → the generator board, editable, saved through the normal path (Solver validation, duplicate
check, publish request).

## Flow

`GenListScreen` **Scan image** → `GenScanRoute` → `GenScanScreen` → **Generate** →
`GenViewModel.loadScanned(grid, name)` → `GeneratorRoute` with `popUpTo(GenListRoute)`.

Two things that will bite:

- **Never route a scan through `GenConfScreen`.** Its `!editing` branch calls `setNonogram`, which rebuilds `tiles` and
  wipes the grid. (The wrench inside `GenScreen` opens `GenConfRoute(editing =
  true)`, which resizes non-destructively — that path is fine.)
- **`loadScanned` leaves `isDirty = true`**, unlike `loadForEdit`. `GenScreen.attemptLeave` keys off it, so an unsaved
  scan gets the confirm dialog instead of vanishing.

## Pipeline

1. `PickedImage.decodeToLumaMap()` — per platform (see **Picker actuals**), reducing to a `LumaMap` of at most
   `WORKING_SIDE` (256) on its longest side. Android: `decodeToImageBitmap()`, then `readPixels` a band at a time into
   one reused buffer (`BAND_BUDGET` ints), off the main thread. Web: the browser decodes an `<img>` and downscales it
   with `drawImage`, since the main thread is the only one there. Alpha is composited over **white** on both; without
   that a transparent PNG scans as a fully filled grid.
2. `LumaMap.otsuThreshold()` — the initial slider value.
3. `defaultDimensions(width, height, side)` — aspect-preserving, longest side `side`. The size is the user's, not the
   pipeline's: `ScanViewModel.sideInput` starts at `DEFAULT_SCAN_SIDE` (30) and is editable *before* the pick as well as
   after it, so `onImagePicked` reads it rather than overwriting it. Only that one number is exposed — rows and columns
   are both derived, which is what stops a resize from skewing the picture, and it lands on the rows for a portrait
   image and on the columns for a landscape one.
4. `LumaMap.toGrid(ScanOptions)` — averages the source pixels under each cell, fills when the average is below
   `threshold`, `invert` flips.

No cropping: the whole image is always used, framing is the user's job.

`toGrid` clamps its sides via `clampToGridSide()`, so the output always satisfies `isWellFormedGrid()`
whatever the caller asked for — a ragged or out-of-range grid makes `Nonogram.colClues` throw.

Reducing to 256px once is what keeps the slider cheap: every recompute after the first runs over ≤65 KB. Matters because
on web `Dispatchers.Default` **is** the main thread.

## Files

|                                      |                                                                                  |
|--------------------------------------|----------------------------------------------------------------------------------|
| `scan/LumaMap.kt`                    | `LumaMap`, `LumaAccumulator` (the box-downsample), `lumaOverWhite`, `scaledSize` |
| `scan/ImageDecode.kt`                | `expect class PickedImage` + `expect suspend decodeToLumaMap` — the only part tests can't reach |
| `scan/ImageToGrid.kt`                | `ScanOptions`, `otsuThreshold`, `defaultDimensions`, `clampToGridSide`, `toGrid` |
| `screens/ImagePicker.kt`             | `expect @Composable rememberImagePicker(onPicked: (PickedImage) -> Unit, onError): () -> Unit` |
| `screens/viewModel/ScanViewModel.kt` | tuning state + `previewGrid`; `MAX_IMAGE_BYTES` = 20 MB                          |
| `screens/GenScanScreen.kt`           | the screen; `GridPreview` is a plain `Canvas`, not `Board`                       |
| `commonTest/scan/ImageToGridTest.kt` | everything except the decode                                                     |

Both acquiring and decoding the image are `expect`/`actual`: `PickedImage` keeps the image in whatever form the
platform produced it (encoded bytes on Android, the `Blob` on web), and `decodeToLumaMap` is where the two differ —
see `docs/web-architecture.md → One thread, and what that costs` for why web cannot use Compose's decoder.

## Picker actuals

- **android**: `ActivityResultContracts.PickVisualMedia` + `ContentResolver` on `Dispatchers.IO`. No manifest permission
  needed. Required adding `activity-compose` to `shared`'s androidMain — it was only on `androidApp`.
  `ImageDecode.android.kt` runs Compose's `decodeToImageBitmap` + banded `readPixels` on `Dispatchers.Default`.
- **web**: one actual each in `webMain`, **no js/wasmJs split**. The split `createDbWorkerDriver` needs does not apply:
  the stdlib's webMain view no longer ships `org.w3c`, so `kotlinx-browser` (now declared explicitly) is the sole DOM
  provider for both targets. A detached `<input type="file">`, `addEventListener("change", …)` rather than `onchange`
  (that one's type does differ across targets), and the picked `File` goes straight into `PickedImage` — no
  `FileReader` copy. `ImageDecode.web.kt` loads it into an `<img>` (again `addEventListener`, for the same reason) and
  reads the downscaled pixels back from a canvas.

A dismissed picker is silent — no pick, no error — like the canceled sign-in in
`GoogleSignInSection`.

## Shared with the generator

`SizeField` (grid-side input, digit filter at the call site, `SIDE_RANGE_HINT` supporting text)
lives in `GenConfScreen.kt`; same package, so no import. Both scan screens use one — the intro's is how the size is set
before an image exists. `AppButton` is in `AppTheme.kt`.
