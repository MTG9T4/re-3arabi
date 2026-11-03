package com.mtg.myseed

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MySeedPlugin : Plugin() {
    override fun load() { registerMainAPI(MySeedProvider()) }
}
