package app.shijie.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object EmergencyRelease {
    val wait: Duration = Duration.ofSeconds(30)
    val duration: Duration = Duration.ofMinutes(5)
    const val MAX_PER_GROUP_PER_DAY = 1

    fun rejection(
        reason: String,
        waitStartedAt: Instant,
        now: Instant,
        grantsForGroupToday: Int,
    ): String? {
        if (reason.isBlank()) return "理由不能为空"
        if (grantsForGroupToday >= MAX_PER_GROUP_PER_DAY) return "本组今日已使用应急放行"
        if (now.isBefore(waitStartedAt.plus(wait))) return "请在拦截页等待 30 秒"
        return null
    }

    fun expiresAt(grantedAt: Instant): Instant = grantedAt.plus(duration)

    fun isCurrentlyActive(
        release: TemporaryOverride,
        now: Instant,
        bootId: String,
        packageName: String,
    ): Boolean {
        if (release.packageName != packageName) return false
        if (release.bootId != bootId) return false
        if (release.cancelledAt != null) return false
        if (now.isBefore(release.grantedAt)) return false
        return now.isBefore(release.expiresAt)
    }

    fun effectiveEnd(release: TemporaryOverride): Instant {
        val cancelled = release.cancelledAt
        return if (cancelled != null && cancelled.isBefore(release.expiresAt)) cancelled else release.expiresAt
    }
}
