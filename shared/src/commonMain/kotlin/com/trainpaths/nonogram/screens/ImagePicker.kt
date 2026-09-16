package com.trainpaths.nonogram.screens

import androidx.compose.runtime.Composable
import com.trainpaths.nonogram.scan.PickedImage

/**
 * Remembers a launcher that opens the platform's image picker and hands back a [PickedImage].
 *
 * The image stays in whatever form its platform produced — see [PickedImage] — so nothing is copied
 * or decoded until `decodeToLumaMap` asks for it. The button itself stays in `GenScanScreen` so its
 * styling lives in one place rather than once per platform.
 *
 * A dismissed picker is silent: it reports neither a pick nor an error, the same way
 * [GoogleSignInSection] swallows a cancelled sign-in.
 */
@Composable
expect fun rememberImagePicker(
    onPicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit
