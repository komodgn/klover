package io.github.klover.detection

/**
 * iOS detector — currently a stub that returns mock results.
 *
 * TODO(on-device iOS): integrate ONNX Runtime for iOS. Sketch:
 *  1. Add the `onnxruntime-c` (or `onnxruntime-objc`) pod via the Kotlin CocoaPods plugin.
 *  2. Generate cinterop bindings and create an `OrtSession` from `Res.readBytes(MODEL_PATH)`.
 *  3. Preprocess with CoreGraphics/Accelerate into a CHW float buffer, run inference,
 *     then reuse [decodeYoloOutput] — the post-processing is already shared in commonMain.
 */
actual fun createCloverDetector(): CloverDetector = MockCloverDetector()
