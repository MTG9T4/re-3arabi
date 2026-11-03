package com.mtg.iwaatch

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IwaatchPlugin : Plugin() {
    override fun load() { registerMainAPI(IwaatchProvider()) }
}
