package com.yiyi.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper
import com.yiyi.xposed.util.CoordTransform

/**
 * 高德地图(AMap)定位SDK Hook
 * 拦截com.amap.api.location包下的所有定位相关API
 *
 * 钉钉使用高德SDK进行定位，这是最关键的hook之一
 * 坐标需要从WGS-84转换为GCJ-02
 */
object AMapHook {

    private const val TAG = "YiYi-AMapHook"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookAMapLocation(lpparam.classLoader)
        hookAMapLocationClient(lpparam.classLoader)
        hookAMapLocationQualityReport(lpparam.classLoader)
    }

    // ========================================
    // AMapLocation Hook - 拦截位置数据读取
    // ========================================
    private fun hookAMapLocation(classLoader: ClassLoader) {
        try {
            val aMapLocClass = XposedHelpers.findClass(
                "com.amap.api.location.AMapLocation", classLoader
            )

            // getLatitude() → 返回GCJ-02纬度
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getLatitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled) return
                        val (gcjLat, _) = CoordTransform.wgs84ToGcj02(config.latitude, config.longitude)
                        param.result = gcjLat
                    }
                })
            } catch (e: Throwable) { XposedBridge.log("$TAG: getLatitude hook failed: ${e.message}") }

            // getLongitude() → 返回GCJ-02经度
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getLongitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled) return
                        val (_, gcjLon) = CoordTransform.wgs84ToGcj02(config.latitude, config.longitude)
                        param.result = gcjLon
                    }
                })
            } catch (e: Throwable) { XposedBridge.log("$TAG: getLongitude hook failed: ${e.message}") }

            // getAccuracy() → 返回伪造精度
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getAccuracy", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.accuracy
                    }
                })
            } catch (e: Throwable) {}

            // getAltitude()
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getAltitude", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.altitude
                    }
                })
            } catch (e: Throwable) {}

            // getSpeed()
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getSpeed", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.speed
                    }
                })
            } catch (e: Throwable) {}

            // getBearing()
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getBearing", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = config.bearing
                    }
                })
            } catch (e: Throwable) {}

            // getLocationType() → 1 = GPS定位结果
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getLocationType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = 1
                    }
                })
            } catch (e: Throwable) {}

            // getProvider() → "gps"
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getProvider", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = "gps"
                    }
                })
            } catch (e: Throwable) {}

            // getErrorCode() → 0 (无错误)
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getErrorCode", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = 0
                    }
                })
            } catch (e: Throwable) {}

            // getErrorInfo() → "成功"
            try {
                XposedHelpers.findAndHookMethod(aMapLocClass, "getErrorInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled) param.result = "成功"
                    }
                })
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ AMapLocation hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: AMapLocation not found (app may not use AMap SDK)")
        }
    }

    // ========================================
    // AMapLocationClient Hook - 拦截定位请求
    // ========================================
    private fun hookAMapLocationClient(classLoader: ClassLoader) {
        try {
            val clientClass = XposedHelpers.findClass(
                "com.amap.api.location.AMapLocationClient", classLoader
            )

            // startLocation() → 触发伪造位置回调
            try {
                XposedHelpers.findAndHookMethod(clientClass, "startLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled) return

                        try {
                            // 尝试获取listener字段
                            val listenerFields = arrayOf("aMapLocationListener", "mListener", "b")
                            var listener: Any? = null

                            for (fieldName in listenerFields) {
                                try {
                                    listener = XposedHelpers.getObjectField(param.thisObject, fieldName)
                                    if (listener != null) break
                                } catch (e: Throwable) { continue }
                            }

                            if (listener == null) {
                                XposedBridge.log("$TAG: AMap listener not found in fields")
                                return
                            }

                            // 创建伪造的AMapLocation
                            val aMapLocClass = XposedHelpers.findClass(
                                "com.amap.api.location.AMapLocation", classLoader
                            )
                            val fakeLoc = aMapLocClass.getDeclaredConstructor().newInstance()

                            val (gcjLat, gcjLon) = CoordTransform.wgs84ToGcj02(config.latitude, config.longitude)
                            XposedHelpers.callMethod(fakeLoc, "setLatitude", gcjLat)
                            XposedHelpers.callMethod(fakeLoc, "setLongitude", gcjLon)
                            XposedHelpers.callMethod(fakeLoc, "setAccuracy", config.accuracy)
                            XposedHelpers.callMethod(fakeLoc, "setAltitude", config.altitude)
                            XposedHelpers.callMethod(fakeLoc, "setLocationType", 1)
                            XposedHelpers.callMethod(fakeLoc, "setProvider", "gps")
                            XposedHelpers.callMethod(fakeLoc, "setErrorCode", 0)
                            XposedHelpers.callMethod(fakeLoc, "setTime", System.currentTimeMillis())

                            // 调用listener.onLocationChanged(fakeLoc)
                            XposedHelpers.callMethod(listener, "onLocationChanged", fakeLoc)
                            XposedBridge.log("$TAG: ✅ Delivered fake AMap location")
                        } catch (e: Throwable) {
                            XposedBridge.log("$TAG: startLocation callback failed: ${e.message}")
                        }
                    }
                })
                XposedBridge.log("$TAG: ✅ AMapLocationClient.startLocation hooked")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: startLocation hook failed: ${e.message}")
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: AMapLocationClient not found")
        }
    }

    // ========================================
    // AMapLocationQualityReport Hook
    // 钉钉检查定位质量报告来判断是否伪造
    // ========================================
    private fun hookAMapLocationQualityReport(classLoader: ClassLoader) {
        try {
            val qrClass = XposedHelpers.findClass(
                "com.amap.api.location.AMapLocationQualityReport", classLoader
            )

            // getGPSStatus() → 1 (GPS正常)
            try {
                XposedHelpers.findAndHookMethod(qrClass, "getGPSStatus", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (PrefsHelper.load().enabled) param.result = 1
                    }
                })
            } catch (e: Throwable) {}

            // getWifiStatus() → 1 (WiFi正常)
            try {
                XposedHelpers.findAndHookMethod(qrClass, "getWifiStatus", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (PrefsHelper.load().enabled) param.result = 1
                    }
                })
            } catch (e: Throwable) {}

            // getNetUseType() → 1 (WiFi连接)
            try {
                XposedHelpers.findAndHookMethod(qrClass, "getNetUseType", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (PrefsHelper.load().enabled) param.result = 1
                    }
                })
            } catch (e: Throwable) {}

            // getSatelitecnt() → 12 (卫星数量)
            try {
                XposedHelpers.findAndHookMethod(qrClass, "getSatelitecnt", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (PrefsHelper.load().enabled) param.result = 12
                    }
                })
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ AMapLocationQualityReport hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: AMapLocationQualityReport not found")
        }
    }
}
