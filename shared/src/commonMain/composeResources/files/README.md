# Model file location

Put your trained YOLOv8-style ONNX model here, named **`clover.onnx`**.

- Path: `shared/src/commonMain/composeResources/files/clover.onnx`
- Expected spec: input `1×3×640×640` (RGB, normalized 0–1), output `1×(4+numClasses)×numAnchors`
  (YOLOv8 detection head).
- If the file is missing, the app automatically falls back to the mock detector.

If your spec differs, adjust `commonMain/.../detection/YoloConfig.kt` (input size / thresholds) and
the Android preprocessing in `CloverDetector.android.kt`.

See [`../../../../../training/README.md`](../../../../../training/README.md) for how to train one for free.
