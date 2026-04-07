package org.kyowa.spotplaying

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object FamilySpotify : ClientModInitializer {

    val LOGGER = LoggerFactory.getLogger("FamilySpotify")
    const val VERSION = "1.0.0"

    private var hudEditorMouseWasDown = false
    private var gameMouseWasDown = false
    private var previousScreen: Screen? = null
    var openEditorNextTick = false
    var openConfigNextTick = false

    override fun onInitializeClient() {
        LOGGER.info("FamilySpotify $VERSION loading...")
        SpotifyConfig.load()
        SpotifyApi.start()
        SpotifyHud.register()
        SpotifyKeybinds.register()
        registerCommands()
        registerTickEvents()
        LOGGER.info("FamilySpotify $VERSION loaded!")
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("familyspotify")
                    .executes { _ -> openConfigNextTick = true; 1 }
                    .then(literal("login").executes { _ -> SpotifyAuth.startAuthFlow(); 1 })
                    .then(literal("editor").executes { _ -> openEditorNextTick = true; 1 })
            )
        }
    }

    private fun registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (openConfigNextTick) { openConfigNextTick = false; SpotifyConfig.openGui() }
            if (openEditorNextTick) { openEditorNextTick = false; client.setScreen(SpotifyHudEditor()) }

            // Save when any screen closes (catches MoulConfig GUI, HUD editor, etc.)
            val currentScreen = client.currentScreen
            if (previousScreen != null && currentScreen == null) {
                SpotifyConfig.save()
            }
            previousScreen = currentScreen

            // HUD editor mouse handling
            val screen = client.currentScreen
            when {
                screen is SpotifyHudEditor -> {
                    val mouseDown = GLFW.glfwGetMouseButton(client.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                    val mx = client.mouse.x / client.window.scaleFactor
                    val my = client.mouse.y / client.window.scaleFactor
                    if (mouseDown && !hudEditorMouseWasDown) screen.onMousePress(mx, my)
                    else if (!mouseDown && hudEditorMouseWasDown) screen.onMouseRelease()
                    hudEditorMouseWasDown = mouseDown
                    gameMouseWasDown = false
                }
                else -> {
                    hudEditorMouseWasDown = false
                    val mouseDown = GLFW.glfwGetMouseButton(client.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                    val mx = client.mouse.x / client.window.scaleFactor
                    val my = client.mouse.y / client.window.scaleFactor
                    if (mouseDown && !gameMouseWasDown) SpotifyHud.handleClick(mx, my)
                    gameMouseWasDown = mouseDown
                }
            }
        }
    }
}
