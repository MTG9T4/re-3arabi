package com.mtg.arabiccartoon

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.app.AlertDialog
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

@CloudstreamPlugin
class ArabicCartoonPlugin : Plugin() {
    override fun load() {
        registerMainAPI(ArabicCartoonProvider())
        openSettings = { context ->
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = WebViewClient()
                loadUrl("https://arabiccartoon.org/login/")
            }
            AlertDialog.Builder(context)
                .setTitle("ArabicCartoon — sign in with your own account")
                .setView(webView)
                .setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
                .create().apply {
                    setOnDismissListener {
                        CookieManager.getInstance().flush()
                        webView.stopLoading()
                        webView.destroy()
                    }
                    show()
                    window?.setLayout(-1, -1)
                }
        }
    }
}
