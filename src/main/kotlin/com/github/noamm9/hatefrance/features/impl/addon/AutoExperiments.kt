package com.github.noamm9.hatefrance.features.impl.addon

import com.github.noamm9.config.types.SliderSetting
import com.github.noamm9.config.types.ToggleSetting
import com.github.noamm9.event.impl.ContainerEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.utils.GuiUtils
import com.github.noamm9.utils.items.ItemUtils.hasGlint
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import gg.essential.universal.UMinecraft

object AutoExperiments : Feature(
    name = "AutoExperiments",
    description = "Solves Chronomatron and Ultrasequencer experiments."
) {
    private val clickDelay by SliderSetting("Click Delay", 200, 100, 1000, 10)
    private val delayVariety by SliderSetting("Delay Variety", 50, 0, 1000, 10)
    private val autoClose by ToggleSetting("Auto Close", true)
    private val serumCount by SliderSetting("Serum Count", 0, 0, 3, 1)
    private val getMaxXp by ToggleSetting("Get Max XP", false)

    private val solver = ExperimentSolver()
    private val cells = List(50) { Cell(it) }
    private var nextClickTime = 0L
    private var experimentScreen: AbstractContainerScreen<*>? = null

    override fun init() {
        register<ContainerEvent.Open> {
            solver.open(event.screen.title.string)
            experimentScreen = event.screen.takeIf { solver.active }
            nextClickTime = 0L
        }

        register<ContainerEvent.Close> { reset() }
        register<WorldChangeEvent> { reset() }

        register<ContainerEvent.MouseClick> {
            if (solver.active) event.isCanceled = true
        }

        register<TickEvent.Start> {
            if (!solver.active) return@register
            val currentPlayer = mc.player
            val screen = experimentScreen
            if (currentPlayer == null || screen == null || UMinecraft.currentScreenObj !== screen || currentPlayer.containerMenu !== screen.menu) {
                reset()
                return@register
            }

            val slots = screen.menu.slots
            if (slots.size < cells.size) return@register
            for (i in 9..49) {
                val stack = slots[i].item
                val cell = cells[i]
                cell.itemId = if (stack.isEmpty) "" else BuiltInRegistries.ITEM.getKey(stack.item).toString()
                cell.count = if (stack.isEmpty) 0 else stack.count
                cell.foil = !stack.isEmpty && stack.hasGlint()
            }
            val now = System.currentTimeMillis()
            solver.nextClick(cells, now >= nextClickTime)?.let { slot ->
                GuiUtils.clickSlot(slot, GuiUtils.ButtonType.MIDDLE)
                nextClickTime = now + delay()
            }

            if (autoClose.value && solver.shouldClose(cells, chronomatronTarget(), ultrasequencerTarget())) {
                currentPlayer.closeContainer()
                reset()
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        reset()
    }

    private fun reset() {
        solver.close()
        nextClickTime = 0L
        experimentScreen = null
    }

    private fun delay(): Long {
        val variety = if (delayVariety.value == 0) 0 else (0..delayVariety.value).random()
        return clickDelay.value.toLong() + variety
    }

    private fun chronomatronTarget() = if (getMaxXp.value) 15 else 11 - serumCount.value

    private fun ultrasequencerTarget() = if (getMaxXp.value) 20 else 8 - serumCount.value
}