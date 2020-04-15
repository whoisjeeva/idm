package app.spidy.idmexample

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import app.spidy.hiper.Hiper
import app.spidy.idm.Idm
import app.spidy.kookaburra.controllers.Browser

class BrowserListener(private val idm: Idm): Browser.Listener {
    private var hiper = Hiper.getAsyncInstance()
    private val cookieManager = CookieManager.getInstance()
    private var cookies = HashMap<String, String>()
    private var pageUrl: String? = null
    private val urlValidator = UrlValidator()

    override fun shouldInterceptRequest(view: WebView, activity: FragmentActivity?, url: String, request: WebResourceRequest?) {
        if (urlValidator.validate(url)) {
            idm.detector.detect(url, request?.requestHeaders, cookies, pageUrl, view, activity)
        }
    }

    override fun onNewUrl(view: WebView, url: String) {
        pageUrl = url
        cookies = HashMap()
        val cooks = cookieManager.getCookie(url)?.split(";")

        if (cooks != null) {
            for (cook in cooks) {
                val nodes = cook.trim().split("=")
                cookies[nodes[0].trim()] = nodes[1].trim()
            }
        }
    }
}