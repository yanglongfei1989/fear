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
}

@Database(entities = [FearEntity::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
    }
}
