package org.kyowa.spotplaying

import com.google.gson.JsonParser
import net.minecraft.client.MinecraftClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val isPlaying: Boolean,
    val progressMs: Long,
    val durationMs: Long,
)

object SpotifyApi {

    @Volatile var current: SpotifyTrack? = null
    @Volatile var albumArtCache: ByteArray? = null
    @Volatile var albumArtUrl: String? = null

    private val http = HttpClient.newHttpClient()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "FamilySpotify-Poller").also { it.isDaemon = true }
    }

    fun start() { executor.scheduleAtFixedRate(::poll, 0, 2, TimeUnit.SECONDS) }

    private fun poll() {
        try {
            if (!SpotifyAuth.refreshTokenIfNeeded()) return
            val token = SpotifyConfig.data.general.accessToken
            val req = HttpRequest.newBuilder()
                .uri(URI("https://api.spotify.com/v1/me/player/currently-playing"))
                .header("Authorization", "Bearer $token").GET().build()
            val res = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() == 204 || res.body().isNullOrBlank()) { current = null; return }
            if (res.statusCode() != 200) return
            val json       = JsonParser.parseString(res.body()).asJsonObject
            val item       = json.getAsJsonObject("item") ?: return
            val title      = item.get("name")?.asString ?: return
            val artists    = item.getAsJsonArray("artists")?.joinToString(", ") { it.asJsonObject.get("name").asString } ?: "Unknown"
            val artUrl     = item.getAsJsonObject("album")?.getAsJsonArray("images")?.firstOrNull()?.asJsonObject?.get("url")?.asString
            val isPlaying  = json.get("is_playing")?.asBoolean ?: false
            val progressMs = json.get("progress_ms")?.asLong ?: 0L
            val durationMs = item.get("duration_ms")?.asLong ?: 0L
            current = SpotifyTrack(title, artists, artUrl, isPlaying, progressMs, durationMs)
            if (artUrl != null && artUrl != albumArtUrl) fetchAlbumArt(artUrl)
        } catch (e: Exception) {
            FamilySpotify.LOGGER.warn("[FamilySpotify] Poll error: ${e.message}")
        }
    }

    private fun fetchAlbumArt(url: String) {
        try {
            val res = http.send(HttpRequest.newBuilder().uri(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
            if (res.statusCode() == 200) {
                val buffered = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(res.body()))
                if (buffered != null) {
                    val out = java.io.ByteArrayOutputStream()
                    javax.imageio.ImageIO.write(buffered, "PNG", out)
                    albumArtCache = out.toByteArray()
                    albumArtUrl   = url
                }
            }
        } catch (e: Exception) {
            FamilySpotify.LOGGER.warn("[FamilySpotify] Album art error: ${e.message}")
        }
    }

    fun pause()  = sendControl("pause",    "PUT")
    fun resume() = sendControl("play",     "PUT")
    fun next()   = sendSkip("next")
    fun prev()   = sendSkip("previous")
    fun volumeUp()   = adjustVolume(10)
    fun volumeDown() = adjustVolume(-10)

    private fun adjustVolume(delta: Int) {
        executor.submit {
            try {
                if (!SpotifyAuth.refreshTokenIfNeeded()) return@submit
                val token = SpotifyConfig.data.general.accessToken
                val stateRes = http.send(HttpRequest.newBuilder().uri(URI("https://api.spotify.com/v1/me/player")).header("Authorization", "Bearer $token").GET().build(), HttpResponse.BodyHandlers.ofString())
                if (stateRes.statusCode() != 200) return@submit
                val currentVol = JsonParser.parseString(stateRes.body()).asJsonObject.getAsJsonObject("device")?.get("volume_percent")?.asInt ?: 50
                val newVol = (currentVol + delta).coerceIn(0, 100)
                http.send(HttpRequest.newBuilder().uri(URI("https://api.spotify.com/v1/me/player/volume?volume_percent=$newVol")).header("Authorization", "Bearer $token").PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                MinecraftClient.getInstance().execute { SpotifyHud.showVolume(newVol) }
            } catch (e: Exception) { FamilySpotify.LOGGER.warn("[FamilySpotify] Volume error: ${e.message}") }
        }
    }

    private fun sendControl(action: String, method: String) {
        executor.submit {
            try {
                if (!SpotifyAuth.refreshTokenIfNeeded()) return@submit
                val token = SpotifyConfig.data.general.accessToken
                http.send(HttpRequest.newBuilder().uri(URI("https://api.spotify.com/v1/me/player/$action")).header("Authorization", "Bearer $token").method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                Thread.sleep(300); poll()
            } catch (e: Exception) { FamilySpotify.LOGGER.warn("[FamilySpotify] Control error ($action): ${e.message}") }
        }
    }

    private fun sendSkip(direction: String) {
        executor.submit {
            try {
                if (!SpotifyAuth.refreshTokenIfNeeded()) return@submit
                val token = SpotifyConfig.data.general.accessToken
                http.send(HttpRequest.newBuilder().uri(URI("https://api.spotify.com/v1/me/player/$direction")).header("Authorization", "Bearer $token").POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
                Thread.sleep(300); poll()
            } catch (e: Exception) { FamilySpotify.LOGGER.warn("[FamilySpotify] Skip error ($direction): ${e.message}") }
        }
    }
}
