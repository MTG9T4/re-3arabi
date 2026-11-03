package com.mtg.shooot

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.mtg.sources.*
import kotlinx.serialization.json.*

class ShoootProvider : MainAPI() {
    override var name = "Shooot"
    override var mainUrl = "https://shooot.me"
    override var lang = "ar"
    override val hasMainPage = true
    override val usesWebView = true
    override val supportedTypes = setOf(TvType.Live)
    override val mainPage = mainPageOf("today" to "مباريات اليوم")
    private val api = "https://cdn.kora-api.org"
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.team(key: String) = (this[key] as? JsonObject)?.let { it.text("name_ar") ?: it.text("name") }.orEmpty()
    private suspend fun catalog() = PublicSportsJson.decode(app.get("$api/api/v1/matches", headers = mapOf("User-Agent" to SOURCE_UA, "Referer" to "$mainUrl/", "Accept" to "application/json")).text).jsonObject
    private suspend fun matches(): List<JsonObject> = (catalog()["matches"] as? JsonArray).orEmpty().map { it.jsonObject }
    private fun card(match: JsonObject): SearchResponse {
        val live = match.text("status") in listOf("1", "3")
        val label = if (live) "مباشر" else match.text("time").orEmpty()
        return newLiveSearchResponse("${match.team("home")} × ${match.team("away")} — $label", "$mainUrl/match/${match.text("id")}") {
            posterUrl = (match["home"] as? JsonObject)?.text("logo")?.let { SiteHtml.absolute("$api/", it) }
        }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest) = newHomePageResponse(request.name, matches().map { card(it) }, false)
    override suspend fun search(query: String) = matches().filter { "${it.team("home")} ${it.team("away")}".contains(query, true) }.map { card(it) }
    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast('/')
        val match = matches().firstOrNull { it.text("id") == id } ?: throw ErrorLoadingException("This match is no longer on today's schedule")
        return newLiveStreamLoadResponse("${match.team("home")} × ${match.team("away")}", url, id) { plot = "${match.text("date")} ${match.text("time")} UTC\n${match.text("status_label")}" }
    }
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val root = catalog()
        val match = (root["matches"] as? JsonArray).orEmpty().map { it.jsonObject }.firstOrNull { it.text("id") == data }
            ?: throw ErrorLoadingException("This match is no longer available")
        if (match.text("has_channels") !in listOf("1", "true")) throw ErrorLoadingException("The stream has not opened yet. Check again near kick-off.")
        val domain = match.text("live_domain") ?: root.text("live_domain") ?: throw ErrorLoadingException("No published player for this match")
        return SourcePlayback.webView(name, "https://$domain/kora.html?m=$data&ref=shooot.me", "$mainUrl/", callback)
    }
}
