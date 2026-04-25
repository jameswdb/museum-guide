package com.example.museumguide.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

/**
 * Wraps CameraX lifecycle management and provides camera frames
 * as Bitmaps for on-device object detection.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    companion object {
        private const val ASPECT_RATIO_WIDTH = 4
        private const val ASPECT_RATIO_HEIGHT = 3
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLens = CameraSelector.LENS_FACING_BACK

    /** Callback receiving each camera frame as a Bitmap for detection. */
    var onFrameCaptured: ((Bitmap, Int) -> Unit)? = null

    /**
     * Start the camera preview and image analysis pipeline.
     * Must be called after the PreviewView is laid out.
     */
    suspend fun startCamera(): Boolean = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { proxy ->
                            proxyToBitmap(proxy)?.let { bitmap ->
                                onFrameCaptured?.invoke(bitmap, proxy.imageInfo.rotationDegrees)
                            }
                            proxy.close()
                        }
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(currentLens)
                    .build()

                provider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, analysis, imageCapture
                )
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Convert YUV_420_888 ImageProxy to a rotated RGB Bitmap.
     *
     * The YuvImage class expects NV21 format. CameraX delivers YUV_420_888 which
     * stores Y, U, V in three separate planes. Each plane may have its own
     * row stride and pixel stride, so we must account for those when copying.
     */
    private fun proxyToBitmap(proxy: ImageProxy): Bitmap? {
        val width = proxy.width
        val height = proxy.height

        // Planes: 0=Y, 1=U (Cb), 2=V (Cr)
        val yPlane = proxy.planes[0]
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]

        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        // Preserve buffer positions
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)

        // Copy Y plane accounting for row stride
        var yOffset = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            val remaining = min(yBuffer.remaining(), width)
            yBuffer.get(nv21, yOffset, remaining)
            yOffset += width
        }

        // Copy V and U planes interleaved as NV21 (VU order)
        val uvWidth = width / 2
        val uvHeight = height / 2
        var uvOffset = width * height
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                // V (Cr) at even positions
                val vPos = row * vRowStride + col * vPixelStride
                nv21[uvOffset] = if (vPos < vBuffer.capacity()) vBuffer.get(vPos) else 0
                // U (Cb) at odd positions
                val uPos = row * uRowStride + col * uPixelStride
                nv21[uvOffset + 1] = if (uPos < uBuffer.capacity()) uBuffer.get(uPos) else 0
                uvOffset += 2
            }
        }

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 85, out)
        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // Rotate based on device orientation
        return bitmap?.let {
            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
        }
    }

    fun shutdown() {
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
