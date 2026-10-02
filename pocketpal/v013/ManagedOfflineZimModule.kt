package top.authorche.agent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.text.Html
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONArray
import org.json.JSONObject
import org.kiwix.libkiwix.JNIKiwix
import org.kiwix.libzim.Archive
import org.kiwix.libzim.Query
import org.kiwix.libzim.Searcher
import java.io.File
import java.util.concurrent.Executors

class ManagedOfflineZimModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  data class Entry(
    val id: String,
    val name: String,
    val path: String,
    val sourceUri: String,
    val articles: Int,
    val bytes: Long
  )

  private val preferences = context.getSharedPreferences("jarvis_offline_zim_v2", 0)
  private val executor = Executors.newSingleThreadExecutor()
  private var pending: Promise? = null
  @Volatile private var libraryReady = false

  private val listener: ActivityEventListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
      if (requestCode != PICK_ZIM) return
      val promise = pending ?: return
      pending = null
      if (resultCode != Activity.RESULT_OK || data?.data == null) {
        promise.resolve(null)
        return
      }
      val uri = data.data!!
      executor.execute {
        try {
          val name = displayName(uri)
          require(name.endsWith(".zim", ignoreCase = true)) { "Виберіть файл .zim." }
          val granted = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
          if (granted != 0) {
            try { context.contentResolver.takePersistableUriPermission(uri, granted) }
            catch (_: SecurityException) {}
          }
          val current = liveEntries()
          val duplicate = current.firstOrNull {
            it.sourceUri == uri.toString() && File(it.path).isFile
          }
          if (duplicate != null) {
            promise.resolve(info(duplicate))
            return@execute
          }
          val imported = importArchive(uri, name)
          saveEntries(current + imported)
          promise.resolve(info(imported))
        } catch (error: Exception) {
          promise.reject("ZIM_IMPORT", error.message, error)
        }
      }
    }
  }

  init { context.addActivityEventListener(listener) }
  override fun getName() = "OfflineZimModule"

  @ReactMethod fun chooseArchive(promise: Promise) {
    if (pending != null) {
      promise.reject("ZIM_PICKER_BUSY", "Вибір архіву вже відкрито")
      return
    }
    val activity = context.currentActivity ?: run {
      promise.reject("ZIM_NO_ACTIVITY", "Відкрийте Jarvis перед вибором архіву")
      return
    }
    pending = promise
    try {
      activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
      }, PICK_ZIM)
    } catch (error: Exception) {
      pending = null
      promise.reject("ZIM_PICKER", error.message, error)
    }
  }

  @ReactMethod fun status(promise: Promise) {
    executor.execute {
      try {
        val entries = liveEntries()
        val archives = Arguments.createArray()
        entries.forEach { archives.pushMap(archiveMap(it)) }
        promise.resolve(Arguments.createMap().apply {
          putBoolean("ready", entries.isNotEmpty())
          putString("name", when (entries.size) {
            0 -> null
            1 -> entries.first().name
            else -> entries.size.toString() + " ZIM архівів"
          })
          putInt("articles", entries.sumOf { it.articles })
          putArray("archives", archives)
        })
      } catch (error: Exception) {
        promise.reject("ZIM_STATUS", error.message, error)
      }
    }
  }

  @ReactMethod fun forgetArchive(promise: Promise) {
    executor.execute {
      try {
        loadEntries().forEach { deleteEntryFile(it) }
        preferences.edit().clear().apply()
        promise.resolve(null)
      } catch (error: Exception) {
        promise.reject("ZIM_DELETE_ALL", error.message, error)
      }
    }
  }

  @ReactMethod fun deleteArchive(id: String, promise: Promise) {
    executor.execute {
      try {
        val entries = loadEntries()
        val target = entries.firstOrNull { it.id == id } ?: error("ZIM archive not found")
        deleteEntryFile(target)
        saveEntries(entries.filterNot { it.path == target.path })
        promise.resolve(null)
      } catch (error: Exception) {
        promise.reject("ZIM_DELETE", error.message, error)
      }
    }
  }

  @ReactMethod fun search(query: String, limit: Double, promise: Promise) {
    val term = query.trim().take(180)
    if (term.length < 2) {
      promise.resolve(Arguments.createArray())
      return
    }
    executor.execute {
      try {
        ensureLibrary()
        val wanted = limit.toInt().coerceIn(1, 30)
        val rows = Arguments.createArray()
        var count = 0
        for (entry in liveEntries()) {
          if (count >= wanted) break
          val archive = Archive(entry.path)
          try {
            val searcher = Searcher(archive)
            try {
              val request = Query(term)
              try {
                val search = searcher.search(request)
                try {
                  val iterator = search.getResults(0, wanted - count)
                  try {
                    while (iterator.hasNext() && count < wanted) {
                      val title = iterator.title ?: break
                      val articlePath = iterator.path ?: ""
                      val snippet = Html.fromHtml(
                        iterator.snippet ?: "", Html.FROM_HTML_MODE_COMPACT
                      ).toString().replace(Regex("\\s+"), " ").trim().take(600)
                      val excerpt = articleExcerpt(archive, articlePath, snippet)
                      rows.pushMap(Arguments.createMap().apply {
                        putString("title", title)
                        putString("path", articlePath)
                        putString("excerpt", excerpt.ifBlank { snippet })
                        putString("archive", entry.name)
                        putString("archiveId", entry.id)
                      })
                      iterator.next()
                      count++
                    }
                  } finally { iterator.dispose() }
                } finally { search.dispose() }
              } finally { request.dispose() }
            } finally { searcher.dispose() }
          } finally { archive.dispose() }
        }
        promise.resolve(rows)
      } catch (error: Exception) {
        promise.reject("ZIM_SEARCH", error.message, error)
      }
    }
  }

  @ReactMethod fun readArticle(
    archiveId: String,
    articlePath: String,
    title: String,
    promise: Promise
  ) {
    if (archiveId.isBlank() || articlePath.isBlank()) {
      promise.reject("ZIM_ARTICLE_INPUT", "Archive and article path are required")
      return
    }
    executor.execute {
      try {
        ensureLibrary()
        val entry = liveEntries().firstOrNull { it.id == archiveId }
          ?: error("ZIM archive not found")
        val archive = Archive(entry.path)
        try {
          val item = archive.getEntryByPath(articlePath).getItem(true)
          require(item.size in 1L..MAX_ARTICLE_BYTES) {
            "Стаття завелика для швидкого вбудованого читача."
          }
          val blob = item.data
          val rawText = try {
            val raw = blob.data.toString(Charsets.UTF_8)
            if (item.mimetype.startsWith("text/html")) {
              Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString()
            } else raw
          } finally { blob.dispose() }
          val cleaned = rawText
            .replace(Regex("[\\t\\r ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(MAX_ARTICLE_CHARS)
          promise.resolve(Arguments.createMap().apply {
            putString("title", title.ifBlank { articlePath })
            putString("archive", entry.name)
            putString("archiveId", entry.id)
            putString("path", articlePath)
            putString("text", cleaned)
            putBoolean("truncated", rawText.length > MAX_ARTICLE_CHARS)
          })
        } finally { archive.dispose() }
      } catch (error: Exception) {
        promise.reject("ZIM_ARTICLE", error.message, error)
      }
    }
  }

  private fun articleExcerpt(archive: Archive, articlePath: String, fallback: String): String =
    try {
      val item = archive.getEntryByPath(articlePath).getItem(true)
      if (item.size !in 1L..500_000L) fallback else {
        val blob = item.data
        try {
          val raw = blob.data.toString(Charsets.UTF_8)
          if (item.mimetype.startsWith("text/html")) {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString()
              .replace(Regex("\\s+"), " ").trim().take(1800)
          } else raw.replace(Regex("\\s+"), " ").trim().take(1800)
        } finally { blob.dispose() }
      }
    } catch (_: Exception) { fallback }

  private fun importArchive(uri: Uri, name: String): Entry {
    ensureLibrary()
    val root = context.getExternalFilesDir("zim") ?: File(context.filesDir, "zim")
    check(root.isDirectory || root.mkdirs()) { "Неможливо створити каталог ZIM." }

    val expected = querySize(uri)
    if (expected > 0) {
      val free = StatFs(root.absolutePath).availableBytes
      require(expected + RESERVE_BYTES < free) {
        "Недостатньо місця. Для імпорту потрібно приблизно " +
          (expected / 1024 / 1024) + " MB плюс резерв."
      }
    }

    val safe = name.map { ch ->
      if (ch.isLetterOrDigit() || ch in "._- ()[]") ch else '_'
    }.joinToString("").take(140).ifBlank { "archive.zim" }
    val id = System.currentTimeMillis().toString() + "-" +
      Integer.toHexString(uri.toString().hashCode())
    val target = File(root, "$id-$safe")
    var copied = 0L

    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().buffered(1024 * 1024).use { output ->
          val buffer = ByteArray(1024 * 1024)
          while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
            copied += n
          }
        }
      } ?: error("Неможливо прочитати вибраний ZIM.")
      require(copied > 0) { "ZIM файл порожній." }
      val archive = Archive(target.canonicalPath)
      val articles = try { archive.articleCount } finally { archive.dispose() }
      return Entry(id, name, target.canonicalPath, uri.toString(), articles, copied)
    } catch (error: Exception) {
      target.delete()
      throw error
    }
  }

  private fun info(entry: Entry) = Arguments.createMap().apply {
    putBoolean("ready", true)
    putString("id", entry.id)
    putString("name", entry.name)
    putInt("articles", entry.articles)
    putDouble("bytes", entry.bytes.toDouble())
  }

  private fun archiveMap(entry: Entry) = Arguments.createMap().apply {
    putString("id", entry.id)
    putString("name", entry.name)
    putDouble("bytes", entry.bytes.toDouble())
    putInt("articles", entry.articles)
  }

  private fun ensureLibrary() {
    if (!libraryReady) synchronized(this) {
      if (!libraryReady) {
        JNIKiwix(context)
        libraryReady = true
      }
    }
  }

  private fun liveEntries(): List<Entry> {
    val all = loadEntries()
    val live = all.filter { File(it.path).isFile }
    if (live.size != all.size) saveEntries(live)
    return live
  }

  private fun loadEntries(): List<Entry> {
    val raw = preferences.getString("archives", "[]") ?: "[]"
    val arr = JSONArray(raw)
    return (0 until arr.length()).mapNotNull { index ->
      try {
        val item = arr.getJSONObject(index)
        val path = item.getString("path")
        Entry(
          item.optString("id").ifBlank { File(path).name.substringBefore('-') },
          item.getString("name"),
          path,
          item.optString("sourceUri", ""),
          item.optInt("articles", 0),
          item.optLong("bytes", 0L)
        )
      } catch (_: Exception) { null }
    }
  }

  private fun saveEntries(entries: List<Entry>) {
    val arr = JSONArray()
    entries.forEach { entry ->
      arr.put(JSONObject().apply {
        put("id", entry.id)
        put("name", entry.name)
        put("path", entry.path)
        put("sourceUri", entry.sourceUri)
        put("articles", entry.articles)
        put("bytes", entry.bytes)
      })
    }
    preferences.edit().putString("archives", arr.toString()).apply()
  }

  private fun deleteEntryFile(entry: Entry) {
    try { File(entry.path).delete() } catch (_: Exception) {}
    if (entry.sourceUri.isNotBlank()) {
      try {
        context.contentResolver.releasePersistableUriPermission(
          Uri.parse(entry.sourceUri), Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {}
    }
  }

  private fun displayName(uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    } ?: (uri.lastPathSegment ?: "archive.zim")

  private fun querySize(uri: Uri): Long =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
      if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
    } ?: -1L

  override fun invalidate() {
    context.removeActivityEventListener(listener)
    pending?.reject("ZIM_CLOSED", "Archive picker closed")
    pending = null
    executor.shutdown()
    super.invalidate()
  }

  companion object {
    private const val PICK_ZIM = 9254
    private const val RESERVE_BYTES = 128L * 1024L * 1024L
    private const val MAX_ARTICLE_BYTES = 8L * 1024L * 1024L
    private const val MAX_ARTICLE_CHARS = 350_000
  }
}
