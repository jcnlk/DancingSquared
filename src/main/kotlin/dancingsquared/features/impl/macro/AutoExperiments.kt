package dancingsquared.features.impl.macro

import com.github.noamm9.config.types.SliderSetting
import com.github.noamm9.config.types.ToggleSetting
import com.github.noamm9.event.impl.ContainerEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.utils.GuiUtils
import com.github.noamm9.utils.items.ItemUtils.hasGlint
import net.minecraft.core.registries.BuiltInRegistries

object AutoExperiments : Feature(
    name = "AutoExperiments",
    description = "Solves Chronomatron and Ultrasequencer experiments."
) {
    private val clickDelay by SliderSetting("Click Delay", 200, 100, 1000, 10)
    private val delayVariety by SliderSetting("Delay Variety", 50, 0, 1000, 10)
    private val autoClose by ToggleSetting("Auto Close", true)
    private val serumCount by SliderSetting("Serum Count", 0, 0, 3, 1)
    private val getMaxXp by ToggleSetting("Get Max XP", false)

    private val solver = Solver()
    private var lastClickTime = 0L

    override fun init() {
        register<ContainerEvent.Open> {
            solver.open(event.screen.title.string)
            lastClickTime = 0L
        }

        register<ContainerEvent.Close> { reset() }

        register<ContainerEvent.MouseClick> {
            if (solver.active) event.isCanceled = true
        }

        register<TickEvent.Start> {
            if (!solver.active) return@register

            val cells = player.containerMenu.slots.map { slot ->
                val stack = slot.item
                Cell(
                    slot = slot.index,
                    itemId = if (stack.isEmpty) "" else BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                    count = if (stack.isEmpty) 0 else stack.count,
                    foil = !stack.isEmpty && stack.hasGlint()
                )
            }
            val now = System.currentTimeMillis()
            solver.nextClick(cells, now, lastClickTime, delay())?.let { slot ->
                GuiUtils.clickSlot(slot, GuiUtils.ButtonType.MIDDLE)
                lastClickTime = now
            }

            if (autoClose.value && solver.shouldClose(cells, chronomatronTarget(), ultrasequencerTarget())) {
                player.closeContainer()
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
        lastClickTime = 0L
    }

    private fun delay(): Long {
        val variety = if (delayVariety.value == 0) 0 else (0..delayVariety.value).random()
        return clickDelay.value.toLong() + variety
    }

    private fun chronomatronTarget() = if (getMaxXp.value) 15 else 11 - serumCount.value

    private fun ultrasequencerTarget() = if (getMaxXp.value) 20 else 9 - serumCount.value

    internal data class Cell(
        val slot: Int,
        val itemId: String,
        val count: Int,
        val foil: Boolean
    )

    private class Solver {
        enum class Mode { NONE, CHRONOMATRON, ULTRASEQUENCER }

        private var mode = Mode.NONE
        private val chronomatron = mutableListOf<Int>()
        private var chronoRevealSeen = false
        private var chronoIndex = 0
        private var chronoRoundComplete = false
        private val ultrasequencer = mutableMapOf<Int, Int>()
        private var ultraIndex = 0

        val active get() = mode != Mode.NONE

        fun open(title: String) {
            mode = when {
                title.startsWith("Chronomatron (") -> Mode.CHRONOMATRON
                title.startsWith("Ultrasequencer (") -> Mode.ULTRASEQUENCER
                else -> Mode.NONE
            }
            clear()
        }

        fun close() {
            mode = Mode.NONE
            clear()
        }

        fun nextClick(cells: List<Cell>, now: Long, lastClick: Long, delay: Long): Int? {
            val canClick = now - lastClick >= delay
            return when (mode) {
                Mode.CHRONOMATRON -> nextChronomatron(cells, canClick)
                Mode.ULTRASEQUENCER -> nextUltrasequencer(cells, canClick)
                Mode.NONE -> null
            }
        }

        fun shouldClose(cells: List<Cell>, chronoTarget: Int, ultraTarget: Int): Boolean {
            return when (mode) {
                Mode.CHRONOMATRON -> chronoRoundComplete && chronomatron.size > chronoTarget
                Mode.ULTRASEQUENCER -> cell(cells)?.itemId == "minecraft:clock" &&
                    ultraIndex >= ultrasequencer.size && ultrasequencer.size >= ultraTarget
                Mode.NONE -> false
            }
        }

        private fun nextChronomatron(cells: List<Cell>, canClick: Boolean): Int? {
            val control = cell(cells)?.itemId ?: return null
            if (control == "minecraft:glowstone") {
                if (chronoRevealSeen && chronoIndex >= chronomatron.size) chronoRoundComplete = true
                chronoRevealSeen = false
                chronoIndex = 0
                return null
            }
            if (control != "minecraft:clock") return null

            cells.firstOrNull { it.slot in 10..43 && it.foil }?.let { reveal ->
                if (!chronoRevealSeen) {
                    chronomatron += reveal.slot
                    chronoRevealSeen = true
                    chronoRoundComplete = false
                    chronoIndex = 0
                }
            }
            return chronomatron.getOrNull(chronoIndex)?.takeIf { canClick }?.also { chronoIndex++ }
        }

        private fun nextUltrasequencer(cells: List<Cell>, canClick: Boolean): Int? {
            val control = cell(cells)?.itemId ?: return null
            if (control == "minecraft:glowstone") {
                ultrasequencer.clear()
                cells.asSequence()
                    .filter { it.slot in 9..44 && isSequenceItem(it) }
                    .sortedBy(Cell::count)
                    .forEach { ultrasequencer[it.count - 1] = it.slot }
                ultraIndex = 0
                return null
            }
            if (control != "minecraft:clock" || !canClick) return null
            return ultrasequencer[ultraIndex++]
        }

        private fun clear() {
            chronomatron.clear()
            chronoRevealSeen = false
            chronoIndex = 0
            chronoRoundComplete = false
            ultrasequencer.clear()
            ultraIndex = 0
        }

        private fun cell(cells: List<Cell>) = cells.firstOrNull { it.slot == 49 }

        private fun isSequenceItem(cell: Cell): Boolean {
            val item = cell.itemId.substringAfter(':')
            return item.endsWith("_dye") || item == "lapis_lazuli" || item == "bone_meal"
        }
    }
}