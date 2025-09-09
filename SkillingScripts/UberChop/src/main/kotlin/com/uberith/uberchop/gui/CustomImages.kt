package com.uberith.uberchop.gui
import net.botwithus.imgui.ImGui

class CustomImages {
    fun renderImage(imageId: Long, width: Float, height: Float, sameLine: Boolean = false) {
        if (imageId == 0L) return
        // Try both Image/image via reflection across known ImGui classes
        val drawn = tryInvokeImGuiImage(imageId, width, height)
        if (!drawn) return
        if (sameLine) {
            try { ImGui.sameLine(0f, 0f) } catch (_: Throwable) { }
        }
    }

    private fun tryInvokeImGuiImage(texId: Long, w: Float, h: Float): Boolean {
        val classes = arrayOf("net.botwithus.imgui.ImGui")
        val methodNames = arrayOf("Image", "image")
        for (cn in classes) {
            try {
                val cls = Class.forName(cn)
                // Try both long and int variants, and both Image/image
                for (mn in methodNames) {
                    try {
                        val m = cls.getMethod(mn, java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                        m.invoke(null, texId, w, h)
                        return true
                    } catch (_: Throwable) { }
                    try {
                        val m = cls.getMethod(mn, java.lang.Integer.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                        m.invoke(null, texId.toInt(), w, h)
                        return true
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
        return false
    }
}
