package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

const val KEY_TITLE_TEMPLATE = "title_template_key"
const val KEY_LAST_CARD = "last_card_key"

class WallpaperProviderService : Service() {

    // Images trouvees (gardees en memoire) et images introuvables (re-testees apres 30 s)
    private val okCache = ConcurrentHashMap<String, Boolean>()
    private val missCache = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            return when (event) {
                is Event.CardFocused -> wallpaperForCard(event)
                else -> emptyList() // on garde le fond actuel
            }
        }

        override fun getPreferences(): String = PreferencesManager.export()

        override fun setPreferences(params: String) {
            PreferencesManager.import(params)
            okCache.clear()
            missCache.clear()
        }
    }

    private fun wallpaperForCard(event: Event.CardFocused): List<Wallpaper> {
        val pkg = event.packageName?.trim().orEmpty()
        val title = event.title?.trim().orEmpty()
        val prefs = PreferencesManager.preferences

        // Memorise ce que Projectivy envoie, pour l'afficher dans les reglages du plugin
        prefs.edit().putString(KEY_LAST_CARD, "titre : \"$title\"\npackage : \"$pkg\"").apply()

        // 1) Image par titre (ex. HDMI), puis 2) image par package (ex. Netflix)
        val urls = listOfNotNull(
            buildUrl(prefs.getString(KEY_TITLE_TEMPLATE, "").orEmpty(), pkg, title),
            buildUrl(PreferencesManager.urlTemplate, pkg, title)
        ).distinct()

        for (url in urls) {
            if (imageExists(url)) {
                return listOf(
                    Wallpaper(url, WallpaperType.IMAGE, WallpaperDisplayMode.CROP, title = title.ifEmpty { null })
                )
            }
        }

        // 3) Image par defaut (optionnelle)
        val fallback = PreferencesManager.fallbackUrl
        if (fallback.isNotBlank()) {
            return listOf(Wallpaper(fallback, WallpaperType.IMAGE, WallpaperDisplayMode.CROP))
        }

        // 4) Sinon : une couleur unie assortie a la tuile
        if (PreferencesManager.useCardColor && event.darkColor != 0) {
            return listOf(Wallpaper(String.format("#%06X", event.darkColor and 0xFFFFFF), WallpaperType.COLOR))
        }

        return emptyList()
    }

    // Renvoie null si le modele est vide ou s'il demande une valeur que la tuile n'a pas
    private fun buildUrl(template: String, pkg: String, title: String): String? {
        if (template.isBlank()) return null
        if (template.contains("{package}") && pkg.isEmpty()) return null
        if (template.contains("{title}") && title.isEmpty()) return null
        return template
            .replace("{package}", Uri.encode(pkg))
            .replace("{title}", Uri.encode(title.lowercase().replace(" ", "-")))
    }

    private fun imageExists(url: String): Boolean {
        if (okCache.containsKey(url)) return true
        val missAt = missCache[url]
        if (missAt != null && System.currentTimeMillis() - missAt < 30_000) return false

        val ok = try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "HEAD"
                c.connectTimeout = 2500
                c.readTimeout = 2500
                c.instanceFollowRedirects = true
                val code = c.responseCode
                c.disconnect()
                code in 200..299
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
        if (ok) okCache[url] = true else missCache[url] = System.currentTimeMillis()
        return ok
    }
}
