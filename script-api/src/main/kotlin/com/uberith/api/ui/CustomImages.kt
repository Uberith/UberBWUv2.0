package com.uberith.api.ui

import net.botwithus.imgui.ImGui

class CustomImages {
    fun renderImage(image: Any, width: Float, height: Float, sameLine: Boolean = false) {
        val drawn = when (image) {
            is Number -> tryInvokeImGuiImageId(image.toLong(), width, height)
            else -> tryInvokeImGuiImageObj(image, width, height)
        }
        if (!drawn) return
        if (sameLine) {
            try { ImGui.sameLine(0f, 0f) } catch (_: Throwable) { }
        }
    }

    private fun tryInvokeImGuiImageId(texId: Long, w: Float, h: Float): Boolean {
        val classes = arrayOf("net.botwithus.imgui.ImGui")
        val methodNames = arrayOf("Image", "image")
        for (cn in classes) {
            try {
                val cls = Class.forName(cn)
                for (mn in methodNames) {
                    try {
                        val m = cls.getMethod(mn, java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                        m.invoke(null, texId, w, h)
                        return true
                    } catch (_: Throwable) { }
                    try {
                        val m = cls.getMethod(mn, Integer.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                        m.invoke(null, texId.toInt(), w, h)
                        return true
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
        return false
    }

    private fun tryInvokeImGuiImageObj(texture: Any, w: Float, h: Float): Boolean {
        val classes = arrayOf("net.botwithus.imgui.ImGui", "net.botwithus.rs3.imgui.ImGui")
        val methodNames = arrayOf("Image", "image")
        for (cn in classes) {
            try {
                val cls = Class.forName(cn)
                for (mn in methodNames) {
                    try {
                        val m = cls.getMethod(mn, Any::class.java, java.lang.Float.TYPE, java.lang.Float.TYPE)
                        m.invoke(null, texture, w, h)
                        return true
                    } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
        // Last-ditch: attempt to extract numeric id from common getters
        try {
            val idM = texture.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("id","getid","handle","gethandle") }
            if (idM != null) {
                val idVal = idM.invoke(texture)
                if (idVal is Number) return tryInvokeImGuiImageId(idVal.toLong(), w, h)
            }
        } catch (_: Throwable) { }
        return false
    }
}

