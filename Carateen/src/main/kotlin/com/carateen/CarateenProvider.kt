package com.carateen

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val UA =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
private const val PAGE_SIZE = 40
private const val CATALOG_TTL_MS = 15 * 60 * 1000L

class CarateenProvider : MainAPI() {
    override var name = "Carateen"
    override var mainUrl = "https://carateen.tv"
    override var lang = "ar"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.Live)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "tg" to "كرتون أيام زمان",
        "sp" to "سبيستون غو",
        "live" to "البث المباشر",
    )

    private data class Catalog(val items: List<JsonObject>, val fetchedAt: Long)
    private val shows = java.util.concurrent.ConcurrentHashMap<String, Catalog>()

    private fun cached(kind: String) = shows[kind]
        ?.takeIf { System.currentTimeMillis() - it.fetchedAt < CATALOG_TTL_MS }?.items

    private val requestHeaders get() = mapOf(
        "User-Agent" to UA,
        "Referer" to "$mainUrl/",
        "Accept" to "application/json",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data == "live") {
            val items = getChannels().mapNotNull { it.toLiveSearch() }
            return newHomePageResponse(request.name, items, false)
        }
        val catalog = getShows(request.data)
        val offset = (page.coerceAtLeast(1) - 1) * PAGE_SIZE
        val items = catalog.drop(offset).take(PAGE_SIZE).mapNotNull { it.toSearch(request.data) }
        return newHomePageResponse(request.name, items, offset + PAGE_SIZE < catalog.size)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return listOf("tg", "sp").flatMap { kind ->
            getShows(kind)
                .filter { it.title(kind)?.contains(q, ignoreCase = true) == true }
                .mapNotNull { it.toSearch(kind) }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val parts = url.substringAfter("$mainUrl/").split("|")
        val kind = parts.getOrNull(0) ?: return null
        val showId = parts.getOrNull(1) ?: return null

        if (kind == "live") {
            val channel = getChannels().firstOrNull { it.str("id") == showId } ?: return null
            val channelName = channel.str("name") ?: return null
            return newLiveStreamLoadResponse(channelName, url, url) {
                this.posterUrl = channel.str("logo")
                    ?.let { if (it.startsWith("http")) it else "$mainUrl$it" }
            }
        }
        val show = getShows(kind).firstOrNull { it.str("id") == showId } ?: return null
        val title = show.title(kind) ?: return null
        val poster = show.poster(kind)
        val plot = show.plot(kind)
        val score = show.str("rating")?.toDoubleOrNull()?.let { Score.from5(it) }
        val tags = listOfNotNull(show.str(if (kind == "sp") "planet_name" else "category"))

        if (show.isMovie(kind)) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.score = score
                this.tags = tags
                this.year = show.year(kind)
            }
        }

        val episodes = fetchEpisodes(kind, showId).mapNotNull { ep ->
            val epId = ep.episodeId(kind) ?: return@mapNotNull null
            newEpisode("$kind|$showId|$epId") {
                this.name = ep.str(if (kind == "sp") "pref" else "title")
                this.episode = ep.str(if (kind == "sp") "number" else "order_id")?.toIntOrNull()
                this.season = ep.str("season")?.toIntOrNull()?.takeIf { it > 0 }
                this.posterUrl = ep.episodePoster(kind) ?: poster
                if (kind == "sp") this.description = ep.str("season") // arc name, e.g. "آرك زعيم الدمار بيروس"
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
            this.score = score
            this.tags = tags
            this.year = show.year(kind)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val parts = data.substringAfter("$mainUrl/").split("|")
        val kind = parts.getOrNull(0) ?: return false
        val showId = parts.getOrNull(1) ?: return false

        if (kind == "live") {
            val channel = getChannels().firstOrNull { it.str("id") == showId } ?: return false
            val streamUrl = channel.str("url") ?: return false
            val link = if (streamUrl.startsWith("http")) streamUrl else "$mainUrl$streamUrl"
            val headers = if (channel.str("isLegacy") == "true") mapOf("User-Agent" to UA)
            else requestHeaders
            callback(
                newExtractorLink(name, channel.str("name") ?: name, link, ExtractorLinkType.M3U8) {
                    this.headers = headers
                    this.referer = if (channel.str("isLegacy") == "true") "" else "$mainUrl/"
                },
            )
            return true
        }
        val targets: List<Pair<String, String?>> = parts.getOrNull(2)?.let { listOf(it to null) }
            ?: fetchEpisodes(kind, showId).mapNotNull { ep ->
                val epId = ep.episodeId(kind) ?: return@mapNotNull null
                epId to (ep.str(if (kind == "sp") "pref" else "title")
                    ?: ep.str(if (kind == "sp") "number" else "order_id"))
            }

        var found = false
        for ((epId, edition) in targets) {
            val link = if (kind == "sp") spLink(epId) else tgLink(epId, showId)
            if (link == null) continue
            val label = if (targets.size > 1 && edition != null) "$name - $edition" else name
            callback(
                newExtractorLink(name, label, link, ExtractorLinkType.M3U8) {
                    this.headers = requestHeaders
                    this.referer = "$mainUrl/"
                },
            )
            found = true
        }
        return found
    }

    private suspend fun getShows(kind: String): List<JsonObject> = cached(kind) ?: run {
        val path = if (kind == "sp") "/api/sp/tvshows" else "/api/tvshows"
        val list = (get(path) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        if (list.isNotEmpty()) shows[kind] = Catalog(list, System.currentTimeMillis())
        list
    }

    private suspend fun getChannels(): List<JsonObject> = cached("live") ?: run {
        val list = ((get("/api/sp/live") as? JsonObject)?.get("channels") as? JsonArray)
            ?.mapNotNull { it as? JsonObject } ?: emptyList()
        if (list.isNotEmpty()) shows["live"] = Catalog(list, System.currentTimeMillis())
        list
    }

    private suspend fun fetchEpisodes(kind: String, showId: String): List<JsonObject> {
        val path = if (kind == "sp") "/api/sp/episodes?id=$showId" else "/api/episodes?id=$showId"
        return (get(path) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    }

    private suspend fun spLink(episodeId: String): String? =
        (post("/api/sp/episode/link", mapOf("episodeId" to episodeId)) as? JsonObject)?.str("link")

    private suspend fun tgLink(episodeId: String, showId: String): String? =
        (get("/api/episode?episodeId=$episodeId&showId=$showId") as? JsonObject)?.str("streamUrl")

    private suspend fun get(path: String): JsonElement =
        CarateenJson.decode(app.get("$mainUrl$path", headers = requestHeaders).text)

    private suspend fun post(path: String, body: Map<String, String>): JsonElement =
        CarateenJson.decode(app.post("$mainUrl$path", json = body, headers = requestHeaders).text)

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.title(kind: String) = str(if (kind == "sp") "name" else "title")

    private fun JsonObject.poster(kind: String) =
        if (kind == "sp") str("cover_full_path")
        else str("poster_cover")?.let { "$mainUrl/assets/img/posters/$it" }

    private fun JsonObject.plot(kind: String) = str(if (kind == "sp") "pref" else "description")

    private fun JsonObject.year(kind: String) =
        if (kind == "sp") null else str("release_year")?.take(4)?.toIntOrNull()

    private fun JsonObject.isMovie(kind: String) =
        if (kind == "sp") str("is_movie") == "1" else str("episodes_count") == "1"

    private fun JsonObject.episodeId(kind: String) =
        if (kind == "sp") str("slug") ?: str("id") else str("id")

    private fun JsonObject.episodePoster(kind: String) =
        if (kind == "sp") str("cover_full_path")
        else str("thumbnail")?.let { "$mainUrl/assets/img/thumbnails/$it" }

    private fun JsonObject.toSearch(kind: String): SearchResponse? {
        val id = str("id") ?: return null
        val title = title(kind) ?: return null
        val poster = poster(kind)
        val score = str("rating")?.toDoubleOrNull()?.let { Score.from5(it) }
        val data = "$kind|$id"

        return if (isMovie(kind)) {
            newMovieSearchResponse(title, data, TvType.Movie) {
                this.posterUrl = poster
                this.score = score
            }
        } else {
            newTvSeriesSearchResponse(title, data, TvType.TvSeries) {
                this.posterUrl = poster
                this.score = score
            }
        }
    }

    private fun JsonObject.toLiveSearch(): SearchResponse? {
        val id = str("id") ?: return null
        val channelName = str("name") ?: return null
        return newLiveSearchResponse(channelName, "live|$id") {
            this.posterUrl = str("logo")?.let { if (it.startsWith("http")) it else "$mainUrl$it" }
        }
    }
}
