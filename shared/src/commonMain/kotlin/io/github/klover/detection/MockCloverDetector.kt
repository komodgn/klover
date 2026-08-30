package io.github.klover.detection

import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * A fake detector that fabricates plausible-looking results so the whole app can run end-to-end
 * before any real ML exists.
 *
 * Behaviour:
 *  - simulates inference latency with a short delay,
 *  - most of the time "finds" 0-2 clovers at random positions with random confidence.
 *
 * Swap this out for a real [CloverDetector] in the Koin module and nothing else changes.
 */
class MockCloverDetector(
    private val random: Random = Random.Default,
    private val simulatedLatencyMs: Long = 700,
) : CloverDetector {

    override suspend fun detect(image: CloverImage): DetectionResult {
        val mark = TimeSource.Monotonic.markNow()
        delay(simulatedLatencyMs)

        // Roughly 65% of the time we "find" something, to make all UI states reachable.
        val detections = if (random.nextFloat() < 0.65f) {
            List(random.nextInt(1, 3)) { randomDetection() }
        } else {
            emptyList()
        }

        return DetectionResult(
            detections = detections,
            inferenceTimeMs = mark.elapsedNow().inWholeMilliseconds,
        )
    }

    private fun randomDetection(): Detection {
        val size = 0.08f + random.nextFloat() * 0.10f // 8%-18% of the image
        val left = random.nextFloat() * (1f - size)
        val top = random.nextFloat() * (1f - size)
        return Detection(
            box = BoundingBox(left = left, top = top, width = size, height = size),
            confidence = 0.75f + random.nextFloat() * 0.24f, // 0.75-0.99
        )
    }
}
