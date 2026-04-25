package com.example.museumguide.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps a TensorFlow Lite model for on-device object detection.
 *
 * The default model is a lightweight MobileNet SSD trained on the COCO dataset.
 * For production use, this should be fine-tuned on museum exhibit images.
 *
 * Detection pipeline:
 * 1. Preprocess: resize & normalise the camera frame to model input size
 * 2. Run inference via TFLite Interpreter
 * 3. Postprocess: filter detections by confidence threshold
 *
 * All inference runs on the calling thread — call from a background coroutine.
 */
class ObjectDetector(
    private val context: Context,
    private val modelFileName: String = "detect.tflite"
) {
    companion object {
        private const val DEFAULT_INPUT_SIZE = 300
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val MAX_DETECTIONS = 10

        /** Labels for the COCO-based detection model. */
        private val COCO_LABELS = arrayOf(
            "background", "person", "bicycle", "car", "motorcycle", "airplane",
            "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "N/A", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep",
            "cow", "elephant", "bear", "zebra", "giraffe",
            "N/A", "backpack", "umbrella", "N/A", "N/A",
            "handbag", "tie", "suitcase", "frisbee", "skis",
            "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "N/A",
            "wine glass", "cup", "fork", "knife", "spoon",
            "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut",
            "cake", "chair", "couch", "potted plant", "bed",
            "N/A", "dining table", "N/A", "N/A", "toilet",
            "N/A", "tv", "laptop", "mouse", "remote",
            "keyboard", "cell phone", "microwave", "oven", "toaster",
            "sink", "refrigerator", "N/A", "book", "clock",
            "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }

    private var interpreter: Interpreter? = null
    private val inputSize: Int = DEFAULT_INPUT_SIZE
    private var isInitialised = AtomicBoolean(false)

    /** Load the TFLite model from assets. */
    fun initialize(): Boolean {
        if (isInitialised.get()) return true
        try {
            val modelBytes = context.assets.open(modelFileName).use { inputStream ->
                inputStream.readBytes()
            }
            val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            byteBuffer.order(ByteOrder.nativeOrder())
            byteBuffer.put(modelBytes)
            interpreter = Interpreter(byteBuffer)
            isInitialised.set(true)
            return true
        } catch (e: Throwable) {
            android.util.Log.e("ObjectDetector", "Failed to initialise TFLite: ${e.message}", e)
            isInitialised.set(false)
            return false
        }
    }

    /**
     * Run detection on a camera frame.
     *
     * @param bitmap Input camera frame
     * @return List of [Detection] sorted by confidence descending
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        if (!isInitialised.get()) return emptyList()

        val tfImage = preprocessBitmap(bitmap)
        val output = Array(1) {
            Array(MAX_DETECTIONS) { FloatArray(4) } // bounding boxes
        }
        val outputClasses = Array(1) { FloatArray(MAX_DETECTIONS) }
        val outputScores = Array(1) { FloatArray(MAX_DETECTIONS) }
        val numDetections = FloatArray(1)

        // Use explicit HashMap instead of mapOf() to avoid Kotlin type inference
        // issues that cause TFLite to misinterpret FloatArray shape [1] as [1, 1].
        val outputMap = HashMap<Int, Any>()
        outputMap[0] = output
        outputMap[1] = outputClasses
        outputMap[2] = outputScores
        outputMap[3] = numDetections

        try {
            interpreter?.runForMultipleInputsOutputs(
                arrayOf(tfImage), outputMap
            )
        } catch (e: Throwable) {
            android.util.Log.e("ObjectDetector", "TFLite inference failed: ${e.message}", e)
            return emptyList()
        }

        return postprocess(output[0], outputClasses[0], outputScores[0], numDetections[0])
    }

    /**
     * Resize the camera frame to the model input size and convert
     * to the quantised byte buffer that TFLite MobileNet SSD expects.
     *
     * The quantized model expects uint8 RGB values in [1, 300, 300, 3] layout,
     * which is 270000 bytes total.
     */
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            // Extract RGB channels (pixel is ARGB_8888)
            byteBuffer.put((pixel shr 16 and 0xFF).toByte()) // R
            byteBuffer.put((pixel shr 8 and 0xFF).toByte())  // G
            byteBuffer.put((pixel and 0xFF).toByte())         // B
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun postprocess(
        boxes: Array<FloatArray>,
        classes: FloatArray,
        scores: FloatArray,
        numDetections: Float
    ): List<Detection> {
        val results = mutableListOf<Detection>()
        val count = minOf(numDetections.toInt(), MAX_DETECTIONS)
        for (i in 0 until count) {
            if (scores[i] < CONFIDENCE_THRESHOLD) continue
            val labelIndex = classes[i].toInt()
            val label = if (labelIndex in COCO_LABELS.indices) {
                COCO_LABELS[labelIndex]
            } else {
                "unknown"
            }
            results.add(
                Detection(
                    label = label,
                    confidence = scores[i],
                    boundingBox = RectF(
                        boxes[i][1], boxes[i][0], boxes[i][3], boxes[i][2]
                    )
                )
            )
        }
        return results.sortedByDescending { it.confidence }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialised.set(false)
    }
}

/**
 * Result from a single object detection inference.
 */
data class Detection(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)
