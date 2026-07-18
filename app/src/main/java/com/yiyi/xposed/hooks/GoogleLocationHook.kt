package com.yiyi.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper

/**
 * Google Play Services 定位Hook
 * 拦截FusedLocationProviderClient等Google定位API
 *
 * 关键Hook点：
 * 1. FusedLocationProviderClient.getLastLocation() → 返回伪造位置
 * 2. FusedLocationProviderClient.requestLocationUpdates() → 投递伪造位置
 * 3. LocationResult.getLocations() → 返回伪造列表
 * 4. LocationResult.getLocation() → 返回伪造位置
 */
object GoogleLocationHook {

    private const val TAG = "YiYi-GoogleHook"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookFusedLocationProvider(lpparam.classLoader)
        hookLocationResult(lpparam.classLoader)
    }

    // ========================================
    // FusedLocationProviderClient Hook
    // ========================================
    private fun hookFusedLocationProvider(classLoader: ClassLoader) {
        try {
            val flpClass = XposedHelpers.findClass(
                "com.google.android.gms.location.FusedLocationProviderClient", classLoader
            )

            // getLastLocation() → Task<Location>
            try {
                XposedHelpers.findAndHookMethod(
                    flpClass, "getLastLocation",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (!config.enabled) return

                            val task = param.result ?: return
                            val fakeLocation = config.createFakeLocation("fused")

                            // 修改Task的结果为伪造位置
                            try {
                                // Task.setResult 不一定可用，尝试直接修改
                                val locationResultClass = XposedHelpers.findClass(
                                    "com.google.android.gms.location.LocationResult", classLoader
                                )
                                val createMethod = locationResultClass.getMethod(
                                    "create", java.util.List::class.java
                                )
                                val fakeList = listOf(fakeLocation)
                                val fakeResult = createMethod.invoke(null, fakeList)

                                // 设置Task结果
                                XposedHelpers.callMethod(task, "setResult", fakeResult)
                            } catch (e: Throwable) {
                                XposedBridge.log("$TAG: getLastLocation setResult failed: ${e.message}")
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: ✅ Hooked FusedLocationProviderClient.getLastLocation")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: ⚠️ getLastLocation hook failed: ${e.message}")
            }

            // requestLocationUpdates → 投递伪造位置
            try {
                XposedHelpers.findAndHookMethod(
                    flpClass, "requestLocationUpdates",
                    XposedHelpers.findClass("com.google.android.gms.location.LocationRequest", classLoader),
                    XposedHelpers.findClass("com.google.android.gms.location.LocationCallback", classLoader),
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (!config.enabled) return

                            val callback = param.args[1] ?: return
                            val fakeLocation = config.createFakeLocation("fused")

                            // 构造LocationResult并调用callback.onLocationResult()
                            try {
                                val locationResultClass = XposedHelpers.findClass(
                                    "com.google.android.gms.location.LocationResult", classLoader
                                )
                                val createMethod = locationResultClass.getMethod(
                                    "create", java.util.List::class.java
                                )
                                val fakeResult = createMethod.invoke(null, listOf(fakeLocation))
                                XposedHelpers.callMethod(callback, "onLocationResult", fakeResult)
                            } catch (e: Throwable) {
                                XposedBridge.log("$TAG: requestLocationUpdates callback failed: ${e.message}")
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: ✅ Hooked FusedLocationProviderClient.requestLocationUpdates")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: ⚠️ requestLocationUpdates hook failed: ${e.message}")
            }

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: FusedLocationProviderClient not found (app may not use Google location services)")
        }
    }

    // ========================================
    // LocationResult Hook
    // ========================================
    private fun hookLocationResult(classLoader: ClassLoader) {
        try {
            val lrClass = XposedHelpers.findClass(
                "com.google.android.gms.location.LocationResult", classLoader
            )

            // getLocations() → 返回包含伪造位置的列表
            try {
                XposedHelpers.findAndHookMethod(
                    lrClass, "getLocations",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (!config.enabled) return

                            @Suppress("UNCHECKED_CAST")
                            val original = param.result as? List<Any> ?: return
                            if (original.isEmpty()) return

                            // 替换列表中每个Location的坐标
                            for (loc in original) {
                                try {
                                    XposedHelpers.callMethod(loc, "setLatitude", config.latitude)
                                    XposedHelpers.callMethod(loc, "setLongitude", config.longitude)
                                    XposedHelpers.callMethod(loc, "setAccuracy", config.accuracy)
                                    XposedHelpers.callMethod(loc, "setAltitude", config.altitude)

                                    // setMock(false)
                                    try {
                                        XposedHelpers.callMethod(loc, "setMock", false)
                                    } catch (e: Throwable) {}
                                } catch (e: Throwable) {}
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: ✅ Hooked LocationResult.getLocations")
            } catch (e: Throwable) {}

            // getLocation() → 返回伪造位置
            try {
                XposedHelpers.findAndHookMethod(
                    lrClass, "getLastLocation",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (!config.enabled) return

                            val loc = param.result ?: return
                            try {
                                XposedHelpers.callMethod(loc, "setLatitude", config.latitude)
                                XposedHelpers.callMethod(loc, "setLongitude", config.longitude)
                                XposedHelpers.callMethod(loc, "setAccuracy", config.accuracy)
                                XposedHelpers.callMethod(loc, "setAltitude", config.altitude)
                                try { XposedHelpers.callMethod(loc, "setMock", false) } catch (e: Throwable) {}
                            } catch (e: Throwable) {}
                        }
                    }
                )
                XposedBridge.log("$TAG: ✅ Hooked LocationResult.getLastLocation")
            } catch (e: Throwable) {}

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: LocationResult not found")
        }
    }
}
