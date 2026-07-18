package com.yiyi.xposed.model

import android.location.Location
import android.os.Build
import android.os.SystemClock

/**
 * 虚拟定位配置数据模型
 * 通过XSharedPreferences在UI进程和目标App进程之间共享
 */
data class FakeLocationConfig(
    val enabled: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 50.0,
    val accuracy: Float = 15.0f,
    val bearing: Float = 0.0f,
    val speed: Float = 0.0f,
    val randomRadius: Int = 0,
    val wifiSpoof: Boolean = true,
    val cellSpoof: Boolean = true,
    val gnssSpoof: Boolean = true,
    val sensorSpoof: Boolean = false
) {
    companion object {
        const val PREFS_NAME = "yiyi_xposed_config"
        const val KEY_ENABLED = "enabled"
        const val KEY_LAT = "latitude"
        const val KEY_LON = "longitude"
        const val KEY_ALT = "altitude"
        const val KEY_ACC = "accuracy"
        const val KEY_BEARING = "bearing"
        const val KEY_SPEED = "speed"
        const val KEY_RANDOM = "randomRadius"
        const val KEY_WIFI = "wifiSpoof"
        const val KEY_CELL = "cellSpoof"
        const val KEY_GNSS = "gnssSpoof"
        const val KEY_SENSOR = "sensorSpoof"
    }

    /**
     * 创建一个伪造的Location对象
     * @param provider 位置提供者名称 (gps/network/fused)
     * @return 伪造的Location对象
     */
    fun createFakeLocation(provider: String = "gps"): Location {
        val (finalLat, finalLon) = applyRandomOffset()

        return Location(provider).apply {
            latitude = finalLat
            longitude = finalLon
            altitude = this@FakeLocationConfig.altitude
            accuracy = this@FakeLocationConfig.accuracy
            bearing = this@FakeLocationConfig.bearing
            speed = this@FakeLocationConfig.speed
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            // Android O+ vertical accuracy
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                verticalAccuracyMeters = this@FakeLocationConfig.accuracy * 1.5f
                bearingAccuracyDegrees = 5.0f
                speedAccuracyMetersPerSecond = 1.0f
            }

            // Android Q+ extras
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setMock(false)
            }
        }
    }

    /**
     * 应用随机偏移（如果配置了randomRadius）
     * 使定位看起来更真实
     */
    private fun applyRandomOffset(): Pair<Double, Double> {
        if (randomRadius <= 0) return Pair(latitude, longitude)

        val offsetLat = (Math.random() - 0.5) * randomRadius / 111000.0
        val offsetLon = (Math.random() - 0.5) * randomRadius / 111000.0 * Math.cos(Math.toRadians(latitude))
        return Pair(latitude + offsetLat, longitude + offsetLon)
    }
}
