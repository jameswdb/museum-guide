package com.example.museumguide

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.museumguide.databinding.ActivityMainBinding
import java.util.Locale

/**
 * Main entry point for the Museum Guide application.
 *
 * Hosts the camera/map navigation via a bottom navigation bar and
 * provides text-to-speech narration functionality shared across fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialise TTS engine
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.CHINESE
                textToSpeech?.setSpeechRate(1.0f) // Normal speed for Chinese narration
            }
        }

        // Set up navigation with bottom bar
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    /**
     * Speak a text string via TTS. Called from fragments when
     * an exhibit is recognised. Interrupts any current narration.
     */
    fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Append text to the TTS queue without interrupting current speech.
     * Used for AI-enhanced narration that plays after local content.
     */
    fun appendSpeak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    /**
     * Stop any ongoing TTS narration.
     */
    fun silence() {
        textToSpeech?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
