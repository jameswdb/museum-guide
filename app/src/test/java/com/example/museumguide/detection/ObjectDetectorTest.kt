package com.example.museumguide.detection

import android.content.Context
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObjectDetectorTest {

    // ── Detection data class ──────────────────────────────────────────────

    @Test
    fun `Detection stores all fields correctly`() {
        val rect = RectF(0.1f, 0.2f, 0.3f, 0.4f)
        val detection = Detection("vase", 0.85f, rect)

        assertEquals("vase", detection.label)
        assertEquals(0.85f, detection.confidence, 0.0f)
        assertSame(rect, detection.boundingBox)
    }

    @Test
    fun `Detection component functions return correct values`() {
        val rect = RectF(0.1f, 0.2f, 0.3f, 0.4f)
        val detection = Detection("vase", 0.85f, rect)

        assertEquals("vase", detection.component1())
        assertEquals(0.85f, detection.component2(), 0.0f)
        assertSame(rect, detection.component3())
    }

    @Test
    fun `Detection copy preserves unchanged fields`() {
        val original = Detection("vase", 0.85f, RectF(0.1f, 0.2f, 0.3f, 0.4f))
        val copy = original.copy(label = "person")

        assertEquals("person", copy.label)
        assertEquals(0.85f, copy.confidence, 0.0f)
        assertEquals(original.boundingBox, copy.boundingBox)
    }

    @Test
    fun `Detection toString includes all fields`() {
        val rect = RectF(0.1f, 0.2f, 0.3f, 0.4f)
        val detection = Detection("vase", 0.85f, rect)
        val str = detection.toString()

        assertTrue(str.contains("vase"))
        assertTrue(str.contains("0.85"))
        assertTrue(str.contains("RectF"))
    }

    // ── COCO_LABELS ──────────────────────────────────────────────────────

    @Test
    fun `COCO_LABELS contains vase at index 86`() {
        val labels = resolveCocoLabels()
        assertEquals("vase", labels[86])
    }

    @Test
    fun `COCO_LABELS contains 91 entries`() {
        val labels = resolveCocoLabels()
        assertEquals(91, labels.size)
    }

    @Test
    fun `COCO_LABELS starts with background`() {
        val labels = resolveCocoLabels()
        assertEquals("background", labels[0])
    }

    @Test
    fun `COCO_LABELS ends with toothbrush`() {
        val labels = resolveCocoLabels()
        assertEquals("toothbrush", labels[90])
    }

    // ── Companion object constants ───────────────────────────────────────

    @Test
    fun `CONFIDENCE_THRESHOLD is 0 dot 5`() {
        val field = ObjectDetector::class.java.getDeclaredField("CONFIDENCE_THRESHOLD")
        field.isAccessible = true
        assertEquals(0.5f, field.getFloat(null), 0.0f)
    }

    @Test
    fun `MAX_DETECTIONS is 10`() {
        val field = ObjectDetector::class.java.getDeclaredField("MAX_DETECTIONS")
        field.isAccessible = true
        assertEquals(10, field.getInt(null))
    }

    // ── Post-processing logic ─────────────────────────────────────────────

    @Test
    fun `postprocess returns empty list when numDetections is zero`() {
        val result = invokePostprocess(
            boxes = arrayOf(),
            classes = floatArrayOf(),
            scores = floatArrayOf(),
            numDetections = 0f
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `postprocess returns empty list when all scores are below threshold`() {
        val result = invokePostprocess(
            boxes = arrayOf(
                floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
                floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f)
            ),
            classes = floatArrayOf(1f, 82f),
            scores = floatArrayOf(0.1f, 0.4f),
            numDetections = 2f
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `postprocess filters detections below 0 dot 5 and keeps those above`() {
        val result = invokePostprocess(
            boxes = arrayOf(
                floatArrayOf(0.10f, 0.20f, 0.30f, 0.40f),
                floatArrayOf(0.50f, 0.60f, 0.70f, 0.80f)
            ),
            classes = floatArrayOf(86f, 86f),
            scores = floatArrayOf(0.3f, 0.9f),
            numDetections = 2f
        )
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].confidence, 0.0f)
        assertEquals("vase", result[0].label)
    }

    @Test
    fun `postprocess includes detections at exactly the confidence threshold`() {
        val result = invokePostprocess(
            boxes = arrayOf(floatArrayOf(0.0f, 0.1f, 0.2f, 0.3f)),
            classes = floatArrayOf(82f),
            scores = floatArrayOf(0.5f),
            numDetections = 1f
        )
        assertEquals(1, result.size)
        assertEquals(0.5f, result[0].confidence, 0.0f)
    }

    @Test
    fun `postprocess sorts results by descending confidence`() {
        val result = invokePostprocess(
            boxes = arrayOf(
                floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
                floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f),
                floatArrayOf(0.0f, 0.1f, 0.2f, 0.3f)
            ),
            classes = floatArrayOf(1f, 82f, 16f),
            scores = floatArrayOf(0.6f, 0.9f, 0.7f),
            numDetections = 3f
        )
        assertEquals(3, result.size)
        assertTrue(result[0].confidence >= result[1].confidence)
        assertTrue(result[1].confidence >= result[2].confidence)
    }

    @Test
    fun `postprocess maps known label index to COCO label string`() {
        val result = invokePostprocess(
            boxes = arrayOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)),
            classes = floatArrayOf(86f),
            scores = floatArrayOf(0.9f),
            numDetections = 1f
        )
        assertEquals("vase", result[0].label)
    }

    @Test
    fun `postprocess returns unknown for out-of-range label index`() {
        val result = invokePostprocess(
            boxes = arrayOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)),
            classes = floatArrayOf(999f),
            scores = floatArrayOf(0.8f),
            numDetections = 1f
        )
        assertEquals("unknown", result[0].label)
    }

    @Test
    fun `postprocess converts bounding box from TFLite format to RectF`() {
        val result = invokePostprocess(
            boxes = arrayOf(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)),
            classes = floatArrayOf(82f),
            scores = floatArrayOf(0.9f),
            numDetections = 1f
        )
        val box = result[0].boundingBox
        // TFLite output is [top, left, bottom, right]
        // RectF expects [left, top, right, bottom]
        assertEquals(0.2f, box.left, 0.0f)
        assertEquals(0.1f, box.top, 0.0f)
        assertEquals(0.4f, box.right, 0.0f)
        assertEquals(0.3f, box.bottom, 0.0f)
    }

    @Test
    fun `postprocess caps detections at MAX_DETECTIONS`() {
        val boxes = Array(20) { floatArrayOf(0.0f, 0.1f, 0.2f, 0.3f) }
        val classes = FloatArray(20) { 1f }
        val scores = FloatArray(20) { 0.9f }

        val result = invokePostprocess(boxes, classes, scores, 20f)

        assertTrue(result.size <= 10)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun invokePostprocess(
        boxes: Array<FloatArray>,
        classes: FloatArray,
        scores: FloatArray,
        numDetections: Float
    ): List<Detection> {
        val detector = ObjectDetector(mock<Context>())
        val method = detector::class.java.declaredMethods
            .first { it.name == "postprocess" }
            .also { it.isAccessible = true }
        return method.invoke(detector, boxes, classes, scores, numDetections) as List<Detection>
    }

    /**
     * Access the private COCO_LABELS field from the companion object.
     *
     * Since all companion members are private, Kotlin optimises the field
     * to a private static field on the enclosing ObjectDetector class
     * rather than on the Companion singleton.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveCocoLabels(): Array<String> {
        val field = ObjectDetector::class.java.getDeclaredField("COCO_LABELS")
        field.isAccessible = true
        return field.get(null) as Array<String>
    }
}
