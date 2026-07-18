package com.yiyi.xposed.util

import android.content.Context
import android.content.SharedPreferences
import com.yiyi.xposed.model.FakeLocationConfig
import de.robv.android.xposed.XSharedPreferences
import java.io.File

/**
 * 配置读写帮助类
 *
 * - UI进程: 使用save()保存配置到SharedPreferences
 * - Hook进程: 使用load()通过XSharedPreferences读取
 *
 * 在VirtualApp环境中，VA框架会处理文件访问权限
 */
object PrefsHelper {

    private const val MODULE_PACKAGE = "com.yiyi.xposed"

    private var xprefs: XSharedPreferences? = null

    // ========================================
    // UI进程端：保存配置
    // ========================================
    fun save(context: Context, config: FakeLocationConfig) {
        val prefs = context.getSharedPreferences(FakeLocationConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(FakeLocationConfig.KEY_ENABLED, config.enabled)
            putString(FakeLocationConfig.KEY_LAT, config.latitude.toString())
            putString(FakeLocationConfig.KEY_LON, config.longitude.toString())
            putString(FakeLocationConfig.KEY_ALT, config.altitude.toString())
            putString(FakeLocationConfig.KEY_ACC, config.accuracy.toString())
            putString(FakeLocationConfig.KEY_BEARING, config.bearing.toString())
            putString(FakeLocationConfig.KEY_SPEED, config.speed.toString())
            putInt(FakeLocationConfig.KEY_RANDOM, config.randomRadius)
            putBoolean(FakeLocationConfig.KEY_WIFI, config.wifiSpoof)
            putBoolean(FakeLocationConfig.KEY_CELL, config.cellSpoof)
            putBoolean(FakeLocationConfig.KEY_GNSS, config.gnssSpoof)
            putBoolean(FakeLocationConfig.KEY_SENSOR, config.sensorSpoof)
        }.apply()

        // 尝试设置文件为世界可读（XSharedPreferences需要）
        try {
            val prefsFile = File(context.dataDir, "shared_prefs/${FakeLocationConfig.PREFS_NAME}.xml")
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false)
                // 尝试设置父目录可执行
                prefsFile.parentFile?.setExecutable(true, false)
                prefsFile.parentFile?.setReadable(true, false)
            }
        } catch (e: Throwable) {
            // 在VirtualApp环境中，VA框架会处理权限
        }
    }

    fun loadFromUI(context: Context): FakeLocationConfig {
        val prefs = context.getSharedPreferences(FakeLocationConfig.PREFS_NAME, Context.MODE_PRIVATE)
        return readConfig(prefs)
    }

    // ========================================
    // Hook进程端：读取配置
    // ========================================
    private fun getXPrefs(): XSharedPreferences {
        return xprefs ?: synchronized(this) {
            xprefs ?: XSharedPreferences(MODULE_PACKAGE, FakeLocationConfig.PREFS_NAME).also {
                it.makeWorldReadable()
                xprefs = it
            }
        }
    }

    fun load(): FakeLocationConfig {
        val prefs = getXPrefs()
        prefs.reload()

        if (!prefs.file.exists()) {
            return FakeLocationConfig(enabled = false)
        }

        return FakeLocationConfig(
            enabled = prefs.getBoolean(FakeLocationConfig.KEY_ENABLED, false),
            latitude = prefs.getString(FakeLocationConfig.KEY_LAT, "0.0")?.toDoubleOrNull() ?: 0.0,
            longitude = prefs.getString(FakeLocationConfig.KEY_LON, "0.0")?.toDoubleOrNull() ?: 0.0,
            altitude = prefs.getString(FakeLocationConfig.KEY_ALT, "50.0")?.toDoubleOrNull() ?: 50.0,
            accuracy = prefs.getString(FakeLocationConfig.KEY_ACC, "15.0")?.toFloatOrNull() ?: 15.0f,
            bearing = prefs.getString(FakeLocationConfig.KEY_BEARING, "0.0")?.toFloatOrNull() ?: 0.0f,
            speed = prefs.getString(FakeLocationConfig.KEY_SPEED, "0.0")?.toFloatOrNull() ?: 0.0f,
            randomRadius = prefs.getInt(FakeLocationConfig.KEY_RANDOM, 0),
            wifiSpoof = prefs.getBoolean(FakeLocationConfig.KEY_WIFI, true),
            cellSpoof = prefs.getBoolean(FakeLocationConfig.KEY_CELL, true),
            gnssSpoof = prefs.getBoolean(FakeLocationConfig.KEY_GNSS, true),
            sensorSpoof = prefs.getBoolean(FakeLocationConfig.KEY_SENSOR, false)
        )
    }

    fun isEnabled(): Boolean {
        val prefs = getXPrefs()
        prefs.reload()
        return prefs.getBoolean(FakeLocationConfig.KEY_ENABLED, false)
    }

    // ========================================
    // 通用：从SharedPreferences读取配置
    // ========================================
    private fun readConfig(prefs: SharedPreferences): FakeLocationConfig {
        return FakeLocationConfig(
            enabled = prefs.getBoolean(FakeLocationConfig.KEY_ENABLED, false),
            latitude = prefs.getString(FakeLocationConfig.KEY_LAT, "0.0")?.toDoubleOrNull() ?: 0.0,
            longitude = prefs.getString(FakeLocationConfig.KEY_LON, "0.0")?.toDoubleOrNull() ?: 0.0,
            altitude = prefs.getString(FakeLocationConfig.KEY_ALT, "50.0")?.toDoubleOrNull() ?: 50.0,
            accuracy = prefs.getString(FakeLocationConfig.KEY_ACC, "15.0")?.toFloatOrNull() ?: 15.0f,
            bearing = prefs.getString(FakeLocationConfig.KEY_BEARING, "0.0")?.toFloatOrNull() ?: 0.0f,
            speed = prefs.getString(FakeLocationConfig.KEY_SPEED, "0.0")?.toFloatOrNull() ?: 0.0f,
            randomRadius = prefs.getInt(FakeLocationConfig.KEY_RANDOM, 0),
            wifiSpoof = prefs.getBoolean(FakeLocationConfig.KEY_WIFI, true),
            cellSpoof = prefs.getBoolean(FakeLocationConfig.KEY_CELL, true),
            gnssSpoof = prefs.getBoolean(FakeLocationConfig.KEY_GNSS, true),
            sensorSpoof = prefs.getBoolean(FakeLocationConfig.KEY_SENSOR, false)
        )
    }
}
