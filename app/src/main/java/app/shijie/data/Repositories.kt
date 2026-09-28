package app.shijie.data

import android.app.usage.UsageEvents
import android.util.Log
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.telecom.TelecomManager
import androidx.room.withTransaction
import app.shijie.domain.BlockWindow
import app.shijie.domain.DayPolicy
import app.shijie.domain.ForegroundEvent
import app.shijie.domain.ForegroundEventKind
import app.shijie.domain.ForegroundSpan
import app.shijie.domain.ForegroundSpans
import app.shijie.domain.OpenSession
import app.shijie.domain.OpenUsage
import app.shijie.domain.ReplayState
import app.shijie.domain.ReplayStateText
import app.shijie.domain.UsageCursor
import app.shijie.domain.UsageLedger
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
    /** Sessions often start long before the day being counted. The first fold looks this far back once. */
    private val lookback = Duration.ofDays(3)
    /** Some phones only return the newest slice of a long event query, which drops the rest of the day. */
    private val eventSlice = Duration.ofHours(1)
    /** After a catch-up, skip another system query until this much time has passed. */
    private val settle = Duration.ofSeconds(20)
    /** Elapsed realtime of the last query that reached "now". Not wall clock, so it survives time changes. */
    private var lastCatchUpElapsed = 0L
    private var flagCache: Map<String, Int> = emptyMap()
    private var flagCacheElapsed = 0L

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

    /**
     * Catches the local ledger up to now. The first run replays the visible week once;
     * later runs only ask the system for events after the saved cursor.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun reconcile(from: LocalDate, to: LocalDate) {
        sync(settle)
    }

    /** Pulls the latest events when the screen turns off or on, without replaying a multi-day backlog. */
    suspend fun flushRecent() {
        val now = clock.now().toEpochMilli()
        val cursor = loadCursor() ?: return
        if (now - cursor.positionMs > Duration.ofHours(2).toMillis()) return
        sync(Duration.ZERO)
    }

    suspend fun todayTotal(open: OpenSession?): Long {
        sync(settle)
        val today = UsageDay.localDate(clock.now(), clock.zone())
        return displayedDaily(today, today).firstOrNull()?.values?.sum() ?: 0L
    }

    suspend fun dailyByPackage(from: LocalDate, to: LocalDate, open: OpenSession?): List<Map<String, Long>> {
        sync(settle)
        return displayedDaily(from, to)
    }

    suspend fun dailyTotals(from: LocalDate, to: LocalDate, open: OpenSession?): List<Pair<LocalDate, Long>> {
        return dailyByPackage(from, to, open).mapIndexed { index, totals ->
            var day = from
            repeat(index) { day = day.plusDays(1) }
            day to totals.values.sum()
        }
    }

    suspend fun ranking(from: LocalDate, to: LocalDate, open: OpenSession?): List<RankedApp> {
        sync(settle)
        val totals = HashMap<String, Long>()
        displayedDaily(from, to).forEach { day ->
            day.forEach { (packageName, millis) ->
                totals[packageName] = (totals[packageName] ?: 0L) + millis
            }
        }
        return totals.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(30)
            .map { RankedApp(it.key, label(it.key), it.value) }
    }

    suspend fun hourlyByPackage(open: OpenSession?): List<Map<String, Long>> {
        sync(settle)
        return displayedHours()
    }

    suspend fun hourlyToday(open: OpenSession?): List<Long> {
        return hourlyByPackage(open).map { hour -> hour.values.sum() }
    }

    suspend fun systemForegroundToday(packages: Set<String>, open: OpenSession? = null): Long {
        if (packages.isEmpty()) return 0L
        sync(settle)
        val today = UsageDay.localDate(clock.now(), clock.zone())
        return displayedDaily(today, today).firstOrNull().orEmpty()
            .filterKeys { it in packages }
            .values
            .sum()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun quotaUsed(
        packages: Set<String>,
        group: RestrictionGroup,
        releases: List<TemporaryOverride>,
        open: OpenSession?,
    ): Long {
        if (packages.isEmpty()) return 0L
        val now = clock.now()
        val zone = clock.zone()
        val today = UsageDay.localDate(now, zone)
        val preview = packageTotal(displayedDaily(today, today).firstOrNull().orEmpty(), packages)
        val remaining = group.dailyQuota.toMillis() - preview
        sync(if (remaining <= 60_000L) Duration.ZERO else settle)
        return packageTotal(displayedDaily(today, today).firstOrNull().orEmpty(), packages)
    }

    suspend fun lastResumedPackage(): String? {
        if (!Permissions.usageGranted(context)) return null
        val now = clock.now()
        val saved = loadCursor()
        val lag = if (saved == null) Long.MAX_VALUE else now.toEpochMilli() - saved.positionMs
        if (saved != null && lag <= settle.toMillis()) {
            return ForegroundSpans.foregroundPackage(saved.replay)
        }
        if (saved != null && lag <= Duration.ofHours(2).toMillis()) {
            sync(Duration.ZERO)
            return loadCursor()?.replay?.let(ForegroundSpans::foregroundPackage)
        }
        val events = readEvents(now.minus(Duration.ofHours(3)), now.plusMillis(1)) ?: return null
        return ForegroundSpans.foregroundPackage(events, now)
    }

    /** Foreground package including events since the last catch-up. Used while a block window is open. */
    suspend fun currentForeground(): String? {
        if (!Permissions.usageGranted(context)) return null
        sync(Duration.ZERO)
        return loadCursor()?.replay?.let(ForegroundSpans::foregroundPackage)
    }

    /**
     * Folds system usage events into the local ledger. Daily UsageStats buckets are not used:
     * on ColorOS those buckets are not aligned to local midnight and omit the open session.
     * A finished day stays in the database. The next read only queries events after the cursor,
     * and an still-open visit is added in memory until its pause is stored once.
     */
    private suspend fun sync(minGap: Duration) = mutex.withLock {
        if (!Permissions.usageGranted(context)) return
        val zone = clock.zone()
        val now = clock.now()
        val endMs = now.toEpochMilli() + 1
        val loaded = loadCursor()
        val originMs = backlogStart(now, zone).toEpochMilli()
        var rebuilding = loaded == null || loaded.positionMs > endMs + 60_000L
        var cursor = if (rebuilding) UsageCursor(originMs, ReplayState()) else loaded!!
        if (cursor.positionMs >= endMs) {
            lastCatchUpElapsed = SystemClock.elapsedRealtime()
            return
        }
        val elapsed = SystemClock.elapsedRealtime()
        val lag = endMs - cursor.positionMs
        if (
            minGap > Duration.ZERO &&
            lag <= minGap.toMillis() &&
            lastCatchUpElapsed != 0L &&
            elapsed - lastCatchUpElapsed < minGap.toMillis()
        ) {
            return
        }
        val flags = flags()
        val oldest = Retention.oldestKeptDate(UsageDay.localDate(now, zone)).toString()
        db.usage().deleteBefore(oldest)
        db.hourly().deleteBefore(oldest)
        var position = Instant.ofEpochMilli(cursor.positionMs)
        val end = Instant.ofEpochMilli(endMs)
        while (position.isBefore(end)) {
            val sliceEnd = if (Duration.between(position, end) <= eventSlice) end else position.plus(eventSlice)
            val queryStart = position.minusMillis(1)
            val events = readEventSlice(queryStart, sliceEnd) ?: return
            val unique = events.distinctBy { event ->
                listOf(event.packageName, event.at.toEpochMilli(), event.kind, event.instanceKey)
            }
            val step = ForegroundSpans.advance(cursor, unique, sliceEnd.toEpochMilli())
            if (rebuilding) {
                resetLedger(originMs)
                rebuilding = false
            }
            commit(step, zone, flags)
            cursor = step.cursor
            position = sliceEnd
        }
        lastCatchUpElapsed = SystemClock.elapsedRealtime()
    }

    private fun backlogStart(now: Instant, zone: ZoneId): Instant {
        val today = UsageDay.localDate(now, zone)
        return UsageDay.start(today.minusDays(6), zone).minus(lookback)
    }

    private suspend fun resetLedger(originMs: Long) {
        db.withTransaction {
            db.usage().deleteAll()
            db.hourly().deleteAll()
            db.meta().put(MetaEntity(FOLD, FOLD_VERSION))
            db.meta().put(MetaEntity(CURSOR, originMs.toString()))
            db.meta().put(MetaEntity(REPLAY, ReplayStateText.encode(ReplayState())))
        }
    }

    private suspend fun loadCursor(): UsageCursor? {
        val version = db.meta().get(FOLD)
        val raw = db.meta().get(CURSOR)?.toLongOrNull()
        if (version != FOLD_VERSION || raw == null) return null
        return UsageCursor(raw, ReplayStateText.decode(db.meta().get(REPLAY).orEmpty()))
    }

    private suspend fun commit(
        step: app.shijie.domain.UsageAdvance,
        zone: ZoneId,
        flags: Map<String, Int>,
    ) {
        val dailyAdds = HashMap<Pair<String, String>, Long>()
        val hourlyAdds = HashMap<HourKey, Long>()
        for (span in step.closed) {
            if (!UsageVisibility.includePackage(flags[span.packageName])) continue
            for (addition in UsageLedger.additions(span, zone)) {
                val date = addition.date.toString()
                val dailyKey = date to addition.packageName
                dailyAdds[dailyKey] = (dailyAdds[dailyKey] ?: 0L) + addition.millis
                val hourKey = HourKey(date, addition.hour, addition.packageName)
                hourlyAdds[hourKey] = (hourlyAdds[hourKey] ?: 0L) + addition.millis
            }
        }
        db.withTransaction {
            for (day in dailyAdds.keys.map { it.first }.toSet()) {
                val existing = db.usage().forDate(day).associate { it.packageName to it.foregroundMillis }.toMutableMap()
                dailyAdds.filterKeys { it.first == day }.forEach { (key, millis) ->
                    existing[key.second] = (existing[key.second] ?: 0L) + millis
                }
                db.usage().upsert(existing.map { DailyUsageEntity(day, it.key, it.value) })
            }
            for (day in hourlyAdds.keys.map { it.date }.toSet()) {
                val existing = db.hourly().forDate(day)
                    .associate { (it.hour to it.packageName) to it.foregroundMillis }
                    .toMutableMap()
                hourlyAdds.filterKeys { it.date == day }.forEach { (key, millis) ->
                    val mapKey = key.hour to key.packageName
                    existing[mapKey] = (existing[mapKey] ?: 0L) + millis
                }
                db.hourly().upsert(existing.map { HourlyUsageEntity(day, it.key.first, it.key.second, it.value) })
            }
            db.meta().put(MetaEntity(CURSOR, step.cursor.positionMs.toString()))
            db.meta().put(MetaEntity(REPLAY, ReplayStateText.encode(step.cursor.replay)))
            db.meta().put(MetaEntity(FOLD, FOLD_VERSION))
        }
    }

    private suspend fun displayedDaily(from: LocalDate, to: LocalDate): List<Map<String, Long>> {
        val now = clock.now()
        val zone = clock.zone()
        val rows = db.usage().between(from.toString(), to.toString())
        val grouped = rows.groupBy { it.date }
        val extra = HashMap<Pair<String, String>, Long>()
        tailAdditions(now, zone).forEach { addition ->
            val key = addition.date.toString() to addition.packageName
            extra[key] = (extra[key] ?: 0L) + addition.millis
        }
        val result = mutableListOf<Map<String, Long>>()
        var day = from
        while (!day.isAfter(to)) {
            val map = HashMap<String, Long>()
            grouped[day.toString()].orEmpty().forEach { row ->
                map[row.packageName] = (map[row.packageName] ?: 0L) + row.foregroundMillis
            }
            extra.filterKeys { it.first == day.toString() }.forEach { (key, millis) ->
                map[key.second] = (map[key.second] ?: 0L) + millis
            }
            result += map
            day = day.plusDays(1)
        }
        return result
    }

    private suspend fun displayedHours(): List<Map<String, Long>> {
        val now = clock.now()
        val zone = clock.zone()
        val today = UsageDay.localDate(now, zone).toString()
        val buckets = List(24) { HashMap<String, Long>() }
        db.hourly().forDate(today).forEach { row ->
            if (row.hour in buckets.indices) buckets[row.hour][row.packageName] = row.foregroundMillis
        }
        tailAdditions(now, zone).forEach { addition ->
            if (addition.date.toString() != today || addition.hour !in buckets.indices) return@forEach
            val map = buckets[addition.hour]
            map[addition.packageName] = (map[addition.packageName] ?: 0L) + addition.millis
        }
        return buckets
    }

    private suspend fun tailAdditions(now: Instant, zone: ZoneId): List<UsageLedger.Addition> {
        val cursor = loadCursor() ?: return emptyList()
        val flags = flags()
        val caughtUp = now.toEpochMilli() - cursor.positionMs <= settle.toMillis()
        val until = if (caughtUp) now else Instant.ofEpochMilli(cursor.positionMs)
        return OpenUsage.spans(cursor.replay, until)
            .filter { UsageVisibility.includePackage(flags[it.packageName]) }
            .flatMap { UsageLedger.additions(it, zone) }
    }

    private fun packageTotal(totals: Map<String, Long>, packages: Set<String>): Long {
        return totals.filterKeys { it in packages }.values.sum()
    }

    private fun flags(): Map<String, Int> {
        val now = SystemClock.elapsedRealtime()
        if (flagCache.isNotEmpty() && now - flagCacheElapsed < FLAG_CACHE_MS) return flagCache
        flagCache = try {
            context.packageManager.getInstalledApplications(0).associate { it.packageName to it.flags }
        } catch (_: RuntimeException) {
            flagCache
        }
        flagCacheElapsed = now
        return flagCache
    }

    private fun readEvents(start: Instant, end: Instant): List<ForegroundEvent>? {
        if (!start.isBefore(end) || !Permissions.usageGranted(context)) return emptyList()
        val parsed = mutableListOf<ForegroundEvent>()
        var cursor = start
        while (cursor.isBefore(end)) {
            val sliceEnd = minOf(cursor.plus(eventSlice), end)
            parsed += readEventSlice(cursor, sliceEnd) ?: return null
            cursor = sliceEnd
        }
        return parsed.distinctBy { event ->
            listOf(event.packageName, event.at.toEpochMilli(), event.kind, event.instanceKey)
        }
    }

    private fun readEventSlice(start: Instant, end: Instant): List<ForegroundEvent>? {
        val events = try {
            usageStats().queryEvents(start.toEpochMilli(), end.toEpochMilli())
        } catch (error: Exception) {
            Log.w("Shijie", "usage events unavailable: ${error.message}")
            return null
        } ?: return null
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

    private data class HourKey(val date: String, val hour: Int, val packageName: String)

    private companion object {
        const val CURSOR = "usage_cursor_ms"
        const val REPLAY = "usage_replay"
        const val FOLD = "usage_fold"
        const val FOLD_VERSION = "1"
        const val FLAG_CACHE_MS = 30 * 60_000L
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
