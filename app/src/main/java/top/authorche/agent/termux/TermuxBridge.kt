package top.authorche.agent.termux

import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

data class TermuxExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val error: String,
) {
    val successful: Boolean get() = exitCode == 0 && error.isBlank()
}

object TermuxBridge {
    const val PACKAGE = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    private const val EXTRA_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val WORKDIR = "/data/data/com.termux/files/home"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
    }.isSuccess

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun execute(context: Context, command: PreparedTermuxCommand): TermuxExecutionResult {
        require(isInstalled(context)) { "Termux не встановлено" }
        require(hasPermission(context)) { "Надайте Jarvis дозвіл RUN_COMMAND у налаштуваннях Android" }

        val ticket = TermuxResultBroker.register()
        val resultIntent = Intent(context, TermuxResultService::class.java)
            .putExtra(TermuxResultService.EXTRA_REQUEST_ID, ticket.id)
        val flags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getService(context, ticket.id, resultIntent, flags)
        val runIntent = Intent().apply {
            setClassName(PACKAGE, SERVICE)
            action = ACTION
            putExtra(EXTRA_PATH, command.executable)
            putExtra(EXTRA_ARGUMENTS, command.arguments.toTypedArray())
            putExtra(EXTRA_WORKDIR, WORKDIR)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_LABEL, "Jarvis | AuthorAi")
            putExtra(EXTRA_DESCRIPTION, command.summary)
            putExtra(EXTRA_PENDING_INTENT, callback)
        }

        return try {
            checkNotNull(context.startService(runIntent)) {
                "Termux відхилив RUN_COMMAND. Перевірте allow-external-apps=true"
            }
            withTimeout(30_000) { ticket.result.await() }
        } finally {
            TermuxResultBroker.remove(ticket.id)
        }
    }
}

private data class TermuxTicket(
    val id: Int,
    val result: CompletableDeferred<TermuxExecutionResult>,
)

private object TermuxResultBroker {
    private val nextId = AtomicInteger(4_000)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxExecutionResult>>()

    fun register(): TermuxTicket {
        val id = nextId.updateAndGet { if (it == Int.MAX_VALUE) 4_000 else it + 1 }
        return TermuxTicket(id, CompletableDeferred<TermuxExecutionResult>().also { pending[id] = it })
    }

    fun complete(id: Int, result: TermuxExecutionResult) {
        pending.remove(id)?.complete(result)
    }

    fun remove(id: Int) {
        pending.remove(id)?.cancel()
    }
}

class TermuxResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getIntExtra(EXTRA_REQUEST_ID, -1) ?: -1
        val bundle = intent?.getBundleExtra("result")
        if (requestId >= 0) {
            TermuxResultBroker.complete(
                requestId,
                if (bundle == null) {
                    TermuxExecutionResult(-1, "", "", "Termux не повернув результат")
                } else {
                    TermuxExecutionResult(
                        exitCode = bundle.getInt("exitCode", -1),
                        stdout = bundle.getString("stdout", "").take(MAX_RESULT),
                        stderr = bundle.getString("stderr", "").take(MAX_RESULT),
                        error = if (bundle.getInt("err", Activity.RESULT_OK) == Activity.RESULT_OK) {
                            ""
                        } else {
                            bundle.getString("errmsg", "Помилка Termux").take(MAX_RESULT)
                        },
                    )
                },
            )
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_REQUEST_ID = "top.authorche.agent.termux.REQUEST_ID"
        private const val MAX_RESULT = 8_000
    }
}
