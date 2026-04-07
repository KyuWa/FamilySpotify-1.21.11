package org.kyowa.spotplaying

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

object SpotifyKeybinds {

    private val prevState = mutableMapOf<Int, Boolean>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val kb = SpotifyConfig.data.keybinds

            if (isJustPressed(client, kb.prevKey))    SpotifyApi.prev()
            if (isJustPressed(client, kb.nextKey))    SpotifyApi.next()
            if (isJustPressed(client, kb.pauseKey)) {
                val t = SpotifyApi.current
                if (t?.isPlaying == true) SpotifyApi.pause() else SpotifyApi.resume()
            }
            if (isJustPressed(client, kb.volUpKey))   SpotifyApi.volumeUp()
            if (isJustPressed(client, kb.volDownKey)) SpotifyApi.volumeDown()
        }
    }

    private fun isJustPressed(client: MinecraftClient, key: Int): Boolean {
        if (key <= 0) return false
        val now = GLFW.glfwGetKey(client.window.handle, key) == GLFW.GLFW_PRESS
        val was = prevState[key] ?: false
        prevState[key] = now
        return now && !was
    }
}
