package top.authorche.agent

import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URLEncoder

class BackgroundWebSearchModule(private val context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {
  private val main = Handler(Looper.getMainLooper())
  private var active: WebView? = null
  private var pending: Promise? = null
  private var engines: List<String> = emptyList()
  private var engineIndex = 0

  override fun getName() = "BackgroundWebSearchModule"

  @ReactMethod fun search(query: String, promise: Promise) {
    if (query.isBlank() || query.length > 180) {
      promise.reject("SEARCH_QUERY", "Search query must contain 1–180 characters")
      return
    }
    main.post {
      if (active != null) {
        promise.reject("SEARCH_BUSY", "Search already running")
        return@post
      }
      pending = promise
      val encoded = URLEncoder.encode(query, "UTF-8")
      engines = listOf(
        "https://www.bing.com/search?setlang=uk-UA&count=10&q=$encoded",
        "https://www.google.com/search?hl=uk&num=10&q=$encoded",
        "https://html.duckduckgo.com/html/?q=$encoded"
      )
      engineIndex = 0

      val view = WebView(context)
      active = view
      view.settings.javaScriptEnabled = true
      view.settings.domStorageEnabled = false
      view.settings.allowFileAccess = false
      view.settings.allowContentAccess = false
      view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
      view.settings.setSupportMultipleWindows(false)
      view.settings.userAgentString =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
          "(KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

      view.webViewClient = object : WebViewClient() {
        override fun onPageFinished(webView: WebView, url: String) {
          if (webView !== active) return
          extract(webView)
        }
      }

      main.postDelayed({
        if (view === active) {
          pending?.reject("SEARCH_TIMEOUT", "Browser search timed out")
          close()
        }
      }, 25000)

      loadCurrent(view)
    }
  }

  private fun loadCurrent(view: WebView) {
    if (engineIndex >= engines.size) {
      pending?.reject("SEARCH_EMPTY", "Browser fallbacks returned no search results")
      close()
      return
    }
    view.loadUrl(engines[engineIndex])
  }

  private fun extract(webView: WebView) {
    val js = """(function(){
      const selectors = [
        'li.b_algo h2 a',
        'div.MjjYud a',
        'a.result__a',
        'main a'
      ];
      const links = [];
      const seen = new Set();
      for (const selector of selectors) {
        for (const a of document.querySelectorAll(selector)) {
          try {
            const raw = a.href || '';
            const u = new URL(raw, location.href);
            let url = u.href;
            const uddg = u.searchParams.get('uddg');
            if (uddg) url = decodeURIComponent(uddg);
            const target = new URL(url);
            const host = target.hostname.toLowerCase();
            if (!/^https?:/.test(target.protocol)) continue;
            if (host.includes('google.') || host.includes('bing.com') ||
                host.includes('duckduckgo.com') || host.includes('microsoft.com')) continue;
            const title = (a.innerText || a.textContent || '').trim();
            if (title.length < 4 || seen.has(url)) continue;
            seen.add(url);
            const context = a.closest('li.b_algo, .result, div.MjjYud, article') || a.parentElement;
            const snippet = (context?.innerText || '').replace(/\s+/g,' ').trim().slice(0,700);
            links.push({title, url, snippet});
            if (links.length >= 10) break;
          } catch (_) {}
        }
        if (links.length >= 5) break;
      }
      return JSON.stringify(links);
    })()""".trimIndent()

    webView.evaluateJavascript(js) { response ->
      if (webView !== active) return@evaluateJavascript
      try {
        val decoded = JSONTokener(response).nextValue().toString()
        val rows = JSONArray(decoded)
        if (rows.length() == 0) {
          engineIndex += 1
          loadCurrent(webView)
          return@evaluateJavascript
        }
        val result = Arguments.createArray()
        for (index in 0 until minOf(rows.length(), 8)) {
          val hit = rows.getJSONObject(index)
          result.pushMap(Arguments.createMap().apply {
            putString("title", hit.optString("title"))
            putString("url", hit.optString("url"))
            putString("snippet", hit.optString("snippet"))
          })
        }
        pending?.resolve(result)
        close()
      } catch (_: Exception) {
        engineIndex += 1
        loadCurrent(webView)
      }
    }
  }

  private fun close() {
    active?.stopLoading()
    active?.destroy()
    active = null
    pending = null
    engines = emptyList()
    engineIndex = 0
  }

  override fun invalidate() {
    main.post {
      pending?.reject("SEARCH_CLOSED", "Search closed")
      close()
    }
    super.invalidate()
  }
}
