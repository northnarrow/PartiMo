package com.partimo.data.cache

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Risposta di ricerca salvata così come ricevuta dal provider, validata prima del salvataggio. */
@Entity(tableName = "cached_responses")
data class CachedResponseEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val storedAtMillis: Long,
)

@Dao
interface CachedResponseDao {

    @Query("SELECT * FROM cached_responses WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): CachedResponseEntity?

    @Upsert
    suspend fun upsert(entity: CachedResponseEntity)

    @Query("DELETE FROM cached_responses WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_responses WHERE storedAtMillis < :thresholdMillis")
    suspend fun deleteOlderThan(thresholdMillis: Long): Int
}

/**
 * Database locale dedicato alla cache delle ricerche.
 *
 * Contiene solo dati ricostruibili dalla rete: in caso di cambio di schema viene ricreato da zero
 * (migrazione distruttiva) e per questo lo schema non viene esportato.
 */
@Database(entities = [CachedResponseEntity::class], version = 1, exportSchema = false)
abstract class PartiMoDatabase : RoomDatabase() {

    abstract fun cachedResponseDao(): CachedResponseDao

    companion object {
        private const val DATABASE_NAME = "partimo_cache.db"

        fun create(context: Context): PartiMoDatabase =
            Room.databaseBuilder(context, PartiMoDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
