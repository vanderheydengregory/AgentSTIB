package com.stib.agent.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.result.ActivityResultLauncher
import java.util.Locale

object SpeechRecognitionUtil {

    fun startSpeechRecognition(
        context: Context,
        launcher: ActivityResultLauncher<Intent>
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            android.util.Log.e("SpeechRecognition", "❌ Reconnaissance vocale non disponible")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH.language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites quelque chose...")
        }

        launcher.launch(intent)
    }
}
