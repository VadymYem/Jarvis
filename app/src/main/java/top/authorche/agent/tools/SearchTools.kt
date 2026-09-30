package top.authorche.agent.tools

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
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

class DeviceSearchTool(context: Context) : AgentTool {
    private val app = context.applicationContext
    override val definition = ToolDefinition(
        "search_device", "Знайти встановлені застосунки на цьому телефоні, локально і без інтернету",
        "{\"query\":\"частина назви застосунку\"}",
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
            ToolResult(true, if (matches.isEmpty()) "Застосунків не знайдено" else "Знайдено ${matches.size} застосунків",
                JSONObject().put("apps", data))
        }.getOrElse { ToolResult(false, it.message ?: "Помилка пошуку на пристрої") }
    }
}

class WebSearchTool(private val settings: AgentSettingsStore) : AgentTool {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    override val definition = ToolDefinition(
        "search_web", "Пошук у Вікіпедії, GitHub, власному SearXNG або Brave Search; джерело обирається у налаштуваннях",
        "{\"query\":\"пошуковий запит\",\"language\":\"uk|en|de\"}",
    )
    override fun risk(arguments: JSONObject) = RiskLevel.LOW
    override fun summarize(arguments: JSONObject) = "Пошук в інтернеті: ${arguments.optString("query")}" 
    override suspend fun execute(arguments: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val query = arguments.optString("query").trim()
            require(query.length in 2..200) { "Запит має містити 2–200 символів" }
            val language = arguments.optString("language", "uk").takeIf { it in setOf("uk", "en", "de") } ?: "uk"
            val config = settings.search()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val results = when (config.provider) {
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
                JSONObject().put("provider", config.provider.name).put("results", results))
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
