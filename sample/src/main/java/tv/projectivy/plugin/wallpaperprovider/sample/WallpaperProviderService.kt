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

class WallpaperProviderService : Service() {

    // Memorise si une URL existe (evite de refaire une requete a chaque passage sur la tuile)
    private val existsCache = ConcurrentHashMap<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            return when (event) {
                is Event.CardFocused -> wallpaperForCard(event)
                // Rien a faire pour les autres evenements : on garde le fond actuel
                else -> emptyList()
            }
        }

        override fun getPreferences(): String = PreferencesManager.export()

        override fun setPreferences(params: String) {
            PreferencesManager.import(params)
            existsCache.clear()
        }
    }

    private fun wallpaperForCard(event: Event.CardFocused): List<Wallpaper> {
        val pkg = event.packageName?.trim().orEmpty()
        val title = event.title?.trim().orEmpty()

        // 1) Image specifique a la tuile (par package et/ou titre)
        val template = PreferencesManager.urlTemplate
        if (template.isNotBlank()) {
            val usesPackage = template.contains("{package}")
            val usesTitle = template.contains("{title}")
            val canBuild = (!usesPackage || pkg.isNotEmpty()) && (!usesTitle || title.isNotEmpty())
            if (canBuild) {
                val url = fillTemplate(template, pkg, title)
                if (imageExists(url)) {
                    return listOf(
                        Wallpaper(url, WallpaperType.IMAGE, WallpaperDisplayMode.CROP, title = title.ifEmpty { null })
                    )
                }
            }
        }

        // 2) Image par defaut (optionnelle)
        val fallback = PreferencesManager.fallbackUrl
        if (fallback.isNotBlank()) {
            return listOf(Wallpaper(fallback, WallpaperType.IMAGE, WallpaperDisplayMode.CROP))
        }

        // 3) Sinon : une couleur unie assortie a la tuile
        if (PreferencesManager.useCardColor && event.darkColor != 0) {
            return listOf(Wallpaper(String.format("#%06X", event.darkColor and 0xFFFFFF), WallpaperType.COLOR))
        }

        return emptyList()
    }

    private fun fillTemplate(template: String, pkg: String, title: String): String =
        template
            .replace("{package}", Uri.encode(pkg))
            .replace("{title}", Uri.encode(title.lowercase().replace(" ", "-")))

    private fun imageExists(url: String): Boolean {
        existsCache[url]?.let { return it }
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
                true // content://, file://... : on laisse Projectivy essayer
            }
        } catch (e: Exception) {
            false
        }
        existsCache[url] = ok
        return ok
    }
}
