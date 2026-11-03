package com.mtg.sources

import kotlinx.serialization.json.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

enum class Site { MYSEED, SPACEPOWER, ARABICCARTOON, CIMACLUB, IWAATCH }
data class SiteCard(val title: String, val url: String, val poster: String?, val series: Boolean)
data class SiteEpisode(val title: String, val url: String, val number: Int?, val season: Int?, val poster: String?)

object SiteHtml {
    fun searchKey(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKD)
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        .replace(Regex("[أإآٱ]"), "ا").replace('ى', 'ي').lowercase()
    fun absolute(base: String, value: String): String =
        runCatching { URI(base).resolve(value.trim()).toString() }.getOrDefault(value)

    fun poster(element: Element, base: String): String? = element.selectFirst("img")?.let {
        it.attr("data-src").ifBlank { it.attr("src") }.takeIf { value -> value.isNotBlank() }
            ?.let { value -> absolute(base, value) }
    }

    fun cards(doc: Document, site: Site): List<SiteCard> {
        val selector = when (site) {
            Site.MYSEED -> "article.tt-card"
            Site.SPACEPOWER -> ".card__link"
            Site.ARABICCARTOON -> ".streamit-card .image-box[href]"
            Site.CIMACLUB -> ".BlocksHolder .Small--Box"
            Site.IWAATCH -> "a[href^=/movie/],a[href^=/serie/]"
        }
        return doc.select(selector).mapNotNull { element ->
            val link = if (element.hasAttr("href")) element else element.selectFirst("a[href]") ?: return@mapNotNull null
            val href = absolute(doc.location(), link.attr("href"))
            val title = element.selectFirst("inner--title h2,h3,.card__title,.streamit-title")?.text()
                ?: element.selectFirst("img[alt]")?.attr("alt")
                ?: link.attr("title").ifBlank { link.text() }
            if (title.isBlank() || !href.startsWith("http")) return@mapNotNull null
            val series = when (site) {
                Site.MYSEED -> href.contains("/series/") || element.selectFirst(".tt-new") != null || element.text().contains("مسلسل")
                Site.SPACEPOWER -> !href.contains("/movie")
                Site.ARABICCARTOON -> href.contains("/tvshows/")
                Site.CIMACLUB -> element.selectFirst(".number") != null || href.contains("/series/")
                Site.IWAATCH -> href.contains("/serie/")
            }
            SiteCard(title.trim(), href, poster(element, doc.location()), series)
        }.distinctBy { it.url }
    }

    fun episodes(doc: Document, site: Site): List<SiteEpisode> {
        val selector = when (site) {
            Site.MYSEED -> ".tt-episode-card"
            Site.SPACEPOWER -> "#episodes .ep__link,.ep-list .ep__link"
            Site.ARABICCARTOON -> ".episode-card"
            Site.CIMACLUB -> "section.allepcont .row a,.episodes--side--list a"
            Site.IWAATCH -> "a[href*=episode]"
        }
        return doc.select(selector).mapNotNull { element ->
            val link = if (element.hasAttr("href")) element else element.selectFirst("a[href]") ?: return@mapNotNull null
            val href = absolute(doc.location(), link.attr("href"))
            val title = element.selectFirst("strong,.ep__title,.title,.ep-info h2")?.text()
                ?: element.selectFirst("img[alt]")?.attr("alt") ?: element.text()
            val numberText = element.attr("data-episode-number").ifBlank {
                element.selectFirst("small,.ep__num,.episode-number,.epnum")?.text().orEmpty()
            }
            val number = Regex("[0-9]+").find(numberText)?.value?.toIntOrNull()
            val season = element.parents().firstOrNull { it.hasClass("tt-season") }
                ?.selectFirst("h2")?.text()?.let { Regex("[0-9]+").find(it)?.value?.toIntOrNull() }
            if (!href.startsWith("http")) return@mapNotNull null
            SiteEpisode(title.ifBlank { "الحلقة ${number ?: ""}" }, href, number, season, poster(element, doc.location()))
        }.distinctBy { it.url }
    }

    fun restricted(doc: Document) = doc.selectFirst(".restricted-block-episode,.restricted-block,.pmpro_content_message") != null

    fun playerTargets(doc: Document): List<String> {
        if (restricted(doc)) return emptyList()
        val targets = mutableListOf<String>()
        doc.select("video[src],video source[src],.player iframe[src],.entry-content iframe[src],#player iframe[src],iframe[src*=embed],.tt-watch-server[data-src],[data-watch]")
            .forEach { element ->
                listOf("src", "data-src", "data-watch").map { element.attr(it) }
                    .filter { it.isNotBlank() }.forEach { targets.add(absolute(doc.location(), it)) }
            }
        doc.select("script[data-player-sources]").forEach { script ->
            runCatching {
                val servers = Json.parseToJsonElement(script.data()) as JsonArray
                servers.forEach { server ->
                    (server.jsonObject["links"] as? JsonArray)?.forEach { item ->
                        val link = item.jsonObject
                        if (link["broken"]?.jsonPrimitive?.booleanOrNull != true) {
                            link["url"]?.jsonPrimitive?.contentOrNull?.let { targets.add(it) }
                        }
                    }
                }
            }
        }
        val direct = Regex("[\"']?(?:file|source|src|url)[\"']?\\s*[:=]\\s*[\"'](https?[^\"'<>]+\\.(?:m3u8|mp4)(?:\\?[^\"'<>]*)?)[\"']", RegexOption.IGNORE_CASE)
        direct.findAll(doc.html().replace("\\/", "/")).forEach { targets.add(it.groupValues[1]) }
        return targets.filter { it.startsWith("http") && !it.contains("a-ads.com") && !it.contains("secureserver.net") }
            .distinct()
    }

    fun hasNext(doc: Document, page: Int): Boolean = doc.select("a[href]").any { element ->
        val href = element.attr("href")
        Regex("/page/${page + 1}(?:[/#?]|$)|[?&]page=${page + 1}(?:[&#]|$)").containsMatchIn(href)
    }
}
