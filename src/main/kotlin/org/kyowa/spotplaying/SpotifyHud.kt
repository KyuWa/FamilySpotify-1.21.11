package org.kyowa.spotplaying

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.io.ByteArrayInputStream

object SpotifyHud {

    private const val ART    = 48
    private const val PAD    = 6
    private const val LINE   = 10
    private const val TEXT_W = 130
    private const val BASE_W = ART + PAD * 3 + TEXT_W
    private const val BASE_H = 58

    private val scale get() = SpotifyConfig.data.general.hudScale
    val boxW get() = (BASE_W * scale).toInt()
    val boxH get() = (BASE_H * scale).toInt()

    var currentX = -300f
    private val targetX get() = SpotifyConfig.data.general.hudX.toFloat()

    private var titleOffset  = 0f
    private var artistOffset = 0f
    private var titleDir     = 1
    private var artistDir    = 1
    private var titlePause   = 100
    private var artistPause  = 100
    private const val SCROLL_SPEED = 0.4f
    private const val PAUSE_TICKS  = 100

    private var volumeLevel       = 50
    private var volumeDisplayTick = 0
    private const val VOLUME_SHOW_TICKS = 80

    data class RenderedBtn(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val action: () -> Unit)
    val renderedButtons = mutableListOf<RenderedBtn>()

    private var albumTexture: NativeImageBackedTexture? = null
    private var albumTextureId: Identifier? = null
    private var lastLoadedArtUrl: String? = null
    private var lastTrackTitle: String? = null

    fun showVolume(vol: Int) {
        volumeLevel = vol
        volumeDisplayTick = VOLUME_SHOW_TICKS
    }

    fun register() {
        HudRenderCallback.EVENT.register { context, _ ->
            if (!SpotifyConfig.data.general.hudEnabled) return@register

            val track  = SpotifyApi.current
            val target = if (track != null || SpotifyConfig.hasRefreshToken()) targetX else -300f
            currentX += (target - currentX) * 0.15f
            if (track == null && currentX < -290f) { renderedButtons.clear(); return@register }

            val screenX  = currentX.toInt()
            val screenY  = SpotifyConfig.data.general.hudY
            val sc       = scale
            val renderer = MinecraftClient.getInstance().textRenderer

            val artBytes = SpotifyApi.albumArtCache
            val artUrl   = SpotifyApi.albumArtUrl
            if (artBytes != null && artUrl != null && artUrl != lastLoadedArtUrl) {
                updateAlbumTexture(artBytes, artUrl)
            }

            val matrices = context.matrices
            matrices.pushMatrix()
            matrices.translate(screenX.toFloat(), screenY.toFloat())
            matrices.scale(sc, sc)

            context.fill(0, 0, BASE_W, BASE_H, SpotifyConfig.bgColor())

            val texId = albumTextureId
            if (texId != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texId, PAD, PAD, 0f, 0f, ART, ART, ART, ART)
            } else {
                context.fill(PAD, PAD, PAD + ART, PAD + ART, SpotifyConfig.artBgColor())
                context.drawText(renderer, "♪", PAD + ART / 2 - 3, PAD + ART / 2 - 4, 0xFFFFFFFF.toInt(), true)
            }

            val tx       = PAD + ART + PAD
            val maxTextW = TEXT_W
            val title    = track?.title  ?: "Not playing"
            val artist   = track?.artist ?: ""

            if (title != lastTrackTitle) {
                titleOffset = 0f; artistOffset = 0f
                titleDir = 1; artistDir = 1
                titlePause = PAUSE_TICKS; artistPause = PAUSE_TICKS
                lastTrackTitle = title
            }

            val titleW  = renderer.getWidth(title)
            val artistW = renderer.getWidth(artist)

            if (titleW > maxTextW) {
                if (titlePause > 0) titlePause--
                else {
                    titleOffset += SCROLL_SPEED * titleDir
                    val maxOff = (titleW - maxTextW + 5).toFloat()
                    if (titleOffset >= maxOff) { titleDir = -1; titlePause = PAUSE_TICKS }
                    if (titleOffset <= 0f)     { titleDir =  1; titlePause = PAUSE_TICKS; titleOffset = 0f }
                }
            }
            if (artistW > maxTextW) {
                if (artistPause > 0) artistPause--
                else {
                    artistOffset += SCROLL_SPEED * artistDir
                    val maxOff = (artistW - maxTextW + 5).toFloat()
                    if (artistOffset >= maxOff) { artistDir = -1; artistPause = PAUSE_TICKS }
                    if (artistOffset <= 0f)     { artistDir =  1; artistPause = PAUSE_TICKS; artistOffset = 0f }
                }
            }

            drawScrolling(context, renderer, title,       tx, PAD,            maxTextW, titleOffset,  screenX, screenY, sc)
            drawScrolling(context, renderer, "§7$artist", tx, PAD + LINE + 2, maxTextW, artistOffset, screenX, screenY, sc)

            val barY = PAD + LINE * 2 + 4
            if (track != null && track.durationMs > 0) {
                val progress = (track.progressMs.toFloat() / track.durationMs * maxTextW).toInt()
                context.fill(tx, barY, tx + maxTextW, barY + 2, SpotifyConfig.barBgColor())
                context.fill(tx, barY, tx + progress,  barY + 2, 0xFF1DB954.toInt())
            }

            val volBarY  = barY + 5
            val volW     = (maxTextW * volumeLevel / 100f).toInt()
            val isActive = volumeDisplayTick > 0
            if (isActive) volumeDisplayTick--

            val volColor = if (isActive) when {
                volumeLevel > 66 -> 0xFF1DB954.toInt()
                volumeLevel > 33 -> 0xFFFFAA00.toInt()
                else             -> 0xFFFF5555.toInt()
            } else 0xFF336644.toInt()

            context.fill(tx, volBarY, tx + maxTextW, volBarY + 2, SpotifyConfig.volBgColor())
            context.fill(tx, volBarY, tx + volW,     volBarY + 2, volColor)

            if (isActive) {
                val volText = "${volumeLevel}%"
                context.drawText(renderer, "§f$volText", tx + maxTextW - renderer.getWidth(volText), volBarY - LINE - 1, 0xFFFFFFFF.toInt(), true)
            }

            renderedButtons.clear()
            val btnAreaY = volBarY + 5
            val btnSize  = 12
            val btnGap   = 10
            val totalW   = btnSize * 3 + btnGap * 2
            var btnX     = tx + (maxTextW - totalW) / 2
            val isPlaying = track?.isPlaying ?: false

            fun drawBtn(icon: String, bx: Int, color: Int, action: () -> Unit) {
                val iw = renderer.getWidth(icon)
                context.drawText(renderer, icon, bx + (btnSize - iw) / 2, btnAreaY, color, true)
                val sx1 = (screenX + bx * sc).toInt() - 2
                val sy1 = (screenY + btnAreaY * sc).toInt() - 2
                val sx2 = (screenX + (bx + btnSize) * sc).toInt() + 2
                val sy2 = (screenY + (btnAreaY + LINE) * sc).toInt() + 2
                renderedButtons.add(RenderedBtn(sx1, sy1, sx2, sy2, action))
            }

            drawBtn("⏮", btnX, 0xFFAAAAAA.toInt()) { SpotifyApi.prev() }
            btnX += btnSize + btnGap
            val snap = isPlaying
            drawBtn(if (isPlaying) "⏸" else "▶", btnX, 0xFF1DB954.toInt()) { if (snap) SpotifyApi.pause() else SpotifyApi.resume() }
            btnX += btnSize + btnGap
            drawBtn("⏭", btnX, 0xFFAAAAAA.toInt()) { SpotifyApi.next() }

            matrices.popMatrix()
        }
    }

    private fun drawScrolling(
        context: net.minecraft.client.gui.DrawContext,
        renderer: TextRenderer,
        text: String,
        x: Int, y: Int,
        maxW: Int,
        offset: Float,
        screenX: Int, screenY: Int, sc: Float
    ) {
        val plain = text.replace(Regex("§."), "")
        if (renderer.getWidth(plain) <= maxW) {
            context.drawText(renderer, text, x, y, 0xFFFFFFFF.toInt(), true)
            return
        }
        val guiScale = MinecraftClient.getInstance().window.scaleFactor
        val scX1 = ((currentX + x * sc) * guiScale).toInt()
        val scY1 = ((screenY + (y - 1) * sc) * guiScale).toInt()
        val scX2 = ((currentX + (x + maxW) * sc) * guiScale).toInt()
        val scY2 = ((screenY + (y + LINE + 1) * sc) * guiScale).toInt()
        context.enableScissor(scX1, scY1, scX2, scY2)
        context.drawText(renderer, text, x - offset.toInt(), y, 0xFFFFFFFF.toInt(), true)
        context.disableScissor()
    }

    fun handleClick(mouseX: Double, mouseY: Double) {
        for (btn in renderedButtons) {
            if (mouseX >= btn.x1 && mouseX <= btn.x2 && mouseY >= btn.y1 && mouseY <= btn.y2) {
                btn.action(); return
            }
        }
    }

    fun getBoxSize(): Pair<Int, Int> = boxW to boxH

    private fun updateAlbumTexture(bytes: ByteArray, url: String) {
        lastLoadedArtUrl = url
        MinecraftClient.getInstance().execute {
            try {
                albumTexture?.close()
                val image   = NativeImage.read(ByteArrayInputStream(bytes))
                val texture = NativeImageBackedTexture({ "familyspotify_album" }, image)
                val id      = Identifier.of("familyspotify", "album_art")
                MinecraftClient.getInstance().textureManager.registerTexture(id, texture)
                albumTexture = texture; albumTextureId = id
            } catch (e: Exception) {
                FamilySpotify.LOGGER.warn("[FamilySpotify] Texture error: ${e.message}")
            }
        }
    }
}
