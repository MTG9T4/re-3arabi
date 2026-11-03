package com.mtg.onlinesport

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OnlineSportLivePlugin : Plugin() {
    override fun load() { registerMainAPI(OnlineSportLiveProvider()) }
}
