package com.github.noamm9.hatefrance

import com.github.noamm9.NoammAddons
import net.fabricmc.api.ClientModInitializer

object HateFrance : ClientModInitializer {
    override fun onInitializeClient() {
        NoammAddons.logger.info("Hi from ${this.javaClass.simpleName}!")
    }
}
