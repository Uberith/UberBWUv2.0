package com.uberith.api.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class Statistics(scriptName: String) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file: File
    private var map: MutableMap<String, Number> = mutableMapOf()

    init {
        val dir = File(System.getProperty("user.home"), ".BotWithUs/uberith/stats")
        if (!dir.exists()) dir.mkdirs()
        file = File(dir, "$scriptName.json")
        load()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            FileReader(file).use {
                val type = object : TypeToken<Map<String, Number>>(){}.type
                val loaded: Map<String, Number>? = gson.fromJson(it, type)
                if (loaded != null) map.putAll(loaded)
            }
        } catch (_: IOException) { }
    }

    private fun save() {
        try { FileWriter(file).use { gson.toJson(map, it) } } catch (_: IOException) { }
    }

    fun saveStatistic(key: String, value: Number) { map[key] = value; save() }
    fun getStatistic(key: String): Number? = map[key]
}

