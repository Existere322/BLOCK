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
import app.shijie.domain.DailyUsageBucket
import app.shijie.domain.ForegroundEvent
import app.shijie.domain.ForegroundEventKind
import app.shijie.domain.ForegroundSpan
import app.shijie.domain.ForegroundSpans
import app.shijie.domain.HourAlignment
import app.shijie.domain.OpenSession
import app.shijie.domain.QuotaAccounting
import app.shijie.domain.RestrictionGroup
import app.shijie.domain.Retention
import app.shijie.domain.SafetyPackages
import app.shijie.domain.TemporaryOverride
import app.shijie.domain.AppTheme
import app.shijie.domain.UsageDay
import app.shijie.domain.UsageVisibility
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
    val otherGroups: List<String>,
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

    suspend fun findByPackage(packageName: String): List<StoredGroup> {
        return db.groups().groupIdsOf(packageName).mapNotNull { get(it) }
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
        val owners = HashMap<String, MutableList<String>>()
        stored.forEach { item ->
            if (item.group.id == currentGroupId) return@forEach
            item.packages.forEach { pkg ->
                owners.getOrPut(pkg) { mutableListOf() }.add(item.group.name)
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
                    otherGroups = owners[packageName].orEmpty(),
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
        const val THEME = "theme"
    }

    suspend fun theme(): AppTheme {
        return if (get(THEME) == AppTheme.PREVIOUS.name) AppTheme.PREVIOUS else AppTheme.CURRENT
    }

    suspend fun setTheme(theme: AppTheme) = put(THEME, theme.name)

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
    /** Sessions often start long before the day being counted. Events are kept for a few days. */
    private val lookback = Duration.ofDays(3)
    /** Some phones only return the newest slice of a long event query, which drops the rest of the day. */
    private val eventSlice = Duration.ofHours(1)

    /**
     * CONTINUE_PREVIOUS_DAY is a hidden UsageEvents type (value 4). The platform treats it as
     * ACTIVITY_RESUMED when a package was still in the foreground across a stats rollover.
     */
    private val continuePreviousDay = 4
    /** Hidden on the SDK we compile against; UsageEvents.Event.ACTIVITY_DESTROYED is 24. */
    private val activityDestroyed = 24
    private val instanceIdMethod: java.lang.reflect.Method? = try {
        UsageEvents.Event::class.java.getMethod("getInstanceId")
    } catch (_: Throwable) {
        null
    }

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
        return db.usage().forDate(today.toString()).sumOf { it.foregroundMillis }
    }

    suspend fun dailyByPackage(from: LocalDate, to: LocalDate, open: OpenSession?): List<Map<String, Long>> {
        reconcile(from, to)
        val rows = db.usage().between(from.toString(), to.toString())
        val grouped = rows.groupBy { it.date }
        val result = mutableListOf<Map<String, Long>>()
        var day = from
        while (!day.isAfter(to)) {
            val map = HashMap<String, Long>()
            grouped[day.toString()].orEmpty().forEach { row ->
                map[row.packageName] = (map[row.packageName] ?: 0L) + row.foregroundMillis
            }
            result += map
            day = day.plusDays(1)
        }
        return result
    }

    suspend fun dailyTotals(from: LocalDate, to: LocalDate, open: OpenSession?): List<Pair<LocalDate, Long>> {
        reconcile(from, to)
        val rows = db.usage().between(from.toString(), to.toString())
        val grouped = rows.groupBy { it.date }
        val result = mutableListOf<Pair<LocalDate, Long>>()
        var day = from
        while (!day.isAfter(to)) {
            val items = grouped[day.toString()].orEmpty()
            result += day to items.sumOf { it.foregroundMillis }
            day = day.plusDays(1)
        }
        return result
    }

    suspend fun ranking(from: LocalDate, to: LocalDate, open: OpenSession?): List<RankedApp> {
        reconcile(from, to)
        val rows = db.usage().between(from.toString(), to.toString())
        val totals = HashMap<String, Long>()
        rows.forEach { totals[it.packageName] = (totals[it.packageName] ?: 0L) + it.foregroundMillis }
        return totals.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(30)
            .map { RankedApp(it.key, label(it.key), it.value) }
    }

    suspend fun hourlyByPackage(open: OpenSession?): List<Map<String, Long>> {
        val now = clock.now()
        val zone = clock.zone()
        val day = UsageDay.localDate(now, zone)
        val start = UsageDay.start(day, zone)
        val totals = systemTotals(start, now).orEmpty()
        val events = readEvents(start, now)
        val buckets = List(24) { HashMap<String, Long>() }
        ForegroundSpans.collect(events, start, now).forEach { span -> addPackageHours(span, zone, buckets) }
        return HourAlignment.scale(buckets, totals, now.atZone(zone).hour).map { hour ->
            hour.filterKeys { it in totals }
        }
    }

    suspend fun hourlyToday(open: OpenSession?): List<Long> {
        return hourlyByPackage(open).map { hour -> hour.values.sum() }
    }

    suspend fun systemForegroundToday(packages: Set<String>, open: OpenSession? = null): Long {
        if (packages.isEmpty()) return 0L
        val now = clock.now()
        val today = UsageDay.localDate(now, clock.zone())
        reconcile(today, today)
        val stored = db.usage().forDate(today.toString())
            .filter { it.packageName in packages }
            .sumOf { it.foregroundMillis }
        return stored
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun quotaUsed(
        packages: Set<String>,
        group: RestrictionGroup,
        releases: List<TemporaryOverride>,
        open: OpenSession?,
    ): Long {
        // 额度直接用系统 UsageStats 的前台时长，不再自行累加事件。
        return systemForegroundToday(packages, open)
    }

    suspend fun lastResumedPackage(): String? {
        val now = clock.now()
        return ForegroundSpans.foregroundPackage(readEvents(now.minus(lookback), now), now)
    }

    private suspend fun reconcileDay(day: LocalDate) {
        val zone = clock.zone()
        val today = UsageDay.localDate(clock.now(), zone)
        val start = UsageDay.start(day, zone)
        val end = if (day == today) clock.now() else UsageDay.nextStart(day, zone)
        val totals = systemTotals(start, end) ?: return
        val rows = totals.map { DailyUsageEntity(day.toString(), it.key, it.value) }
        db.withTransaction {
            db.usage().deleteDate(day.toString())
            if (rows.isNotEmpty()) db.usage().upsert(rows)
        }
    }

    /**
     * Daily buckets come from the system's saved UsageStats. totalTimeInForeground is the
     * focused time only: visible-but-unfocused time and foreground-service time are not added.
     * System packages are dropped. queryAndAggregateUsageStats chooses its own interval and can
     * include adjacent days, so daily buckets are requested and kept only for the local day.
     * null means the platform query failed; the caller then keeps the last saved result.
     */
    private fun systemTotals(start: Instant, end: Instant): Map<String, Long>? {
        if (!start.isBefore(end)) return emptyMap()
        if (!Permissions.usageGranted(context)) return null
        val begin = start.toEpochMilli()
        val finish = end.toEpochMilli()
        val stats = try {
            usageStats().queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, finish)
        } catch (_: SecurityException) {
            return null
        } catch (_: RuntimeException) {
            return null
        } ?: return null
        val buckets = stats.map { usage ->
            DailyUsageBucket(usage.packageName, usage.firstTimeStamp, usage.totalTimeInForeground)
        }
        val flags = applicationFlags()
        return UsageDay.totalsForDate(buckets, UsageDay.localDate(start, clock.zone()), clock.zone())
            .filter { (packageName, millis) ->
                millis > 0L && UsageVisibility.includePackage(flags[packageName])
            }
    }

    private fun applicationFlags(): Map<String, Int> {
        return try {
            context.packageManager.getInstalledApplications(0).associate { it.packageName to it.flags }
        } catch (_: RuntimeException) {
            emptyMap()
        }
    }

    private fun readEvents(start: Instant, end: Instant): List<ForegroundEvent> {
        if (!start.isBefore(end) || !Permissions.usageGranted(context)) return emptyList()
        val parsed = mutableListOf<ForegroundEvent>()
        var cursor = start
        while (cursor.isBefore(end)) {
            val sliceEnd = minOf(cursor.plus(eventSlice), end)
            parsed += readEventSlice(cursor, sliceEnd)
            cursor = sliceEnd
        }
        return parsed.distinctBy { event ->
            listOf(event.packageName, event.at.toEpochMilli(), event.kind, event.instanceKey)
        }
    }

    private fun readEventSlice(start: Instant, end: Instant): List<ForegroundEvent> {
        val events = try {
            usageStats().queryEvents(start.toEpochMilli(), end.toEpochMilli())
        } catch (_: SecurityException) {
            return emptyList()
        } ?: return emptyList()
        val raw = UsageEvents.Event()
        val parsed = mutableListOf<ForegroundEvent>()
        while (events.getNextEvent(raw)) {
            val eventType = raw.eventType
            val timeStamp = raw.timeStamp
            val packageName = raw.packageName.orEmpty()
            val className = raw.className.orEmpty()
            val instanceId = reflectedInstanceId(raw)
            val kind = when (eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                continuePreviousDay,
                -> ForegroundEventKind.RESUME
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundEventKind.PAUSE
                UsageEvents.Event.ACTIVITY_STOPPED,
                activityDestroyed,
                -> ForegroundEventKind.STOP
                UsageEvents.Event.SCREEN_INTERACTIVE -> ForegroundEventKind.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundEventKind.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundEventKind.SHUTDOWN
                else -> null
            } ?: continue
            if (packageName.isEmpty() &&
                kind != ForegroundEventKind.SCREEN_ON &&
                kind != ForegroundEventKind.SCREEN_OFF &&
                kind != ForegroundEventKind.SHUTDOWN
            ) {
                continue
            }
            val instanceKey = when {
                instanceId != 0 -> instanceId
                className.isNotEmpty() -> className.hashCode()
                else -> 0
            }
            parsed += ForegroundEvent(packageName, Instant.ofEpochMilli(timeStamp), kind, instanceKey)
        }
        return parsed
    }

    private fun reflectedInstanceId(event: UsageEvents.Event): Int {
        val method = instanceIdMethod ?: return 0
        return try {
            method.invoke(event) as? Int ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun addPackageHours(span: ForegroundSpan, zone: ZoneId, buckets: List<MutableMap<String, Long>>) {
        var cursor = span.start
        while (cursor.isBefore(span.end)) {
            val zoned = cursor.atZone(zone)
            val hourEnd = zoned.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
            val sliceEnd = if (hourEnd.isBefore(span.end)) hourEnd else span.end
            val hour = zoned.hour
            if (hour in buckets.indices) {
                val map = buckets[hour]
                map[span.packageName] = (map[span.packageName] ?: 0L) + Duration.between(cursor, sliceEnd).toMillis()
            }
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
