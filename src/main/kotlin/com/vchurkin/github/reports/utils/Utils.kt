package com.vchurkin.github.reports.utils

import java.io.BufferedWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun Instant.toLocalDate(): LocalDate = this.atZone(ZoneId.systemDefault()).toLocalDate()

const val CSV_DELIMITER = ","

const val OUTPUT_DIR_DEFAULT = "./build"

fun BufferedWriter.writeCsvLine(vararg values: Any?) {
    values.forEachIndexed { index, value ->
        if (index > 0) this.write(CSV_DELIMITER)
        when (value) {
            is String -> this.write("\"$value\"")
            null -> this.write("")
            else -> this.write("$value")
        }
    }
    this.write("\n")
    this.flush()
}