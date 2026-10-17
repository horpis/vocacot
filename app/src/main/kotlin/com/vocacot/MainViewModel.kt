package com.vocacot

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vocacot.settings.AppSettings
import com.vocacot.settings.RecognitionLanguage
import com.vocacot.settings.SettingsRepository
import com.vocacot.settings.UiLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceUiState(
    val recognizedText: String = "",
    val statusText: String = "Нажмите кнопку для старта",
    val isListening: Boolean = false,
    val hasPermission: Boolean = false,
    val needInstallRecognizer: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    val settingsRepository = SettingsRepositoryProvider.get(context)

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var sessionId = 0

    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY ->
                    onHeadsetChanged("Гарнитура отключена, перезапуск...")
                Intent.ACTION_HEADSET_PLUG -> {
                    if (intent.getIntExtra("state", -1) == 1) {
                        onHeadsetChanged("Гарнитура подключена, перезапуск...")
                    }
                }
            }
        }
    }

    private fun onHeadsetChanged(message: String) {
        if (_uiState.value.isListening) {
            _uiState.update { it.copy(statusText = message) }
            scheduleRestart()
        }
    }

    // ---------- Состояние распознавания ----------

    fun checkPermission() {
        _uiState.update {
            it.copy(
                hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
        } else {
            checkPermission()
            if (_uiState.value.hasPermission) {
                startListening()
            } else {
                _uiState.update { it.copy(statusText = "Нет разрешения на микрофон") }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) startListening()
        else _uiState.update { it.copy(statusText = "Нет разрешения на микрофон") }
    }

    fun dismissInstallPrompt() {
        _uiState.update { it.copy(needInstallRecognizer = false) }
    }

    // ---------- Настройки ----------

    fun setUiLanguage(lang: UiLanguage) = viewModelScope.launch {
        settingsRepository.setUiLanguage(lang)
        val locales = if (lang.tag == "system") LocaleListCompat.getEmptyLocaleList()
                      else LocaleListCompat.forLanguageTags(lang.tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setRecognitionLanguage(lang: RecognitionLanguage) = viewModelScope.launch {
        settingsRepository.setRecognitionLanguage(lang)
    }

    fun setMonitoringEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMonitoringEnabled(enabled)
        MonitoringService.setEnabled(context, enabled)
    }

    fun setMasterPhraseEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMasterPhraseEnabled(enabled)
    }

    fun setMasterPhrase(phrase: String) = viewModelScope.launch {
        settingsRepository.setMasterPhrase(phrase)
    }

    fun setAutostartOnBoot(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutostartOnBoot(enabled)
    }

    fun setSoundOnTrigger(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSoundOnTrigger(enabled)
    }

    fun setPreferOffline(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setPreferOffline(enabled)
    }

    // ---------- Распознавание ----------

    private fun isSpeechServiceAvailable(): Boolean {
        val recognizerOk = SpeechRecognizer.isRecognitionAvailable(context)
        val intentOk = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .resolveActivity(context.packageManager) != null
        return recognizerOk && intentOk
    }

    private fun startListening() {
        restartJob?.cancel()
        sessionId++

        if (speechRecognizer == null) {
            if (!isSpeechServiceAvailable()) {
                _uiState.update {
                    it.copy(
                        isListening = false,
                        statusText = "Сервис распознавания речи не установлен",
                        needInstallRecognizer = true
                    )
                }
                return
            }

            speechRecognizer = try {
                SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(recognitionListener)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isListening = false, statusText = "Ошибка инициализации: ${e.message}")
                }
                return
            }
        }

        try {
            val currentSettings = settings.value
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentSettings.recognitionLanguage.tag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (currentSettings.preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            speechRecognizer?.startListening(intent)
            _uiState.update { it.copy(isListening = true, statusText = "Запуск...") }
        } catch (e: Exception) {
            speechRecognizer?.destroy()
            speechRecognizer = null
            _uiState.update {
                it.copy(
                    isListening = false,
                    statusText = "Сервис распознавания не отвечает",
                    needInstallRecognizer = true
                )
            }
        }
    }

    private fun stopListening() {
        restartJob?.cancel()
        sessionId++
        speechRecognizer?.stopListening()
        _uiState.update { it.copy(isListening = false, statusText = "Остановлено") }
    }

    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        val mySession = sessionId
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(delayMs)
            if (mySession == sessionId && _uiState.value.isListening) {
                startListening()
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _uiState.update { it.copy(statusText = "Слушаю...") }
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _uiState.update { it.copy(statusText = "Обработка...") }
        }

        override fun onError(error: Int) {
            _uiState.update { it.copy(statusText = "Ошибка распознавания: $error") }
            if (error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY &&
                error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            ) {
                scheduleRestart()
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotEmpty()) {
                _uiState.update { it.copy(recognizedText = text) }
            }
            _uiState.update { it.copy(statusText = "Распознано") }
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotEmpty()) {
                _uiState.update { it.copy(recognizedText = text) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        context.registerReceiver(audioReceiver, filter)
    }

    fun unregisterHeadsetReceiver() {
        try {
            context.unregisterReceiver(audioReceiver)
        } catch (e: Exception) {
            // Receiver не был зарегистрирован
        }
    }

    override fun onCleared() {
        restartJob?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onCleared()
    }

    companion object {
        private const val RESTART_DELAY_MS = 300L
    }
}
