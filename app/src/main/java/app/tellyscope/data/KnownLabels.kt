// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

/**
 * Display names for common TV packages, used when the label helper can't run on a TV (old
 * Android, an OEM block). Names only: no brand icons ship with the app, so these get a letter
 * avatar. Add a package only with the name its own APK shows.
 */
object KnownLabels {
    operator fun get(pkg: String): String? = names[pkg]

    private val names = mapOf(
        // Android and Google TV system apps
        "android" to "Android System",
        "com.android.systemui" to "System UI",
        "com.android.tv.settings" to "Settings",
        "com.android.vending" to "Google Play Store",
        "com.android.chrome" to "Chrome",
        "com.android.bluetooth" to "Bluetooth",
        "com.android.shell" to "Shell",
        "com.android.providers.downloads" to "Download Manager",
        "com.android.packageinstaller" to "Package installer",
        "com.google.android.packageinstaller" to "Package installer",
        "com.google.android.gms" to "Google Play services",
        "com.google.android.gsf" to "Google Services Framework",
        "com.google.android.tvlauncher" to "Android TV Home",
        "com.google.android.apps.tv.launcherx" to "Google TV Home",
        "com.google.android.apps.mediashell" to "Chromecast built-in",
        "com.google.android.tv.remote.service" to "Android TV Remote Service",
        "com.google.android.inputmethod.latin" to "Gboard",
        "com.google.android.tts" to "Speech Services by Google",
        "com.google.android.play.games" to "Google Play Games",
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        // Streaming and media apps
        "com.netflix.ninja" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "com.apple.atve.androidtv.appletv" to "Apple TV",
        "com.spotify.tv.android" to "Spotify",
        "com.plexapp.android" to "Plex",
        "org.jellyfin.androidtv" to "Jellyfin",
        "org.xbmc.kodi" to "Kodi",
        "org.videolan.vlc" to "VLC",
        "tv.twitch.android.app" to "Twitch",
        "com.crunchyroll.crunchyroid" to "Crunchyroll",
        "com.teamsmart.videomanager.tv" to "SmartTube",
        // Launchers
        "com.spocky.projengmenu" to "Projectivy Launcher",
    )
}
