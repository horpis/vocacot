package com.vocacot

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import com.vocacot.ui.SettingsScreen
import com.vocacot.ui.theme.VocacotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val VOICE = "voice"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

        setContent {
            VocacotTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                // Применяем сохранённый язык интерфейса ДО отрисовки
                LaunchedEffect(Unit) {
                    val tag = vm.settings.first().uiLanguage.tag
                    val locales = if (tag == "system") LocaleListCompat.getEmptyLocaleList()
                                  else LocaleListCompat.forLanguageTags(tag)
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> vm.onPermissionResult(granted) }

                // Receiver гарнитуры — только пока экран показан
                DisposableEffect(Unit) {
                    vm.registerHeadsetReceiver()
                    onDispose { vm.unregisterHeadsetReceiver() }
                }

                // Диалог "установите Google"
                if (state.needInstallRecognizer) {
                    AlertDialog(
                        onDismissRequest = { vm.dismissInstallPrompt() },
                        title = { Text("Распознавание речи недоступно") },
                        text = {
                            Text("Для работы голосового ввода требуется приложение «Google». Установить его из Play Store?")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                vm.dismissInstallPrompt()
                                openSpeechServiceInstall()
                            }) { Text("Установить") }
                        },
                        dismissButton = {
                            TextButton(onClick = { vm.dismissInstallPrompt() }) { Text("Отмена") }
                        }
                    )
                }

                NavHost(navController = navController, startDestination = Routes.VOICE) {

                    composable(Routes.VOICE) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Vocacot") },
                                    actions = {
                                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            VoiceControlScreen(
                                state = state,
                                onToggleListening = {
                                    if (!state.hasPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        vm.toggleListening()
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }

                    composable(Routes.SETTINGS) {
                        val settings by vm.settings.collectAsStateWithLifecycle()
                        SettingsScreen(
                            settings = settings,
                            onSetUiLanguage = vm::setUiLanguage,
                            onSetRecognitionLanguage = vm::setRecognitionLanguage,
                            onSetMonitoring = vm::setMonitoringEnabled,
                            onSetMasterPhraseEnabled = vm::setMasterPhraseEnabled,
                            onSetMasterPhrase = vm::setMasterPhrase,
                            onSetAutostartOnBoot = vm::setAutostartOnBoot,
                            onSetSoundOnTrigger = vm::setSoundOnTrigger,
                            onSetPreferOffline = vm::setPreferOffline,
                            onBackupFile = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup в файл — следующий шаг",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRestoreFile = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Restore из файла — следующий шаг",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onBackupCloud = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Облако: подключим Firebase следующим шагом",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onRestoreCloud = {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Облако: подключим Firebase следующим шагом",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onBack = { navController.popBackStack() },
                            appVersion = appVersion
                        )
                    }
                }
            }
        }
    }

    /** Открывает страницу приложения «Google» в Play Store */
    private fun openSpeechServiceInstall() {
        val packageName = "com.google.android.googlequicksearchbox"
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                setPackage("com.android.vending")
            })
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            })
        }
    }
}

@Composable
fun VoiceControlScreen(
    state: VoiceUiState,
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
            text = state.statusText,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = state.recognizedText.ifEmpty { "..." },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )
        Button(
            onClick = onToggleListening,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isListening) "Остановить" else "Слушать")
        }
    }
}
