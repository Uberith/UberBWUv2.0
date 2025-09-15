package com.uberith.uberchop

import com.example.config.ConfigService
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Properties-based persistence for UberChop Settings with profile support.
 * Uses the shared ConfigService to resolve workspace/profile file locations and
 * writes atomically where supported.
 */
class SettingsStore(private val configService: ConfigService) {
    private val log = LoggerFactory.getLogger(SettingsStore::class.java)

    /** Loads settings for profile, overlaying on [base] defaults; never throws. */
    fun load(profile: String, base: Settings = Settings()): Settings {
        val file = configService.configFile(profile)
        val props = Properties()
        if (Files.exists(file)) {
            try {
                Files.newInputStream(file).use { input ->
                    BufferedInputStream(input).use { props.load(it) }
                }
            } catch (e: Exception) {
                log.warn("Failed to read settings from {}: {}", file, e.message)
            }
        }
        return fromProperties(props, base)
    }

    /** Saves settings for profile atomically; logs on error, never throws. */
    fun save(profile: String, s: Settings) {
        val file = configService.configFile(profile)
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        val props = toProperties(s)
        try {
            Files.createDirectories(file.parent)
            Files.newOutputStream(tmp).use { out ->
                BufferedOutputStream(out).use { props.store(it, "UberChop settings") }
            }
        } catch (e: Exception) {
            log.warn("Failed to write temp settings {}: {}", tmp, e.message)
            return
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            log.info("Saved settings to {} (atomic)", file)
        } catch (e: Exception) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
                log.info("Saved settings to {} (replace)", file)
            } catch (e2: Exception) {
                log.warn("Failed to replace settings {}: {}", file, e2.message)
                try { Files.deleteIfExists(tmp) } catch (_: Exception) { }
            }
        }
    }

    private fun toProperties(s: Settings): Properties {
        val p = Properties()
        // Booleans and ints
        p.setProperty("perform_random_break", s.performRandomBreak.toString())
        p.setProperty("break_frequency", s.breakFrequency.toString())
        p.setProperty("min_break", s.minBreak.toString())
        p.setProperty("max_break", s.maxBreak.toString())

        p.setProperty("logout_enable", s.logoutDurationEnable.toString())
        p.setProperty("logout_hours", s.logoutHours.toString())
        p.setProperty("logout_minutes", s.logoutMinutes.toString())
        p.setProperty("logout_seconds", s.logoutSeconds.toString())

        p.setProperty("afk_enable", s.enableAfk.toString())
        p.setProperty("afk_every_min", s.afkEveryMin.toString())
        p.setProperty("afk_every_max", s.afkEveryMax.toString())
        p.setProperty("afk_duration_min", s.afkDurationMin.toString())
        p.setProperty("afk_duration_max", s.afkDurationMax.toString())

        p.setProperty("auto_stop_enable", s.enableAutoStop.toString())
        p.setProperty("stop_after_hours", s.stopAfterHours.toString())
        p.setProperty("stop_after_minutes", s.stopAfterMinutes.toString())
        p.setProperty("stop_after_xp", s.stopAfterXp.toString())
        p.setProperty("stop_after_logs", s.stopAfterLogs.toString())

        p.setProperty("pickup_nests", s.pickupNests.toString())
        p.setProperty("tree_rotation", s.enableTreeRotation.toString())

        p.setProperty("world_hopping", s.enableWorldHopping.toString())
        p.setProperty("use_notepaper", s.useMagicNotepaper.toString())
        p.setProperty("use_crystallise", s.useCrystallise.toString())
        p.setProperty("use_juju", s.useJujuPotions.toString())

        p.setProperty("auto_progress_tree", s.autoProgressTree.toString())
        p.setProperty("auto_upgrade_tree", s.autoUpgradeTree.toString())
        p.setProperty("tanning_product", s.tanningProductIndex.toString())

        p.setProperty("min_ping", s.minPing.toString())
        p.setProperty("max_ping", s.maxPing.toString())
        p.setProperty("min_population", s.minPopulation.toString())
        p.setProperty("max_population", s.maxPopulation.toString())
        p.setProperty("hop_delay_ms", s.hopDelayMs.toString())
        p.setProperty("members_only", s.memberOnlyWorlds.toString())
        p.setProperty("f2p_only", s.onlyFreeToPlay.toString())
        p.setProperty("hop_on_chat", s.hopOnChat.toString())
        p.setProperty("hop_on_crowd", s.hopOnCrowd.toString())
        p.setProperty("player_threshold", s.playerThreshold.toString())
        p.setProperty("hop_on_no_trees", s.hopOnNoTrees.toString())

        p.setProperty("saved_tree_type", s.savedTreeType.toString())
        p.setProperty("saved_location", s.savedLocation)
        p.setProperty("log_handling_mode", s.logHandlingMode.toString())
        p.setProperty("withdraw_wood_box", s.withdrawWoodBox.toString())

        // Lists
        p.setProperty("deposit_include", s.depositInclude.joinToString(",") { escape(it) })
        p.setProperty("deposit_keep", s.depositKeep.joinToString(",") { escape(it) })

        // Custom locations as custom.<name>.<field>
        s.customLocations.forEach { (name, c) ->
            c.chopX?.let { p.setProperty("custom.$name.chopX", it.toString()) }
            c.chopY?.let { p.setProperty("custom.$name.chopY", it.toString()) }
            c.chopZ?.let { p.setProperty("custom.$name.chopZ", it.toString()) }
            c.bankX?.let { p.setProperty("custom.$name.bankX", it.toString()) }
            c.bankY?.let { p.setProperty("custom.$name.bankY", it.toString()) }
            c.bankZ?.let { p.setProperty("custom.$name.bankZ", it.toString()) }
        }

        p.setProperty("config_version", "1")
        return p
    }

    private fun fromProperties(p: Properties, base: Settings): Settings {
        fun b(key: String, def: Boolean) = p.getProperty(key)?.toBooleanStrictOrNull() ?: def
        fun i(key: String, def: Int) = p.getProperty(key)?.toIntOrNull() ?: def
        fun s(key: String, def: String) = p.getProperty(key) ?: def

        val out = base.copy()
        out.performRandomBreak = b("perform_random_break", out.performRandomBreak)
        out.breakFrequency = i("break_frequency", out.breakFrequency)
        out.minBreak = i("min_break", out.minBreak)
        out.maxBreak = i("max_break", out.maxBreak)

        out.logoutDurationEnable = b("logout_enable", out.logoutDurationEnable)
        out.logoutHours = i("logout_hours", out.logoutHours)
        out.logoutMinutes = i("logout_minutes", out.logoutMinutes)
        out.logoutSeconds = i("logout_seconds", out.logoutSeconds)

        out.enableAfk = b("afk_enable", out.enableAfk)
        out.afkEveryMin = i("afk_every_min", out.afkEveryMin)
        out.afkEveryMax = i("afk_every_max", out.afkEveryMax)
        out.afkDurationMin = i("afk_duration_min", out.afkDurationMin)
        out.afkDurationMax = i("afk_duration_max", out.afkDurationMax)

        out.enableAutoStop = b("auto_stop_enable", out.enableAutoStop)
        out.stopAfterHours = i("stop_after_hours", out.stopAfterHours)
        out.stopAfterMinutes = i("stop_after_minutes", out.stopAfterMinutes)
        out.stopAfterXp = i("stop_after_xp", out.stopAfterXp)
        out.stopAfterLogs = i("stop_after_logs", out.stopAfterLogs)

        out.pickupNests = b("pickup_nests", out.pickupNests)
        out.enableTreeRotation = b("tree_rotation", out.enableTreeRotation)

        out.enableWorldHopping = b("world_hopping", out.enableWorldHopping)
        out.useMagicNotepaper = b("use_notepaper", out.useMagicNotepaper)
        out.useCrystallise = b("use_crystallise", out.useCrystallise)
        out.useJujuPotions = b("use_juju", out.useJujuPotions)

        out.autoProgressTree = b("auto_progress_tree", out.autoProgressTree)
        out.autoUpgradeTree = b("auto_upgrade_tree", out.autoUpgradeTree)
        out.tanningProductIndex = i("tanning_product", out.tanningProductIndex)

        out.minPing = i("min_ping", out.minPing)
        out.maxPing = i("max_ping", out.maxPing)
        out.minPopulation = i("min_population", out.minPopulation)
        out.maxPopulation = i("max_population", out.maxPopulation)
        out.hopDelayMs = i("hop_delay_ms", out.hopDelayMs)
        out.memberOnlyWorlds = b("members_only", out.memberOnlyWorlds)
        out.onlyFreeToPlay = b("f2p_only", out.onlyFreeToPlay)
        out.hopOnChat = b("hop_on_chat", out.hopOnChat)
        out.hopOnCrowd = b("hop_on_crowd", out.hopOnCrowd)
        out.playerThreshold = i("player_threshold", out.playerThreshold)
        out.hopOnNoTrees = b("hop_on_no_trees", out.hopOnNoTrees)

        out.savedTreeType = i("saved_tree_type", out.savedTreeType).coerceIn(0, TreeTypes.ALL.size - 1)
        out.savedLocation = s("saved_location", out.savedLocation)
        out.logHandlingMode = i("log_handling_mode", out.logHandlingMode)
        out.withdrawWoodBox = b("withdraw_wood_box", out.withdrawWoodBox)

        // Lists
        p.getProperty("deposit_include")?.let { out.depositInclude = splitEscaped(it).toMutableList() }
        p.getProperty("deposit_keep")?.let { out.depositKeep = splitEscaped(it).toMutableList() }

        // Custom locations
        val customs = mutableMapOf<String, CustomLocation>()
        p.stringPropertyNames().filter { it.startsWith("custom.") }.forEach { key ->
            // custom.<name>.<field>
            val parts = key.split('.')
            if (parts.size == 3) {
                val name = parts[1]
                val field = parts[2]
                val cl = customs.getOrPut(name) { CustomLocation() }
                val v = p.getProperty(key)
                when (field) {
                    "chopX" -> cl.chopX = v.toIntOrNull()
                    "chopY" -> cl.chopY = v.toIntOrNull()
                    "chopZ" -> cl.chopZ = v.toIntOrNull()
                    "bankX" -> cl.bankX = v.toIntOrNull()
                    "bankY" -> cl.bankY = v.toIntOrNull()
                    "bankZ" -> cl.bankZ = v.toIntOrNull()
                }
            }
        }
        if (customs.isNotEmpty()) {
            out.customLocations.clear(); out.customLocations.putAll(customs)
        }

        // Clamp ranges
        if (out.minBreak > out.maxBreak) { val t = out.minBreak; out.minBreak = out.maxBreak; out.maxBreak = t }
        if (out.afkEveryMin > out.afkEveryMax) { val t = out.afkEveryMin; out.afkEveryMin = out.afkEveryMax; out.afkEveryMax = t }
        if (out.afkDurationMin > out.afkDurationMax) { val t = out.afkDurationMin; out.afkDurationMin = out.afkDurationMax; out.afkDurationMax = t }

        return out
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace(",", "\\,")
    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                sb.append(s[i + 1])
                i += 2
            } else {
                sb.append(ch); i++
            }
        }
        return sb.toString()
    }
    private fun splitEscaped(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == ',') {
                out.add(unescape(cur.toString()))
                cur.setLength(0)
                i++
            } else if (ch == '\\') {
                if (i + 1 < s.length) { cur.append(s[i + 1]); i += 2 } else { i++ }
            } else { cur.append(ch); i++ }
        }
        if (cur.isNotEmpty()) out.add(unescape(cur.toString()))
        return out
    }
}
