package io.github.klover.platform

import androidx.compose.runtime.Composable

/**
 * Returns a launcher lambda that opens the system photo picker; the chosen image's encoded bytes
 * are delivered to [onImagePicked].
 *
 * Android uses `PickVisualMedia` (no runtime permission needed). iOS is currently a stub.
 */
@Composable
expect fun rememberGalleryLauncher(onImagePicked: (ByteArray) -> Unit): () -> Unit
