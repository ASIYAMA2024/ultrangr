package com.kugoo.dashboard.data.export

import android.content.Context
import android.os.Environment
import com.kugoo.dashboard.data.local.RideSessionEntity
import com.kugoo.dashboard.data.local.TelemetrySampleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RideExportManager {

    fun exportRideToCsv(
        context: Context,
        ride: RideSessionEntity,
        samples: List<TelemetrySampleEntity>
    ): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "KugooExports"
        ).apply { mkdirs() }

        val file = File(dir, "kugoo_ride_${ride.id}_${ride.startTimeEpochMs}.csv")
        file.bufferedWriter().use { out ->
            out.write("timestamp_ms,speed_kmh,speed_source,voltage_v,current_a,power_w,soc_pct,motor_temp_c,ctrl_temp_c,batt_temp_c\n")
            for (s in samples) {
                out.write(
                    "${s.timestampMs},${s.speedKmh ?: ""},${s.speedSource},${s.voltageV ?: ""},${s.currentA ?: ""},${s.powerW ?: ""},${s.socPercent ?: ""},${s.motorTempC ?: ""},${s.controllerTempC ?: ""},${s.batteryTempC ?: ""}\n"
                )
            }
        }
        return file
    }

    fun exportRideToJson(
        context: Context,
        ride: RideSessionEntity,
        samples: List<TelemetrySampleEntity>
    ): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "KugooExports"
        ).apply { mkdirs() }

        val file = File(dir, "kugoo_ride_${ride.id}_${ride.startTimeEpochMs}.json")
        val root = JSONObject().apply {
            put("rideId", ride.id)
            put("startTimeEpochMs", ride.startTimeEpochMs)
            put("endTimeEpochMs", ride.endTimeEpochMs)
            put("durationSec", ride.durationSec)
            put("distanceKm", ride.distanceKm)
            put("maxSpeedKmh", ride.maxSpeedKmh)
            put("avgSpeedKmh", ride.avgSpeedKmh)
            put("wattHoursUsed", ride.wattHoursUsed)
            val arr = JSONArray()
            for (s in samples) {
                arr.put(
                    JSONObject().apply {
                        put("timestampMs", s.timestampMs)
                        put("speedKmh", s.speedKmh)
                        put("speedSource", s.speedSource)
                        put("voltageV", s.voltageV)
                        put("currentA", s.currentA)
                        put("powerW", s.powerW)
                        put("socPercent", s.socPercent)
                        put("motorTempC", s.motorTempC)
                        put("controllerTempC", s.controllerTempC)
                        put("batteryTempC", s.batteryTempC)
                    }
                )
            }
            put("samples", arr)
        }
        file.writeText(root.toString(2))
        return file
    }
}
