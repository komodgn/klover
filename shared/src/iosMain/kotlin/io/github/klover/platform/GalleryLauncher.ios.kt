package io.github.klover.platform

import androidx.compose.runtime.Composable

/**
 * iOS stub. TODO: present a `PHPickerViewController` from the current UIViewController, read the
 * selected item's `Data` into a `ByteArray`, and deliver it to [onImagePicked].
 */
@Composable
actual fun rememberGalleryLauncher(onImagePicked: (ByteArray) -> Unit): () -> Unit = {}
