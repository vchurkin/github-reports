package com.vchurkin.github.reports.utils

import java.time.Instant
import java.time.ZoneId

fun Instant.toLocalDate() = this.atZone(ZoneId.systemDefault()).toLocalDate()