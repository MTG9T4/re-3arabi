package com.mtg.spacepower

import com.lagradost.cloudstream3.mainPageOf
import com.mtg.sources.*

class SpacePowerFanProvider : HtmlCatalogProvider(Site.SPACEPOWER) {
    override var name = "SpacePowerFan"
    override var mainUrl = "https://spacepowerfan.com"
    override val mainPage = mainPageOf(
        "/cartoon/" to "الكرتون",
        "/anime/" to "الأنمي",
        "/series/" to "المسلسلات",
        "/movies/" to "الأفلام",
        "/episodes/" to "أحدث الحلقات",
    )
}
