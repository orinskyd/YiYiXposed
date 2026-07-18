package com.yiyi.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper
import com.yiyi.xposed.util.CoordTransform

/**
 * 百度地图(Baidu)定位SDK Hook
 * 拦截com.baidu.location包下的定位相关API
 *
 * 坐标需要从WGS-84转换为BD-09
 */
object BaiduHook {

    private const val TAG = "YiYi-BaiduHook"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookBDLocation(lpparam.classLoader)
    }

    // ========================================
    // BDLocation Hook
    // ========================================
    private fun hookBDLocation(classLoader: ClassLoader) {
        try {
            val bdLocClass = XposedHelpers.findClass(
                "com.baidu.location.BDLocation", classLoader
            )

            // getLatitude() → BD-09纬度
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled) return
                        val (bdLat, _) = CoordTransform.wgs84ToBd09(config.latitude, config.longitude)
                        param.result = bdLat
                    }
                })
            } catch (e: Throwable) {}

            // getLongitude() → BD-09经度
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled) return
                        val (_, bdLon) = CoordTransform.wgs84ToBd09(config.latitude, config.longitude)
                        param.result = bdLon
                    }
                })
            } catch (e: Throwable) {}

            // getRadius() → 精度
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getRadius", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.accuracy
                    }
                })
            } catch (e: Throwable) {}

            // getAltitude()
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getAltitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.altitude
                    }
                })
            } catch (e: Throwable) {}

            // getSpeed()
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getSpeed", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.speed
                    }
                })
            } catch (e: Throwable) {}

            // getDirection()
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getDirection", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.bearing
                    }
                })
            } catch (e: Throwable) {}

            // getLocType() → 61 (GPS定位成功)
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getLocType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = 61
                    }
                })
            } catch (e: Throwable) {}

            // getLocTypeDescription() → "gps location success"
            try {
                XposedHelpers.findAndHookMethod(bdLocClass, "getLocTypeDescription", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = "gps location success"
                    }
                })
            } catch (e: Throwable) {}

            // getProvince(), getCity(), getDistrict() → 返回空（不提供地理编码）
            // 不hook这些方法，让百度SDK自行解析

            XposedBridge.log("$TAG: ✅ BDLocation hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: BDLocation not found (app may not use Baidu SDK)")
        }
    }
}
