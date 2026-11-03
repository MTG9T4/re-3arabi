package com.mtg.sources

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document
import java.net.URLEncoder

abstract class HtmlCatalogProvider(private val site: Site) : MainAPI() {
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon, TvType.Anime)
    protected open fun headers() = mapOf("User-Agent" to SOURCE_UA, "Referer" to "$mainUrl/")
    protected suspend fun document(url: String): Document = app.get(url, headers = headers()).document

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = "$mainUrl${request.data}"
        val url = if (page <= 1) base else "${base.trimEnd('/')}/page/$page/"
        val doc = document(url)
        return newHomePageResponse(request.name, SiteHtml.cards(doc, site).map { card(it) }, SiteHtml.hasNext(doc, page))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val value = URLEncoder.encode(query, "UTF-8")
        val path = if (site == Site.MYSEED) "/search?q=$value" else "/?s=$value"
        return SiteHtml.cards(document("$mainUrl$path"), site).map { card(it) }
    }

    protected fun card(item: SiteCard): SearchResponse = if (item.series) {
        newTvSeriesSearchResponse(item.title, item.url, TvType.TvSeries) { posterUrl = item.poster }
    } else {
        newMovieSearchResponse(item.title, item.url, TvType.Movie) { posterUrl = item.poster }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = document(url)
        val title = doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() } ?: throw ErrorLoadingException("Title not found")
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = doc.selectFirst(".StoryArea p,.myseed-single-story p,.detail__synopsis,.synopsis,.entry-content p")?.text()
            ?: doc.selectFirst("meta[name=description]")?.attr("content")
        val entries = SiteHtml.episodes(doc, site)
        val series = entries.isNotEmpty() || url.contains("/series/") || url.contains("/tvshows/") || url.contains("/anime/")
        if (series) {
            val episodes = entries.map { item -> newEpisode(item.url) {
                name = item.title; episode = item.number; season = item.season; posterUrl = item.poster ?: poster
            } }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) { posterUrl = poster; this.plot = plot }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) { posterUrl = poster; this.plot = plot }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        var doc = document(data)
        if (SiteHtml.restricted(doc)) throw ErrorLoadingException("This title requires an ArabicCartoon subscription and a signed-in account.")
        val watch = when (site) {
            Site.MYSEED -> doc.selectFirst("a.myseed-single-watch-btn")?.absUrl("href")
            Site.CIMACLUB -> doc.selectFirst("a[href*=/watch]")?.absUrl("href")
            else -> null
        }
        if (!watch.isNullOrBlank()) doc = document(watch)
        return SourcePlayback.targets(name, SiteHtml.playerTargets(doc), doc.location(), subtitleCallback, callback)
    }
}
