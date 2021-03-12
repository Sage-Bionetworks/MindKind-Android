package org.sagebionetworks.research.mindkind.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.temporal.ChronoUnit

/**
 * Helper class to limit the rate of logging events
 */
open class RateLimiter(val limitTime: Long) {
    private var lastEventTime: LocalDateTime? = null

    open fun getLastEventTime(): LocalDateTime? {
        return lastEventTime
    }

    open fun setLastEventTime(time: LocalDateTime?) {
        lastEventTime = time
    }

    /**
     * @param eventTime time of event to possibly limit
     * @return if eventTime should be limited or not.
     *          note: if returning true, eventTime will
     *          be used as limit time on next function call
     */
    open fun shouldLimit(eventTime: LocalDateTime): Boolean {
        val last = getLastEventTime() ?: run {
            setLastEventTime(LocalDateTime.now())
            return false
        }
        if (ChronoUnit.MILLIS.between(last, eventTime) > limitTime) {
            setLastEventTime(LocalDateTime.now())
            return false
        }
        return true
    }

    open fun shouldAllow(eventTime: LocalDateTime): Boolean {
        return !shouldLimit(eventTime)
    }
}

open class NoLimitRateLimiter: RateLimiter(0) {
    override fun shouldLimit(eventTime: LocalDateTime): Boolean {
        return false
    }
}