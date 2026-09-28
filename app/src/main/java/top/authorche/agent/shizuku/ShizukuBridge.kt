package top.authorche.agent.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import top.authorche.agent.BuildConfig

enum class ShizukuState {
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    CONNECTING,
    READY,
}

object ShizukuBridge {
    private val mutableState = MutableStateFlow(ShizukuState.UNAVAILABLE)
    val state: StateFlow<ShizukuState> = mutableState.asStateFlow()

    private var appContext: Context? = null
    @Volatile private var service: IPrivilegedService? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        mutableState.value = ShizukuState.UNAVAILABLE
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) connect()
        else mutableState.value = ShizukuState.PERMISSION_REQUIRED
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IPrivilegedService.Stub.asInterface(binder)
            mutableState.value = ShizukuState.READY
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            refresh()
        }
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            mutableState.value = ShizukuState.UNAVAILABLE
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) connect()
        else Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun refresh() {
        mutableState.value = when {
            !Shizuku.pingBinder() -> ShizukuState.UNAVAILABLE
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShizukuState.PERMISSION_REQUIRED
            service != null -> ShizukuState.READY
            else -> ShizukuState.CONNECTING
        }
        if (mutableState.value == ShizukuState.CONNECTING) connect()
    }

    suspend fun execute(command: List<String>): String = withContext(Dispatchers.IO) {
        val bound = service ?: error("Shizuku UserService ще не підключений")
        bound.execute(command.toTypedArray())
    }

    fun uid(): Int? = runCatching { service?.uid() }.getOrNull()

    private fun connect() {
        val context = appContext ?: return
        if (!Shizuku.pingBinder()) {
            mutableState.value = ShizukuState.UNAVAILABLE
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ShizukuState.PERMISSION_REQUIRED
            return
        }
        if (service != null) {
            mutableState.value = ShizukuState.READY
            return
        }

        mutableState.value = ShizukuState.CONNECTING
        val args = Shizuku.UserServiceArgs(ComponentName(context, PrivilegedService::class.java))
            .daemon(false)
            .processNameSuffix("agent_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
        Shizuku.bindUserService(args, connection)
    }

    private const val PERMISSION_REQUEST_CODE = 8401
}
