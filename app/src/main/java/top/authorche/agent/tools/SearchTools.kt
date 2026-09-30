package top.authorche.agent.tools

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.DocumentsContract
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import top.authorche.agent.agent.AgentTool
import top.authorche.agent.agent.RiskLevel
import top.authorche.agent.agent.ToolDefinition
import top.authorche.agent.agent.ToolResult
import top.authorche.agent.provider.AgentSettingsStore
import top.authorche.agent.provider.SearchProvider

class DeviceSearchTool(context: Context, private val settings: AgentSettingsStore) : AgentTool {
    private val app = context.applicationContext
    override val definition = ToolDefinition(
        "search_device", "Знайти застосунки та файли у вибраній користувачем теці на телефоні, офлайн",
        "{\"query\":\"частина назви застосунку або файлу\"}",
    )
    override fun risk(arguments: JSONObject) = RiskLevel.LOW
    override fun summarize(arguments: JSONObject) = "Пошук на пристрої: ${arguments.optString("query")}" 
    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val query = arguments.optString("query").trim()
            require(query.length >= 2) { "Вкажіть щонайменше 2 символи" }
            val launcher = app.getSystemService(LauncherApps::class.java)
            val matches = launcher.getActivityList(null, Process.myUserHandle())
                .filter { it.label.toString().contains(query, ignoreCase = true) ||
                    it.applicationInfo.packageName.contains(query, ignoreCase = true) }
                .distinctBy { it.applicationInfo.packageName }
                .take(12)
            val data = JSONArray()
            matches.forEach { data.put(JSONObject().put("name", it.label.toString())
                .put("package", it.applicationInfo.packageName)) }
            val files = runCatching { searchFiles(query) }.getOrDefault(JSONArray())
            ToolResult(true, "Знайдено ${matches.size} застосунків і ${files.length()} файлів",
                JSONObject().put("apps", data).put("files", files))
        }.getOrElse { ToolResult(false, it.message ?: "Помилка пошуку на пристрої") }
    }

    private fun searchFiles(query: String): JSONArray {
        val tree = settings.searchFolder() ?: return JSONArray()
        val resolver = app.contentResolver
        val root = DocumentsContract.getTreeDocumentId(tree)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(root to 0)
        val found = JSONArray()
        var visited = 0
        while (queue.isNotEmpty() && visited < 1200 && found.length() < 20) {
            val (parent, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && visited++ < 1200) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    if (name.contains(query, ignoreCase = true) && found.length() < 20) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        found.put(JSONObject().put("name", name).put("uri", uri.toString()))
                    }
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR && depth < 4) queue.add(id to depth + 1)
                }
            }
        }
        return found
    }
}

class WebSearchTool(private val settings: AgentSettingsStore) : AgentTool {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    override val definition = ToolDefinition(
        "search_web", "Пошук у Вікіпедії, GitHub, власному SearXNG або Brave Search; джерело обирається у налаштуваннях",
        "{\"query\":\"пошуковий запит\",\"language\":\"uk|en|de\",\"source\":\"selected|wikipedia|github\"}",
    )
    override fun risk(arguments: JSONObject) = RiskLevel.LOW
    override fun summarize(arguments: JSONObject) = "Пошук в інтернеті: ${arguments.optString("query")}" 
    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val query = arguments.optString("query").trim()
            require(query.length in 2..200) { "Запит має містити 2–200 символів" }
            val language = arguments.optString("language", "uk").takeIf { it in setOf("uk", "en", "de") } ?: "uk"
            val config = settings.search()
            val provider = when (arguments.optString("source")) {
                "wikipedia" -> SearchProvider.WIKIPEDIA
                "github" -> SearchProvider.GITHUB
                else -> config.provider
            }
            val encoded = URLEncoder.encode(query, "UTF-8")
            val results = when (provider) {
                SearchProvider.WIKIPEDIA -> wikipedia(language, encoded)
                SearchProvider.GITHUB -> github(encoded)
                SearchProvider.SEARXNG -> {
                    require(config.searxngUrl.startsWith("https://")) { "Вкажіть HTTPS-адресу SearXNG" }
                    searxng(config.searxngUrl, encoded, language)
                }
                SearchProvider.BRAVE -> {
                    require(config.braveKey.isNotBlank()) { "Вкажіть Brave Search API-ключ" }
                    brave(encoded, config.braveKey)
                }
            }
            ToolResult(true, if (results.length() == 0) "Нічого не знайдено" else "Знайдено ${results.length()} результатів",
                JSONObject().put("provider", provider.name).put("results", results))
        }.getOrElse { ToolResult(false, it.message ?: "Пошук не вдався") }
    }

    private fun get(url: String, key: String? = null): JSONObject {
        val builder = Request.Builder().url(url).header("User-Agent", "JarvisAuthorAI/0.3 (https://authorche.top)")
        if (key != null) builder.header("X-Subscription-Token", key)
        http.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "Пошуковий сервер повернув HTTP ${response.code}" }
            return JSONObject(response.body?.charStream()?.readText()?.take(250_000).orEmpty())
        }
    }

    private fun wikipedia(lang: String, query: String): JSONArray {
        val pages = get("https://$lang.wikipedia.org/w/rest.php/v1/search/page?q=$query&limit=6")
            .optJSONArray("pages") ?: JSONArray()
        return JSONArray().apply { for (i in 0 until pages.length()) {
            val page = pages.getJSONObject(i)
            put(JSONObject().put("title", page.optString("title"))
                .put("url", "https://$lang.wikipedia.org/wiki/" +
                    URLEncoder.encode(page.optString("key"), "UTF-8").replace("+", "%20"))
                .put("snippet", page.optString("excerpt").replace(Regex("<[^>]*>"), "").take(450)))
        } }
    }

    private fun github(query: String): JSONArray {
        val items = get("https://api.github.com/search/repositories?q=$query&per_page=6")
            .optJSONArray("items") ?: JSONArray()
        return JSONArray().apply { for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            put(JSONObject().put("title", item.optString("full_name"))
                .put("url", item.optString("html_url"))
                .put("snippet", item.optString("description").take(450)))
        } }
    }

    private fun searxng(base: String, query: String, lang: String): JSONArray {
        val results = get("${base.trimEnd('/')}/search?q=$query&format=json&language=$lang")
            .optJSONArray("results") ?: JSONArray()
        return JSONArray().apply { for (i in 0 until minOf(results.length(), 6)) {
            val item = results.getJSONObject(i)
            put(JSONObject().put("title", item.optString("title"))
                .put("url", item.optString("url"))
                .put("snippet", item.optString("content").take(450)))
        } }
    }

    private fun brave(query: String, key: String): JSONArray {
        val results = get("https://api.search.brave.com/res/v1/web/search?q=$query&count=6", key)
            .optJSONObject("web")?.optJSONArray("results") ?: JSONArray()
        return JSONArray().apply { for (i in 0 until minOf(results.length(), 6)) {
            val item = results.getJSONObject(i)
            put(JSONObject().put("title", item.optString("title"))
                .put("url", item.optString("url"))
                .put("snippet", item.optString("description").take(450)))
        } }
    }
}
