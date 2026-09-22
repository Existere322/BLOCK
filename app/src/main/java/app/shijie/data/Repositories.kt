package app.shijie.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.room.withTransaction
import app.shijie.domain.BlockWindow
import app.shijie.domain.DayPolicy
import app.shijie.domain.ForegroundEvent
import app.shijie.domain.ForegroundEventKind
import app.shijie.domain.ForegroundSpan
import app.shijie.domain.ForegroundSpans
import app.shijie.domain.OpenSession
import app.shijie.domain.QuotaAccounting
import app.shijie.domain.RestrictionGroup
import app.shijie.domain.Retention
import app.shijie.domain.SafetyPackages
import app.shijie.domain.TemporaryOverride
import app.shijie.domain.UsageDay
import app.shijie.domain.WorkdayCalendar
import app.shijie.domain.WorkdayOverride
import app.shijie.domain.toWeekdayMask
import app.shijie.domain.toWeekdays
import app.shijie.system.AppClock
import app.shijie.system.Permissions
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class StoredGroup(
    val group: RestrictionGroup,
    val packages: List<String>,
)

data class LaunchableRow(
    val packageName: String,
    val label: String,
    val otherGroup: String?,
)

data class RankedApp(
    val packageName: String,
    val label: String,
    val millis: Long,
)

class GroupRepository(
    private val context: Context,
    private val db: ShijieDatabase,
) {
    fun observe(): Flow<List<StoredGroup>> = db.groups().observe().map { rows -> rows.map { it.toStored() } }

    suspend fun snapshot(): List<StoredGroup> = withContext(Dispatchers.IO) {
        db.groups().all().map { it.toStored() }
    }

    suspend fun get(id: Long): StoredGroup? = db.groups().get(id)?.toStored()

    suspend fun findByPackage(packageName: String): StoredGroup? {
        val id = db.groups().groupIdOf(packageName) ?: return null
        return get(id)
    }

    suspend fun save(group: RestrictionGroup, packages: Set<String>): Long = withContext(Dispatchers.IO) {
        val illegal = packages.filter { isSafety(it) }
        if (illegal.isNotEmpty()) {
            throw IllegalArgumentException("系统关键应用不能加入限制组")
        }
        db.withTransaction {
            val id = if (group.id == 0L) {
                db.groups().insert(group.toEntity().copy(id = 0))
            } else {
                db.groups().update(group.toEntity())
                group.id
            }
            db.groups().clearWindows(id)
            val windows = group.blockWindows.filter { it.isValid() }.map {
                BlockWindowEntity(groupId = id, startMinute = it.startMinute, endMinute = it.endMinute)
            }
            if (windows.isNotEmpty()) db.groups().insertWindows(windows)
            if (packages.isEmpty()) {
                db.groups().clearApps(id)
            } else {
                packages.forEach { db.groups().upsertApp(GroupAppEntity(it, id)) }
                db.groups().deleteMissing(id, packages.toList())
            }
            id
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { db.groups().delete(id) }

    fun isSafety(packageName: String): Boolean {
        return SafetyPackages.isSafety(
            packageName = packageName,
            selfPackage = context.packageName,
            homePackages = homePackages(),
            dialerPackage = dialerPackage(),
        )
    }

    fun isHome(packageName: String): Boolean {
        return packageName in homePackages() || packageName in SafetyPackages.knownHomes ||
            packageName.endsWith(".launcher") || packageName.endsWith(".launcher3")
    }

    suspend fun launchable(currentGroupId: Long): List<LaunchableRow> = withContext(Dispatchers.IO) {
        val stored = db.groups().all().map { it.toStored() }
        val owner = HashMap<String, String>()
        stored.forEach { item ->
            if (item.group.id != currentGroupId) {
                item.packages.forEach { owner[it] = item.group.name }
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { resolve ->
                val packageName = resolve.activityInfo?.packageName ?: return@mapNotNull null
                if (isSafety(packageName)) return@mapNotNull null
                LaunchableRow(
                    packageName = packageName,
                    label = resolve.loadLabel(context.packageManager)?.toString().orEmpty().ifBlank { packageName },
                    otherGroup = owner[packageName],
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    fun label(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun dialerPackage(): String? {
        return context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }
}

class WorkdayRepository(private val db: ShijieDatabase) {
    suspend fun calendar(): WorkdayCalendar {
        val overrides = db.workdays().all().associate { LocalDate.parse(it.date) to it.workday }
        return WorkdayCalendar(overrides)
    }

    suspend fun list(): List<WorkdayOverride> {
        return db.workdays().all().map { WorkdayOverride(LocalDate.parse(it.date), it.workday) }
    }

    suspend fun set(date: LocalDate, workday: Boolean) {
        db.workdays().upsert(WorkdayEntity(date.toString(), workday))
    }

    suspend fun clear(date: LocalDate) {
        db.workdays().delete(date.toString())
    }
}

class OverrideRepository(private val db: ShijieDatabase) {
    suspend fun grantedOn(groupId: Long, day: LocalDate, zone: ZoneId): List<TemporaryOverride> {
        val start = UsageDay.start(day, zone).toEpochMilli()
        val end = UsageDay.nextStart(day, zone).toEpochMilli()
        return db.overrides().grantedBetween(groupId, start, end).map { it.toDomain() }
    }

    suspend fun overlapping(groupId: Long, day: LocalDate, zone: ZoneId): List<TemporaryOverride> {
        val start = UsageDay.start(day, zone).toEpochMilli()
        val end = UsageDay.nextStart(day, zone).toEpochMilli()
        return db.overrides().overlapping(groupId, start, end).map { it.toDomain() }
    }

    suspend fun insert(release: TemporaryOverride) {
        db.overrides().insert(release.toEntity())
    }

    suspend fun cancelOtherBoots(bootId: String, now: Instant) {
        db.overrides().cancelOtherBoots(bootId, now.toEpochMilli())
    }

    suspend fun deleteOlderThan(cutoff: Instant) {
        db.overrides().deleteGrantedBefore(cutoff.toEpochMilli())
    }
}

class MetaStore(private val db: ShijieDatabase) {
    suspend fun get(key: String): String? = db.meta().get(key)

    suspend fun put(key: String, value: String) {
        db.meta().put(MetaEntity(key, value))
    }

    suspend fun isOnboardingDone(): Boolean = get(ONBOARDING) == "1"

    suspend fun setOnboardingDone() = put(ONBOARDING, "1")

    companion object {
        const val ONBOARDING = "onboarding_done"
        const val LAST_EVENT = "last_foreground_event"
        const val LOCK_UNTIL = "lock_until"
    }

    suspend fun lockInstant(): Instant? {
        return get(LOCK_UNTIL)?.toLongOrNull()?.let(Instant::ofEpochMilli)
    }

    suspend fun isLocked(now: Instant): Boolean {
        val until = lockInstant() ?: return false
        return now.isBefore(until)
    }

    suspend fun engageLock(until: Instant) {
        put(LOCK_UNTIL, until.toEpochMilli().toString())
    }
}

class UsageRepository(
    private val context: Context,
    private val db: ShijieDatabase,
    private val clock: AppClock,
) {
    private val mutex = Mutex()
    private val lastUsed = HashMap<String, Long>()

    suspend fun reconcile(from: LocalDate, to: LocalDate) = mutex.withLock {
        if (!Permissions.usageGranted(context)) return
        val today = UsageDay.localDate(clock.now(), clock.zone())
        db.usage().deleteBefore(Retention.oldestKeptDate(today).toString())
        var day = from
        while (!day.isAfter(to)) {
            reconcileDay(day)
            day = day.plusDays(1)
        }
    }

    suspend fun todayTotal(open: OpenSession?): Long {
        val now = clock.now()
        val zone = clock.zone()
        val today = UsageDay.localDate(now, zone)
        reconcile(today, today)
        val rows = db.usage().forDate(today.toString())
        var sum = 0L
        val seen = HashSet<String>()
        for (row in rows) {
            seen += row.packageName
            sum += row.foregroundMillis + tail(row.packageName, open, now)
        }
        if (open != null && open.packageName !in seen) {
            sum += Duration.between(open.since, now).toMillis().coerceAtLeast(0L)
        }
        return sum
    }

    suspend fun dailyTotals(from: LocalDate, to: LocalDate, open: OpenSession?): List<Pair<LocalDate, Long>> {
        reconcile(from, to)
        val rows = db.usage().between(from.toString(), to.toString())
        val now = clock.now()
        val today = UsageDay.localDate(now, clock.zone())
        val grouped = rows.groupBy { it.date }
        val result = mutableListOf<Pair<LocalDate, Long>>()
        var day = from
        while (!day.isAfter(to)) {
            val items = grouped[day.toString()].orEmpty()
            var sum = items.sumOf { it.foregroundMillis }
            if (day == today && open != null) sum += tail(open.packageName, open, now)
            result += day to sum
            day = day.plusDays(1)
        }
        return result
    }

    suspend fun ranking(from: LocalDate, to: LocalDate, open: OpenSession?): List<RankedApp> {
        reconcile(from, to)
        val rows = db.usage().between(from.toString(), to.toString())
        val totals = HashMap<String, Long>()
        rows.forEach { totals[it.packageName] = (totals[it.packageName] ?: 0L) + it.foregroundMillis }
        val now = clock.now()
        val today = UsageDay.localDate(now, clock.zone())
        if (open != null && !today.isBefore(from) && !today.isAfter(to)) {
            val pkg = open.packageName
            totals[pkg] = (totals[pkg] ?: 0L) + tail(pkg, open, now)
        }
        return totals.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(30)
            .map { RankedApp(it.key, label(it.key), it.value) }
    }

    suspend fun hourlyToday(open: OpenSession?): List<Long> {
        val now = clock.now()
        val zone = clock.zone()
        val day = UsageDay.localDate(now, zone)
        val start = UsageDay.start(day, zone)
        val events = readEvents(start, now).toMutableList()
        if (open != null) events += ForegroundEvent(open.packageName, open.since, ForegroundEventKind.RESUME)
        val buckets = LongArray(24)
        ForegroundSpans.collect(events, start, now).forEach { span -> addHours(span, zone, buckets) }
        return buckets.toList()
    }

    suspend fun systemForegroundToday(packages: Set<String>, open: OpenSession? = null): Long {
        if (packages.isEmpty()) return 0L
        val now = clock.now()
        val today = UsageDay.localDate(now, clock.zone())
        reconcile(today, today)
        val stored = db.usage().forDate(today.toString())
            .filter { it.packageName in packages }
            .sumOf { it.foregroundMillis }
        val live = if (open != null && open.packageName in packages) tail(open.packageName, open, now) else 0L
        return stored + live
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun quotaUsed(
        packages: Set<String>,
        group: RestrictionGroup,
        releases: List<TemporaryOverride>,
        open: OpenSession?,
    ): Long {
        // 额度对照系统屏幕使用时间，包含分组建立之前今天已经用掉的时长。
        return systemForegroundToday(packages, open)
    }

    suspend fun lastResumedPackage(): String? {
        val now = clock.now()
        val zone = clock.zone()
        val start = UsageDay.start(UsageDay.localDate(now, zone), zone)
        var current: String? = null
        for (event in readEvents(start, now).sortedBy { it.at }) {
            when (event.kind) {
                ForegroundEventKind.RESUME -> current = event.packageName
                ForegroundEventKind.PAUSE -> if (current == event.packageName) current = null
            }
        }
        return current
    }

    private suspend fun reconcileDay(day: LocalDate) {
        val zone = clock.zone()
        val start = UsageDay.start(day, zone).toEpochMilli()
        val end = UsageDay.nextStart(day, zone).toEpochMilli()
        val stats = try {
            usageStats().queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
        } catch (_: SecurityException) {
            return
        }
        val summed = HashMap<String, Long>()
        for (item in stats) {
            if (item.totalTimeInForeground <= 0L) continue
            summed[item.packageName] = (summed[item.packageName] ?: 0L) + item.totalTimeInForeground
            if (day == UsageDay.localDate(clock.now(), zone)) {
                lastUsed[item.packageName] = maxOf(lastUsed[item.packageName] ?: 0L, item.lastTimeUsed)
            }
        }
        val rows = summed.map { DailyUsageEntity(day.toString(), it.key, it.value) }
        db.withTransaction {
            db.usage().deleteDate(day.toString())
            if (rows.isNotEmpty()) db.usage().upsert(rows)
        }
    }

    private fun readEvents(start: Instant, end: Instant): List<ForegroundEvent> {
        if (!Permissions.usageGranted(context)) return emptyList()
        val events = try {
            usageStats().queryEvents(start.toEpochMilli(), end.toEpochMilli())
        } catch (_: SecurityException) {
            return emptyList()
        } ?: return emptyList()
        val raw = UsageEvents.Event()
        val parsed = mutableListOf<ForegroundEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(raw)
            val kind = when (raw.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventKind.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> ForegroundEventKind.PAUSE
                else -> null
            } ?: continue
            val packageName = raw.packageName ?: continue
            parsed += ForegroundEvent(packageName, Instant.ofEpochMilli(raw.timeStamp), kind)
        }
        return parsed
    }

    private fun tail(packageName: String, open: OpenSession?, now: Instant): Long {
        if (open == null || open.packageName != packageName) return 0L
        val from = maxOf(lastUsed[packageName] ?: open.since.toEpochMilli(), open.since.toEpochMilli())
        return (now.toEpochMilli() - from).coerceAtLeast(0L)
    }

    private fun addHours(span: ForegroundSpan, zone: ZoneId, buckets: LongArray) {
        var cursor = span.start
        while (cursor.isBefore(span.end)) {
            val zoned = cursor.atZone(zone)
            val hourEnd = zoned.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
            val sliceEnd = if (hourEnd.isBefore(span.end)) hourEnd else span.end
            val hour = zoned.hour
            if (hour in buckets.indices) buckets[hour] += Duration.between(cursor, sliceEnd).toMillis()
            cursor = sliceEnd
        }
    }

    private fun usageStats(): UsageStatsManager {
        return context.getSystemService(UsageStatsManager::class.java)
    }

    private fun label(packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}

private fun GroupWithRules.toStored(): StoredGroup {
    val entity = group
    val model = RestrictionGroup(
        id = entity.id,
        name = entity.name,
        colorArgb = entity.colorArgb,
        iconKey = entity.iconKey,
        enabled = entity.enabled,
        startDate = entity.startDate?.let(LocalDate::parse),
        endDate = entity.endDate?.let(LocalDate::parse),
        dayPolicy = DayPolicy.valueOf(entity.dayPolicy),
        customWeekdays = entity.weekdaysMask.toWeekdays(),
        blockWindows = windows.map { BlockWindow(it.startMinute, it.endMinute) },
        dailyQuota = Duration.ofMillis(entity.quotaMillis),
    )
    return StoredGroup(model, apps.map { it.packageName })
}

private fun RestrictionGroup.toEntity(): GroupEntity {
    return GroupEntity(
        id = id,
        name = name,
        colorArgb = colorArgb,
        iconKey = iconKey,
        enabled = enabled,
        startDate = startDate?.toString(),
        endDate = endDate?.toString(),
        dayPolicy = dayPolicy.name,
        weekdaysMask = customWeekdays.toWeekdayMask(),
        quotaMillis = dailyQuota.toMillis(),
    )
}

private fun OverrideEntity.toDomain(): TemporaryOverride {
    return TemporaryOverride(
        groupId = groupId,
        packageName = packageName,
        reason = reason,
        grantedAt = Instant.ofEpochMilli(grantedAtEpochMs),
        expiresAt = Instant.ofEpochMilli(expiresAtEpochMs),
        cancelledAt = cancelledAtEpochMs?.let(Instant::ofEpochMilli),
        bootId = bootId,
    )
}

private fun TemporaryOverride.toEntity(): OverrideEntity {
    return OverrideEntity(
        groupId = groupId,
        packageName = packageName,
        reason = reason.trim(),
        grantedAtEpochMs = grantedAt.toEpochMilli(),
        expiresAtEpochMs = expiresAt.toEpochMilli(),
        cancelledAtEpochMs = cancelledAt?.toEpochMilli(),
        bootId = bootId,
    )
}
