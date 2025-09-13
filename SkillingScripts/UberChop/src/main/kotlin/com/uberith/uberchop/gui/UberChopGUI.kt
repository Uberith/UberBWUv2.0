package com.uberith.uberchop.gui

import com.uberith.uberchop.UberChop
import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import com.uberith.api.ui.ColorManager
import com.uberith.api.ui.CustomImages

class UberChopGUI(private val script: UberChop) : BuildableUI {
    // 0 Overview, 1 Core, 2 Handlers, 3 WorldHop, 4 Advanced, 5 Statistics, 6 Support, 7 Debug
    private var selectedTab: Int = 0
    private val FIXED_W = 560f
    private val FIXED_H = 620f
    private val CONTENT_H = 455f
    // Target tree selection will use a true Combo box (dropdown)
    private var minimized: Boolean = false

    // Textures (loaded once, freed on demand)
    private var logoImg: Any? = null
    private var logoBytesSize: Int = 0
    private var logoLoadSource: String = ""
    private val log = LoggerFactory.getLogger(UberChopGUI::class.java)
    fun preload() {
        // Prepare for load during first draw when the UI context is ready
        unloadTextures()
    }

    private fun adjustInt(label: String, value: Int, min: Int, max: Int, step: Int = 1): Int {
        ImGui.text(label)
        ImGui.sameLine(0f, 6f)
        var v = value
        if (ImGui.button("-", 24f, 0f)) v = (v - step).coerceAtLeast(min)
        ImGui.sameLine(0f, 4f)
        ImGui.text(v.toString())
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("+", 24f, 0f)) v = (v + step).coerceAtMost(max)
        return v
    }

    fun render(workspace: Workspace) {
        renderInternal()
    }

    override fun buildUI() {
        // BuildableUI entrypoint used by XAPI public UI systems
        renderInternal()
    }

    private fun renderInternal() {
        // Fixed-size, small-screen friendly window (supports compact mode)
        if (minimized) {
            ImGui.setNextWindowSize(360f, 140f)
        } else {
            ImGui.setNextWindowSize(FIXED_W, FIXED_H)
        }

        if (ImGui.begin("UberChop", 0)) {
            val cm = ColorManager()
            cm.pushColors()
            // Top logo + summary with minimize/expand toggle
            drawLogoBar()
            ImGui.text("Runtime ${script.formattedRuntime()}  |  Logs ${script.logsChopped} (${script.logsPerHour()}/h)  |  Status ${script.status}")
            ImGui.sameLine(0f, 8f)
            if (minimized) {
                if (ImGui.button("Expand", 72f, 0f)) minimized = false
            } else {
                if (ImGui.button("Minimize", 86f, 0f)) minimized = true
            }
            ImGui.separator()
            if (minimized) {
                val worldText = tryGetWorldIdText()
                val pingText = tryGetPingMsText()
                val coordText = tryGetPlayerCoordsText()
                ImGui.text("W: $worldText  |  Ping: $pingText ms  |  XYZ: $coordText")
                cm.popColors()
                ImGui.end()
                return
            }

            // Left navigation (vertical buttons with icons + selection marker), Right content (scrollable)
            val navW = 120f
            if (ImGui.beginChild("LeftNav", navW, CONTENT_H, true, 0)) {
                val navCount = 8
                val fudge = 12f // extra safety to avoid scrollbar
                val available = CONTENT_H - fudge
                // No vertical spacing between buttons; compute height to fill column
                // Reduce height further to ensure no scrollbar (trim 8px), keep clickable minimum
                val btnH = ((available / navCount) - 8f).coerceAtLeast(18f)
                val rightPad = 22f

                drawNavItem(0, "Overview", navW, cm, btnH, rightPad)
                drawNavItem(1, "Core", navW, cm, btnH, rightPad)
                drawNavItem(2, "Handlers", navW, cm, btnH, rightPad)
                drawNavItem(3, "WorldHop", navW, cm, btnH, rightPad)
                drawNavItem(4, "Advanced", navW, cm, btnH, rightPad)
                drawNavItem(5, "Statistics", navW, cm, btnH, rightPad)
                drawNavItem(6, "Support", navW, cm, btnH, rightPad)
                drawNavItem(7, "Debug", navW, cm, btnH, rightPad)
            }
            ImGui.endChild()

            ImGui.sameLine(0f, 8f)

            // Darker child background for content area to add depth
            ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, 12f/255f, 18f/255f, 45f/255f, 0.62f)
            if (ImGui.beginChild("RightContent", 0f, CONTENT_H, true, 0)) {
                // Inner child with darker border to simulate inner shadow
                val borderDark = cm.getColor(ColorManager.ColorType.BorderShadow) ?: intArrayOf(8,8,16,170)
                val bf = cm.colorToFloats(borderDark)
                ImGui.pushStyleColor(ColorManager.ColorType.Border.index, bf[0], bf[1], bf[2], bf[3])
                if (ImGui.beginChild("RightInner", 0f, 0f, true, 0)) {
                    when (selectedTab) {
                        0 -> drawOverview()
                        1 -> drawCore()
                        2 -> drawHandlers()
                        3 -> drawWorldHop()
                        4 -> drawAdvanced()
                        5 -> drawStatistics()
                        6 -> drawSupport()
                        7 -> drawDebug()
                    }
                }
                ImGui.endChild()
                ImGui.popStyleColor(1)
            }
            ImGui.endChild()
            ImGui.popStyleColor(1)

            // Bottom status bar
            ImGui.separator()
            val worldText = tryGetWorldIdText()
            val pingText = tryGetPingMsText()
            val coordText = tryGetPlayerCoordsText()
            val animText = tryGetAnimIdText()
            ImGui.text("W: $worldText  |  Ping: $pingText ms  |  XYZ: $coordText  |  Anim: $animText")
            cm.popColors()
        }
        ImGui.end()
    }

    private fun drawLogoBar() {
        // Ensure we (re)attempt loading on the render thread where the UI context is valid
        if (logoImg == null) { loadTextures() }
        if (ImGui.beginChild("LogoBar", 0f, 56f, false, 0)) {
            if (logoImg != null) {
                // Render via shared utility for consistency
                CustomImages().renderImage(logoImg as Any, 220f, 44f)
            } else {
                // Fallback text title when no texture API or image not loaded yet
                ImGui.text("Uberith")
            }
        }
        ImGui.endChild()
    }

    // External-style helpers for images
    private fun loadImage(path: String): ByteArray? {
        // Try multiple classloader strategies, with and without leading slash
        val candidates = listOf(
            path,
            "/$path",
            // Additional fallbacks observed in some script loaders
            path.substringAfterLast('/'),
            "/" + path.substringAfterLast('/')
        )
        val loaders: List<Pair<String, (String) -> java.io.InputStream?>> = listOf(
            "this.classLoader" to { p -> this::class.java.classLoader?.getResourceAsStream(p) },
            "thread.context" to { p -> Thread.currentThread().contextClassLoader?.getResourceAsStream(p) },
            "class.getResource" to { p -> this::class.java.getResourceAsStream(p) },
            "system" to { p -> java.lang.ClassLoader.getSystemResourceAsStream(p) }
        )
        for (cand in candidates) {
            for ((src, op) in loaders) {
                try {
                    val ins = op(cand) ?: continue
                    // First, try raw bytes (in case ImageIO has trouble);
                    // ImGui.loadTexture expects a PNG byte array.
                    ins.use { raw ->
                        val rawBytes = raw.readBytes()
                        if (rawBytes.isNotEmpty()) { logoLoadSource = "$src:$cand (raw)"; return rawBytes }
                    }
                } catch (_: Throwable) { }
                try {
                    val ins2 = op(cand) ?: continue
                    ins2.use { stream ->
                        val img = ImageIO.read(stream) ?: return@use
                        ByteArrayOutputStream().use { baos ->
                            ImageIO.write(img, "png", baos)
                            val bytes = baos.toByteArray()
                            if (bytes.isNotEmpty()) { logoLoadSource = "$src:$cand (ImageIO)"; return bytes }
                        }
                    }
                } catch (_: Throwable) { }
            }
            // Enumerate resources as a fallback
            try {
                val cl = this::class.java.classLoader
                val en = cl?.getResources(cand)
                if (en != null) {
                    while (en.hasMoreElements()) {
                        val url = en.nextElement()
                        try {
                            url.openStream().use { s ->
                                val raw = s.readBytes()
                                if (raw.isNotEmpty()) { logoLoadSource = "enumeration:$cand"; return raw }
                            }
                        } catch (_: Throwable) { }
                    }
                }
            } catch (_: Throwable) { }

            // Directly read from the hosting JAR as a last-ditch classpath fallback
            try {
                val loc = this::class.java.protectionDomain?.codeSource?.location
                val uri = try { loc?.toURI() } catch (_: Throwable) { null }
                val pathStr = uri?.path ?: uri?.schemeSpecificPart
                if (pathStr != null && pathStr.endsWith(".jar")) {
                    val jarFile = java.io.File(pathStr)
                    if (jarFile.isFile) {
                        java.util.zip.ZipFile(jarFile).use { zip ->
                            val names = listOf(cand.trimStart('/'), cand)
                            for (n in names) {
                                val e = zip.getEntry(n.trimStart('/'))
                                if (e != null) {
                                    zip.getInputStream(e).use { ins ->
                                        val raw = ins.readBytes()
                                        if (raw.isNotEmpty()) { logoLoadSource = "jar-entry:$n"; return raw }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) { }
        }
        // Last-resort: try common filesystem locations
        try {
            val home = System.getProperty("user.home") ?: ""
            val jarUrl = this::class.java.protectionDomain?.codeSource?.location
            val jarDir = try { java.nio.file.Paths.get(jarUrl?.toURI()).parent?.toString() } catch (_: Throwable) { null }
            val fsCandidates = listOfNotNull(
                java.nio.file.Paths.get(home, ".BotWithUs", "resources", path).toString(),
                java.nio.file.Paths.get(home, ".BotWithUs", "scripts", "images", path.substringAfterLast('/')).toString(),
                java.nio.file.Paths.get(home, "BotWithUs", "scripts", "local", "images", path.substringAfterLast('/')).toString(),
                jarDir?.let { java.nio.file.Paths.get(it, "images", path.substringAfterLast('/')).toString() },
                java.nio.file.Paths.get("images", path.substringAfterLast('/')).toString()
            )
            for (p in fsCandidates) {
                try {
                    val f = java.io.File(p)
                    if (f.isFile && f.canRead()) {
                        val raw = f.readBytes()
                        if (raw.isNotEmpty()) { logoLoadSource = "fs:$p"; return raw }
                        // Try ImageIO re-encode for safety
                        javax.imageio.ImageIO.read(f)?.let { img ->
                            ByteArrayOutputStream().use { baos ->
                                javax.imageio.ImageIO.write(img, "png", baos)
                                val bytes = baos.toByteArray()
                                if (bytes.isNotEmpty()) { logoLoadSource = "fs-io:$p"; return bytes }
                            }
                        }
                    }
                } catch (_: Throwable) { }
            }
        } catch (_: Throwable) { }
        // Not found: mark source for UI visibility
        if (logoLoadSource.isEmpty()) logoLoadSource = "not-found:$path"
        return null
    }

    private fun loadTextures() {
        if (logoImg != null) return
        // Skip if no image/texture API is exposed by current ImGui runtime
        if (!hasImGuiImageApi()) {
            // No image rendering available in current runtime; skip quietly
            return
        }
        loadImage("images/Uberith_Logo_Full_Text.png")?.let { bytes ->
            logoBytesSize = bytes.size
            val tex1 = loadTexture(bytes)
            if (tex1 != null) {
                logoImg = tex1
                return
            }
            // As a fallback, try re-encoding via ImageIO to normalize PNGs
            reencodePng(bytes)?.let { normalized ->
                val tex2 = loadTexture(normalized)
                if (tex2 != null) {
                    logoImg = tex2
                    return
                }
            }
        }
        // Leave logoImg as 0L to trigger retry on subsequent frames
    }

    private fun reencodePng(bytes: ByteArray): ByteArray? {
        return try {
            val img = ImageIO.read(bytes.inputStream()) ?: return null
            ByteArrayOutputStream().use { baos ->
                ImageIO.write(img, "png", baos)
                baos.toByteArray().takeIf { it.isNotEmpty() }
            }
        } catch (_: Throwable) { null }
    }

    private fun unloadTextures() {
        if (logoImg != null) {
            freeTexture(logoImg as Any)
            logoImg = null
        }
    }

    private fun loadTexture(bytes: ByteArray): Any? {
        val classes = arrayOf(
            "net.botwithus.imgui.ImGui",
            "net.botwithus.rs3.imgui.ImGui",
            "net.botwithus.client.imgui.ImGui",
            // Possible alternate texture hosts
            "net.botwithus.imgui.Textures",
            "net.botwithus.rs3.imgui.Textures",
            "net.botwithus.client.imgui.Textures",
            "net.botwithus.imgui.ImageLoader",
            "net.botwithus.rs3.imgui.ImageLoader",
            "net.botwithus.client.imgui.ImageLoader"
        )
        val methodNames = arrayOf(
            "loadTexture",
            "loadTexturePng",
            "createTexture",
            "createImage",
            "createTextureFromPng",
            "loadTextureFromBytes"
        )
        // Prepare a direct ByteBuffer variant in case the API expects it
        val directBuf = try {
            val bb = java.nio.ByteBuffer.allocateDirect(bytes.size)
            bb.put(bytes)
            bb.flip()
            bb
        } catch (_: Throwable) { null }

        for (cn in classes) {
            val cls = try { Class.forName(cn) } catch (_: Throwable) { null } ?: continue
            for (mn in methodNames) {
                // Try (byte[])
                try {
                    val m = cls.getMethod(mn, ByteArray::class.java)
                    val res = m.invoke(null, bytes)
                    when (res) {
                        is Number -> if (res.toLong() != 0L) return res
                        null -> {}
                        else -> return res // texture-like object
                    }
                } catch (_: Throwable) { }
                // Try (ByteBuffer)
                if (directBuf != null) {
                    try {
                        val m = cls.getMethod(mn, java.nio.ByteBuffer::class.java)
                        val res = m.invoke(null, directBuf)
                        when (res) {
                            is Number -> if (res.toLong() != 0L) return res
                            null -> {}
                            else -> return res
                        }
                    } catch (_: Throwable) { }
                }
            }
            // Signature-agnostic sweep: any static method taking (byte[]) or (ByteBuffer) and returning number
            try {
                for (m in cls.methods) {
                    if (!java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
                    val p = m.parameterTypes
                    if (p.size == 1 && (p[0] == ByteArray::class.java || p[0] == java.nio.ByteBuffer::class.java)) {
                        val arg = if (p[0] == ByteArray::class.java) bytes else directBuf
                        if (arg != null) {
                            try {
                                val res = m.invoke(null, arg)
                                when (res) {
                                    is Number -> if (res.toLong() != 0L) return res
                                    null -> {}
                                    else -> return res
                                }
                            } catch (_: Throwable) { }
                        }
                    }
                }
            } catch (_: Throwable) { }
        }
        // Try common String/URL/InputStream based loaders
        try {
            // Prepare candidates for location parameters
            val tmpFile = kotlin.runCatching {
                val f = java.io.File.createTempFile("uberith_logo_", ".png")
                f.deleteOnExit()
                f.outputStream().use { it.write(bytes) }
                f
            }.getOrNull()
            val urlSelf = try { this::class.java.getResource("/images/Uberith_Logo_Full_Text.png") } catch (_: Throwable) { null }
            val locStrings = listOf(
                "images/Uberith_Logo_Full_Text.png",
                "/images/Uberith_Logo_Full_Text.png",
                tmpFile?.absolutePath
            ).filterNotNull()
            for (cn in classes) {
                val cls = try { Class.forName(cn) } catch (_: Throwable) { null } ?: continue
                val names = arrayOf(
                    "loadTexture", "createTexture", "createImage",
                    "loadTextureFromFile", "createTextureFromFile", "loadTextureResource"
                )
                for (mn in names) {
                    // (String)
                    for (ls in locStrings) {
                        try {
                            val m = cls.getMethod(mn, String::class.java)
                            val res = m.invoke(null, ls)
                            when (res) {
                                is Number -> if (res.toLong() != 0L) return res
                                null -> {}
                                else -> return res
                            }
                        } catch (_: Throwable) { }
                    }
                    // (java.net.URL)
                    if (urlSelf != null) {
                        try {
                            val m = cls.getMethod(mn, java.net.URL::class.java)
                            val res = m.invoke(null, urlSelf)
                            when (res) {
                                is Number -> if (res.toLong() != 0L) return res
                                null -> {}
                                else -> return res
                            }
                        } catch (_: Throwable) { }
                    }
                    // (InputStream)
                    try {
                        val m = cls.getMethod(mn, java.io.InputStream::class.java)
                        val res = bytes.inputStream().use { ins -> m.invoke(null, ins) }
                        when (res) {
                            is Number -> if (res.toLong() != 0L) return res
                            null -> {}
                            else -> return res
                        }
                    } catch (_: Throwable) { }
                }
            }
        } catch (_: Throwable) { }
        // Decode PNG and try width/height + pixel buffer signatures
        try {
            val img = ImageIO.read(bytes.inputStream())
            if (img != null) {
                val w = img.width
                val h = img.height
                // Convert to RGBA8
                val argbImage = if (img.type == java.awt.image.BufferedImage.TYPE_INT_ARGB) img else {
                    val tmp = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    val g = tmp.createGraphics()
                    try { g.drawImage(img, 0, 0, null) } finally { g.dispose() }
                    tmp
                }
                val data = IntArray(w * h)
                argbImage.getRGB(0, 0, w, h, data, 0, w)
                val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4)
                // Convert ARGB -> RGBA
                for (p in data) {
                    val a = (p ushr 24) and 0xFF
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    buf.put(r.toByte())
                    buf.put(g.toByte())
                    buf.put(b.toByte())
                    buf.put(a.toByte())
                }
                buf.flip()

                val ctorNames = arrayOf(
                    "createTexture",
                    "createImage",
                    "loadTexture",
                    "uploadTexture",
                    "registerTexture"
                )
                val intCls = java.lang.Integer.TYPE
                val longCls = java.lang.Long.TYPE
                val floatCls = java.lang.Float.TYPE
                val bufCls = java.nio.ByteBuffer::class.java

                for (cn in classes) {
                    val cls = try { Class.forName(cn) } catch (_: Throwable) { null } ?: continue
                    for (mn in ctorNames) {
                        // Try (ByteBuffer, int, int)
                        try {
                            val m = cls.getMethod(mn, bufCls, intCls, intCls)
                            val res = m.invoke(null, buf, w, h)
                            when (res) {
                                is Number -> if (res.toLong() != 0L) return res
                                null -> {}
                                else -> return res
                            }
                        } catch (_: Throwable) { }
                        // Try (int, int, ByteBuffer)
                        try {
                            val m = cls.getMethod(mn, intCls, intCls, bufCls)
                            val res = m.invoke(null, w, h, buf)
                            when (res) {
                                is Number -> if (res.toLong() != 0L) return res
                                null -> {}
                                else -> return res
                            }
                        } catch (_: Throwable) { }
                        // Try (long, long, ByteBuffer) in case of long sizes
                        try {
                            val m = cls.getMethod(mn, longCls, longCls, bufCls)
                            val res = m.invoke(null, w.toLong(), h.toLong(), buf)
                            when (res) {
                                is Number -> if (res.toLong() != 0L) return res
                                null -> {}
                                else -> return res
                            }
                        } catch (_: Throwable) { }
                        // Try (ByteBuffer, float, float)
                        try {
                            val m = cls.getMethod(mn, bufCls, floatCls, floatCls)
                            val res = m.invoke(null, buf, w.toFloat(), h.toFloat())
                            when (res) {
                                is Number -> if (res.toLong() != 0L) return res
                                null -> {}
                                else -> return res
                            }
                        } catch (_: Throwable) { }
                    }
                    // Signature-agnostic sweep: any static method with ByteBuffer + (int|long) + (int|long)
                    try {
                        for (m in cls.methods) {
                            if (!java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
                            val p = m.parameterTypes
                            if (p.size != 3) continue
                            val hasBuf = p.any { it == bufCls }
                            val other = p.filter { it != bufCls }
                            if (!hasBuf) continue
                            if (other.size == 2 && other.all { it == intCls || it == longCls || it == floatCls }) {
                                try {
                                    val args = arrayOfNulls<Any>(3)
                                    var ai = 0
                                    for (t in p) {
                                        args[ai++] = when (t) {
                                            bufCls -> buf
                                            intCls -> w
                                            longCls -> w.toLong()
                                            floatCls -> w.toFloat()
                                            else -> null
                                        }
                                    }
                                    // second dimension as h
                                    for (i in p.indices) {
                                        if (p[i] == intCls || p[i] == longCls || p[i] == floatCls) { args[i] = if (args[i] == w || args[i] == w.toLong() || args[i] == w.toFloat()) h else args[i] }
                                    }
                                    val res = m.invoke(null, *args)
                                    when (res) {
                                        is Number -> if (res.toLong() != 0L) return res
                                        null -> {}
                                        else -> return res
                                    }
                                } catch (_: Throwable) { }
                            }
                        }
                    } catch (_: Throwable) { }
                }
            }
        } catch (_: Throwable) { }

        // Suppress noisy reflection diagnostics in production; keep UI clean if texture API is absent
        return null
    }

    private fun hasImGuiImageApi(): Boolean {
        val candidates = arrayOf("net.botwithus.imgui.ImGui", "net.botwithus.rs3.imgui.ImGui")
        for (cn in candidates) {
            try {
                val cls = Class.forName(cn)
                // Look for an image draw method signature commonly used
                val m1 = cls.methods.firstOrNull {
                    java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name.equals("Image", true) &&
                        it.parameterCount == 3 && it.parameterTypes[0] == java.lang.Long.TYPE
                }
                if (m1 != null) return true
            } catch (_: Throwable) { }
        }
        return false
    }

    private fun freeTexture(tex: Any) {
        val classes = arrayOf("net.botwithus.imgui.ImGui", "net.botwithus.rs3.imgui.ImGui")
        val methodNames = arrayOf("freeTexture", "deleteTexture", "destroyTexture")
        for (cn in classes) {
            val cls = try { Class.forName(cn) } catch (_: Throwable) { null } ?: continue
            for (mn in methodNames) {
                try { cls.getMethod(mn, java.lang.Long.TYPE).invoke(null, (tex as? Number)?.toLong() ?: return); return } catch (_: Throwable) { }
                try { cls.getMethod(mn, java.lang.Integer.TYPE).invoke(null, (tex as? Number)?.toInt() ?: return); return } catch (_: Throwable) { }
                try { cls.getMethod(mn, Any::class.java).invoke(null, tex); return } catch (_: Throwable) { }
            }
        }
    }

    private fun tryInvokeImGuiImage(texId: Long, w: Float, h: Float): Boolean {
        val classes = arrayOf("net.botwithus.imgui.ImGui", "net.botwithus.rs3.imgui.ImGui")
        for (cn in classes) {
            try {
                val cls = Class.forName(cn)
                // Try Image(Long, float, float)
                try {
                    val m = cls.getMethod("Image", java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                    m.invoke(null, texId, w, h)
                    return true
                } catch (_: Throwable) { }
                // Try image(long, float, float)
                try {
                    val m = cls.getMethod("image", java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                    m.invoke(null, texId, w, h)
                    return true
                } catch (_: Throwable) { }
            } catch (_: Throwable) { }
        }
        return false
    }

    private fun header(title: String, cm: ColorManager) {
        val fa = cm.accentF()
        // Accent-colored title text
        ImGui.pushStyleColor(ColorManager.ColorType.Text.index, fa[0], fa[1], fa[2], fa[3])
        ImGui.text(title)
        ImGui.popStyleColor(1)
        // Accent underline bar
        ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, fa[0], fa[1], fa[2], fa[3])
        ImGui.beginChild("underline_$title", 0f, 2f, false, 0)
        ImGui.endChild()
        ImGui.popStyleColor(1)
        ImGui.spacing()
    }

    private fun drawNavItem(index: Int, label: String, navW: Float, cm: ColorManager, btnH: Float, rightPad: Float) {
        val isSelected = (selectedTab == index)
        if (isSelected) {
            // Colored selection bar using modern accent
            run {
                val f = cm.accentF()
                ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, f[0], f[1], f[2], f[3])
            }
            ImGui.beginChild("NavSelBar$index", 5f, btnH, false, 0)
            ImGui.endChild()
            ImGui.popStyleColor(1)
            ImGui.sameLine(0f, 6f)
            // Accent the active button background
            val fa = cm.accentF()
            val fh = cm.accentHoverF()
            val ft = cm.accentActiveF()
            ImGui.pushStyleColor(ColorManager.ColorType.Button.index, fa[0], fa[1], fa[2], fa[3])
            ImGui.pushStyleColor(ColorManager.ColorType.ButtonHovered.index, fh[0], fh[1], fh[2], fh[3])
            ImGui.pushStyleColor(ColorManager.ColorType.ButtonActive.index, ft[0], ft[1], ft[2], ft[3])
        } else {
            ImGui.beginChild("NavSelBar$index", 5f, btnH, false, 0)
            ImGui.endChild()
            ImGui.sameLine(0f, 6f)
        }
        val shown = label
        val clicked = ImGui.button(shown, navW - 5f - 6f - rightPad, btnH)
        if (isSelected) ImGui.popStyleColor(3)
        if (clicked) {
            selectedTab = index
        }
    }

    private fun drawNavSpacer(height: Float, id: Int) {
        // Create vertical space using an empty child of specified height (stable ID to avoid rebuild churn)
        ImGui.beginChild("NavSpacer_$id", 0f, height, false, 0)
        ImGui.endChild()
    }

    private fun drawOverview() {
        ImGui.separator()
        ImGui.text("Target Tree:")
        ImGui.sameLine(0f, 6f)
        val allTrees = com.uberith.uberchop.TreeTypes.ALL
        val currentIdx = script.settings.savedTreeType.coerceIn(0, allTrees.size - 1)
        val currentName = allTrees[currentIdx]
        var treeChanged = false
        if (ImGui.beginCombo("##targetTreeCombo", currentName, 0)) {
            for (i in allTrees.indices) {
                val isSelected = (i == currentIdx)
                if (ImGui.selectable(allTrees[i], isSelected, 0, 0f, 0f)) {
                    script.settings.savedTreeType = i
                    script.targetTree = allTrees[i]
                    treeChanged = true
                }
            }
            ImGui.endCombo()
        }

        // Dynamic Location combo (options change with selected tree)
        val curTreeLower = script.targetTree.lowercase()
        val filtered = script.treeLocations.filter { loc ->
            loc.availableTrees.any { at ->
                val a = at.lowercase()
                a.contains(curTreeLower) || curTreeLower.contains(a)
            }
        }
        // If tree changed and current location is no longer valid, reset to first valid
        if (treeChanged && filtered.none { it.name == script.location }) {
            val fallback = filtered.firstOrNull()?.name ?: script.treeLocations.firstOrNull()?.name ?: ""
            script.location = fallback
            script.settings.savedLocation = fallback
        }
        val locationNames = filtered.map { it.name }
        val curLocName = script.location
        val curLocIdx = locationNames.indexOf(curLocName).coerceAtLeast(0)
        ImGui.text("Location:")
        ImGui.sameLine(0f, 6f)
        val shownLoc = if (locationNames.isNotEmpty()) locationNames.getOrElse(curLocIdx) { locationNames.first() } else "-"
        if (ImGui.beginCombo("##locationCombo", shownLoc, 0)) {
            for (i in locationNames.indices) {
                val isSelected = (i == curLocIdx)
                if (ImGui.selectable(locationNames[i], isSelected, 0, 0f, 0f)) {
                    script.location = locationNames[i]
                    script.settings.savedLocation = script.location
                }
            }
            ImGui.endCombo()
        }

        ImGui.separator()
        // Requirements (single section on Overview)
        ImGui.text("Requirements")
        val treeType = resolveTreeType(script.targetTree)
        val reqLevel = treeType?.levelReq
        ImGui.text("Recommended WC Level: ${reqLevel ?: "—"}")
        // Show user's WC level, colored green if >= recommended, else red
        val userWc = UberChop().WCLevel
        ImGui.text("Your WC Level:")
        ImGui.sameLine(0f, 6f)
        when {
            userWc == null -> {
                ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.8f, 0.8f, 0.8f, 1f)
                ImGui.text("N/A")
                ImGui.popStyleColor(1)
            }
            reqLevel == null -> {
                ImGui.text(userWc.toString())
            }
            userWc >= reqLevel -> {
                ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.35f, 0.84f, 0.49f, 1f) // green
                ImGui.text(userWc.toString())
                ImGui.popStyleColor(1)
            }
            else -> {
                ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.94f, 0.36f, 0.36f, 1f) // red
                ImGui.text(userWc.toString())
                ImGui.popStyleColor(1)
            }
        }

        // Configured tiles checks
        run {
            val curLoc = script.location
            // Chop
            val effChop = try {
                val sel = script.treeLocations.firstOrNull { it.name == curLoc }
                val o = script.settings.customLocations[curLoc]
                if (o?.chopX != null && o.chopY != null && o.chopZ != null) net.botwithus.rs3.world.Coordinate(o.chopX!!, o.chopY!!, o.chopZ!!) else sel?.chop
            } catch (_: Throwable) { null }
            drawStatusLine("Configured chop tile?", effChop != null)

            // Bank
            val effBank = try {
                val sel = script.treeLocations.firstOrNull { it.name == curLoc }
                val o = script.settings.customLocations[curLoc]
                if (o?.bankX != null && o.bankY != null && o.bankZ != null) net.botwithus.rs3.world.Coordinate(o.bankX!!, o.bankY!!, o.bankZ!!) else sel?.bank
            } catch (_: Throwable) { null }
            drawStatusLine("Configured bank tile?", effBank != null)
        }

        // No Inventory Hints or Bank/Chop sections on Overview per request
    }

    private fun drawCore() {
        var b = ImGui.checkbox("Pickup Nests", script.settings.pickupNests)
        script.settings.pickupNests = b
        b = ImGui.checkbox("Enable Tree Rotation", script.settings.enableTreeRotation)
        script.settings.enableTreeRotation = b
        b = ImGui.checkbox("Enable World Hopping", script.settings.enableWorldHopping)
        script.settings.enableWorldHopping = b
        b = ImGui.checkbox("Use Magic Notepaper", script.settings.useMagicNotepaper)
        script.settings.useMagicNotepaper = b
        b = ImGui.checkbox("Use Crystallise", script.settings.useCrystallise)
        script.settings.useCrystallise = b
        b = ImGui.checkbox("Use Juju Potions", script.settings.useJujuPotions)
        script.settings.useJujuPotions = b
        b = ImGui.checkbox("Withdraw Wood Box", script.settings.withdrawWoodBox)
        script.settings.withdrawWoodBox = b
    }

    // --- Helpers for Overview readiness ---
    private fun drawStatusLine(label: String, ok: Boolean, unknown: Boolean = false) {
        ImGui.text(label)
        ImGui.sameLine(0f, 8f)
        if (unknown) {
            // Gray/unknown state
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.8f, 0.8f, 0.8f, 1f)
            ImGui.text("Unknown")
            ImGui.popStyleColor(1)
            return
        }
        if (ok) {
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.35f, 0.84f, 0.49f, 1f) // green
            ImGui.text("OK")
        } else {
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.94f, 0.36f, 0.36f, 1f) // red
            ImGui.text("Missing")
        }
        ImGui.popStyleColor(1)
    }

    private fun resolveTreeType(name: String): com.uberith.api.game.skills.woodcutting.TreeType? {
        val values = com.uberith.api.game.skills.woodcutting.TreeType.values()
        val norm = name.trim().lowercase()
        // Prefer exact match on displayName or its short form without trailing " tree"
        val exact = values.firstOrNull { t ->
            val disp = t.displayName.trim().lowercase()
            val short = disp.removeSuffix(" tree")
            norm.equals(disp, true) || norm.equals(short, true)
        }
        if (exact != null) return exact
        // Fallback: lenient contains match
        return values.firstOrNull { t ->
            val disp = t.displayName.trim().lowercase()
            disp.contains(norm) || norm.contains(disp)
        }
    }

    private fun hasBackpackItemRegex(pattern: String): Boolean {
        return try {
            val cls = Class.forName("com.uberith.api.game.inventory.Backpack")
            val pat = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
            val m = cls.getMethod("contains", java.util.regex.Pattern::class.java)
            (m.invoke(null, pat) as? Boolean) == true
        } catch (_: Throwable) { false }
    }

    private fun hasBankItemRegex(pattern: String): Boolean {
        return try {
            val cls = Class.forName("com.uberith.api.game.inventory.Bank")
            val pat = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
            val m = cls.methods.firstOrNull { it.name == "contains" && it.parameterTypes.size == 1 && it.parameterTypes[0] == java.util.regex.Pattern::class.java }
            if (m != null) {
                (m.invoke(null, pat) as? Boolean) == true
            } else {
                false
            }
        } catch (_: Throwable) { false }
    }

    private fun drawHandlers() {
        header("Break Handler", ColorManager())
        var b = ImGui.checkbox("Random Breaks", script.settings.performRandomBreak)
        script.settings.performRandomBreak = b
        script.settings.breakFrequency = adjustInt("Breaks/hr", script.settings.breakFrequency, 0, 12)
        script.settings.minBreak = adjustInt("Min Break (s)", script.settings.minBreak, 0, 600, 5)
        script.settings.maxBreak = adjustInt("Max Break (s)", script.settings.maxBreak, 0, 3600, 5)

        ImGui.separator()
        header("Logout Handler", ColorManager())
        b = ImGui.checkbox("Enable Timed Logout", script.settings.logoutDurationEnable)
        script.settings.logoutDurationEnable = b
        script.settings.logoutHours = adjustInt("After (h)", script.settings.logoutHours, 0, 24)
        script.settings.logoutMinutes = adjustInt("After (m)", script.settings.logoutMinutes, 0, 59)
        script.settings.logoutSeconds = adjustInt("After (s)", script.settings.logoutSeconds, 0, 59)

        ImGui.separator()
        header("AFK Handler", ColorManager())
        b = ImGui.checkbox("Enable AFK", script.settings.enableAfk)
        script.settings.enableAfk = b
        script.settings.afkEveryMin = adjustInt("Every min (m)", script.settings.afkEveryMin, 0, 180)
        script.settings.afkEveryMax = adjustInt("Every max (m)", script.settings.afkEveryMax, 0, 240)
        script.settings.afkDurationMin = adjustInt("Duration min (s)", script.settings.afkDurationMin, 0, 600, 5)
        script.settings.afkDurationMax = adjustInt("Duration max (s)", script.settings.afkDurationMax, 0, 3600, 5)

        ImGui.separator()
        header("Auto-Stop", ColorManager())
        b = ImGui.checkbox("Enable Auto-Stop", script.settings.enableAutoStop)
        script.settings.enableAutoStop = b
        script.settings.stopAfterHours = adjustInt("After (h)", script.settings.stopAfterHours, 0, 48)
        script.settings.stopAfterMinutes = adjustInt("After (m)", script.settings.stopAfterMinutes, 0, 59)
        script.settings.stopAfterXp = adjustInt("After XP", script.settings.stopAfterXp, 0, 50_000_000, 1000)
        script.settings.stopAfterLogs = adjustInt("After Logs", script.settings.stopAfterLogs, 0, 1_000_000, 10)
    }

    private fun drawWorldHop() {
        header("World Hop Filters", ColorManager())
        script.settings.minPing = adjustInt("Min Ping", script.settings.minPing, 0, 1000, 5)
        script.settings.maxPing = adjustInt("Max Ping", script.settings.maxPing, 0, 1000, 5)
        script.settings.minPopulation = adjustInt("Min Pop", script.settings.minPopulation, 0, 4000, 10)
        script.settings.maxPopulation = adjustInt("Max Pop", script.settings.maxPopulation, 0, 4000, 10)
        script.settings.hopDelayMs = adjustInt("Hop Delay (ms)", script.settings.hopDelayMs, 0, 60_000, 100)
        var b = ImGui.checkbox("Members-Only Worlds", script.settings.memberOnlyWorlds)
        script.settings.memberOnlyWorlds = b
        b = ImGui.checkbox("Only F2P", script.settings.onlyFreeToPlay)
        script.settings.onlyFreeToPlay = b
        b = ImGui.checkbox("Hop On Chat Activity", script.settings.hopOnChat)
        script.settings.hopOnChat = b
        b = ImGui.checkbox("Hop On Crowd Threshold", script.settings.hopOnCrowd)
        script.settings.hopOnCrowd = b
        script.settings.playerThreshold = adjustInt("Player Threshold", script.settings.playerThreshold, 0, 200, 1)
        b = ImGui.checkbox("Hop On No Trees", script.settings.hopOnNoTrees)
        script.settings.hopOnNoTrees = b
    }

    private fun drawAdvanced() {
        ImGui.text("Saved Preferences")
        script.settings.savedTreeType = adjustInt("Saved Tree Type", script.settings.savedTreeType, 0, 50, 1)
        ImGui.text("Saved Location: ${script.settings.savedLocation}")
        ImGui.separator()
        ImGui.text("Custom Locations")
        val curLoc = script.location
        ImGui.text("Selected: $curLoc")
        // Show effective chop/bank tiles
        val effChop = try {
            val sel = script.treeLocations.firstOrNull { it.name == curLoc }
            val c = script.run { 
                // reflection-free call into effectiveChopFor via public path is not available; recompute
                val o = script.settings.customLocations[curLoc]
                if (o?.chopX != null && o.chopY != null && o.chopZ != null) net.botwithus.rs3.world.Coordinate(o.chopX!!, o.chopY!!, o.chopZ!!) else sel?.chop
            }
            if (c != null) "${'$'}{c.x}, ${'$'}{c.y}, ${'$'}{c.plane}" else "—"
        } catch (_: Throwable) { "—" }
        val effBank = try {
            val sel = script.treeLocations.firstOrNull { it.name == curLoc }
            val o = script.settings.customLocations[curLoc]
            val b = if (o?.bankX != null && o.bankY != null && o.bankZ != null) net.botwithus.rs3.world.Coordinate(o.bankX!!, o.bankY!!, o.bankZ!!) else sel?.bank
            if (b != null) "${'$'}{b.x}, ${'$'}{b.y}, ${'$'}{b.plane}" else "—"
        } catch (_: Throwable) { "—" }
        ImGui.text("Chop: $effChop  |  Bank: $effBank")
        if (ImGui.button("Set Chop Tile", 120f, 0f)) {
            try {
                val cls = Class.forName("net.botwithus.rs3.entities.LocalPlayer")
                val me = cls.getMethod("self").invoke(null)
                val coord = me?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("coordinate") }?.invoke(me)
                val x = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("x","getx") }?.invoke(coord) as? Number
                val y = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("y","gety") }?.invoke(coord) as? Number
                val z = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("plane") || it.name.lowercase() == "z" || it.name.lowercase() == "getz" }?.invoke(coord) as? Number
                if (x != null && y != null && z != null) {
                    val map = script.settings.customLocations
                    val cur = map[curLoc] ?: com.uberith.uberchop.CustomLocation()
                    cur.chopX = x.toInt(); cur.chopY = y.toInt(); cur.chopZ = z.toInt()
                    map[curLoc] = cur
                }
            } catch (_: Throwable) {}
        }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear Chop", 90f, 0f)) {
            val map = script.settings.customLocations
            val cur = map[curLoc]
            if (cur != null) { cur.chopX = null; cur.chopY = null; cur.chopZ = null; if (cur.bankX==null && cur.bankY==null && cur.bankZ==null) map.remove(curLoc) else map[curLoc]=cur }
        }
        if (ImGui.button("Set Bank Tile", 120f, 0f)) {
            try {
                val cls = Class.forName("net.botwithus.rs3.entities.LocalPlayer")
                val me = cls.getMethod("self").invoke(null)
                val coord = me?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("coordinate") }?.invoke(me)
                val x = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("x","getx") }?.invoke(coord) as? Number
                val y = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("y","gety") }?.invoke(coord) as? Number
                val z = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("plane") || it.name.lowercase() == "z" || it.name.lowercase() == "getz" }?.invoke(coord) as? Number
                if (x != null && y != null && z != null) {
                    val map = script.settings.customLocations
                    val cur = map[curLoc] ?: com.uberith.uberchop.CustomLocation()
                    cur.bankX = x.toInt(); cur.bankY = y.toInt(); cur.bankZ = z.toInt()
                    map[curLoc] = cur
                }
            } catch (_: Throwable) {}
        }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear Bank", 90f, 0f)) {
            val map = script.settings.customLocations
            val cur = map[curLoc]
            if (cur != null) { cur.bankX = null; cur.bankY = null; cur.bankZ = null; if (cur.chopX==null && cur.chopY==null && cur.chopZ==null) map.remove(curLoc) else map[curLoc]=cur }
        }
        ImGui.separator()
        ImGui.text("Deposit Filters")
        ImGui.text("Include: ${'$'}{script.settings.depositInclude.size}  |  Keep: ${'$'}{script.settings.depositKeep.size}")
        if (ImGui.button("Add Blossom", 110f, 0f)) { if (!script.settings.depositInclude.contains("Crystal tree blossom")) script.settings.depositInclude.add("Crystal tree blossom") }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Add Bamboo", 110f, 0f)) { if (!script.settings.depositInclude.contains("Bamboo")) script.settings.depositInclude.add("Bamboo") }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Keep Nests", 110f, 0f)) { if (!script.settings.depositKeep.contains("Bird's nest")) script.settings.depositKeep.add("Bird's nest") }
        if (ImGui.button("Clear Include", 110f, 0f)) { script.settings.depositInclude.clear() }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear Keep", 110f, 0f)) { script.settings.depositKeep.clear() }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Add From Clipboard", 160f, 0f)) {
            try {
                val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val data = cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                val text = data?.trim()
                if (!text.isNullOrEmpty()) {
                    if (!script.settings.depositInclude.contains(text)) script.settings.depositInclude.add(text)
                }
            } catch (_: Throwable) { }
        }
        // Show Include list with remove buttons
        if (ImGui.beginChild("IncludeList", 0f, 100f, true, 0)) {
            ImGui.text("Include List")
            val iter = script.settings.depositInclude.listIterator()
            var idx = 0
            while (iter.hasNext()) {
                val name = iter.next()
                ImGui.text(name)
                ImGui.sameLine(0f, 8f)
                if (ImGui.button("x##inc_$idx", 20f, 0f)) { iter.remove() }
                idx++
            }
        }
        ImGui.endChild()
        // Show Keep list with remove buttons and clipboard add to Keep
        if (ImGui.beginChild("KeepList", 0f, 100f, true, 0)) {
            ImGui.text("Keep List")
            val iterK = script.settings.depositKeep.listIterator()
            var k = 0
            while (iterK.hasNext()) {
                val name = iterK.next()
                ImGui.text(name)
                ImGui.sameLine(0f, 8f)
                if (ImGui.button("x##keep_$k", 20f, 0f)) { iterK.remove() }
                k++
            }
            if (ImGui.button("Add Keep From Clipboard", 200f, 0f)) {
                try {
                    val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val data = cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    val text = data?.trim()
                    if (!text.isNullOrEmpty() && !script.settings.depositKeep.contains(text)) script.settings.depositKeep.add(text)
                } catch (_: Throwable) { }
            }
        }
        ImGui.endChild()
        ImGui.separator()
        ImGui.text("Auto-Skill")
        var b = ImGui.checkbox("Auto-Progress Tree", script.settings.autoProgressTree)
        script.settings.autoProgressTree = b
        b = ImGui.checkbox("Auto-Upgrade Tree", script.settings.autoUpgradeTree)
        script.settings.autoUpgradeTree = b
        script.settings.tanningProductIndex = adjustInt("Preset/Prod Index", script.settings.tanningProductIndex, 0, 20)
    }

    private fun drawStatistics() {
        ImGui.text("Statistics")
        ImGui.separator()
        ImGui.text("Runtime: ${script.formattedRuntime()}")
        ImGui.text("Logs chopped: ${script.logsChopped}")
        ImGui.text("Logs/hour: ${script.logsPerHour()}")
        ImGui.separator()
        ImGui.text("Target: ${script.targetTree}")
        ImGui.text("Phase: ${script.phase}")
        ImGui.text("Log handling: " + when (script.settings.logHandlingMode) {
            1 -> "Magic Notepaper"
            2 -> "No Bank"
            else -> "Bank logs + nests"
        })
    }

    private fun drawSupport() {
        ImGui.text("Support")
        ImGui.separator()
        ImGui.text("Having issues or suggestions?")
        ImGui.text("- Share logs, screenshots, and steps to reproduce.")
        ImGui.text("- Include your target tree and location.")
        ImGui.separator()
        ImGui.text("Quick Tips")
        ImGui.text("- Use Magic Notepaper mode when stationary.")
        ImGui.text("- WorldHop filters can reduce crowds.")
        ImGui.text("- Breaks/AFK help reduce detection risk.")
    }

    private var debugSelectable = false
    private fun drawDebug() {
        ImGui.text("Debug Console")
        ImGui.sameLine(0f, 8f)
        debugSelectable = ImGui.checkbox("Selectable", debugSelectable)
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Copy All", 90f, 0f)) {
            try {
                val all = readUberChopLogTail(1200).joinToString("\n")
                val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                cb.setContents(java.awt.datatransfer.StringSelection(all), null)
            } catch (_: Throwable) { }
        }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear File", 90f, 0f)) {
            try {
                val home = System.getProperty("user.home")
                val path = java.nio.file.Paths.get(home, ".BotWithUs", "logs", "UberChop.log")
                java.nio.file.Files.newBufferedWriter(path, java.nio.charset.StandardCharsets.UTF_8).use { /* truncate */ }
            } catch (_: Throwable) { }
        }
        ImGui.separator()
        val lines = readUberChopLogTail(400)
        if (ImGui.beginChild("DebugScroll", 0f, 0f, true, 0)) {
            if (debugSelectable) {
                var idx = 0
                for (ln in lines) {
                    val clicked = ImGui.selectable("$idx: $ln", false, 0, 0f, 0f)
                    if (clicked) {
                        try {
                            val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            cb.setContents(java.awt.datatransfer.StringSelection(ln), null)
                        } catch (_: Throwable) { }
                    }
                    idx++
                }
            } else {
                for (ln in lines) ImGui.text(ln)
            }
        }
        ImGui.endChild()
    }

    private fun readUberChopLogTail(maxLines: Int): List<String> {
        return try {
            val home = System.getProperty("user.home")
            val path = java.nio.file.Paths.get(home, ".BotWithUs", "logs", "UberChop.log")
            if (!java.nio.file.Files.exists(path)) return emptyList()
            val all = java.nio.file.Files.readAllLines(path)
            if (all.size <= maxLines) all else all.takeLast(maxLines)
        } catch (_: Throwable) { emptyList() }
    }

    private fun tryGetWorldIdText(): String {
        return try {
            // Prefer LoginManager.getGameWorlds -> pick the first members-matching world with lowest ping as heuristic
            try {
                val lm = Class.forName("net.botwithus.rs3.login.LoginManager")
                val m = lm.getMethod("getGameWorlds")
                val worlds = (m.invoke(null) as? java.util.Collection<*>)?.toList() ?: emptyList()
                var best: Any? = null
                var bestPing = Int.MAX_VALUE
                for (w in worlds) {
                    try {
                        val ping = (w!!.javaClass.getMethod("getPing").invoke(w) as? Number)?.toInt() ?: continue
                        if (ping < bestPing) { bestPing = ping; best = w }
                    } catch (_: Throwable) { }
                }
                if (best != null) {
                    val id = best.javaClass.getMethod("getWorldId").invoke(best) as? Number
                    if (id != null) return id.toString()
                }
            } catch (_: Throwable) { }
            "N/A"
        } catch (_: Throwable) { "N/A" }
    }

    private fun tryGetPingMsText(): String {
        return try {
            // Prefer LoginManager.getGameWorlds and use lowest ping as heuristic
            try {
                val lm = Class.forName("net.botwithus.rs3.login.LoginManager")
                val m = lm.getMethod("getGameWorlds")
                val worlds = (m.invoke(null) as? java.util.Collection<*>)?.toList() ?: emptyList()
                var bestPing: Int? = null
                for (w in worlds) {
                    try {
                        val ping = (w!!.javaClass.getMethod("getPing").invoke(w) as? Number)?.toInt() ?: continue
                        if (bestPing == null || ping < bestPing!!) bestPing = ping
                    } catch (_: Throwable) { }
                }
                if (bestPing != null) return bestPing.toString()
            } catch (_: Throwable) { }
            "N/A"
        } catch (_: Throwable) { "N/A" }
    }

    private fun tryGetPlayerCoordsText(): String {
        return try {
            // Prefer RS3 LocalPlayer API via reflection to avoid hard linking
            val cls = Class.forName("net.botwithus.rs3.entities.LocalPlayer")
            val mSelf = cls.getMethod("self")
            val player = mSelf.invoke(null) ?: return "—"
            val mCoord = player.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("coordinate") }
            val coord = mCoord?.invoke(player) ?: return "—"
            val xM = coord.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("x", "getx", "getxcoord", "getxcoordinate") }
            val yM = coord.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("y", "gety", "getycoord", "getycoordinate") }
            val zM = coord.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("z", "getz", "getplane", "getlevel") }
            val x = (xM?.invoke(coord) as? Number)?.toInt()
            val y = (yM?.invoke(coord) as? Number)?.toInt()
            val z = (zM?.invoke(coord) as? Number)?.toInt()
            if (x != null && y != null && z != null) "$x, $y, $z" else "—"
        } catch (_: Throwable) {
            try {
                // Fallback: XAPI style LocalPlayer
                val cls = Class.forName("net.botwithus.xapi.game.actor.LocalPlayer")
                val mSelf = cls.getMethod("self")
                val player = mSelf.invoke(null) ?: return "—"
                val mCoord = player.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("coordinate") }
                val coord = mCoord?.invoke(player) ?: return "—"
                val xM = coord.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("x") }
                val yM = coord.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("y") }
                val zM = coord.javaClass.methods.firstOrNull { it.parameterCount == 0 && (it.name.lowercase().contains("z") || it.name.lowercase().contains("plane") || it.name.lowercase().contains("level")) }
                val x = (xM?.invoke(coord) as? Number)?.toInt()
                val y = (yM?.invoke(coord) as? Number)?.toInt()
                val z = (zM?.invoke(coord) as? Number)?.toInt()
                if (x != null && y != null && z != null) "$x, $y, $z" else "—"
            } catch (_: Throwable) { "—" }
        }
    }

    private fun tryGetAnimIdText(): String {
        return try {
            val cls = Class.forName("net.botwithus.rs3.entities.LocalPlayer")
            val mSelf = cls.getMethod("self")
            val player = mSelf.invoke(null) ?: return "-"
            val mAnim = player.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("animation") }
            val id = (mAnim?.invoke(player) as? Number)?.toInt()
            id?.toString() ?: "-"
        } catch (_: Throwable) { "-" }
    }
}
