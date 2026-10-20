package com.github.noamm9.hatefrance.compat

import com.github.noamm9.ui.clickgui.ClickGuiScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { ClickGuiScreen() }
    }
}
