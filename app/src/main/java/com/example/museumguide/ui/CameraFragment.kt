package com.example.museumguide.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.museumguide.MainActivity
import com.example.museumguide.R
import com.example.museumguide.ai.AiTourGuideService
import com.example.museumguide.camera.CameraManager
import com.example.museumguide.databinding.FragmentCameraBinding
import com.example.museumguide.detection.ObjectDetector
import com.example.museumguide.exhibit.ExhibitRepository
import com.example.museumguide.model.ExhibitDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main camera fragment that handles:
 * 1. Camera preview via CameraX
 * 2. Real-time object detection via TensorFlow Lite
 * 3. 2-second dwell detection on recognised objects
 * 4. Displaying exhibit info overlay when an exhibit is identified
 * 5. Gallery image picker for testing detection on static images
 *
 * The dwell detection works by tracking the most recently detected label.
 * If the same label persists for ≥2 seconds (across consecutive frames),
 * the exhibit introduction is triggered.
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private var cameraManager: CameraManager? = null
    private var objectDetector: ObjectDetector? = null
    private lateinit var exhibitRepository: ExhibitRepository
    private var aiTourGuideService: AiTourGuideService? = null

    /** Tracks the currently recognised exhibit label for dwell detection. */
    private var currentDetectionLabel: String? = null
    /** Timestamp (System.nanoTime) when [currentDetectionLabel] was first seen. */
    private var detectionStartTime: Long = 0L
    /** Whether we have already triggered an introduction for this detection. */
    private var introductionTriggered = false

    /** Minimum milliseconds a label must persist before triggering. */
    private val dwellThresholdMs = 2000L

    /** Frame-skip counter — run detection every N frames for performance. */
    private var frameSkip = 0
    private val frameInterval = 3

    /** Whether the gallery review overlay is currently shown. */
    private var isGalleryMode = false

    /** Most recent camera frame bitmap for shutter capture. */
    private var lastBitmap: Bitmap? = null

    // Gallery image picker launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onGalleryImagePicked(uri)
        }
    }

    // Camera permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = ExhibitDatabase.getInstance(requireContext())
        exhibitRepository = ExhibitRepository(db.exhibitDao())

        // Initialise AI tour guide service (multi-provider)
        aiTourGuideService = AiTourGuideService(requireContext().applicationContext)

        // Seed sample data for demo
        lifecycleScope.launch {
            exhibitRepository.seedSampleData()
        }

        // Initialise TFLite detector
        objectDetector = ObjectDetector(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            val success = objectDetector?.initialize() ?: false
            if (!success && isAdded) {
                withContext(Dispatchers.Main) {
                    binding.scanHintText.text = "AI检测不可用（当前设备不兼容）\n请使用API 26+的设备"
                }
            }
        }

        // Check camera permission
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Gallery picker button
        binding.btnPickGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Shutter button — immediate detection on current frame
        binding.btnShutter.setOnClickListener {
            if (!isGalleryMode) {
                triggerImmediateDetection()
            }
        }

        // Close gallery overlay button
        binding.btnCloseGallery.setOnClickListener {
            closeGalleryMode()
        }

        // Read more button
        binding.btnReadMore.setOnClickListener {
            currentDetectionLabel?.let { label ->
                lifecycleScope.launch {
                    val exhibit = exhibitRepository.findExhibitByLabel(label)
                    if (exhibit != null) {
                        val bundle = Bundle().apply {
                            putLong("exhibit_id", exhibit.id)
                        }
                        findNavController().navigate(
                            R.id.action_camera_to_exhibit_detail,
                            bundle
                        )
                    }
                }
            }
        }
        // Initialise gallery overlay as hidden
        binding.galleryOverlay.visibility = View.GONE
    }

    /**
     * Initialise the camera and start the frame processing loop.
     */
    private fun startCamera() {
        cameraManager = CameraManager(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView
        )

        lifecycleScope.launch {
            val started = cameraManager?.startCamera() ?: false
            if (started) {
                startDetectionLoop()
            }
        }
    }

    /**
     * Listen for camera frames and run detection with frame skipping.
     */
    private fun startDetectionLoop() {
        cameraManager?.onFrameCaptured = { bitmap, _ ->
            lastBitmap = bitmap
            // Frame-skipping for performance
            if (frameSkip++ % frameInterval == 0) {
                processFrame(bitmap)
            }
        }
    }

    /**
     * Process a single camera frame: run detection and update dwell tracking.
     */
    private fun processFrame(bitmap: android.graphics.Bitmap) {
        if (isGalleryMode) return // Pause real-time detection during gallery review
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val detections = objectDetector?.detect(bitmap) ?: emptyList()
                withContext(Dispatchers.Main) {
                    handleDetectionResult(detections)
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraFragment", "Real-time detection failed", e)
            }
        }
    }

    /**
     * Evaluate detection results and update dwell timer.
     *
     * If no detection meets the threshold, reset the dwell timer.
     * If the same label persists past [dwellThresholdMs], trigger the intro.
     */
    private fun handleDetectionResult(
        detections: List<com.example.museumguide.detection.Detection>
    ) {
        val top = detections.firstOrNull()
        val now = System.nanoTime()

        if (top == null || top.confidence < 0.5f) {
            // Nothing detected — reset
            resetDwell()
            return
        }

        val label = top.label

        if (label == currentDetectionLabel) {
            // Same label still present — check dwell duration
            val elapsedMs = (now - detectionStartTime) / 1_000_000
            if (elapsedMs >= dwellThresholdMs && !introductionTriggered) {
                introductionTriggered = true
                onExhibitRecognised(label)
            }
        } else {
            // New label — start dwell timer
            currentDetectionLabel = label
            detectionStartTime = now
            introductionTriggered = false
        }
    }

    /**
     * Called when a label has been steadily detected for 2+ seconds.
     * First tries the local database; if no match is found, falls back
     * to AI-generated content via Gemini API.
     */
    private fun onExhibitRecognised(label: String) {
        lifecycleScope.launch {
            val exhibit = exhibitRepository.findExhibitByLabel(label)
            if (exhibit != null) {
                // Local exhibit found — show info and narrate
                showExhibitInfo(exhibit)
                startNarration(exhibit)
                // Concurrently fetch AI-enhanced narration (plays after delay)
                fetchAndPlayAiNarration(label)
            } else {
                // No local match — use AI to generate description on the fly
                showAiGeneratedContent(label)
            }
        }
    }

    /**
     * Fetch AI-generated narration in background and play it after
     * a brief delay so the local TTS plays first.
     */
    private suspend fun fetchAndPlayAiNarration(label: String) {
        val aiResponse = withContext(Dispatchers.IO) {
            aiTourGuideService?.generateDescription(label)
        }
        if (aiResponse != null && aiResponse.description.isNotBlank()) {
            delay(3000L) // Let local TTS play first
            if (isAdded) {
                (requireActivity() as? MainActivity)?.appendSpeak(aiResponse.description)
            }
        }
    }

    /**
     * No local exhibit matched — show a loading indicator, then populate
     * the overlay with AI-generated content and narrate it.
     */
    private suspend fun showAiGeneratedContent(label: String) {
        // Show loading state
        binding.exhibitInfoOverlay.visibility = View.VISIBLE
        binding.exhibitNameText.text = "识别中…"
        binding.exhibitBriefText.text = "正在获取AI导游讲解…"
        binding.scanHintText.visibility = View.GONE

        val aiResponse = withContext(Dispatchers.IO) {
            aiTourGuideService?.generateDescription(label)
        }

        if (aiResponse != null && isAdded) {
            binding.exhibitNameText.text = aiResponse.title
            binding.exhibitBriefText.text = aiResponse.brief

            val narrationText = buildString {
                append(aiResponse.description)
                if (aiResponse.significance.isNotBlank()) {
                    append("\n\n")
                    append(aiResponse.significance)
                }
            }
            (requireActivity() as? MainActivity)?.speak(narrationText)
        }
    }

    /**
     * Populate the bottom info overlay with exhibit details.
     */
    private fun showExhibitInfo(exhibit: com.example.museumguide.model.Exhibit) {
        binding.exhibitNameText.text = exhibit.name
        binding.exhibitEraText.text = exhibit.era
        binding.exhibitBriefText.text = exhibit.brief
        binding.exhibitInfoOverlay.visibility = View.VISIBLE
        binding.scanHintText.visibility = View.GONE
    }

    /**
     * Start text-to-speech narration for the exhibit.
     */
    private fun startNarration(exhibit: com.example.museumguide.model.Exhibit) {
        // Delegate to the TTS manager in the activity
        (requireActivity() as? com.example.museumguide.MainActivity)?.speak(exhibit.brief)
    }

    private fun resetDwell() {
        currentDetectionLabel = null
        detectionStartTime = 0L
        introductionTriggered = false
    }

    // ── Gallery image picker ──────────────────────────────────────────

    /**
     * Called when the user picks an image from the gallery.
     * Loads the image, tries AI multimodal recognition first,
     * and falls back to TFLite detection if AI is unavailable or fails.
     */
    private fun onGalleryImagePicked(uri: Uri) {
        isGalleryMode = true
        binding.galleryOverlay.visibility = View.VISIBLE
        binding.galleryResultText.visibility = View.GONE
        binding.galleryResultText.text = getString(R.string.gallery_processing)
        binding.loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Decode the selected image
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        binding.galleryResultText.text = "无法加载图片"
                        binding.galleryResultText.visibility = View.VISIBLE
                        binding.loadingIndicator.visibility = View.GONE
                    }
                    return@launch
                }

                // Show the image in the overlay
                withContext(Dispatchers.Main) {
                    binding.galleryImageView.setImageBitmap(bitmap)
                    binding.loadingIndicator.visibility = View.GONE
                    // Update UI: show AI processing text
                    binding.galleryResultText.text = getString(R.string.gallery_ai_processing)
                    binding.galleryResultText.visibility = View.VISIBLE
                }

                // Step 1: Try AI multimodal recognition first
                val hasApiKey = com.example.museumguide.BuildConfig.GEMINI_API_KEY.isNotBlank()
                if (hasApiKey) {
                    val aiResponse = aiTourGuideService?.generateDescriptionFromImage(bitmap)
                    if (aiResponse != null
                        && aiResponse.title != "识别失败"
                        && aiResponse.title != "识别出错"
                        && aiResponse.title != "图片识别"
                    ) {
                        withContext(Dispatchers.Main) {
                            binding.galleryResultText.text =
                                "${aiResponse.title} — ${aiResponse.brief}"
                        }
                        return@launch
                    }
                }

                // Step 2: Fallback to TFLite detection
                val detections = objectDetector?.detect(bitmap) ?: emptyList()

                withContext(Dispatchers.Main) {
                    if (detections.isNotEmpty()) {
                        val top = detections.first()
                        val confidencePercent = (top.confidence * 100).toInt()
                        binding.galleryResultText.text = getString(
                            R.string.gallery_result, top.label, confidencePercent
                        )
                    } else {
                        binding.galleryResultText.text = getString(R.string.gallery_no_detection)
                    }
                    binding.galleryResultText.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                android.util.Log.e("CameraFragment", "Gallery detection failed", e)
                withContext(Dispatchers.Main) {
                    binding.galleryResultText.text = "识别出错: ${e.message}"
                    binding.galleryResultText.visibility = View.VISIBLE
                    binding.loadingIndicator.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Close the gallery overlay and return to camera preview mode.
     */
    private fun closeGalleryMode() {
        isGalleryMode = false
        binding.galleryOverlay.visibility = View.GONE
        binding.galleryResultText.visibility = View.GONE
        binding.galleryImageView.setImageBitmap(null)
    }

    /**
     * Trigger immediate detection on the most recent camera frame.
     * This bypasses the 2-second dwell mechanism — useful for the shutter button.
     */
    private fun triggerImmediateDetection() {
        val bitmap = lastBitmap ?: return
        if (isGalleryMode) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val detections = objectDetector?.detect(bitmap) ?: emptyList()
                val top = detections.firstOrNull()
                if (top != null && top.confidence >= 0.5f) {
                    withContext(Dispatchers.Main) {
                        // Bypass dwell — directly trigger recognition
                        currentDetectionLabel = top.label
                        detectionStartTime = System.nanoTime()
                        introductionTriggered = false
                        onExhibitRecognised(top.label)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "未检测到物体，请对准展品", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraFragment", "Shutter detection failed", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        objectDetector?.close()
        cameraManager?.shutdown()
        _binding = null
    }
}
