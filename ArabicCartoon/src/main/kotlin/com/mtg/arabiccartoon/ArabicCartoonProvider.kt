package com.mtg.arabiccartoon

import android.webkit.CookieManager
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.SearchResponse
import com.mtg.sources.*

class ArabicCartoonProvider : HtmlCatalogProvider(Site.ARABICCARTOON) {
    override var name = "ArabicCartoon"
    override var mainUrl = "https://arabiccartoon.org"
    override val mainPage = mainPageOf(
        "/home/" to "كرتون عربي",
        "/tv-shows-2/" to "مسلسلات كرتون",
        "/movies-أفلام-كرتون/" to "أفلام كرتون",
    )
    private var cachedCatalog: Pair<Long, List<SiteCard>>? = null

    override suspend fun search(query: String): List<SearchResponse> {
        val cached = cachedCatalog?.takeIf { System.currentTimeMillis() - it.first < 15 * 60 * 1000L }
        val items = cached?.second ?: mainPage.flatMap {
            SiteHtml.cards(document("$mainUrl${it.data}"), Site.ARABICCARTOON)
        }.distinctBy { it.url }.also { cachedCatalog = System.currentTimeMillis() to it }
        val key = SiteHtml.searchKey(query)
        return items.filter { SiteHtml.searchKey(it.title).contains(key) }.map { card(it) }
    }

    override fun headers(): Map<String, String> {
        val cookie = CookieManager.getInstance().getCookie(mainUrl)
        return super.headers() + if (cookie.isNullOrBlank()) emptyMap() else mapOf("Cookie" to cookie)
    }
}
