package com.yiyi.xposed.util

import kotlin.math.*

/**
 * 坐标转换工具
 * WGS-84 (GPS原始坐标) <-> GCJ-02 (火星坐标, 高德/腾讯使用) <-> BD-09 (百度坐标)
 *
 * 原理：中国国内地图服务使用加密坐标，需要转换后才能匹配
 */
object CoordTransform {

    private const val A = 6378245.0          // 长半轴
    private const val EE = 0.00669342162296594323  // 偏心率平方
    private const val X_PI = PI * 3000.0 / 180.0

    // ========================================
    // WGS-84 -> GCJ-02
    // ========================================
    fun wgs84ToGcj02(wgsLat: Double, wgsLon: Double): Pair<Double, Double> {
        if (outOfChina(wgsLat, wgsLon)) return Pair(wgsLat, wgsLon)

        var dLat = transformLat(wgsLon - 105.0, wgsLat - 35.0)
        var dLon = transformLon(wgsLon - 105.0, wgsLat - 35.0)
        val radLat = wgsLat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI / 180.0)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI / 180.0)
        return Pair(wgsLat + dLat, wgsLon + dLon)
    }

    // ========================================
    // GCJ-02 -> WGS-84
    // ========================================
    fun gcj02ToWgs84(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        if (outOfChina(gcjLat, gcjLon)) return Pair(gcjLat, gcjLon)

        val (magicLat, magicLon) = wgs84ToGcj02(gcjLat, gcjLon) // 反向迭代近似
        val dLat = magicLat - gcjLat
        val dLon = magicLon - gcjLon
        return Pair(gcjLat - dLat, gcjLon - dLon)
    }

    // ========================================
    // GCJ-02 -> BD-09
    // ========================================
    fun gcj02ToBd09(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        val z = sqrt(gcjLon * gcjLon + gcjLat * gcjLat) + 0.00002 * sin(gcjLat * X_PI)
        val theta = atan2(gcjLat, gcjLon) + 0.000003 * cos(gcjLon * X_PI)
        val bdLon = z * cos(theta) + 0.0065
        val bdLat = z * sin(theta) + 0.006
        return Pair(bdLat, bdLon)
    }

    // ========================================
    // WGS-84 -> BD-09
    // ========================================
    fun wgs84ToBd09(wgsLat: Double, wgsLon: Double): Pair<Double, Double> {
        val (gcjLat, gcjLon) = wgs84ToGcj02(wgsLat, wgsLon)
        return gcj02ToBd09(gcjLat, gcjLon)
    }

    // ========================================
    // 判断是否在中国境外（境外不需要转换）
    // ========================================
    private fun outOfChina(lat: Double, lon: Double): Boolean {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
