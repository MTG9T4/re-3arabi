package com.mtg.myseed

import com.lagradost.cloudstream3.mainPageOf
import com.mtg.sources.*

class MySeedProvider : HtmlCatalogProvider(Site.MYSEED) {
    override var name = "MySeed"
    override var mainUrl = "https://myseed.cam"
    override val mainPage = mainPageOf(
        "/films" to "الأفلام",
        "/seriestv" to "المسلسلات",
        "/episodestv" to "أحدث الحلقات",
        "/category/افلام-كرتون" to "أفلام كرتون",
    )
}
