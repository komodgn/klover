# 🍀 Train the clover detection model (free)

The app detects four-leaf clovers using a `clover.onnx` model. This folder shows how to train it
**for free** using public datasets.

## Quick path (Colab, a few clicks)

1. Upload [`train_clover_colab.ipynb`](./train_clover_colab.ipynb) to [Google Colab](https://colab.research.google.com)
   (`File > Upload notebook`).
2. `Runtime > Change runtime type > T4 GPU` (free).
3. Sign up at [Roboflow](https://roboflow.com) (free) → copy your API key → paste it into cell 2.
4. Run the cells top to bottom → `clover.onnx` downloads automatically.
5. Put `clover.onnx` in `shared/src/commonMain/composeResources/files/` and rebuild the app.

Done. No server, no payment.

## Cost

| Item | Cost |
| --- | --- |
| Dataset (public on Roboflow Universe) | Free |
| GPU training (Colab / Kaggle free tier) | Free |
| On-device inference | Free (no server) |

## Recommended public datasets (Roboflow Universe)

| Dataset | Size | Classes | Notes |
| --- | --- | --- | --- |
| [4 Leaf Clover Detect](https://universe.roboflow.com/test-ara07/4-leaf-clover-detect) | 1,985 | 4-leaf, 5-leaf | 98.8% mAP@50, good balance (default) |
| [Lucky4](https://universe.roboflow.com/sat-6hlvd/lucky4) | 15,763 | 3/4/5-leaf | Large, YOLOv11 |
| [Hunting for Four-Leaf Clovers](https://universe.roboflow.com/adam-fonagy/hunting-for-four-leaf-clovers) | 486 | by leaf count | Built for a mobile app |

> Different datasets may order classes differently. Check the class indices printed by the notebook
> and set the **4-leaf class number** in `YoloConfig.TARGET_CLASS_INDEX`.

## Spec (must match the app)

- Input: `1×3×640×640`, RGB, normalized 0–1 (`imgsz=640`)
- Output: `1×(4+numClasses)×8400` (YOLOv8 detection head, NMS excluded — NMS runs in the app)
- If you change the spec, update `detection/YoloConfig.kt` and the Android preprocessing in
  `CloverDetector.android.kt`.

## License note

Ultralytics YOLOv8 is **AGPL-3.0**. Free for personal / training / open-source use, but a
closed-source commercial release requires an Ultralytics commercial license or a differently
licensed model.
