package top.authorche.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.role.RoleManager
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import top.authorche.agent.accessibility.AgentAccessibilityService
import top.authorche.agent.agent.AgentTraceEntry
import top.authorche.agent.agent.PendingConfirmation
import top.authorche.agent.connectors.ConnectorRegistry
import top.authorche.agent.connectors.ConnectorState
import top.authorche.agent.connectors.ConnectorStatus
import top.authorche.agent.shizuku.ShizukuBridge
import top.authorche.agent.shizuku.ShizukuState
import top.authorche.agent.termux.TermuxBridge
import top.authorche.agent.ui.AuthorTheme
import top.authorche.agent.provider.ProviderKind
import top.authorche.agent.voice.VoiceRecognitionController
import top.authorche.agent.voice.VoiceRecognitionState
import top.authorche.agent.voice.VoiceProfile

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val voiceInvocation = MutableStateFlow(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_ASSIST) voiceInvocation.value += 1
        enableEdgeToEdge()
        setContent {
            AuthorTheme {
                AuthorAgentScreen(
                    viewModel = viewModel,
                    openAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    requestAssistantRole = ::requestAssistantRole,
                    voiceInvocation = voiceInvocation,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST) voiceInvocation.value += 1
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                !roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)
            ) {
                startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                return
            }
        }
        startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorAgentScreen(
    viewModel: MainViewModel,
    openAccessibility: () -> Unit,
    requestAssistantRole: () -> Unit,
    voiceInvocation: StateFlow<Long>,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shizukuState by ShizukuBridge.state.collectAsStateWithLifecycle()
    var accessibilityReady by remember { mutableStateOf(AgentAccessibilityService.isConnected()) }
    var task by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }
    var voiceRecognition by remember { mutableStateOf(VoiceRecognitionState()) }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var pendingPhoneFind by remember { mutableStateOf(false) }
    val modelImportStatus by viewModel.modelImportStatus.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val voiceInvocationId by voiceInvocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var emailConnector by remember(context) { mutableStateOf(ConnectorRegistry.email(context)) }
    var termuxConnector by remember(context) { mutableStateOf(ConnectorRegistry.termux(context)) }

    val voiceController = remember(context, viewModel) {
        VoiceRecognitionController(
            context = context,
            onState = { voiceRecognition = it },
            onResult = { recognized ->
                task = recognized
                viewModel.handleVoiceCommand(recognized)
            },
        )
    }
    DisposableEffect(voiceController) {
        onDispose { voiceController.close() }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val termuxPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        termuxConnector = ConnectorRegistry.termux(context)
    }
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importLocalModel) }
    val voiceReferencePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::setVoiceReference) }
    val finderTrackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::setFindPhoneTrack) }
    val voicePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (pendingPhoneFind) viewModel.findPhone()
        if (
            pendingVoiceStart &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            voiceController.start()
        }
        pendingPhoneFind = false
        pendingVoiceStart = false
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        while (true) {
            accessibilityReady = AgentAccessibilityService.isConnected()
            ShizukuBridge.refresh()
            emailConnector = ConnectorRegistry.email(context)
            termuxConnector = ConnectorRegistry.termux(context)
            delay(1_000)
        }
    }
    LaunchedEffect(voiceInvocationId) {
        if (voiceInvocationId <= 0) return@LaunchedEffect
        val missing = requiredVoicePermissions().filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            voiceController.start()
        } else {
            pendingVoiceStart = true
            voicePermissions.launch(missing.toTypedArray())
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
                    emailConnector = emailConnector,
                    termuxConnector = termuxConnector,
                    onAccessibility = openAccessibility,
                    onShizuku = ShizukuBridge::requestPermission,
                    onTermux = {
                        if (TermuxBridge.isInstalled(context)) {
                            termuxPermission.launch(TermuxBridge.PERMISSION)
                        } else {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://f-droid.org/packages/com.termux/"),
                                ),
                            )
                        }
                    },
                )
            }
            item {
                VoiceCard(
                    recognition = voiceRecognition,
                    status = voiceStatus,
                    onListen = {
                        val missing = requiredVoicePermissions().filter {
                            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            voiceController.start()
                        } else {
                            pendingVoiceStart = true
                            voicePermissions.launch(missing.toTypedArray())
                        }
                    },
                    onStopListening = voiceController::stop,
                    onFindPhone = {
                        val permission = mediaAudioPermission()
                        if (permission == null ||
                            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.findPhone()
                        } else {
                            pendingPhoneFind = true
                            voicePermissions.launch(arrayOf(permission))
                        }
                    },
                    onStopFinding = viewModel::stopFindingPhone,
                    onSettings = { showVoiceSettings = true },
                    onBecomeAssistant = requestAssistantRole,
                )
            }
            item {
                TaskComposer(
                    task = task,
                    onTaskChange = { task = it },
                    running = state.running,
                    onRun = { viewModel.run(task) },
                    onSettings = { showSettings = true },
                )
            }
            item {
                StatusCard(
                    status = state.status,
                    running = state.running,
                    answer = state.finalAnswer,
                    error = state.error,
                    onReset = viewModel::reset,
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
    if (showVoiceSettings) {
        VoiceSettingsDialog(
            initial = viewModel.loadVoiceProfile(),
            onDismiss = { showVoiceSettings = false },
            onReferenceAudio = { voiceReferencePicker.launch(arrayOf("audio/*")) },
            onFinderTrack = { finderTrackPicker.launch(arrayOf("audio/mpeg", "audio/*")) },
            onSpeakResponses = viewModel::setSpeakResponses,
            onPreview = viewModel::previewVoice,
        )
    }
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
                    text = "J",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.padding(start = 13.dp)) {
                Text(
                    text = "Jarvis | AuthorAi",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "by AuthorChe · Приватний агент вашого телефона",
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
private fun VoiceCard(
    recognition: VoiceRecognitionState,
    status: String,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onFindPhone: () -> Unit,
    onStopFinding: () -> Unit,
    onSettings: () -> Unit,
    onBecomeAssistant: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (recognition.listening) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (recognition.listening) "●" else "🎙", color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Голосовий режим", fontWeight = FontWeight.SemiBold)
                    Text(
                        recognition.error ?: recognition.partialText.ifBlank { status },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recognition.error == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onSettings) { Text("Голос") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (recognition.listening) onStopListening else onListen,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (recognition.listening) "Зупинити" else "Говорити")
                }
                OutlinedButton(onClick = onFindPhone) { Text("Я тут") }
            }
            Text(
                "Скажіть: «Джарвіс, де ти?» — буде запущено Downloads/was-wollen-wir-trinken.mp3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onStopFinding, modifier = Modifier.align(Alignment.End)) {
                Text("Зупинити сигнал пошуку")
            }
            OutlinedButton(onClick = onBecomeAssistant, modifier = Modifier.fillMaxWidth()) {
                Text("Зробити системним помічником")
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    accessibilityReady: Boolean,
    shizukuState: ShizukuState,
    emailConnector: ConnectorStatus,
    termuxConnector: ConnectorStatus,
    onAccessibility: () -> Unit,
    onShizuku: () -> Unit,
    onTermux: () -> Unit,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            CapabilityRow(
                title = emailConnector.title,
                ready = emailConnector.state == ConnectorState.READY,
                detail = emailConnector.detail,
                action = null,
                onAction = {},
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            CapabilityRow(
                title = termuxConnector.title,
                ready = termuxConnector.state == ConnectorState.READY,
                detail = termuxConnector.detail,
                action = when (termuxConnector.state) {
                    ConnectorState.READY -> null
                    ConnectorState.PERMISSION_REQUIRED -> "Дозволити"
                    ConnectorState.UNAVAILABLE -> "Встановити"
                },
                onAction = onTermux,
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
        title = { Text("Модель Jarvis | AuthorAi") },
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

@Composable
private fun VoiceSettingsDialog(
    initial: VoiceProfile,
    onDismiss: () -> Unit,
    onReferenceAudio: () -> Unit,
    onFinderTrack: () -> Unit,
    onSpeakResponses: (Boolean) -> Unit,
    onPreview: () -> Unit,
) {
    var speakResponses by remember { mutableStateOf(initial.speakResponses) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Голос і голосовий зліпок") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Голосові команди розпізнаються локально, якщо пристрій має on-device speech service. " +
                        "Відповіді зараз озвучує системний TTS Android.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Озвучувати відповіді", fontWeight = FontWeight.Medium)
                        Text("Працює і для локальної GGUF-моделі", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = speakResponses,
                        onCheckedChange = {
                            speakResponses = it
                            onSpeakResponses(it)
                        },
                    )
                }
                HorizontalDivider()
                Text("Голосовий зліпок", fontWeight = FontWeight.SemiBold)
                Text(
                    initial.referenceAudioUri?.lastPathSegment?.let { "Еталон збережено: $it" }
                        ?: "Еталон голосу ще не додано",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onReferenceAudio, modifier = Modifier.fillMaxWidth()) {
                    Text("Імпортувати еталон голосу")
                }
                Text(
                    "Еталон зберігається як приватне локальне посилання. Справжній clone‑TTS буде " +
                        "вмикатися лише після встановлення сумісного локального ONNX‑рушія; системний " +
                        "голос не видається за клон.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                HorizontalDivider()
                Text("Музика пошуку телефона", fontWeight = FontWeight.SemiBold)
                Text(
                    initial.findPhoneTrackUri?.lastPathSegment
                        ?: "Автопошук: Downloads/was-wollen-wir-trinken.mp3",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onFinderTrack, modifier = Modifier.fillMaxWidth()) {
                    Text("Обрати файл вручну")
                }
                OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                    Text("Перевірити голос")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } },
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

private fun requiredVoicePermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    mediaAudioPermission()?.let(::add)
}

private fun mediaAudioPermission(): String? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_AUDIO
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> Manifest.permission.READ_EXTERNAL_STORAGE
    else -> null
}
