package com.mtg.onlinesport

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.mtg.sources.*
import org.jsoup.nodes.Element

class OnlineSportLiveProvider : MainAPI() {
    override var name = "OnlineSportLive"
    override var mainUrl = "https://onlinesportlive.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Live)
    override val mainPage = mainPageOf("aspwp-today" to "مباريات اليوم", "aspwp-tomorrow" to "مباريات الغد")
    private suspend fun items(tab: String): List<SearchResponse> {
        val doc = app.get(mainUrl, headers = mapOf("User-Agent" to SOURCE_UA)).document
        return doc.select("#$tab .AF_EvItem").mapNotNull { item ->
            val encoded = item.selectFirst("[data-enc]")?.attr("data-enc") ?: return@mapNotNull null
            val url = PublicSportsJson.matchUrl(encoded) ?: return@mapNotNull null
            val teams = item.select(".AF_TeamName").map { it.text() }
            if (teams.size < 2) return@mapNotNull null
            val time = item.selectFirst(".AF_EvTime")?.text().orEmpty()
            val status = item.selectFirst(".AF_StaText")?.text().orEmpty()
            newLiveSearchResponse("${teams[0]} × ${teams[1]} — $time $status", url) { posterUrl = SiteHtml.poster(item, "$mainUrl/") }
        }.distinctBy { it.url }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest) = newHomePageResponse(request.name, items(request.data), false)
    override suspend fun search(query: String) = (items("aspwp-today") + items("aspwp-tomorrow")).filter { it.name.contains(query, true) }.distinctBy { it.url }
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: "OnlineSportLive"
        return newLiveStreamLoadResponse(title, url, url) { posterUrl = doc.selectFirst("meta[property=og:image]")?.attr("content"); plot = doc.selectFirst(".entry-content")?.text() }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data, headers = mapOf("User-Agent" to SOURCE_UA, "Referer" to "$mainUrl/")).document
        val targets = SiteHtml.playerTargets(doc)
        if (targets.isEmpty()) throw ErrorLoadingException("This match page has no active stream. Broadcast links may only appear while the match is live.")
        return SourcePlayback.targets(name, targets, data, subtitleCallback, callback) || SourcePlayback.webView(name, data, "$mainUrl/", callback)
    }
}
