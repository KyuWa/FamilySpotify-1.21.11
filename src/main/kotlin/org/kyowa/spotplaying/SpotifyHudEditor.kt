package org.kyowa.spotplaying

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class SpotifyHudEditor : Screen(Text.literal("FamilySpotify HUD Editor")) {

    private val scale  get() = SpotifyConfig.data.general.hudScale
    private val bArt   get() = (48 * scale).toInt()
    private val bPad   get() = (6  * scale).toInt()
    private val bLineH get() = (10 * scale).toInt()
    private val bW     get() = ((48 + 6 * 3 + 120) * scale).toInt()
    private val bH     get() = ((48 + 6 * 2 + 14)  * scale).toInt()

    private var dragging = false
    private var dragOffX = 0.0
    private var dragOffY = 0.0

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0x88000000.toInt())
        val tr   = MinecraftClient.getInstance().textRenderer
        val hint = "§7Drag to move  |  §eScroll §7to resize  |  §eEsc §7to save"
        context.drawText(tr, hint, (width - tr.getWidth(hint.replace(Regex("§."), ""))) / 2, 8, 0xFFFFFFFF.toInt(), false)

        if (dragging) {
            SpotifyConfig.data.general.hudX = (mouseX - dragOffX).toInt().coerceIn(0, width  - bW)
            SpotifyConfig.data.general.hudY = (mouseY - dragOffY).toInt().coerceIn(0, height - bH)
        }

        val x = SpotifyConfig.data.general.hudX
        val y = SpotifyConfig.data.general.hudY

        context.fill(x, y, x + bW, y + bH, 0xCC111111.toInt())
        context.fill(x + bPad, y + bPad, x + bPad + bArt, y + bPad + bArt, 0xFF1DB954.toInt())
        context.drawText(tr, "♪", x + bPad + bArt / 2 - 3, y + bPad + bArt / 2 - 4, 0xFFFFFFFF.toInt(), true)
        val tx = x + bPad + bArt + bPad
        context.drawText(tr, "Song Title", tx, y + bPad,               0xFFFFFFFF.toInt(), true)
        context.drawText(tr, "§7Artist",   tx, y + bPad + bLineH + 2,  0xFFFFFFFF.toInt(), true)

        // Scale label
        context.drawText(tr, "§7${"%.1f".format(scale)}x", x, y + bH + 3, 0xFFFFFFFF.toInt(), true)

        // Border
        context.fill(x - 1, y - 1, x + bW + 1, y,          0xFFFFFF00.toInt())
        context.fill(x - 1, y + bH, x + bW + 1, y + bH + 1, 0xFFFFFF00.toInt())
        context.fill(x - 1, y, x, y + bH,                   0xFFFFFF00.toInt())
        context.fill(x + bW, y, x + bW + 1, y + bH,         0xFFFFFF00.toInt())

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val delta = if (verticalAmount > 0) 0.1f else -0.1f
        SpotifyConfig.data.general.hudScale = "%.1f".format((scale + delta).coerceIn(0.5f, 3.0f)).toFloat()
        return true
    }

    fun onMousePress(mouseX: Double, mouseY: Double) {
        val x = SpotifyConfig.data.general.hudX
        val y = SpotifyConfig.data.general.hudY
        if (mouseX >= x && mouseX <= x + bW && mouseY >= y && mouseY <= y + bH) {
            dragging = true; dragOffX = mouseX - x; dragOffY = mouseY - y
        }
    }

    fun onMouseRelease() { dragging = false }

    override fun close() { SpotifyConfig.save(); super.close() }
    override fun shouldPause() = false
}
