package com.uberith.api.utils

import java.awt.Desktop
import java.net.URI

object Support {
    fun openUrl(url: String) {
        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) } catch (_: Throwable) {}
    }
}

