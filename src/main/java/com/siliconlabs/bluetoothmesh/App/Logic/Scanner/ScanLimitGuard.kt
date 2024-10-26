/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.Scanner

import org.tinylog.Logger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ScanLimitGuard {
    private val lastScans = mutableListOf<Duration>()

    fun storeScanningTimestamp(): Result<Unit> {
        val currentTime = System.currentTimeMillis().milliseconds

        return if (canStartImmediately(currentTime)) {
            lastScans.add(currentTime)
            logStoring(currentTime)
            Result.success(Unit)
        } else {
            Result.failure(ScansLimitReached(getRemainingTimeInWindow(currentTime)))
        }
    }

    private fun logStoring(currentTime: Duration) {
        val log = Instant.ofEpochMilli(currentTime.inWholeMilliseconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(timestampFormatter)

        Logger.debug { "store timestamp $log" }
    }

    private val timestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun canStartImmediately(currentTime: Duration): Boolean {
        cleanOldestScans(currentTime)
        return lastScans.size < maximumAllowedScanCount
    }

    fun getDelayDuration(): Duration {
        val currentTime = System.currentTimeMillis().milliseconds
        cleanOldestScans(currentTime)

        return when (lastScans.size) {
            0 -> 0.milliseconds
            maximumAllowedScanCount -> {
                getRemainingTimeInWindow(currentTime) + constantDelay
            }
            else -> {
                val latestScanTime = lastScans.last()
                val timeSinceLatestScanning = currentTime - latestScanTime
                val remainingTimeInSafeInterval = safeInterval - timeSinceLatestScanning

                remainingTimeInSafeInterval.coerceAtLeast(0.milliseconds) + constantDelay
            }
        }
    }

    private fun cleanOldestScans(currentTime: Duration) {
        val timeLimit = currentTime - timeWindow
        lastScans.retainAll { it > timeLimit }
    }

    private fun getRemainingTimeInWindow(currentTime: Duration): Duration {
        val oldestScanTime = lastScans.first()
        val timeSinceOldestScanning = currentTime - oldestScanTime

        return timeWindow - timeSinceOldestScanning
    }

    data class ScansLimitReached(val remainingTime: Duration) :
        Throwable("Can't start scanning immediately; next scan possible after $remainingTime")

    companion object {
        private const val maximumAllowedScanCount = 5
        private val timeWindow = 30.seconds
        private val safeInterval = timeWindow / maximumAllowedScanCount

        private val constantDelay = 10.milliseconds
    }
}