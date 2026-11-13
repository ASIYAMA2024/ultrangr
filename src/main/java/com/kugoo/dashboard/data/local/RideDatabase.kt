package com.kugoo.dashboard.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ride_sessions")
data class RideSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationSec: Long,
    val distanceKm: Double,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val maxPowerWatts: Double,
    val wattHoursUsed: Double,
    val maxMotorTempC: Double?,
    val maxControllerTempC: Double?,
    val minVoltageV: Double?,
    val maxCurrentA: Double?
)

@Entity(
    tableName = "telemetry_samples",
    foreignKeys = [
        ForeignKey(
            entity = RideSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["rideId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("rideId")]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val sampleId: Long = 0,
    val rideId: Long,
    val timestampMs: Long,
    val speedKmh: Double?,
    val speedSource: String,
    val voltageV: Double?,
    val currentA: Double?,
    val powerW: Double?,
    val socPercent: Int?,
    val motorTempC: Double?,
    val controllerTempC: Double?,
    val batteryTempC: Double?
)

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideSessionEntity): Long

    @Update
    suspend fun updateRide(ride: RideSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: TelemetrySampleEntity)

    @Query("SELECT * FROM ride_sessions ORDER BY startTimeEpochMs DESC")
    fun observeAllRides(): Flow<List<RideSessionEntity>>

    @Query("SELECT * FROM telemetry_samples WHERE rideId = :rideId ORDER BY timestampMs ASC")
    suspend fun getSamplesForRide(rideId: Long): List<TelemetrySampleEntity>

    @Query("DELETE FROM ride_sessions WHERE id = :rideId")
    suspend fun deleteRideById(rideId: Long)
}

@Database(
    entities = [RideSessionEntity::class, TelemetrySampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile
        private var INSTANCE: RideDatabase? = null

        fun getInstance(context: Context): RideDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "kugoo_cockpit_rides.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
