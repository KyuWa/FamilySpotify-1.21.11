package org.kyowa.spotplaying

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors

object SpotifyAuth {

    private const val CLIENT_ID    = "263c318a079f4d0ebee2bdbf5b6a22c4"
    private const val WORKER_URL   = "https://spotify.kyowa.uk"
    private const val REDIRECT_URI = "$WORKER_URL/callback"
    private const val SCOPES       = "user-read-playback-state user-modify-playback-state user-read-currently-playing"
    private const val LOCAL_PORT   = 8888

    private val http = HttpClient.newHttpClient()
    private var localServer: HttpServer? = null

    fun startAuthFlow() {
        startLocalServer()
        val authUrl = "https://accounts.spotify.com/authorize?" +
                "client_id=$CLIENT_ID" +
                "&response_type=code" +
                "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                "&scope=${URLEncoder.encode(SCOPES, "UTF-8")}"

        val opened = tryOpenBrowser(authUrl)
        MinecraftClient.getInstance().execute {
            val player = MinecraftClient.getInstance().player ?: return@execute
            if (opened) {
                player.sendMessage(Text.literal("§a[FamilySpotify] §7Browser opened — log in to Spotify!"), false)
            } else {
                player.sendMessage(Text.literal("§a[FamilySpotify] §7Could not open browser. Click the link:"), false)
                player.sendMessage(
                    Text.literal("§b§n$authUrl")
                        .setStyle(Style.EMPTY.withClickEvent(ClickEvent.OpenUrl(URI.create(authUrl)))),
                    false
                )
            }
        }
    }

    private fun tryOpenBrowser(url: String): Boolean {
        try { Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url)); return true } catch (e: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url.replace("&", "^&"))); return true } catch (e: Exception) {}
        try { if (java.awt.Desktop.isDesktopSupported()) { java.awt.Desktop.getDesktop().browse(URI(url)); return true } } catch (e: Exception) {}
        return false
    }

    private fun startLocalServer() {
        stopLocalServer()
        try {
            val server = HttpServer.create(InetSocketAddress("localhost", LOCAL_PORT), 0)
            server.executor = Executors.newSingleThreadExecutor()
            server.createContext("/spotify-auth") { exchange ->
                val params = (exchange.requestURI.query ?: "").split("&").mapNotNull {
                    val p = it.split("=", limit = 2)
                    if (p.size == 2) p[0] to p[1] else null
                }.toMap()

                val accessToken  = params["access_token"]  ?: ""
                val refreshToken = params["refresh_token"] ?: ""
                val expiresIn    = params["expires_in"]?.toLongOrNull() ?: 3600L

                if (accessToken.isNotEmpty()) {
                    SpotifyConfig.data.general.accessToken    = accessToken
                    SpotifyConfig.data.general.refreshToken   = refreshToken
                    SpotifyConfig.data.general.tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000
                    SpotifyConfig.save()

                    MinecraftClient.getInstance().execute {
                        MinecraftClient.getInstance().player?.sendMessage(
                            Text.literal("§a[FamilySpotify] §7Connected to Spotify!"), false
                        )
                    }

                    val html = """
                        <html><body style="font-family:sans-serif;text-align:center;margin-top:100px;background:#191414;color:#1DB954">
                        <h1>✓ FamilySpotify connected!</h1>
                        <p style="color:white">You can close this tab and go back to Minecraft.</p>
                        </body></html>
                    """.trimIndent().toByteArray()
                    exchange.sendResponseHeaders(200, html.size.toLong())
                    exchange.responseBody.write(html)
                    exchange.responseBody.close()
                    stopLocalServer()
                } else {
                    val html = "<html><body>Auth failed — no token received.</body></html>".toByteArray()
                    exchange.sendResponseHeaders(400, html.size.toLong())
                    exchange.responseBody.write(html)
                    exchange.responseBody.close()
                }
            }
            server.start()
            localServer = server
        } catch (e: Exception) {
            FamilySpotify.LOGGER.warn("[FamilySpotify] Could not start local server: ${e.message}")
        }
    }

    private fun stopLocalServer() { localServer?.stop(0); localServer = null }

    fun refreshTokenIfNeeded(): Boolean {
        if (SpotifyConfig.hasValidToken()) return true
        if (!SpotifyConfig.hasRefreshToken()) return false
        return try {
            val body = """{"refresh_token":"${SpotifyConfig.data.general.refreshToken}"}"""
            val req = HttpRequest.newBuilder()
                .uri(URI("$WORKER_URL/refresh"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val res  = http.send(req, HttpResponse.BodyHandlers.ofString())
            val json = JsonParser.parseString(res.body()).asJsonObject
            val newToken   = json.get("access_token")?.asString  ?: return false
            val expiresIn  = json.get("expires_in")?.asLong      ?: 3600L
            val newRefresh = json.get("refresh_token")?.asString
            SpotifyConfig.data.general.accessToken    = newToken
            SpotifyConfig.data.general.tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000
            if (newRefresh != null) SpotifyConfig.data.general.refreshToken = newRefresh
            SpotifyConfig.save()
            true
        } catch (e: Exception) {
            FamilySpotify.LOGGER.warn("[FamilySpotify] Token refresh failed: ${e.message}")
            false
        }
    }
}
