package com.mtg.sources

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

const val SOURCE_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

object SourcePlayback {
    private val media = Regex("(?i)\\.(m3u8|mp4)(?:[?#]|$)")
    suspend fun targets(name: String, urls: List<String>, referer: String, subtitles: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = coroutineScope {
        val count = AtomicInteger()
        val limited = Semaphore(4)
        val emit: (ExtractorLink) -> Unit = { count.incrementAndGet(); callback(it) }
        urls.distinct().map { target -> async {
            limited.withPermit {
                if (count.get() >= 2) return@withPermit
                withTimeoutOrNull(18_000) {
                    try {
                        if (media.containsMatchIn(target)) {
                            direct(name, target, referer, emit)
                        } else {
                            loadExtractor(target, referer, subtitles, emit)
                            if (target.contains("4shared.com/")) {
                                val page = app.get(target, headers = mapOf("User-Agent" to SOURCE_UA, "Referer" to referer)).document
                                SiteHtml.playerTargets(page).filter { media.containsMatchIn(it) }.forEach { direct(name, it, target, emit) }
                            }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* Another published server may still work. */ }
                }
            }
        } }.awaitAll()
        count.get() > 0
    }

    suspend fun direct(name: String, url: String, referer: String, callback: (ExtractorLink) -> Unit, headers: Map<String, String> = emptyMap()) {
        val type = if (url.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
        callback(newExtractorLink(name, name, url, type) {
            this.referer = referer
            this.headers = mapOf("User-Agent" to SOURCE_UA, "Referer" to referer) + headers
        })
    }

    suspend fun webView(name: String, url: String, referer: String, callback: (ExtractorLink) -> Unit): Boolean {
        val pattern = Regex("(?i)https?://.*(?:\\.(?:m3u8|mp4)(?:[?].*)?|/api/v1/(?:movie|serie)/.+/(?:stream|play)/.+)")
        val result = WebViewResolver(pattern, useOkhttp = false, timeout = 60_000L)
            .resolveUsingWebView(url, referer = referer).first ?: return false
        val headers = result.headers.toMap().filterKeys { it.lowercase() !in setOf("range", "host", "connection", "accept-encoding") }
        direct(name, result.url.toString(), result.header("Referer") ?: referer, callback, headers)
        return true
    }
}
