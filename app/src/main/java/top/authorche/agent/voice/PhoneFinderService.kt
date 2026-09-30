package top.authorche.agent.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import top.authorche.agent.AuthorAgentApplication
import top.authorche.agent.MainActivity
import top.authorche.agent.R

class PhoneFinderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousAlarmVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startSignal()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop() }
        player?.release()
        player = null
        stopVibration()
        restoreVolume()
        abandonAudioFocus()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun startSignal() {
        if (player != null) return

        acquireWakeLock()
        maximizeAlarmVolume()
        requestAudioFocus()
        startVibration()

        val preferred = VoiceProfileStore(this).load().findPhoneTrackUri
        val track = preferred?.takeIf(::canOpen) ?: findWasWollenWirTrinken() ?: defaultAlarm()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(this@PhoneFinderService, track)
                isLooping = true
                setVolume(1f, 1f)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    playFallbackAlarm(track)
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            playFallbackAlarm(track)
        }
        handler.postDelayed({ stopSelf() }, MAX_SIGNAL_DURATION_MS)
    }

    private fun playFallbackAlarm(failed: Uri) {
        val fallback = defaultAlarm()
        if (failed == fallback) {
            stopSelf()
            return
        }
        player?.release()
        player = runCatching {
            MediaPlayer.create(this, fallback)?.apply {
                isLooping = true
                setVolume(1f, 1f)
                start()
            }
        }.getOrNull()
        if (player == null) stopSelf()
    }

    private fun findWasWollenWirTrinken(): Uri? = runCatching {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? COLLATE NOCASE OR " +
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR " +
            "${MediaStore.Audio.Media.TITLE} LIKE ?"
        val args = arrayOf(
            FIND_PHONE_FILE_NAME,
            "%was-wollen-wir-trinken%",
            "%was wollen wir trinken%",
        )
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
            )
        }
    }.getOrNull()

    private fun canOpen(uri: Uri): Boolean = runCatching {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun defaultAlarm(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun maximizeAlarmVolume() {
        val audio = getSystemService(AudioManager::class.java)
        previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0,
        )
    }

    private fun restoreVolume() {
        val previous = previousAlarmVolume ?: return
        runCatching {
            getSystemService(AudioManager::class.java)
                .setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        }
        previousAlarmVolume = null
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
        audioFocusRequest = request
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        audioFocusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun startVibration() {
        val vibrator = getSystemService(Vibrator::class.java)
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 300, 700, 800), 0)
        vibrator.vibrate(effect)
    }

    private fun stopVibration() {
        getSystemService(Vibrator::class.java).cancel()
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:phone-finder")
            .apply { acquire(MAX_SIGNAL_DURATION_MS + 5_000L) }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PhoneFinderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, AuthorAgentApplication.PHONE_FINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle("Jarvis | AuthorAi — я тут")
            .setContentText("Гучний сигнал допомагає знайти телефон")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Зупинити", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "top.authorche.agent.action.FIND_PHONE"
        const val ACTION_STOP = "top.authorche.agent.action.STOP_FIND_PHONE"
        private const val NOTIFICATION_ID = 8_117
        private const val MAX_SIGNAL_DURATION_MS = 15 * 60 * 1_000L
        private const val FIND_PHONE_FILE_NAME = "was-wollen-wir-trinken.mp3"
    }
}
