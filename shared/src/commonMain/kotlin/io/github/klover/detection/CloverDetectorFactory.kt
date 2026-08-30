package io.github.klover.detection

/**
 * Creates the platform's best available [CloverDetector].
 *
 * - **Android**: an ONNX Runtime detector that loads `files/clover.onnx`. If the model is missing
 *   or fails to load, it transparently falls back to [MockCloverDetector].
 * - **iOS**: currently a [MockCloverDetector] stub — real ONNX Runtime integration (CocoaPods +
 *   cinterop) is a follow-up.
 */
expect fun createCloverDetector(): CloverDetector
