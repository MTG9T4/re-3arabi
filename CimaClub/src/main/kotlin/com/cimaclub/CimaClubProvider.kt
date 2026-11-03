package com.cimaclub

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CimaClub : MainAPI() {
    override var mainUrl = "https://w.cimacub.com"
    override var name = "CimaClub"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override val mainPage = mainPageOf(
        "$mainUrl/category/افلام-اجنبي/" to "أفلام أجنبي",
        "$mainUrl/category/افلام-عربي/" to "أفلام عربي",
        "$mainUrl/category/افلام-كرتون/" to "أفلام كرتون",
        "$mainUrl/category/مسلسلات-اجنبي/" to "مسلسلات أجنبي",
        "$mainUrl/category/مسلسلات-تركية/" to "مسلسلات تركية",
        "$mainUrl/category/مسلسلات-عربي/" to "مسلسلات عربي",
        "$mainUrl/category/مسلسلات-اسيوية/" to "مسلسلات اسيوية",
        "$mainUrl/category/مسلسلات-انمي/" to "مسلسلات انمي",
        "$mainUrl/category/مسلسلات-كرتون/" to "مسلسلات كرتون",
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = this.selectFirst("inner--title > h2")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }?.ifBlank { null }

        val isTv = href.contains("/series/") || href.contains("/مسلسل-") || this.selectFirst(".number") != null

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document
        return document.select("div.BlocksHolder > div.Small--Box").mapNotNull { element ->
            val title = element.selectFirst("inner--title > h2")?.text()?.trim() ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst("img")?.let {
                it.attr("data-src").ifBlank { it.attr("src") }
            }?.ifBlank { null }
            val isTv = href.contains("/series/") || href.contains("/مسلسل-") || element.selectFirst(".number") != null

            if (isTv) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }
        }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}page/$page/"
        } else {
            request.data
        }

        val document = app.get(url).document
        val items = document.select("div.BlocksHolder > div.Small--Box").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val isTvSeries = document.selectFirst("h1.PostTitle")?.text()?.contains("مسلسل") == true || url.contains("/series/") || url.contains("/مسلسل-") ||
                document.select("section.allepcont .row a").size > 1

        val title = document.selectFirst("h1.PostTitle")?.text()?.trim()
            ?: throw RuntimeException("Title not found on page: $url")
        val watchUrl = document.selectFirst("a[href*=/watch]")?.absUrl("href")
            ?: "${url.trimEnd('/')}/watch"
        val poster =
            document.selectFirst(".MainSingle .left .image img")?.attr("src")?.ifBlank { null }
        val plot = document.selectFirst(".StoryArea p")?.text()?.replace("قصة العرض", "")?.trim()
        val tags = document.select(".TaxContent a[href*='/genre/']").mapNotNull { it.text() }
        val year =
            document.selectFirst(".TaxContent a[href*='/release-year/']")?.text()?.toIntOrNull()
        val contentRating =
            document.select(".half-tags li span:contains(التصنيف العمرى)").firstOrNull()
                ?.parent()?.selectFirst("a")?.text()?.trim()

        if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            val seasons = document.select("section.allseasonss .Small--Box a")

            if (seasons.isNotEmpty()) {
                seasons.amap { seasonLink ->
                    val seasonUrl = seasonLink.attr("href")
                    val seasonDoc = if (seasonUrl == url) document else app.get(seasonUrl).document
                    val seasonNumText =
                        seasonLink.selectFirst(".epnum span")?.nextSibling()?.toString()?.trim()
                    val seasonNum = seasonNumText?.toIntOrNull()

                    seasonDoc.select("section.allepcont .row a").map { ep ->
                        newEpisode(ep.attr("href")) {
                            this.name = ep.selectFirst(".ep-info h2")?.text()
                            episode =
                                ep.selectFirst(".epnum")?.ownText()?.trim()?.toIntOrNull()
                            season = seasonNum
                            posterUrl = poster
                        }
                    }
                }.flatten().toCollection(episodes)
            } else {
                document.select("section.allepcont .row a").mapTo(episodes) { ep ->
                    newEpisode(ep.attr("href")) {
                        this.name = ep.selectFirst(".ep-info h2")?.text()
                        this.episode = ep.selectFirst(".epnum")?.ownText()?.trim()?.toIntOrNull()
                        this.posterUrl = poster
                    }
                }
            }

            if (episodes.isEmpty()) {
                episodes.add(newEpisode(watchUrl) {
                    this.name = title
                    this.episode = Regex("الحلقة\\s*(\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
                    this.posterUrl = poster
                })
            }
            return newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodes.distinctBy { it.data }
                    .sortedWith(compareBy({ it.season }, { it.episode }))
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.contentRating = contentRating
            }
        } else {
            return newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = watchUrl
            ) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year
                this.contentRating = contentRating
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val tag = "CimaClub"
        var found = false
        val emit: (ExtractorLink) -> Unit = { found = true; callback(it) }
        val response = app.get(
            data,
            headers = mapOf("Referer" to data)
        )
        val document = response.document
        document.select("ul#watch li").forEach {
            val embedUrl = it.attr("data-watch")
            if (embedUrl.isNotBlank()) {
                Log.d(tag, "Found Embed: $embedUrl")
                loadExtractor(embedUrl, data, subtitleCallback, emit)
            }
        }
        document.select(".ServersList.Download a").forEach {
            val downloadUrl = it.attr("href")
            if (downloadUrl.isNotBlank()) {
                Log.d(tag, "Found Download Link: $downloadUrl")
                loadExtractor(downloadUrl, data, subtitleCallback, emit)
            }
        }

        return found
    }
}
