package org.kyowa.spotplaying

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.*
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.processor.BuiltinMoulConfigGuis
import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor
import net.minecraft.client.MinecraftClient
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpotifyConfigData : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("§aFamilySpotify")

    @Expose @JvmField
    @Category(name = "General", desc = "General settings")
    var general = GeneralSettings()

    @Expose @JvmField
    @Category(name = "Keybinds", desc = "Keybind settings")
    var keybinds = KeybindSettings()
}

class GeneralSettings {
    @Expose @JvmField
    @ConfigOption(name = "Show HUD", desc = "Toggle the Spotify HUD overlay on or off.")
    @ConfigEditorBoolean
    var hudEnabled: Boolean = true

    @Expose @JvmField
    @ConfigOption(name = "HUD Style", desc = "Background style of the HUD.")
    @ConfigEditorDropdown(values = ["Dark", "Transparent", "Spotify Green"])
    var hudStyle: Int = 0

    // No @Expose on buttons — they should not be serialized
    @JvmField
    @ConfigOption(name = "Open HUD Editor", desc = "Drag the HUD to reposition it.")
    @ConfigEditorButton(buttonText = "Open")
    var openEditor: Runnable = Runnable { FamilySpotify.openEditorNextTick = true }

    @JvmField
    @ConfigOption(name = "Login to Spotify", desc = "Connect your Spotify account.")
    @ConfigEditorButton(buttonText = "Login")
    var login: Runnable = Runnable { SpotifyAuth.startAuthFlow() }

    @Expose var hudX: Int = 5
    @Expose var hudY: Int = 5
    @Expose var hudScale: Float = 1.0f
    @Expose var accessToken: String = ""
    @Expose var refreshToken: String = ""
    @Expose var tokenExpiresAt: Long = 0L
}

class KeybindSettings {
    @Expose @JvmField
    @ConfigOption(name = "Previous Song", desc = "Go to the previous song.")
    @ConfigEditorKeybind(defaultKey = -1)
    var prevKey: Int = -1

    @Expose @JvmField
    @ConfigOption(name = "Next Song", desc = "Skip to the next song.")
    @ConfigEditorKeybind(defaultKey = -1)
    var nextKey: Int = -1

    @Expose @JvmField
    @ConfigOption(name = "Play / Pause", desc = "Play or pause the current song.")
    @ConfigEditorKeybind(defaultKey = -1)
    var pauseKey: Int = -1

    @Expose @JvmField
    @ConfigOption(name = "Volume Up", desc = "Increase Spotify volume by 10%.")
    @ConfigEditorKeybind(defaultKey = -1)
    var volUpKey: Int = -1

    @Expose @JvmField
    @ConfigOption(name = "Volume Down", desc = "Decrease Spotify volume by 10%.")
    @ConfigEditorKeybind(defaultKey = -1)
    var volDownKey: Int = -1
}

object SpotifyConfig {
    private val gson = GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .setPrettyPrinting()
        .create()

    // Same path as what's already working in run/config/familyspotify/config.json
    private val configFile get() = File(
        MinecraftClient.getInstance().runDirectory,
        "config/familyspotify/config.json"
    )

    var data = SpotifyConfigData()
    private lateinit var processor: MoulConfigProcessor<SpotifyConfigData>
    private lateinit var editor: MoulConfigEditor<SpotifyConfigData>
    private var editorInitialized = false
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun load() {
        configFile.parentFile.mkdirs()

        if (configFile.exists()) {
            try {
                FileReader(configFile).use { fr ->
                    val loaded = gson.fromJson(fr, SpotifyConfigData::class.java)
                    if (loaded != null) data = loaded
                }
                FamilySpotify.LOGGER.info("[FamilySpotify] Config loaded from ${configFile.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
                data = SpotifyConfigData()
            }
        } else {
            FamilySpotify.LOGGER.info("[FamilySpotify] No config found, creating default at ${configFile.absolutePath}")
            save()
        }

        processor = MoulConfigProcessor(data)
        BuiltinMoulConfigGuis.addProcessors(processor)
        ConfigProcessorDriver(processor).processConfig(data)

        scheduler.scheduleAtFixedRate({ save() }, 60, 60, TimeUnit.SECONDS)
    }

    fun save() {
        try {
            configFile.parentFile.mkdirs()
            FileWriter(configFile).use { fw -> fw.write(gson.toJson(data)) }
            FamilySpotify.LOGGER.info("[FamilySpotify] Config saved to ${configFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openGui() {
        if (!editorInitialized) {
            editor = MoulConfigEditor(processor)
            editorInitialized = true
        }
        IMinecraft.getInstance().openWrappedScreen(editor)
    }

    fun hasValidToken()   = data.general.accessToken.isNotEmpty() && System.currentTimeMillis() < data.general.tokenExpiresAt
    fun hasRefreshToken() = data.general.refreshToken.isNotEmpty()

    fun bgColor(): Int = when (data.general.hudStyle) {
        1    -> 0x00000000
        2    -> 0xCC1a6632.toInt()
        else -> 0xCC111111.toInt()
    }
    fun artBgColor(): Int = when (data.general.hudStyle) {
        1    -> 0x00000000
        2    -> 0xFF1DB954.toInt()
        else -> 0xFF1DB954.toInt()
    }
    fun barBgColor(): Int = when (data.general.hudStyle) {
        1    -> 0x44FFFFFF.toInt()
        2    -> 0xFF145a32.toInt()
        else -> 0xFF555555.toInt()
    }
    fun volBgColor(): Int = when (data.general.hudStyle) {
        1    -> 0x33FFFFFF.toInt()
        2    -> 0xFF145a32.toInt()
        else -> 0xFF333333.toInt()
    }
}
