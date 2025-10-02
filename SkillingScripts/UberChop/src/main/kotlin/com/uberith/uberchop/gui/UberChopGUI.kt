package com.uberith.uberchop.gui

import com.uberith.uberchop.UberChop
import com.uberith.uberchop.config.TreeLocations
import com.uberith.uberchop.config.QueueEntry
import com.uberith.uberchop.config.TreeTypes
import net.botwithus.imgui.ImGui
import net.botwithus.kxapi.imgui.ImGuiUI
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import com.uberith.api.ui.ColorManager
import com.uberith.api.ui.CustomImages
import com.uberith.api.ui.NavigationUI
import net.botwithus.kxapi.game.skilling.impl.woodcutting.TreeType
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.world.ClientState
import java.util.Locale
import net.botwithus.ui.workspace.Workspace
import net.botwithus.scripts.Info

class UberChopGUI(private val script: UberChop) : ImGuiUI() {
    // Tab identifiers mirror the navigation order for the shared UI components
    private var selectedTab: String = "Overview"
    private val tabOrder = listOf(
        "Overview",
        "Core",
        "Handlers",
        "WorldHop",
        "Advanced",
        "Statistics",
        "Support",
        "Debug"
    )
    private val WINDOW_W = 800f
    private val WINDOW_H = 620f
    private val NAV_W = 180f
    private val NAV_BUTTON_H = 40f
    private var appliedInitialWindowSize = false
    // Target tree selection will use a true Combo box (dropdown)

    private val windowTitle by lazy {
        val version = script.javaClass.getAnnotation(Info::class.java)?.version?.takeIf { it.isNotBlank() } ?: "?"
        "UberChop Settings | $version"
    }

    // UI state for manual coordinate entry on Overview (for "Anywhere")
    private var coordUiLocName: String = ""
    private var chopXArr = intArrayOf(0)
    private var chopYArr = intArrayOf(0)
    private var chopZArr = intArrayOf(0)
    private var bankXArr = intArrayOf(0)
    private var bankYArr = intArrayOf(0)
    private var bankZArr = intArrayOf(0)
    private var chopXYZText: String = ""
    private val logHandlingOptions = listOf("Bank Logs", "Burn Logs", "Fletch Logs")
    private var queueGoalInput = 1000
    private var bankXYZText: String = ""

    // Textures (loaded once, freed on demand)
    private var logoImg: Any? = null
    private var logoBytesSize: Int = 0
    private var logoLoadSource: String = ""
    private val log = LoggerFactory.getLogger(UberChopGUI::class.java)
    fun preload() {
        // Prepare for load during first draw when the UI context is ready
        unloadTextures()
    }

    fun render(@Suppress("UNUSED_PARAMETER") workspace: Workspace) {
        renderInternal()
    }

    private fun formatNumber(value: Number): String = String.format(Locale.US, "%,d", value.toLong())

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
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

    override fun build() = imguiUI {
        renderInternal()
    }


    private fun renderInternal() {
        script.ensureUiSettingsLoaded()
        if (!tabOrder.contains(selectedTab)) {
            selectedTab = tabOrder.first()
        }

        if (!appliedInitialWindowSize) {
            ImGui.setNextWindowSize(WINDOW_W, WINDOW_H)
            appliedInitialWindowSize = true
        }
        try {
            ImGui.setNextWindowBgAlpha(0.82f)
        } catch (_: Throwable) { }

        if (ImGui.begin(windowTitle, 0)) {
            val colorManager = ColorManager()
            colorManager.pushColors()

            drawHeaderSection()

            ImGui.separator()

            if (ImGui.beginChild("NavigationPanel", NAV_W, 0f, false, 0)) {
                drawNavigationPanel()
            }
            ImGui.endChild()

            ImGui.sameLine(0f, 16f)

            if (ImGui.beginChild("ContentPanel", 0f, 0f, false, 0)) {
                when (selectedTab) {
                    "Overview" -> drawOverview()
                    "Core" -> drawCore()
                    "Handlers" -> drawHandlers()
                    "WorldHop" -> drawWorldHop()
                    "Advanced" -> drawAdvanced()
                    "Statistics" -> drawStatistics()
                    "Support" -> drawSupport()
                    "Debug" -> drawDebug()
                }
            }
            ImGui.endChild()

            ImGui.separator()
            drawFooter()
            colorManager.popColors()
        }
        ImGui.end()
    }

    private fun drawHeaderSection() {
        if (ImGui.beginChild("HeaderSection", 0f, 120f, false, 0)) {
            drawLogoBar()
            ImGui.spacing()
            ImGui.text("Runtime ${script.formattedRuntime()}  |  Logs ${formatNumber(script.logsChopped)} (${formatNumber(script.logsPerHour())}/h)  |  Status ${script.currentStatus}")
            script.currentQueueProgress()?.let { progress ->
                ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.62f, 0.82f, 1f, 1f)
                val label = if (progress.goal > 0) {
                    val completed = (progress.goal - progress.remaining).coerceAtLeast(0)
                    "Queue ${progress.index + 1}/${progress.total}: ${progress.remaining} logs remaining (${completed}/${progress.goal})"
                } else {
                    "Queue ${progress.index + 1}/${progress.total}: ${progress.remaining} logs remaining"
                }
                ImGui.text(label)
                ImGui.popStyleColor(1)
            }
        }
        ImGui.endChild()
    }

    private fun drawNavigationPanel() {
        NavigationUI(
            selectedTab = selectedTab,
            availableTabs = tabOrder,
            onTabSelected = { selectedTab = it },
            buttonWidth = NAV_W - 20f,
            buttonHeight = NAV_BUTTON_H
        ).draw()
    }

    private fun drawFooter() {
        val player = runCatching { LocalPlayer.self() }.getOrNull()
        val worldText = ClientState.GAME.id.toString()
        val coordText = player?.coordinate?.toString() ?: "?"
        val animText = player?.animationId?.toString() ?: "?"
        ImGui.text("W: $worldText  |  XYZ: $coordText  |  Anim: $animText")
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
                ImGui.text("Uberith Gaming")
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
        val accent = cm.colorToFloats(cm.buttonSelectedColor)
        ImGui.pushStyleColor(ColorManager.ColorType.Text.index, accent[0], accent[1], accent[2], accent[3])
        ImGui.text(title)
        ImGui.popStyleColor(1)
        ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, accent[0], accent[1], accent[2], accent[3])
        ImGui.beginChild("underline_$title", 0f, 2f, false, 0)
        ImGui.endChild()
        ImGui.popStyleColor(1)
        ImGui.spacing()
    }

    private fun drawNavSpacer(height: Float, id: Int) {
        // Create vertical space using an empty child of specified height (stable ID to avoid rebuild churn)
        ImGui.beginChild("NavSpacer_$id", 0f, height, false, 0)
        ImGui.endChild()
    }

    private fun drawOverview() {
        ImGui.separator()

        val spacing = 30f
        val minLeftWidth = 320f
        val minRightWidth = 200f
        val contentWidth = (WINDOW_W - NAV_W - 20f).coerceAtLeast(minLeftWidth + minRightWidth + spacing)

        val usableWidth = contentWidth - spacing
        val targetRightWidth = (usableWidth * 0.42f).coerceAtLeast(minRightWidth)
        var rightColumnWidth = targetRightWidth.coerceAtMost(usableWidth - minLeftWidth)
        var leftColumnWidth = usableWidth - rightColumnWidth

        if (leftColumnWidth < minLeftWidth) {
            leftColumnWidth = minLeftWidth
            rightColumnWidth = usableWidth - leftColumnWidth
        }
        if (rightColumnWidth < minRightWidth) {
            rightColumnWidth = minRightWidth
            leftColumnWidth = (usableWidth - rightColumnWidth).coerceAtLeast(minLeftWidth)
        }

        if (ImGui.beginChild("OverviewLeftColumn", leftColumnWidth, 0f, false, 0)) {
            drawOverviewLeftColumn(leftColumnWidth)
        }
        ImGui.endChild()

        ImGui.sameLine(0f, spacing)

        if (ImGui.beginChild("OverviewRightColumn", rightColumnWidth, 0f, false, 0)) {
            drawOverviewRightColumn()
        }
        ImGui.endChild()

    }

    private fun drawOverviewLeftColumn(maxWidth: Float) {
        var pendingSettingsRefresh = false

        ImGui.text("Target Tree:")
        ImGui.sameLine(0f, 6f)

        val allTrees = TreeTypes.ALL
        val currentIdx = script.settings.savedTreeType.coerceIn(0, allTrees.size - 1)
        val currentName = allTrees[currentIdx]

        data class TreeEntry(val idx: Int, val name: String, val level: Int?)
        fun levelFor(name: String): Int? =
            try {
                resolveTreeType(name)?.levelReq
            } catch (_: Throwable) { null }

        val entries = allTrees.mapIndexed { i, n -> TreeEntry(i, n, levelFor(n)) }
        val known = entries.filter { it.level != null }.sortedBy { it.level!! }
        val unknown = entries.filter { it.level == null }
        val sorted = known + unknown

        fun labelFor(e: TreeEntry): String = e.level?.let { "[${it}] ${e.name}" } ?: e.name
        val currentLabel = labelFor(TreeEntry(currentIdx, currentName, levelFor(currentName)))

        var treeChanged = false
        if (ImGui.beginCombo("##targetTreeCombo", currentLabel, 0)) {
            for (e in sorted) {
                val isSelected = (e.idx == currentIdx)
                val lbl = labelFor(e)
                if (ImGui.selectable(lbl, isSelected, 0, 0f, 0f)) {
                    script.settings.savedTreeType = e.idx
                    script.targetTree = e.name
                    treeChanged = true
                    pendingSettingsRefresh = true
                }
            }
            ImGui.endCombo()
        }

        val filtered = TreeLocations.locationsFor(script.targetTree).ifEmpty { script.treeLocations }
        if (treeChanged && filtered.none { it.name == script.location }) {
            val fallback = filtered.firstOrNull()?.name ?: script.treeLocations.firstOrNull()?.name ?: ""
            script.location = fallback
            script.settings.savedLocation = fallback
            pendingSettingsRefresh = true
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
                    pendingSettingsRefresh = true
                }
            }
            ImGui.endCombo()
        }

        if (pendingSettingsRefresh) {
            script.onSettingsChanged()
            pendingSettingsRefresh = false
        }

        val curLogIdx = script.settings.logHandlingMode.coerceIn(0, logHandlingOptions.lastIndex)
        ImGui.text("On logs:")
        ImGui.sameLine(0f, 6f)
        val shownLog = logHandlingOptions.getOrElse(curLogIdx) { logHandlingOptions.first() }
        if (ImGui.beginCombo("##logsActionCombo", shownLog, 0)) {
            for (i in logHandlingOptions.indices) {
                val isSelected = (i == curLogIdx)
                if (ImGui.selectable(logHandlingOptions[i], isSelected, 0, 0f, 0f)) {
                    script.settings.logHandlingMode = i
                    script.onSettingsChanged()
                }
            }
            ImGui.endCombo()
        }

        val curLoc = script.location
        if (coordUiLocName != curLoc) {
            coordUiLocName = curLoc
            try {
                val sel = script.treeLocations.firstOrNull { it.name == curLoc }
                val o = script.settings.customLocations[curLoc]
                val effChop = if (o?.chopX != null && o.chopY != null && o.chopZ != null) {
                    net.botwithus.rs3.world.Coordinate(o.chopX!!, o.chopY!!, o.chopZ!!)
                } else sel?.chop
                val effBank = if (o?.bankX != null && o.bankY != null && o.bankZ != null) {
                    net.botwithus.rs3.world.Coordinate(o.bankX!!, o.bankY!!, o.bankZ!!)
                } else sel?.bank
                chopXYZText = effChop?.let { "${it.x()},${it.y()},${it.z()}" } ?: ""
                bankXYZText = effBank?.let { "${it.x()},${it.y()},${it.z()}" } ?: ""
            } catch (_: Throwable) { }
        }

        val locationLabel = curLoc.ifBlank { "Anywhere" }
        if (locationLabel.equals("Anywhere", ignoreCase = true)) {
            ImGui.separator()
            ImGui.text("Manual Coordinates")
            ImGui.spacing()

            ImGui.text("Override tiles for $locationLabel. Leave blank to use defaults.")
            ImGui.spacing()

            ImGui.text("Chop:")
            ImGui.sameLine(0f, 6f)
            chopXYZText = ImGui.inputText("##chop", chopXYZText, 0)
            ImGui.sameLine(0f, 6f)
            if (ImGui.button("Apply##chop", 70f, 0f)) {
                parseXYZ(chopXYZText)?.let { (x, y, z) ->
                    val map = script.settings.customLocations
                    val cur = map[curLoc] ?: com.uberith.uberchop.config.CustomLocation()
                    cur.chopX = x; cur.chopY = y; cur.chopZ = z
                    map[curLoc] = cur
                    script.onSettingsChanged()
                }
            }
            ImGui.sameLine(0f, 6f)
            if (ImGui.button("Use Player##chop", 110f, 0f)) {
                try {
                    val cls = Class.forName("net.botwithus.rs3.entities.LocalPlayer")
                    val me = cls.getMethod("self").invoke(null)
                    val coord = me?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("coordinate") }?.invoke(me)
                    val x = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("x","getx") }?.invoke(coord) as? Number
                    val y = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("y","gety") }?.invoke(coord) as? Number
                    val z = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && (it.name.lowercase().contains("plane") || it.name.lowercase() in setOf("z","getz")) }?.invoke(coord) as? Number
                    if (x != null && y != null && z != null) {
                        chopXYZText = "${x.toInt()},${y.toInt()},${z.toInt()}"
                        val map = script.settings.customLocations
                        val cur = map[curLoc] ?: com.uberith.uberchop.config.CustomLocation()
                        cur.chopX = x.toInt(); cur.chopY = y.toInt(); cur.chopZ = z.toInt()
                        map[curLoc] = cur
                        script.onSettingsChanged()
                    }
                } catch (_: Throwable) { }
            }
            run {
                val valid = chopXYZText.isBlank() || parseXYZ(chopXYZText) != null
                if (!valid) {
                    ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.94f, 0.36f, 0.36f, 1f)
                    ImGui.text("Format: X,Y,Z")
                    ImGui.popStyleColor(1)
                }
            }

            bankXYZText = ImGui.inputText("Bank", bankXYZText, 0)
            if (ImGui.button("Apply##bank", 70f, 0f)) {
                parseXYZ(bankXYZText)?.let { (x, y, z) ->
                    val map = script.settings.customLocations
                    val cur = map[curLoc] ?: com.uberith.uberchop.config.CustomLocation()
                    cur.bankX = x; cur.bankY = y; cur.bankZ = z
                    map[curLoc] = cur
                    script.onSettingsChanged()
                }
            }
            ImGui.sameLine(0f, 6f)
            if (ImGui.button("Use Player##bank", 110f, 0f)) {
                try {
                    val cls = Class.forName("net.botwithus.rs3.entities.LocalPlayer")
                    val me = cls.getMethod("self").invoke(null)
                    val coord = me?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("coordinate") }?.invoke(me)
                    val x = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("x","getx") }?.invoke(coord) as? Number
                    val y = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && it.name.lowercase() in setOf("y","gety") }?.invoke(coord) as? Number
                    val z = coord?.javaClass?.methods?.firstOrNull { it.parameterCount == 0 && (it.name.lowercase().contains("plane") || it.name.lowercase() in setOf("z","getz")) }?.invoke(coord) as? Number
                    if (x != null && y != null && z != null) {
                        bankXYZText = "${x.toInt()},${y.toInt()},${z.toInt()}"
                        val map = script.settings.customLocations
                        val cur = map[curLoc] ?: com.uberith.uberchop.config.CustomLocation()
                        cur.bankX = x.toInt(); cur.bankY = y.toInt(); cur.bankZ = z.toInt()
                        map[curLoc] = cur
                        script.onSettingsChanged()
                    }
                } catch (_: Throwable) { }
            }
            run {
                val valid = bankXYZText.isBlank() || parseXYZ(bankXYZText) != null
                if (!valid) {
                    ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.94f, 0.36f, 0.36f, 1f)
                    ImGui.text("Format: X,Y,Z")
                    ImGui.popStyleColor(1)
                }
            }
        }

        ImGui.spacing()
        drawQueueSection()
    }

    private fun drawOverviewRightColumn() {
        ImGui.text("Requirements")
        ImGui.separator()

        val treeType = resolveTreeType(script.targetTree)
        val reqLevel = treeType?.levelReq
        val userWc = script.WCLevel

        ImGui.text("Required Woodcutting Level:")
        ImGui.sameLine(0f, 6f)
        val requiredLevelText = reqLevel?.toString() ?: "\u2014"
        if (reqLevel != null && userWc != null) {
            val meetsRequirement = userWc >= reqLevel
            val color = if (meetsRequirement) {
                floatArrayOf(0.35f, 0.84f, 0.49f, 1f)
            } else {
                floatArrayOf(0.94f, 0.36f, 0.36f, 1f)
            }
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, color[0], color[1], color[2], color[3])
            ImGui.text(requiredLevelText)
            ImGui.popStyleColor(1)
        } else {
            ImGui.text(requiredLevelText)
        }

        ImGui.text("Woodcutting Level:")
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
            userWc >= (reqLevel ?: -1) -> {
                ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.35f, 0.84f, 0.49f, 1f)
                ImGui.text(userWc.toString())
                ImGui.popStyleColor(1)
            }
            else -> {
                ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.94f, 0.36f, 0.36f, 1f)
                ImGui.text(userWc.toString())
                ImGui.popStyleColor(1)
            }
        }

        ImGui.text("Tree Selected:")
        ImGui.sameLine(0f, 6f)
        ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.35f, 0.84f, 0.49f, 1f)
        ImGui.text(script.targetTree.ifBlank { "-" })
        ImGui.popStyleColor(1)

        ImGui.spacing()
        ImGui.separator()
        ImGui.text("Readiness Checks")
        ImGui.separator()

        val curLoc = script.location
        val effChop = try {
            val sel = script.treeLocations.firstOrNull { it.name == curLoc }
            val o = script.settings.customLocations[curLoc]
            if (o?.chopX != null && o.chopY != null && o.chopZ != null) net.botwithus.rs3.world.Coordinate(o.chopX!!, o.chopY!!, o.chopZ!!) else sel?.chop
        } catch (_: Throwable) { null }
        drawStatusLine("Configured chop tile?", effChop != null)

        val effBank = try {
            val sel = script.treeLocations.firstOrNull { it.name == curLoc }
            val o = script.settings.customLocations[curLoc]
            if (o?.bankX != null && o.bankY != null && o.bankZ != null) net.botwithus.rs3.world.Coordinate(o.bankX!!, o.bankY!!, o.bankZ!!) else sel?.bank
        } catch (_: Throwable) { null }
        drawStatusLine("Configured bank tile?", effBank != null)

    }

    private fun drawQueueSection() {
        ImGui.text("Queue Manager")
        ImGui.separator()

        var snapshot = script.queueSnapshot()
        val queueEnabled = snapshot.enabled
        val toggledQueueEnabled = ImGui.checkbox("Enable queue", queueEnabled)
        if (toggledQueueEnabled != queueEnabled) {
            script.setQueueEnabled(toggledQueueEnabled)
            snapshot = script.queueSnapshot()
        }

        if (!snapshot.enabled) {
            ImGui.separator()
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.75f, 0.75f, 0.75f, 1f)
            ImGui.text("Enable the queue to add jobs and view scheduled work.")
            ImGui.popStyleColor(1)
            return
        }

        ImGui.sameLine(0f, -1f)
        if (ImGui.button("Reset Progress", 140f, 0f)) {
            script.resetEntireQueueProgress()
            snapshot = script.queueSnapshot()
        }

        script.currentQueueProgress()?.let { progress ->
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.62f, 0.82f, 1f, 1f)
            val label = if (progress.goal > 0) {
                val completed = (progress.goal - progress.remaining).coerceAtLeast(0)
                "Active job ${progress.index + 1}/${progress.total}: ${progress.remaining} logs remaining (${completed}/${progress.goal})"
            } else {
                "Active job ${progress.index + 1}/${progress.total}: ${progress.remaining} logs remaining"
            }
            ImGui.text(label)
            ImGui.popStyleColor(1)
        }

        ImGui.separator()
        ImGui.text("Add Job (uses selections above)")

        val treeOptions = TreeTypes.ALL
        val savedTreeIndex = if (treeOptions.isEmpty()) -1 else script.settings.savedTreeType.coerceIn(0, treeOptions.lastIndex)
        val selectedTree = treeOptions.getOrElse(savedTreeIndex) { script.targetTree }.ifBlank { script.targetTree }
        val resolvedLocation = script.location.ifBlank {
            script.settings.savedLocation.ifBlank { script.treeLocations.firstOrNull()?.name ?: "" }
        }
        val displayedLocation = resolvedLocation.ifBlank { "Anywhere" }
        val logMode = script.settings.logHandlingMode.coerceIn(0, logHandlingOptions.lastIndex)
        val handlingLabel = logHandlingOptions[logMode]

        ImGui.text("Next job will use ${selectedTree.ifBlank { "—" }} @ ${displayedLocation.ifBlank { "—" }} ($handlingLabel)")

        val updatedGoal = ImGui.inputInt("Logs to chop", queueGoalInput, 100, 500, 0)
        if (updatedGoal != queueGoalInput) {
            queueGoalInput = updatedGoal.coerceAtLeast(1)
        }
        if (queueGoalInput < 1) {
            queueGoalInput = 1
        }

        val hasValidTree = savedTreeIndex >= 0 && selectedTree.isNotBlank()
        val canAddJob = hasValidTree && displayedLocation.isNotBlank() && queueGoalInput > 0
        if (!canAddJob) {
            ImGui.beginDisabled(true)
        }
        if (ImGui.button("Add to Queue", 138f, 0f)) {
            val entry = QueueEntry(
                treeDisplayName = selectedTree,
                treeTypeIndex = savedTreeIndex,
                location = displayedLocation,
                logHandlingMode = logMode,
                goal = queueGoalInput.coerceAtLeast(1),
                remaining = queueGoalInput.coerceAtLeast(1)
            )
            script.addQueueEntry(entry)
            snapshot = script.queueSnapshot()
        }
        if (!canAddJob) {
            ImGui.endDisabled()
        }
        if (!hasValidTree) {
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.94f, 0.36f, 0.36f, 1f)
            ImGui.text("Select a target tree above before adding to the queue.")
            ImGui.popStyleColor(1)
        }

        ImGui.separator()
        ImGui.text("Scheduled Jobs")
        if (snapshot.entries.isEmpty()) {
            ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.8f, 0.8f, 0.8f, 1f)
            ImGui.text("Queue is empty. Add a job above to get started.")
            ImGui.popStyleColor(1)
            return
        }
        val rowsVisible = snapshot.entries.size
        val rowHeight = 72f
        val baseHeight = (rowsVisible * rowHeight + 28f).coerceAtLeast(140f)
        val listHeight = baseHeight.coerceAtMost(260f)
        val childOpen = ImGui.beginChild("QueueList", 0f, listHeight, true, 0)
        if (childOpen) {
            snapshot.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    ImGui.separator()
                }
                val active = snapshot.enabled && index == snapshot.activeIndex
                val header = if (active) "â–¶ Job ${index + 1}" else "Job ${index + 1}"
                if (active) {
                    ImGui.pushStyleColor(ColorManager.ColorType.Text.index, 0.62f, 0.82f, 1f, 1f)
                }
                ImGui.text(header)
                if (active) {
                    ImGui.popStyleColor(1)
                }
                ImGui.text("Tree: ${entry.treeName}")
                ImGui.text("Location: ${entry.location}")
                val modeText = logHandlingOptions.getOrElse(entry.logHandlingMode) { logHandlingOptions.first() }
                ImGui.text("On logs: $modeText")

                val remaining = entry.remaining.coerceAtLeast(0)
                val goal = entry.goal.coerceAtLeast(0)
                if (goal > 0) {
                    val completed = (goal - remaining).coerceAtLeast(0)
                    ImGui.text("$remaining left (${completed}/${goal})")
                } else {
                    ImGui.text("$remaining logs remaining")
                }

                if (ImGui.button("Start##queue_start_$index", 60f, 0f)) {
                    script.startQueueAt(index, resetRemaining = false)
                    snapshot = script.queueSnapshot()
                }
                ImGui.sameLine(0f, -1f)
                if (ImGui.button("Reset##queue_reset_$index", 60f, 0f)) {
                    script.resetQueueEntry(index)
                    snapshot = script.queueSnapshot()
                }
                ImGui.sameLine(0f, -1f)
                if (ImGui.button("Remove##queue_remove_$index", 70f, 0f)) {
                    script.removeQueueEntry(index)
                    snapshot = script.queueSnapshot()
                    return@forEachIndexed
                }
                if (index > 0) {
                    ImGui.sameLine(0f, -1f)
                    if (ImGui.smallButton("â–²##queue_up_$index")) {
                        script.moveQueueEntryUp(index)
                        snapshot = script.queueSnapshot()
                    }
                }
                if (index < snapshot.entries.size - 1) {
                    ImGui.sameLine(0f, -1f)
                    if (ImGui.smallButton("â–¼##queue_down_$index")) {
                        script.moveQueueEntryDown(index)
                        snapshot = script.queueSnapshot()
                    }
                }
            }
        }
        ImGui.endChild()
    }

    private fun drawCore() {
        var changed = false
        run {
            val old = script.settings.pickupNests
            val v = ImGui.checkbox("Pickup Nests", old)
            if (v != old) { script.settings.pickupNests = v; changed = true }
        }
        run {
            val old = script.settings.enableTreeRotation
            val v = ImGui.checkbox("Enable Tree Rotation", old)
            if (v != old) { script.settings.enableTreeRotation = v; changed = true }
        }
        run {
            val old = script.settings.enableWorldHopping
            val v = ImGui.checkbox("Enable World Hopping", old)
            if (v != old) { script.settings.enableWorldHopping = v; changed = true }
        }
        run {
            val old = script.settings.useMagicNotepaper
            val v = ImGui.checkbox("Use Magic Notepaper", old)
            if (v != old) { script.settings.useMagicNotepaper = v; changed = true }
        }
        run {
            val old = script.settings.useCrystallise
            val v = ImGui.checkbox("Use Crystallise", old)
            if (v != old) { script.settings.useCrystallise = v; changed = true }
        }
        run {
            val old = script.settings.useJujuPotions
            val v = ImGui.checkbox("Use Juju Potions", old)
            if (v != old) { script.settings.useJujuPotions = v; changed = true }
        }
        run {
            val old = script.settings.withdrawWoodBox
            val v = ImGui.checkbox("Use Wood Box", old)
            if (v != old) { script.settings.withdrawWoodBox = v; changed = true }
        }
        if (changed) script.onSettingsChanged()
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

    private fun resolveTreeType(name: String): TreeType? {
        val values = TreeType.entries.toTypedArray()
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

    // Parse "X,Y,Z" into a triple or null if invalid
    private fun parseXYZ(text: String): Triple<Int, Int, Int>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val parts = t.split(',')
        if (parts.size != 3) return null
        return try {
            val x = parts[0].trim().toInt()
            val y = parts[1].trim().toInt()
            val z = parts[2].trim().toInt()
            Triple(x, y, z)
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawHandlers() {
        var changed = false
        header("Break Handler", ColorManager())
        run {
            val old = script.settings.performRandomBreak
            val v = ImGui.checkbox("Random Breaks", old)
            if (v != old) { script.settings.performRandomBreak = v; changed = true }
        }
        run {
            val old = script.settings.breakFrequency
            val v = adjustInt("Breaks/hr", old, 0, 12)
            if (v != old) { script.settings.breakFrequency = v; changed = true }
        }
        run {
            val old = script.settings.minBreak
            val v = adjustInt("Min Break (s)", old, 0, 600, 5)
            if (v != old) { script.settings.minBreak = v; changed = true }
        }
        run {
            val old = script.settings.maxBreak
            val v = adjustInt("Max Break (s)", old, 0, 3600, 5)
            if (v != old) { script.settings.maxBreak = v; changed = true }
        }

        ImGui.separator()
        header("Logout Handler", ColorManager())
        run {
            val old = script.settings.logoutDurationEnable
            val v = ImGui.checkbox("Enable Timed Logout", old)
            if (v != old) { script.settings.logoutDurationEnable = v; changed = true }
        }
        run {
            val old = script.settings.logoutHours
            val v = adjustInt("After (h)", old, 0, 24)
            if (v != old) { script.settings.logoutHours = v; changed = true }
        }
        run {
            val old = script.settings.logoutMinutes
            val v = adjustInt("After (m)", old, 0, 59)
            if (v != old) { script.settings.logoutMinutes = v; changed = true }
        }
        run {
            val old = script.settings.logoutSeconds
            val v = adjustInt("After (s)", old, 0, 59)
            if (v != old) { script.settings.logoutSeconds = v; changed = true }
        }

        ImGui.separator()
        header("AFK Handler", ColorManager())
        run {
            val old = script.settings.enableAfk
            val v = ImGui.checkbox("Enable AFK", old)
            if (v != old) { script.settings.enableAfk = v; changed = true }
        }
        run {
            val old = script.settings.afkEveryMin
            val v = adjustInt("Every min (m)", old, 0, 180)
            if (v != old) { script.settings.afkEveryMin = v; changed = true }
        }
        run {
            val old = script.settings.afkEveryMax
            val v = adjustInt("Every max (m)", old, 0, 240)
            if (v != old) { script.settings.afkEveryMax = v; changed = true }
        }
        run {
            val old = script.settings.afkDurationMin
            val v = adjustInt("Duration min (s)", old, 0, 600, 5)
            if (v != old) { script.settings.afkDurationMin = v; changed = true }
        }
        run {
            val old = script.settings.afkDurationMax
            val v = adjustInt("Duration max (s)", old, 0, 3600, 5)
            if (v != old) { script.settings.afkDurationMax = v; changed = true }
        }

        ImGui.separator()
        header("Auto-Stop", ColorManager())
        run {
            val old = script.settings.enableAutoStop
            val v = ImGui.checkbox("Enable Auto-Stop", old)
            if (v != old) { script.settings.enableAutoStop = v; changed = true }
        }
        run {
            val old = script.settings.stopAfterHours
            val v = adjustInt("After (h)", old, 0, 48)
            if (v != old) { script.settings.stopAfterHours = v; changed = true }
        }
        run {
            val old = script.settings.stopAfterMinutes
            val v = adjustInt("After (m)", old, 0, 59)
            if (v != old) { script.settings.stopAfterMinutes = v; changed = true }
        }
        run {
            val old = script.settings.stopAfterXp
            val v = adjustInt("After XP", old, 0, 50_000_000, 1000)
            if (v != old) { script.settings.stopAfterXp = v; changed = true }
        }
        run {
            val old = script.settings.stopAfterLogs
            val v = adjustInt("After Logs", old, 0, 1_000_000, 10)
            if (v != old) { script.settings.stopAfterLogs = v; changed = true }
        }

        if (changed) script.onSettingsChanged()
    }

    private fun drawWorldHop() {
        header("World Hop Filters", ColorManager())
        var changed = false
        run {
            val old = script.settings.minPing
            val v = adjustInt("Min Ping", old, 0, 1000, 5)
            if (v != old) { script.settings.minPing = v; changed = true }
        }
        run {
            val old = script.settings.maxPing
            val v = adjustInt("Max Ping", old, 0, 1000, 5)
            if (v != old) { script.settings.maxPing = v; changed = true }
        }
        run {
            val old = script.settings.minPopulation
            val v = adjustInt("Min Pop", old, 0, 4000, 10)
            if (v != old) { script.settings.minPopulation = v; changed = true }
        }
        run {
            val old = script.settings.maxPopulation
            val v = adjustInt("Max Pop", old, 0, 4000, 10)
            if (v != old) { script.settings.maxPopulation = v; changed = true }
        }
        run {
            val old = script.settings.hopDelayMs
            val v = adjustInt("Hop Delay (ms)", old, 0, 60_000, 100)
            if (v != old) { script.settings.hopDelayMs = v; changed = true }
        }
        run {
            val old = script.settings.memberOnlyWorlds
            val v = ImGui.checkbox("Members-Only Worlds", old)
            if (v != old) { script.settings.memberOnlyWorlds = v; changed = true }
        }
        run {
            val old = script.settings.onlyFreeToPlay
            val v = ImGui.checkbox("Only F2P", old)
            if (v != old) { script.settings.onlyFreeToPlay = v; changed = true }
        }
        run {
            val old = script.settings.hopOnChat
            val v = ImGui.checkbox("Hop On Chat Activity", old)
            if (v != old) { script.settings.hopOnChat = v; changed = true }
        }
        run {
            val old = script.settings.hopOnCrowd
            val v = ImGui.checkbox("Hop On Crowd Threshold", old)
            if (v != old) { script.settings.hopOnCrowd = v; changed = true }
        }
        run {
            val old = script.settings.playerThreshold
            val v = adjustInt("Player Threshold", old, 0, 200, 1)
            if (v != old) { script.settings.playerThreshold = v; changed = true }
        }
        run {
            val old = script.settings.hopOnNoTrees
            val v = ImGui.checkbox("Hop On No Trees", old)
            if (v != old) { script.settings.hopOnNoTrees = v; changed = true }
        }
        if (changed) script.onSettingsChanged()
    }

    private fun drawAdvanced() {
        ImGui.text("Saved Preferences")
        run {
            val old = script.settings.savedTreeType
            val v = adjustInt("Saved Tree Type", old, 0, 50, 1)
            if (v != old) { script.settings.savedTreeType = v; script.onSettingsChanged() }
        }
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
            if (c != null) "${c.x}, ${c.y}, ${c.z}" else "-"
        } catch (_: Throwable) { "\u2014" }
        val effBank = try {
            val sel = script.treeLocations.firstOrNull { it.name == curLoc }
            val o = script.settings.customLocations[curLoc]
            val b = if (o?.bankX != null && o.bankY != null && o.bankZ != null) net.botwithus.rs3.world.Coordinate(o.bankX!!, o.bankY!!, o.bankZ!!) else sel?.bank
            if (b != null) "${b.x}, ${b.y}, ${b.z}" else "-"
        } catch (_: Throwable) { "\u2014" }
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
                    val cur = map[curLoc] ?: com.uberith.uberchop.config.CustomLocation()
                    cur.chopX = x.toInt(); cur.chopY = y.toInt(); cur.chopZ = z.toInt()
                    map[curLoc] = cur
                    script.onSettingsChanged()
                }
            } catch (_: Throwable) {}
        }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear Chop", 90f, 0f)) {
            val map = script.settings.customLocations
            val cur = map[curLoc]
            if (cur != null) { cur.chopX = null; cur.chopY = null; cur.chopZ = null; if (cur.bankX==null && cur.bankY==null && cur.bankZ==null) map.remove(curLoc) else map[curLoc]=cur }
            script.onSettingsChanged()
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
                    val cur = map[curLoc] ?: com.uberith.uberchop.config.CustomLocation()
                    cur.bankX = x.toInt(); cur.bankY = y.toInt(); cur.bankZ = z.toInt()
                    map[curLoc] = cur
                    script.onSettingsChanged()
                }
            } catch (_: Throwable) {}
        }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear Bank", 90f, 0f)) {
            val map = script.settings.customLocations
            val cur = map[curLoc]
            if (cur != null) { cur.bankX = null; cur.bankY = null; cur.bankZ = null; if (cur.chopX==null && cur.chopY==null && cur.chopZ==null) map.remove(curLoc) else map[curLoc]=cur }
            script.onSettingsChanged()
        }
        ImGui.separator()
        ImGui.text("Deposit Filters")
        ImGui.text("Include: ${script.settings.depositInclude.size}  |  Keep: ${script.settings.depositKeep.size}")
        if (ImGui.button("Add Blossom", 110f, 0f)) { if (!script.settings.depositInclude.contains("Crystal tree blossom")) { script.settings.depositInclude.add("Crystal tree blossom"); script.onSettingsChanged() } }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Add Bamboo", 110f, 0f)) { if (!script.settings.depositInclude.contains("Bamboo")) { script.settings.depositInclude.add("Bamboo"); script.onSettingsChanged() } }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Keep Nests", 110f, 0f)) { if (!script.settings.depositKeep.contains("Bird's nest")) { script.settings.depositKeep.add("Bird's nest"); script.onSettingsChanged() } }
        if (ImGui.button("Clear Include", 110f, 0f)) { script.settings.depositInclude.clear(); script.onSettingsChanged() }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Clear Keep", 110f, 0f)) { script.settings.depositKeep.clear(); script.onSettingsChanged() }
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Add From Clipboard", 160f, 0f)) {
            try {
                val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val data = cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                val text = data?.trim()
                if (!text.isNullOrEmpty()) {
                    if (!script.settings.depositInclude.contains(text)) { script.settings.depositInclude.add(text); script.onSettingsChanged() }
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
                if (ImGui.button("x##inc_$idx", 20f, 0f)) { iter.remove(); script.onSettingsChanged() }
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
                if (ImGui.button("x##keep_$k", 20f, 0f)) { iterK.remove(); script.onSettingsChanged() }
                k++
            }
            if (ImGui.button("Add Keep From Clipboard", 200f, 0f)) {
                try {
                    val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val data = cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    val text = data?.trim()
                    if (!text.isNullOrEmpty() && !script.settings.depositKeep.contains(text)) { script.settings.depositKeep.add(text); script.onSettingsChanged() }
                } catch (_: Throwable) { }
            }
        }
        ImGui.endChild()
        ImGui.separator()
        ImGui.text("Auto-Skill")
        run {
            val old = script.settings.autoProgressTree
            val v = ImGui.checkbox("Auto-Progress Tree", old)
            if (v != old) { script.settings.autoProgressTree = v; script.onSettingsChanged() }
        }
        run {
            val old = script.settings.autoUpgradeTree
            val v = ImGui.checkbox("Auto-Upgrade Tree", old)
            if (v != old) { script.settings.autoUpgradeTree = v; script.onSettingsChanged() }
        }
        run {
            val old = script.settings.tanningProductIndex
            val v = adjustInt("Preset/Prod Index", old, 0, 20)
            if (v != old) { script.settings.tanningProductIndex = v; script.onSettingsChanged() }
        }
    }

    private fun drawStatistics() {
        ImGui.text("Statistics")
        ImGui.separator()
        ImGui.text("Runtime: ${script.formattedRuntime()}")
        ImGui.text("Logs chopped: ${formatNumber(script.logsChopped)} (${formatNumber(script.logsPerHour())} /h)")
        ImGui.text("XP gained: ${formatNumber(script.woodcuttingXpGained())} (${formatNumber(script.woodcuttingXpPerHour())} /h)")
        ImGui.text("Levels gained: ${formatNumber(script.woodcuttingLevelsGained())}")
        if (script.settings.pickupNests) {
            ImGui.text("Bird nests collected: ${formatNumber(script.birdNestsCollected)} (${formatNumber(script.birdNestsPerHour())} /h)")
        }
        ImGui.separator()
        ImGui.text("Target: ${script.targetTree}")
        ImGui.text("Log handling: " + when (script.settings.logHandlingMode.coerceIn(0, 2)) {
            1 -> "Burn Logs"
            2 -> "Fletch Logs"
            else -> "Bank Logs"
        })
        ImGui.separator()
        ImGui.text("Overall Logs: ${formatNumber(script.lifetimeLogsChopped())} (${formatNumber(script.lifetimeLogsPerHour())} /h)")
        ImGui.text("Overall Bird nests: ${formatNumber(script.lifetimeBirdNestsCollected())} (${formatNumber(script.lifetimeBirdNestsPerHour())} /h)")
        ImGui.text("Overall XP: ${formatNumber(script.lifetimeWoodcuttingXpGained())} (${formatNumber(script.lifetimeWoodcuttingXpPerHour())} /h)")
        ImGui.text("Overall Levels: ${formatNumber(script.lifetimeWoodcuttingLevelsGained())}")
        ImGui.text("Overall Runtime: ${formatDuration(script.lifetimeRuntimeMillis())}")
    }

    private fun drawDebug() {
        ImGui.text("Debug")
        ImGui.separator()
        ImGui.text("Status: ${script.currentStatus}")
        ImGui.text("Runtime: ${script.formattedRuntime()}")
        ImGui.text("Logs chopped: ${formatNumber(script.logsChopped)} (${formatNumber(script.logsPerHour())} /h)")
        ImGui.text("XP gained: ${formatNumber(script.woodcuttingXpGained())} (${formatNumber(script.woodcuttingXpPerHour())} /h)")

        val player = runCatching { LocalPlayer.self() }.getOrNull()
        if (player != null) {
            ImGui.text("Player position: ${player.coordinate}")
            ImGui.text("Animation: ${player.animationId}")
        } else {
            ImGui.text("Player position: unavailable")
        }

        val queueSnapshot = script.queueSnapshot()
        ImGui.separator()
        ImGui.text("Queue enabled: ${if (queueSnapshot.enabled) "Yes" else "No"}")
        ImGui.text("Active index: ${queueSnapshot.activeIndex}")
        if (ImGui.beginChild("DebugQueue", 0f, 150f, true, 0)) {
            if (queueSnapshot.entries.isEmpty()) {
                ImGui.text("Queue is empty")
            } else {
                queueSnapshot.entries.forEachIndexed { index, entry ->
                    val status = when {
                        queueSnapshot.enabled && index == queueSnapshot.activeIndex -> "Active"
                        else -> "Idle"
                    }
                    ImGui.text("#${index + 1} ${entry.treeName} @ ${entry.location} (${entry.remaining}/${entry.goal}) [$status]")
                }
            }
        }
        ImGui.endChild()

        ImGui.separator()
        ImGui.text("Break handler: ${if (script.settings.performRandomBreak) "Enabled" else "Disabled"}")
        ImGui.text("Auto-stop: ${if (script.settings.enableAutoStop) "Enabled" else "Disabled"}")
        ImGui.text("World hop: ${if (script.settings.enableWorldHopping) "Enabled" else "Disabled"}")
        ImGui.text("AFK handler: ${if (script.settings.enableAfk) "Enabled" else "Disabled"}")
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

}














