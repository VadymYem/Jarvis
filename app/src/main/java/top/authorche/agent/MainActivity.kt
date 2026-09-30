package top.authorche.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.authorche.agent.accessibility.AgentAccessibilityService
import top.authorche.agent.agent.AgentTraceEntry
import top.authorche.agent.agent.PendingConfirmation
import top.authorche.agent.shizuku.ShizukuBridge
import top.authorche.agent.shizuku.ShizukuState
import top.authorche.agent.ui.AuthorTheme
import top.authorche.agent.provider.ProviderKind
import top.authorche.agent.provider.SearchProvider
import top.authorche.agent.provider.SearchSettings
import top.authorche.agent.voice.VoiceSession

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var voice: VoiceSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceSession(this, viewModel::run)
        enableEdgeToEdge()
        setContent {
            AuthorTheme {
                AuthorAgentScreen(
                    viewModel = viewModel,
                    voice = voice,
                    openAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        voice.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorAgentScreen(
    viewModel: MainViewModel,
    voice: VoiceSession,
    openAccessibility: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shizukuState by ShizukuBridge.state.collectAsStateWithLifecycle()
    var accessibilityReady by remember { mutableStateOf(AgentAccessibilityService.isConnected()) }
    var task by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSearchSettings by remember { mutableStateOf(false) }
    var folderStatus by remember { mutableStateOf(viewModel.searchFolder()) }
    val modelImportStatus by viewModel.modelImportStatus.collectAsStateWithLifecycle()
    val findPhoneStatus by viewModel.findPhoneStatus.collectAsStateWithLifecycle()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importLocalModel) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { folderStatus = viewModel.saveSearchFolder(it) } }
    val songPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFindPhoneSong) }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) voice.start() }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state) { voice.onAgentState(state) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        while (true) {
            accessibilityReady = AgentAccessibilityService.isConnected()
            ShizukuBridge.refresh()
            delay(1_000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BrandHeader() }
            item {
                CapabilityCard(
                    accessibilityReady = accessibilityReady,
                    shizukuState = shizukuState,
                    onAccessibility = openAccessibility,
                    onShizuku = ShizukuBridge::requestPermission,
                )
            }
            item {
                AgentCapabilities(viewModel.capabilities.map { it.name to it.description }) { task = it }
            }
            item {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Вибрати теку для локального пошуку") }
                Text(folderStatus, style = MaterialTheme.typography.bodySmall)
            }
            item {
                OutlinedButton(onClick = { songPicker.launch(arrayOf("audio/mpeg", "audio/*")) }) {
                    Text("Звук пошуку телефону · вибрати MP3")
                }
                findPhoneStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                VoiceCard(
                    voice = voice,
                    onStart = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                            voice.start()
                        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }
            item {
                TaskComposer(
                    task = task,
                    onTaskChange = { task = it },
                    running = state.running,
                    onRun = { viewModel.run(task) },
                    onSettings = { showSettings = true },
                    onSearch = { showSearchSettings = true },
                )
            }
            item {
                StatusCard(
                    status = state.status,
                    running = state.running,
                    answer = state.finalAnswer,
                    error = state.error,
                    onReset = viewModel::reset,
                    onStop = viewModel::stop,
                )
            }
            if (state.trace.isNotEmpty()) {
                item {
                    Text(
                        text = "Хід виконання",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(state.trace) { entry -> TraceItem(entry) }
            }
            item { AuthorFooter() }
        }
    }

    state.confirmation?.let {
        ConfirmationDialog(
            confirmation = it,
            onApprove = { viewModel.confirm(true) },
            onReject = { viewModel.confirm(false) },
        )
    }
    if (showSettings) {
        ProviderSettingsDialog(
            initial = viewModel.loadSettings(),
            importStatus = modelImportStatus,
            loadProvider = viewModel::loadSettings,
            onDismiss = { showSettings = false },
            onImportModel = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
            onSave = { provider, endpoint, model, key ->
                viewModel.saveSettings(provider, endpoint, model, key)
                showSettings = false
            },
        )
    }
    if (showSearchSettings) {
        SearchSettingsDialog(
            initial = viewModel.searchSettings(),
            onDismiss = { showSearchSettings = false },
            onSave = { provider, url, key ->
                viewModel.saveSearch(provider, url, key)
                showSearchSettings = false
            },
        )
    }
}

@Composable
private fun AgentCapabilities(tools: List<Pair<String, String>>, onExample: (String) -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Дії агента · ${tools.size} інструментів", fontWeight = FontWeight.SemiBold)
            tools.forEach { (name, description) ->
                Text("• $description ($name)", style = MaterialTheme.typography.bodySmall)
            }
            Text("Спробуйте команду", fontWeight = FontWeight.Medium)
            listOf("Знайди на телефоні Telegram", "Знайди у Вікіпедії історію Києва",
                "Відкрий календар", "Який заряд телефону?",
                "Знайди мій телефон", "Нагадай мені про зустріч через 30 хвилин")
                .forEach { example -> AssistChip(onClick = { onExample(example) }, label = { Text(example) }) }
        }
    }
}

@Composable
private fun VoiceCard(voice: VoiceSession, onStart: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Жива голосова розмова", fontWeight = FontWeight.SemiBold)
            Text(if (voice.onDevice) "Системне локальне розпізнавання доступне"
                else "Системне розпізнавання; офлайн залежить від установленого рушія",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("uk-UA", "en-US", "de-DE").forEach { locale ->
                    FilterChip(selected = voice.language == locale,
                        onClick = { voice.selectLanguage(locale) }, label = { Text(locale.substring(0, 2).uppercase()) })
                }
            }
            Text(voice.status, color = MaterialTheme.colorScheme.primary)
            if (voice.transcript.isNotBlank()) Text("Ви: ${voice.transcript}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (voice.active) {
                    Button(onClick = voice::listenAgain) { Text("Слухати / перервати") }
                    OutlinedButton(onClick = voice::stop) { Text("Завершити") }
                } else Button(onClick = onStart) { Text("Почати розмову") }
            }
        }
    }
}

@Composable
private fun SearchSettingsDialog(
    initial: SearchSettings,
    onDismiss: () -> Unit,
    onSave: (SearchProvider, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf(initial.provider) }
    var url by remember { mutableStateOf(initial.searxngUrl) }
    var key by remember { mutableStateOf(initial.braveKey) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Джерело пошуку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchProvider.entries.forEach { choice ->
                    FilterChip(selected = choice == provider, onClick = { provider = choice },
                        label = { Text(choice.title) })
                }
                Text("Вікіпедія шукає статті, GitHub — репозиторії. Для загального вебпошуку підключіть SearXNG або Brave API.",
                    style = MaterialTheme.typography.bodySmall)
                if (provider == SearchProvider.SEARXNG) OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("HTTPS URL SearXNG") })
                if (provider == SearchProvider.BRAVE) OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("Brave Search API ключ") },
                    visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = {
            runCatching { onSave(provider, url, key) }.onFailure { error = it.message }
        }) { Text("Зберегти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

@Composable
private fun BrandHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "J✦",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.padding(start = 13.dp)) {
                Text(
                    text = "Jarvis | AuthorAI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "by AuthorChe · Ваш приватний агент дій",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Технології можуть бути красивими, коли в них є душа.",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CapabilityCard(
    accessibilityReady: Boolean,
    shizukuState: ShizukuState,
    onAccessibility: () -> Unit,
    onShizuku: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Можливості пристрою", fontWeight = FontWeight.SemiBold)
            CapabilityRow(
                title = "Керування інтерфейсом",
                ready = accessibilityReady,
                detail = if (accessibilityReady) "Accessibility підключено" else "Потрібно ввімкнути службу",
                action = if (accessibilityReady) null else "Увімкнути",
                onAction = onAccessibility,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            CapabilityRow(
                title = "Розширені дії ADB",
                ready = shizukuState == ShizukuState.READY,
                detail = when (shizukuState) {
                    ShizukuState.READY -> "Shizuku готовий · UID ${ShizukuBridge.uid() ?: 2000}"
                    ShizukuState.CONNECTING -> "Підключення…"
                    ShizukuState.PERMISSION_REQUIRED -> "Потрібен дозвіл Shizuku"
                    ShizukuState.UNAVAILABLE -> "Запустіть Shizuku на пристрої"
                },
                action = if (shizukuState == ShizukuState.READY) null else "Підключити",
                onAction = onShizuku,
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    ready: Boolean,
    detail: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(
                    if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    CircleShape,
                ),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun TaskComposer(
    task: String,
    onTaskChange: (String) -> Unit,
    running: Boolean,
    onRun: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Що потрібно зробити?", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = task,
                onValueChange = onTaskChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                minLines = 3,
                maxLines = 7,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Наприклад: відкрий календар і нагадай мені подзвонити Андреасу через 30 хвилин") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRun,
                    enabled = task.isNotBlank() && !running,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (running) "Виконую…" else "Виконати")
                }
                OutlinedButton(onClick = onSettings, enabled = !running) {
                    Text("Модель")
                }
            }
            OutlinedButton(onClick = onSearch, enabled = !running) { Text("Пошуковики") }
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    running: Boolean,
    answer: String?,
    error: String?,
    onReset: () -> Unit,
    onStop: () -> Unit,
) {
    AnimatedVisibility(running || answer != null || error != null) {
        Surface(
            color = if (error == null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(status, fontWeight = FontWeight.SemiBold)
                }
                answer?.let { Text(it, Modifier.padding(top = 10.dp)) }
                error?.let { Text(it, Modifier.padding(top = 10.dp)) }
                if (!running) {
                    TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                        Text("Нове завдання")
                    }
                } else {
                    TextButton(onClick = onStop, modifier = Modifier.align(Alignment.End)) {
                        Text("Зупинити агента")
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceItem(entry: AgentTraceEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            val color = when (entry.successful) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.secondary
            }
            Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, CircleShape))
            Column(Modifier.padding(start = 12.dp)) {
                Text(entry.title, fontWeight = FontWeight.Medium)
                Text(
                    entry.detail,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    confirmation: PendingConfirmation,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Підтвердьте дію") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(confirmation.summary)
                Text(
                    "Рівень ризику: ${confirmation.risk.ukrainianName()}",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Дозволити") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Відхилити") } },
    )
}

@Composable
private fun ProviderSettingsDialog(
    initial: top.authorche.agent.provider.ProviderSettings,
    importStatus: String?,
    loadProvider: (ProviderKind) -> top.authorche.agent.provider.ProviderSettings,
    onDismiss: () -> Unit,
    onImportModel: () -> Unit,
    onSave: (ProviderKind, String, String, String) -> Unit,
) {
    var provider by remember { mutableStateOf(initial.provider) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Модель AuthorAI") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Оберіть локальну GGUF-модель, Gemini або OpenAI-сумісний API. Ключі шифруються Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProviderKind.entries.forEach { item ->
                        FilterChip(
                            selected = item == provider,
                            onClick = {
                                provider = item
                                loadProvider(item).also { saved ->
                                    endpoint = saved.endpoint
                                    model = saved.model
                                    key = saved.apiKey
                                }
                                error = null
                            },
                            label = { Text(item.title) },
                        )
                    }
                }
                if (provider == ProviderKind.LOCAL_GGUF) {
                    Text(
                        text = initial.localModelPath.substringAfterLast('/').ifBlank {
                            "GGUF-модель ще не імпортована"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onImportModel, modifier = Modifier.fillMaxWidth()) {
                        Text("Імпортувати .gguf")
                    }
                    importStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API endpoint") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Модель") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API-ключ") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching { onSave(provider, endpoint, model, key) }
                    .onFailure { error = it.message }
            }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

private fun top.authorche.agent.agent.RiskLevel.ukrainianName(): String = when (this) {
    top.authorche.agent.agent.RiskLevel.LOW -> "низький"
    top.authorche.agent.agent.RiskLevel.MEDIUM -> "середній"
    top.authorche.agent.agent.RiskLevel.HIGH -> "високий"
    top.authorche.agent.agent.RiskLevel.CRITICAL -> "критичний"
}

@Composable
private fun AuthorFooter() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Створено Автором",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "AuthorChe · поет · музикант · розробник",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
