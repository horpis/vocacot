package com.vocacot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.media.AudioManager
// ВАЖНО: Если ваша тема называется TestaTheme, замените VocacotTheme на TestaTheme ниже
import com.vocacot.ui.theme.VocacotTheme 

class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening by mutableStateOf(false)
    private var recognizedText by mutableStateOf("")
    private var statusText by mutableStateOf("Нажмите кнопку для старта")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            statusText = "Нет разрешения на микрофон"
            Toast.makeText(this, "Требуется разрешение на микрофон", Toast.LENGTH_SHORT).show()
        }
    }
    
// BroadcastReceiver для отслеживания гарнитуры
    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Гарнитура отключена
                    if (isListening) {
                        statusText = "Гарнитура отключена, перезапуск..."
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isListening) startListening()
                        }, 500)
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    // Гарнитура подключена/отключена
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1 && isListening) {
                        // Гарнитура подключена
                        statusText = "Гарнитура подключена, перезапуск..."
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isListening) startListening()
                        }, 500)
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
           // Регистрация BroadcastReceiver
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        registerReceiver(audioReceiver, filter)

        // Защита от краша при инициализации распознавателя
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            setupRecognitionListener()
        } catch (e: Exception) {
            statusText = "Ошибка инициализации: ${e.message}"
        }

        setContent {
            VocacotTheme { // <-- Убедитесь, что имя совпадает с вашим Theme.kt (или TestaTheme)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceControlScreen(
                        recognizedText = recognizedText,
                        statusText = statusText,
                        isListening = isListening,
                        onToggleListening = { toggleListening() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Если распознавание было активно, перезапускаем его
        if (isListening) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (isListening) startListening()
            }, 300)
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText = "Слушаю..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                statusText = "Обработка..."
            }

            override fun onError(error: Int) {
                statusText = "Ошибка распознавания: $error"
                // Перезапуск только если это не фатальная ошибка занятости
                if (isListening && error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY&&
        error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isListening) startListening()
                    }, 500)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                recognizedText = text
                statusText = "Распознано"
                
                if (isListening) startListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) recognizedText = text
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun toggleListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            statusText = "Остановлено"
        } else {
            checkPermissionAndStart()
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startListening()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
            isListening = true
            statusText = "Запуск..."
        } catch (e: Exception) {
            statusText = "Ошибка запуска: ${e.message}"
            isListening = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        try {
            unregisterReceiver(audioReceiver)
        } catch (e: Exception) {
            // Receiver может быть не зарегистрирован
        }
    }
}

@Composable
fun VoiceControlScreen(
    recognizedText: String,
    statusText: String,
    isListening: Boolean,
    onToggleListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = recognizedText.ifEmpty { "..." },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )
        Button(
            onClick = onToggleListening,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isListening) "Остановить" else "Слушать")
        }
    }
}