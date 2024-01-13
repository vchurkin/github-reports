package com.vchurkin.github.reports

import java.util.*

class EnvProperties {
    private val envFile: Properties = loadEnvFile()

    fun get(key: String): Any? {
        return System.getenv()[key] ?: envFile[key]
    }

    private fun loadEnvFile(): Properties {
        val properties = Properties()
        try {
            properties.load(EnvProperties::class.java.classLoader.getResourceAsStream("env.properties"))
        } catch (e: Exception) {
            println("Error reading properties file: ${e.message}")
        }
        return properties
    }
}