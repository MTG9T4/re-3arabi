package com.mtg.shooot

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ShoootPlugin : Plugin() {
    override fun load() { registerMainAPI(ShoootProvider()) }
}
