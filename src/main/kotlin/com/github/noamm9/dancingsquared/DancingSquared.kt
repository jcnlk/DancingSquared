package com.github.noamm9.dancingsquared

import com.github.noamm9.NoammAddons
import net.fabricmc.api.ClientModInitializer

object DancingSquared : ClientModInitializer {
    override fun onInitializeClient() {
        NoammAddons.logger.info("Hi from ${this.javaClass.simpleName}!")
    }
}
