package com.github.noamm9.dancingsquared.features.impl.macro

import com.github.noamm9.commands.CommandBuilder
import com.github.noamm9.config.ConfigManager
import com.github.noamm9.config.types.KeybindSetting
import com.github.noamm9.config.types.SliderSetting
import com.github.noamm9.config.types.TextInputSetting
import com.github.noamm9.config.types.ToggleSetting
import com.github.noamm9.event.impl.MouseClickEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.init.types.ICommandProvider
import com.github.noamm9.utils.ChatUtils
import com.github.noamm9.utils.MathUtils
import com.github.noamm9.utils.PlayerUtils
import com.github.noamm9.utils.items.ItemUtils.itemUUID
import com.github.noamm9.utils.items.ItemUtils.skyblockId
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import gg.essential.universal.UMinecraft
import kotlinx.coroutines.*
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.world.phys.BlockHitResult
import java.util.Random
import kotlin.time.Duration.Companion.milliseconds

// https://github.com/jcnlk/quoi/blob/multiversion/src/main/kotlin/quoi/module/impl/general/AutoClicker.kt
object AutoClicker : Feature(
    name = "Auto Clicker",
    description = "Automatically clicks while the configured mouse or keyboard key is held."
), ICommandProvider {
    private val breakBlocks by ToggleSetting("Allow Breaking Blocks", false)
        .withDescription("Keeps left click held while looking at a breakable block.")
    private val stopOnSwap by ToggleSetting("Stop On Item Swap", false)
        .withDescription("Stops both clickers when the selected hotbar slot changes.")
    private val favouriteItems by ToggleSetting("Whitelist Only", false)
        .withDescription("Only clicks while holding an item listed below.")
    private val favLeft by TextInputSetting("Left Click Whitelist", "")
        .withDescription("Comma-separated item UUIDs, SkyBlock IDs, or display names.")
        .showIf { favouriteItems.value }
    private val favRight by TextInputSetting("Right Click Whitelist", "")
        .withDescription("Comma-separated item UUIDs, SkyBlock IDs, or display names.").showIf { favouriteItems.value }

    private val blockDungeonBreaker by ToggleSetting("Block Dungeon Breaker", true)
        .withDescription("Prevents clicking while holding Dungeon Breaker.")

    private val leftClick by ToggleSetting("Enable Left Click", false)
    private val leftCpsMin by SliderSetting("Left CPS Min", 10, 1, 20, 1).showIf { leftClick.value }
    private val leftCpsMax by SliderSetting("Left CPS Max", 12, 1, 20, 1).showIf { leftClick.value }
    private val leftClickKeybind by KeybindSetting("Left Click Keybind", 0).apply { isMouse = true }

    private val rightClick by ToggleSetting("Enable Right Click", false)
    private val rightCpsMin by SliderSetting("Right CPS Min", 10, 1, 20, 1).showIf { rightClick.value }
    private val rightCpsMax by SliderSetting("Right CPS Max", 12, 1, 20, 1).showIf { rightClick.value }
    private val rightClickKeybind by KeybindSetting("Right Click Keybind", 1).apply { isMouse = true }

    private var leftJob: Job? = null
    private var rightJob: Job? = null
    private var lastHeldSlot = -1
    private var isMining = false
    private var wasEnabled = false
    private val random = Random()
    private val leftTiming = ClickTiming()
    private val rightTiming = ClickTiming()

    private data class ClickTiming(
        var lastDriftTime: Long = 0L,
        var baseCpsDrift: Double = 10.0
    )

    override fun init() {
        register<MouseClickEvent> {
            if (event.action != 1) return@register
            if (event.button !in 0..1 || currentScreen != null) return@register

            val isLeft = event.button == 0
            val enabledForButton = if (isLeft) leftClick.value else rightClick.value
            val keybind = if (isLeft) leftClickKeybind else rightClickKeybind
            if (enabledForButton && keybind.matches(event.button, true) && shouldClick(isLeft)) {
                event.isCanceled = true
            }
        }

        register<TickEvent.End> {
            val currentPlayer = mc.player ?: run {
                reset()
                return@register
            }

            val currentSlot = currentPlayer.inventory.selectedSlot
            if (lastHeldSlot == -1) lastHeldSlot = currentSlot
            if (stopOnSwap.value && currentSlot != lastHeldSlot) reset()
            lastHeldSlot = currentSlot

            if (currentScreen != null) {
                reset()
                return@register
            }

            if (shouldAutoClick(true)) startClicking(true) else stopClicking(true)
            if (shouldAutoClick(false)) startClicking(false) else stopClicking(false)
            updateMiningState()
        }

        register<WorldChangeEvent> { reset() }
    }

    override fun onDisable() {
        if (wasEnabled) reset()
        super.onDisable()
    }

    override fun onEnable() {
        wasEnabled = true
        super.onEnable()
    }

    override fun CommandBuilder.command() {
        setName("autoclicker", "ac")
        description("Manage the Auto Clicker whitelist")
        whitelistAction("add", WhitelistAction.ADD)
        whitelistAction("remove", WhitelistAction.REMOVE)
        whitelistAction("clear", WhitelistAction.CLEAR)
    }

    @Suppress("unused")
    private enum class WhitelistAction { ADD, REMOVE, CLEAR }

    private fun CommandBuilder.whitelistAction(name: String, action: WhitelistAction) {
        literal(name) {
            argument("button", StringArgumentType.word()) {
                suggests { listOf("left", "right") }
                runs { context -> updateWhitelist(context, action) }
            }
        }
    }

    private fun updateWhitelist(
        context: CommandContext<FabricClientCommandSource>,
        action: WhitelistAction
    ) {
        val side = when (StringArgumentType.getString(context, "button").lowercase()) {
            "left" -> WhitelistSide.LEFT
            "right" -> WhitelistSide.RIGHT
            else -> null
        }
        if (side == null) {
            ChatUtils.modMessage("&cUse left or right as the button.")
            return
        }

        val values = whitelist(side).toMutableSet()
        val item = heldItemKey()
        val message = when (action) {
            WhitelistAction.ADD -> {
                if (item == null) "&cYou are not holding an item."
                else if (!values.add(item)) "&cThis item is already in the ${side.label} list."
                else {
                    side.setting.value = values.sorted().joinToString(", ")
                    "&aAdded ${itemDisplayName()} &ato the ${side.label} list."
                }
            }

            WhitelistAction.REMOVE -> {
                if (item == null) "&cYou are not holding an item."
                else if (!values.remove(item)) "&cThis item is not in the ${side.label} list."
                else {
                    side.setting.value = values.sorted().joinToString(", ")
                    "&aRemoved ${itemDisplayName()} &afrom the ${side.label} list."
                }
            }

            WhitelistAction.CLEAR -> {
                side.setting.value = ""
                "&aCleared the ${side.label} list."
            }
        }
        ConfigManager.save()
        ChatUtils.modMessage(message)
    }

    private fun heldItemKey(): String? {
        val stack = mc.player?.mainHandItem?.takeUnless { it.isEmpty } ?: return null
        return stack.itemUUID.takeUnless(String::isBlank)
            ?: stack.skyblockId.takeUnless(String::isBlank)
            ?: stack.hoverName.string.takeUnless(String::isBlank)
    }

    private fun itemDisplayName(): String =
        mc.player?.mainHandItem?.hoverName?.string ?: "item"

    private fun whitelistValues(setting: TextInputSetting): Set<String> =
        setting.value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private fun shouldClick(isLeft: Boolean): Boolean {
        if (currentScreen != null) return false
        if (blockDungeonBreaker.value && player.mainHandItem.skyblockId == "DUNGEONBREAKER") {
            return false
        }

        if (!favouriteItems.value) return true
        val held = heldItemKey() ?: return false
        return held in whitelistValues(if (isLeft) favLeft else favRight)
    }

    private fun shouldAutoClick(isLeft: Boolean): Boolean {
        val enabledForButton = if (isLeft) leftClick else rightClick
        val keybind = if (isLeft) leftClickKeybind else rightClickKeybind
        return enabled && enabledForButton.value && keybind.isDown() && shouldClick(isLeft)
    }

    private val lookingAtBreakable: Boolean
        get() {
            if (!breakBlocks.value) return false
            val hit = mc.hitResult as? BlockHitResult ?: return false
            val level = mc.level ?: return false
            val state = level.getBlockState(hit.blockPos)
            return !state.isAir && state.fluidState.isEmpty
        }

    private val currentScreen
        get() = UMinecraft.currentScreenObj

    @Suppress("unused")
    internal enum class WhitelistSide(val label: String) {
        LEFT("left"), RIGHT("right");

        val setting: TextInputSetting
            get() = if (this == LEFT) favLeft else favRight
    }

    private fun whitelist(side: WhitelistSide): Set<String> = whitelistValues(side.setting)

    private fun startClicking(isLeft: Boolean) {
        if (isLeft) {
            if (leftJob?.isActive == true) return
            leftJob = scope.launch { click(true) }
        } else {
            if (rightJob?.isActive == true) return
            rightJob = scope.launch { click(false) }
        }
    }

    private fun stopClicking(isLeft: Boolean) {
        if (isLeft) {
            leftJob?.cancel()
            leftJob = null
        } else {
            rightJob?.cancel()
            rightJob = null
        }
    }

    private fun updateMiningState() {
        val shouldMine = leftJob?.isActive == true && lookingAtBreakable
        if (shouldMine == isMining) return

        mc.options.keyAttack.isDown = shouldMine
        isMining = shouldMine
    }

    private fun getNextClick(now: Long, isLeft: Boolean): Long {
        val timing = if (isLeft) leftTiming else rightTiming
        if (now - timing.lastDriftTime > 1000) {
            val minCps = if (isLeft) leftCpsMin else rightCpsMin
            val maxCps = if (isLeft) leftCpsMax else rightCpsMax
            val targetCps = ((minCps.value + maxCps.value) / 2.0).coerceAtLeast(1.0)
            timing.baseCpsDrift = (targetCps + MathUtils.gaussianRandom(-10, 10) / 10.0).coerceAtLeast(1.0)
            timing.lastDriftTime = now
        }

        val baseDelay = (1000.0 / timing.baseCpsDrift).toLong()
        val gaussian = random.nextGaussian()
        val offset = if (gaussian < 0) (gaussian * 10).toLong() else (gaussian * 25).toLong()
        var finalDelay = baseDelay + offset

        val roll = random.nextDouble()
        if (roll < 0.01) finalDelay += random.nextInt(100, 250)
        else if (roll < 0.03) finalDelay = random.nextInt(5, 15).toLong()

        return now + finalDelay.coerceAtLeast(1L)
    }

    private suspend fun click(isLeft: Boolean) {
        val job = currentCoroutineContext()[Job] ?: return

        while (job.isActive) {
            val now = System.currentTimeMillis()
            val nextClick = CompletableDeferred<Long>()
            mc.execute {
                if (!job.isActive || !shouldAutoClick(isLeft)) {
                    nextClick.complete(-1L)
                    return@execute
                }

                if (!isLeft) PlayerUtils.rightClick()
                else if (!lookingAtBreakable) PlayerUtils.leftClick()

                nextClick.complete(getNextClick(now, isLeft))
            }

            val clickAt = nextClick.await()
            if (clickAt < 0) return
            delay((clickAt - System.currentTimeMillis()).coerceAtLeast(1L).milliseconds)
        }
    }

    private fun reset() {
        stopClicking(true)
        stopClicking(false)
        mc.options.keyAttack.isDown = false
        isMining = false
        lastHeldSlot = -1
    }
}
