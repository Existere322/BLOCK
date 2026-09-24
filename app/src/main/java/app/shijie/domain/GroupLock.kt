package app.shijie.domain

import java.time.Instant

/**
 * While the app is locked, an existing group's schedule and quota stay put.
 * Name, color, and icon may still change. Creating a group is not an edit.
 */
object GroupLock {
    fun rejection(
        existing: RestrictionGroup,
        proposed: RestrictionGroup,
        existingPackages: Set<String>,
        proposedPackages: Set<String>,
    ): String? {
        if (!sameRules(existing, proposed)) return "上锁期间不能修改时间限制或使用时长"
        if (existingPackages != proposedPackages) return "上锁期间不能调整分组里的应用"
        return null
    }

    fun sameRules(before: RestrictionGroup, after: RestrictionGroup): Boolean {
        return before.enabled == after.enabled &&
            before.startDate == after.startDate &&
            before.endDate == after.endDate &&
            before.dayPolicy == after.dayPolicy &&
            before.customWeekdays == after.customWeekdays &&
            before.blockWindows == after.blockWindows &&
            before.dailyQuota == after.dailyQuota
    }
}

/** An app in several groups is blocked when any one of those groups blocks it. */
object RestrictionUnion {
    fun strictest(decisions: List<BlockDecision>): BlockDecision {
        val blocks = decisions.filterIsInstance<BlockDecision.Block>()
        if (blocks.isEmpty()) return BlockDecision.Allow
        return blocks.maxWith(
            compareBy<BlockDecision.Block> { it.endsAt ?: Instant.MAX }.thenBy { it.groupId },
        )
    }
}
