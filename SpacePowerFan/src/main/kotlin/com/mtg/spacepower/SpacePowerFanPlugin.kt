package com.mtg.spacepower

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SpacePowerFanPlugin : Plugin() {
    override fun load() { registerMainAPI(SpacePowerFanProvider()) }
}
