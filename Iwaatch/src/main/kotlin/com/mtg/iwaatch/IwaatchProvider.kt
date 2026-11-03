package com.mtg.iwaatch

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.mtg.sources.*
import kotlinx.serialization.json.*
import java.net.URLEncoder

class IwaatchProvider : MainAPI() {
    override var name = "Iwaatch"
    override var mainUrl = "https://iwaatch.com"
    override var lang = "ar"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf("movie" to "الأفلام", "serie" to "المسلسلات")

    private suspend fun api(path: String): JsonObject {
        val response = app.get("$mainUrl/api/v1/$path", headers = mapOf("User-Agent" to SOURCE_UA, "Origin" to mainUrl, "Referer" to "$mainUrl/"))
        val root = Json.parseToJsonElement(response.text).jsonObject
        if (root["success"]?.jsonPrimitive?.booleanOrNull != true) throw ErrorLoadingException("Iwaatch catalog is temporarily unavailable")
        return root
    }
    private fun JsonObject.text(key: String) = this[key]?.let { (it as? JsonPrimitive)?.contentOrNull }
    private fun JsonObject.title() = text("title_ar")?.takeIf { it.isNotBlank() } ?: text("title_en") ?: "Iwaatch"
    private fun card(item: JsonObject, kind: String): SearchResponse {
        val id = item.text("id") ?: throw ErrorLoadingException("Missing title ID")
        val poster = item.text("cover")?.let { SiteHtml.absolute(mainUrl, it) }
        return if (kind == "serie") newTvSeriesSearchResponse(item.title(), "$mainUrl/serie/$id", TvType.TvSeries) { posterUrl = poster }
        else newMovieSearchResponse(item.title(), "$mainUrl/movie/$id", TvType.Movie) { posterUrl = poster }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val root = api("${request.data}?limit=40&offset=${(page.coerceAtLeast(1) - 1) * 40}&sort=created_at&order=desc")
        val items = (root["data"] as? JsonArray).orEmpty().map { card(it.jsonObject, request.data) }
        return newHomePageResponse(request.name, items, items.size == 40)
    }
    override suspend fun search(query: String): List<SearchResponse> = listOf("movie", "serie").flatMap { kind ->
        val root = api("$kind/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=100&offset=0")
        (root["data"] as? JsonArray).orEmpty().map { card(it.jsonObject, kind) }
    }
    override suspend fun load(url: String): LoadResponse {
        val kind = if (url.contains("/serie/")) "serie" else "movie"
        var id = url.substringAfterLast('/').substringBefore('?')
        if (!Regex("[0-9a-fA-F-]{36}").matches(id)) {
            val page = app.get("$mainUrl/view/$id").text.replace("\\\"", "\"")
            id = Regex("\"movieId\":\"([^\"]+)\"").find(page)?.groupValues?.get(1)
                ?: throw ErrorLoadingException("Unable to find Iwaatch title")
        }
        val item = api("$kind/$id")["data"]!!.jsonObject
        val slug = item.text("slug") ?: id
        val poster = item.text("cover")?.let { SiteHtml.absolute(mainUrl, it) }
        val plot = item.text("description_ar") ?: item.text("description_en")
        if (kind == "movie") return newMovieLoadResponse(item.title(), url, TvType.Movie, "$mainUrl/view/$slug") { posterUrl = poster; this.plot = plot }
        val seasons = api("serie/$id/seasons")["data"] as? JsonArray ?: JsonArray(emptyList())
        val episodes = seasons.flatMap { season ->
            val value = season.jsonObject
            val seasonNumber = value.text("number")?.toIntOrNull() ?: value.text("season_number")?.toIntOrNull() ?: 1
            val seasonId = value.text("id") ?: return@flatMap emptyList()
            val entries = api("serie/$id/seasons/$seasonId/episodes")["data"] as? JsonArray ?: JsonArray(emptyList())
            entries.map { entry ->
                val ep = entry.jsonObject
                val number = ep.text("number")?.toIntOrNull() ?: ep.text("episode_number")?.toIntOrNull()
                newEpisode("$mainUrl/view/serie/$slug?season=$seasonNumber&episode=${number ?: 1}") { name = ep.title(); this.season = seasonNumber; episode = number }
            }
        }
        return newTvSeriesLoadResponse(item.title(), url, TvType.TvSeries, episodes) { posterUrl = poster; this.plot = plot }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        SourcePlayback.webView(name, data, "$mainUrl/", callback)
}
