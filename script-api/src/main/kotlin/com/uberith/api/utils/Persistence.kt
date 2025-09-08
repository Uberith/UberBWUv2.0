package com.uberith.api.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.Type

class Persistence<T>(private val fileName: String, private val type: Type) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun file(): File {
        val dir = File(System.getProperty("user.home"), ".BotWithUs/uberith")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    fun saveData(data: T) {
        val f = file()
        try {
            FileWriter(f).use { gson.toJson(data, it) }
        } catch (_: IOException) { }
    }

    fun loadData(): T? {
        val f = file()
        if (!f.exists()) return null
        return try {
            FileReader(f).use { gson.fromJson<T>(it, type) }
        } catch (_: IOException) { null }
    }

    companion object {
        inline fun <reified T> typeToken(): Type = object : TypeToken<T>(){}.type
    }
}

