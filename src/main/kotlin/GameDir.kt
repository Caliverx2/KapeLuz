package org.lewapnoob.KapeLuz

import java.io.File

/**
 * Leniwie inicjalizowany, globalny obiekt przechowujący ścieżkę do głównego folderu gry.
 * Używa standardowych lokalizacji dla różnych systemów operacyjnych, aby zapisy były zawsze w tym samym miejscu.
 */
internal val gameDir: File by lazy {
    val appName = "KapeLuz"
    val dottedName = ".$appName"

    val userHome = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase(java.util.Locale.ROOT)

    val path = when {
        os.contains("win") -> {
            val appData = System.getenv("APPDATA")
            if (appData != null) File(appData, dottedName) else File(userHome, dottedName)
        }
        os.contains("mac") -> {
            File(userHome, "Library/Application Support/$dottedName")
        }
        else -> {
            File(userHome, dottedName)
        }
    }
    path.apply { mkdirs() }
}