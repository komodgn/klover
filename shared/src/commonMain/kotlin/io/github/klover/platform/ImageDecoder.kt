package io.github.klover.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes encoded image bytes (PNG/JPEG/…) into a Compose [ImageBitmap], or null on failure. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
