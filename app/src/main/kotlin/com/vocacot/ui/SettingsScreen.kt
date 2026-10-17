package com.vocacot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vocacot.settings.AppSettings
import com.vocacot.settings.RecognitionLanguage
import com.vocacot.settings.UiLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetUiLanguage: (UiLanguage) -> Unit,
    onSetRecognitionLanguage: (RecognitionLanguage) -> Unit,
    onSetMonitoring: (Boolean) -> Unit,
    onSetMasterPhraseEnabled: (Boolean) -> Unit,
    onSetMasterPhrase: (String) -> Unit,
    onSetAutostartOnBoot: (Boolean) -> Unit,
    onSetSoundOnTrigger: (Boolean) -> Unit,
    onSetPreferOffline: (Boolean) -> Unit,
    onBackupFile: () -> Unit,
    onRestoreFile: () -> Unit,
    onBackupCloud: () -> Unit,
    onRestoreCloud: () -> Unit,
    onBack: () -> Unit,
    appVersion: String
) {
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            // --- Язык приложения (выпадающий список) ---
            SettingsHeader("Язык приложения")
            SettingsDropdown(
                label = "Язык интерфейса",
                selectedText = settings.uiLanguage.displayName,
                items = UiLanguage.entries.map { it.displayName },
                selectedIndex = settings.uiLanguage.ordinal,
                onSelect = { index -> onSetUiLanguage(UiLanguage.entries[index]) }
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // --- Распознавание речи ---
            SettingsHeader("Распознавание речи")
            SettingsDropdown(
                label = "Язык распознавания",
                selectedText = settings.recognitionLanguage.displayName,
                items = RecognitionLanguage.entries.map { it.displayName },
                selectedIndex = settings.recognitionLanguage.ordinal,
                onSelect = { index -> onSetRecognitionLanguage(RecognitionLanguage.entries[index]) }
            )
            SwitchRow(
                title = "Только офлайн-распознавание",
                subtitle = "Не отправлять аудио в сеть",
                checked = settings.preferOffline,
                onCheckedChange = onSetPreferOffline
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // --- Мониторинг ---
            SettingsHeader("Мониторинг")
            SwitchRow(
                title = "Слушать в фоне",
                subtitle = "Распознавание работает, даже когда приложение свёрнуто",
                checked = settings.monitoringEnabled,
                onCheckedChange = onSetMonitoring
            )
            SwitchRow(
                title = "Мастер-фраза",
                subtitle = "Реагировать только после специальной фразы",
                checked = settings.masterPhraseEnabled,
                onCheckedChange = onSetMasterPhraseEnabled
            )
            // Поле мастер-фразы — только если переключатель включён
            if (settings.masterPhraseEnabled) {
                OutlinedTextField(
                    value = settings.masterPhrase,
                    onValueChange = onSetMasterPhrase,
                    label = { Text("Введите мастер-фразу") },
                    placeholder = { Text("например: окей, помощник") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            SwitchRow(
                title = "Автозапуск при включении устройства",
                subtitle = "Мониторинг стартует после перезагрузки телефона",
                checked = settings.autostartOnBoot,
                onCheckedChange = onSetAutostartOnBoot
            )
            SwitchRow(
                title = "Сигнал при срабатывании",
                subtitle = "Звук, когда распознана мастер-фраза",
                checked = settings.soundOnTrigger,
                onCheckedChange = onSetSoundOnTrigger
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // --- Данные ---
            SettingsHeader("Резервная копия")
            ClickableRow("Сохранить в файл", onBackupFile)
            ClickableRow("Восстановить из файла", onRestoreFile)
            ClickableRow("Сохранить в облако", onBackupCloud)
            ClickableRow("Восстановить из облака", onRestoreCloud)

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            ClickableRow("О приложении") { showAbout = true }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Vocacot") },
            text = { Text("Голосовой помощник.\nВерсия: $appVersion") },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("OK") }
            }
        )
    }
}

/** Универсальный выпадающий список — переиспользуем для обоих языков */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    selectedText: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()   // в compose-material3 1.3+: menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ClickableRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}
