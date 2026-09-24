package app.shijie.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "restriction_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val enabled: Boolean,
    val startDate: String?,
    val endDate: String?,
    val dayPolicy: String,
    val weekdaysMask: Int,
    val quotaMillis: Long,
)

@Entity(
    tableName = "group_apps",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["packageName", "groupId"],
    indices = [Index("groupId")],
)
data class GroupAppEntity(
    val packageName: String,
    val groupId: Long,
)

@Entity(
    tableName = "block_windows",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class BlockWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(tableName = "workday_overrides")
data class WorkdayEntity(
    @PrimaryKey val date: String,
    val workday: Boolean,
)

@Entity(
    tableName = "daily_usage",
    primaryKeys = ["date", "packageName"],
)
data class DailyUsageEntity(
    val date: String,
    val packageName: String,
    val foregroundMillis: Long,
)

@Entity(
    tableName = "temporary_overrides",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId"), Index("grantedAtEpochMs")],
)
data class OverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val packageName: String,
    val reason: String,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val cancelledAtEpochMs: Long?,
    val bootId: String,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class GroupWithRules(
    @Embedded val group: GroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val windows: List<BlockWindowEntity>,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val apps: List<GroupAppEntity>,
)

@Dao
interface GroupDao {
    @Transaction
    @Query("SELECT * FROM restriction_groups ORDER BY name")
    fun observe(): Flow<List<GroupWithRules>>

    @Transaction
    @Query("SELECT * FROM restriction_groups")
    suspend fun all(): List<GroupWithRules>

    @Transaction
    @Query("SELECT * FROM restriction_groups WHERE id = :id")
    suspend fun get(id: Long): GroupWithRules?

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM restriction_groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT groupId FROM group_apps WHERE packageName = :packageName")
    suspend fun groupIdsOf(packageName: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: GroupAppEntity)

    @Query("DELETE FROM group_apps WHERE groupId = :groupId")
    suspend fun clearApps(groupId: Long)

    @Query("DELETE FROM group_apps WHERE groupId = :groupId AND packageName NOT IN (:keep)")
    suspend fun deleteMissing(groupId: Long, keep: List<String>)

    @Query("DELETE FROM block_windows WHERE groupId = :groupId")
    suspend fun clearWindows(groupId: Long)

    @Insert
    suspend fun insertWindows(windows: List<BlockWindowEntity>)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun forDate(date: String): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_usage WHERE date >= :fromDate AND date <= :toDate")
    suspend fun between(fromDate: String, toDate: String): List<DailyUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<DailyUsageEntity>)

    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun deleteDate(date: String)

    @Query("DELETE FROM daily_usage WHERE date < :date")
    suspend fun deleteBefore(date: String)
}

@Dao
interface OverrideDao {
    @Query(
        "SELECT * FROM temporary_overrides WHERE groupId = :groupId AND grantedAtEpochMs >= :start AND grantedAtEpochMs < :end",
    )
    suspend fun grantedBetween(groupId: Long, start: Long, end: Long): List<OverrideEntity>

    @Query(
        "SELECT * FROM temporary_overrides WHERE groupId = :groupId AND grantedAtEpochMs < :end AND expiresAtEpochMs > :start",
    )
    suspend fun overlapping(groupId: Long, start: Long, end: Long): List<OverrideEntity>

    @Insert
    suspend fun insert(entity: OverrideEntity)

    @Query(
        "UPDATE temporary_overrides SET cancelledAtEpochMs = :now WHERE bootId != :bootId AND cancelledAtEpochMs IS NULL",
    )
    suspend fun cancelOtherBoots(bootId: String, now: Long)

    @Query("DELETE FROM temporary_overrides WHERE grantedAtEpochMs < :cutoff")
    suspend fun deleteGrantedBefore(cutoff: Long)
}

@Dao
interface WorkdayDao {
    @Query("SELECT * FROM workday_overrides ORDER BY date")
    suspend fun all(): List<WorkdayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkdayEntity)

    @Query("DELETE FROM workday_overrides WHERE date = :date")
    suspend fun delete(date: String)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: MetaEntity)
}

@Database(
    entities = [
        GroupEntity::class,
        GroupAppEntity::class,
        BlockWindowEntity::class,
        WorkdayEntity::class,
        DailyUsageEntity::class,
        OverrideEntity::class,
        MetaEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ShijieDatabase : RoomDatabase() {
    abstract fun groups(): GroupDao
    abstract fun usage(): UsageDao
    abstract fun overrides(): OverrideDao
    abstract fun workdays(): WorkdayDao
    abstract fun meta(): MetaDao

    companion object {
        @Volatile private var instance: ShijieDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_apps_new` (`packageName` TEXT NOT NULL, `groupId` INTEGER NOT NULL, PRIMARY KEY(`packageName`, `groupId`), FOREIGN KEY(`groupId`) REFERENCES `restriction_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `group_apps_new` (`packageName`, `groupId`) SELECT `packageName`, `groupId` FROM `group_apps`",
                )
                db.execSQL("DROP TABLE `group_apps`")
                db.execSQL("ALTER TABLE `group_apps_new` RENAME TO `group_apps`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_group_apps_groupId` ON `group_apps` (`groupId`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Replace the original preset swatches in existing groups so upgrades use the
                // new palette too. Colors outside that preset remain user controlled.
                val swatches = listOf(
                    0xFFC67C4E.toInt() to 0xFF1468E8.toInt(),
                    0xFF527A73.toInt() to 0xFF9137D7.toInt(),
                    0xFF78885A.toInt() to 0xFFF18A27.toInt(),
                    0xFFC19A56.toInt() to 0xFF249A87.toInt(),
                    0xFF81708E.toInt() to 0xFF5E73D7.toInt(),
                    0xFFCA786C.toInt() to 0xFFE36D79.toInt(),
                    0xFF5E748D.toInt() to 0xFF5C829D.toInt(),
                    0xFF806653.toInt() to 0xFF9671A6.toInt(),
                )
                swatches.forEach { (old, replacement) ->
                    db.execSQL(
                        "UPDATE restriction_groups SET colorArgb = ? WHERE colorArgb = ?",
                        arrayOf(replacement, old),
                    )
                }
            }
        }

        fun get(context: Context): ShijieDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShijieDatabase::class.java,
                    "shijie.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
