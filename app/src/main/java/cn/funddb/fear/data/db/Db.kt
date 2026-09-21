package cn.funddb.fear.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room

@Entity(tableName = "fear_points")
data class FearEntity(
    @PrimaryKey val date: String,
    val fear: Double?,
    val indexValue: Double?,
    val symbol: String,
    val fetchedAt: Long,
)

@Dao
interface FearDao {
    @Query("SELECT * FROM fear_points WHERE symbol = :symbol ORDER BY date ASC")
    suspend fun history(symbol: String): List<FearEntity>

    @Query("SELECT * FROM fear_points WHERE symbol = :symbol ORDER BY date DESC LIMIT 2")
    suspend fun latestTwo(symbol: String): List<FearEntity>

    @Query("SELECT COUNT(*) FROM fear_points WHERE symbol = :symbol")
    suspend fun count(symbol: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<FearEntity>)

    @Query("DELETE FROM fear_points WHERE symbol = :symbol")
    suspend fun clear(symbol: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: FearMeta)

    @Query("SELECT * FROM fear_meta WHERE symbol = :symbol LIMIT 1")
    suspend fun meta(symbol: String): FearMeta?
}

/** 官方数值面板：当前值/属性/往期四环（getbasedata 下发，日更）。 */
@Entity(tableName = "fear_meta")
data class FearMeta(
    @PrimaryKey val symbol: String,
    val num: Double?,
    val statusStr: String?,
    val currentTime: String?,
    /** PastRing 列表的 JSON（含 name/value/label/colorHex） */
    val ringsJson: String,
    val fetchedAt: Long,
)

@Database(entities = [FearEntity::class, FearMeta::class], version = 2, exportSchema = false)
abstract class FearDatabase : androidx.room.RoomDatabase() {
    abstract fun fearDao(): FearDao

    companion object {
        @Volatile private var instance: FearDatabase? = null

        fun get(context: Context): FearDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FearDatabase::class.java,
                    "fear.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
