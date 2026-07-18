package com.yiyi.xposed.hooks

import android.annotation.SuppressLint
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper

/**
 * GNSS（全球导航卫星系统）状态Hook
 * 钉钉通过检查GPS卫星状态来验证定位真实性
 * 需要伪造卫星数量、信号强度等信息
 *
 * Hook点：
 * 1. GnssStatus.getSatelliteCount() → 返回合理数量(8-12)
 * 2. GnssStatus.getCn0DbHz() → 返回合理信号强度
 * 3. GnssStatus.usedInFix() → 返回true
 * 4. GnssStatus.getElevation/ getAzimuth → 返回合理角度
 * 5. LocationManager.addGnssStatusListener → 拦截回调
 */
object GnssHook {

    private const val TAG = "YiYi-GnssHook"

    // 伪造的卫星参数
    private const val SATELLITE_COUNT = 12
    private val usedInFix = booleanArrayOf(
        true, true, true, true, true, true, true, true, false, false, false, false
    )
    private val cn0DbHz = floatArrayOf(
        30.0f, 28.0f, 25.0f, 32.0f, 27.0f, 24.0f, 29.0f, 26.0f, 20.0f, 18.0f, 22.0f, 19.0f
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookGnssStatus(lpparam.classLoader)
        hookGnssStatusCallback(lpparam.classLoader)
    }

    // ========================================
    // GnssStatus Hook (API 24+)
    // ========================================
    @SuppressLint("NewApi")
    private fun hookGnssStatus(classLoader: ClassLoader) {
        try {
            val gnssClass = XposedHelpers.findClass("android.location.GnssStatus", classLoader)

            // getSatelliteCount() → 返回12
            try {
                XposedHelpers.findAndHookMethod(gnssClass, "getSatelliteCount", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled && config.gnssSpoof) {
                            param.result = SATELLITE_COUNT
                        }
                    }
                })
                XposedBridge.log("$TAG: ✅ Hooked GnssStatus.getSatelliteCount")
            } catch (e: Throwable) {}

            // usedInFix(int) → 返回伪造值
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "usedInFix",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) {
                                val idx = param.args[0] as Int
                                param.result = idx < SATELLITE_COUNT && usedInFix[idx]
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: ✅ Hooked GnssStatus.usedInFix")
            } catch (e: Throwable) {}

            // getCn0DbHz(int) → 返回伪造信号强度
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "getCn0DbHz",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) {
                                val idx = param.args[0] as Int
                                param.result = if (idx < SATELLITE_COUNT) cn0DbHz[idx] else 15.0f
                            }
                        }
                    }
                )
                XposedBridge.log("$TAG: ✅ Hooked GnssStatus.getCn0DbHz")
            } catch (e: Throwable) {}

            // getElevationDegrees(int) → 返回合理仰角
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "getElevationDegrees",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) {
                                val idx = param.args[0] as Int
                                // 15-75度仰角
                                param.result = 15.0f + (idx * 60.0f / SATELLITE_COUNT)
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            // getAzimuthDegrees(int) → 返回合理方位角
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "getAzimuthDegrees",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) {
                                val idx = param.args[0] as Int
                                // 0-360度均匀分布
                                param.result = (idx * 360.0f / SATELLITE_COUNT)
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            // hasCarrierFrequencyHz(int) → true
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "hasCarrierFrequencyHz",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) param.result = true
                        }
                    }
                )
            } catch (e: Throwable) {}

            // getCarrierFrequencyHz(int) → 返回L1频率(1575.42MHz)
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "getCarrierFrequencyHz",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) param.result = 1575420000.0f
                        }
                    }
                )
            } catch (e: Throwable) {}

            // getConstellationType(int) → 1 (GPS)
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "getConstellationType",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) param.result = 1
                        }
                    }
                )
            } catch (e: Throwable) {}

            // hasAlmanacData(int) → true
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "hasAlmanacData",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) param.result = true
                        }
                    }
                )
            } catch (e: Throwable) {}

            // hasEphemerisData(int) → true
            try {
                XposedHelpers.findAndHookMethod(
                    gnssClass, "hasEphemerisData",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val config = PrefsHelper.load()
                            if (config.enabled && config.gnssSpoof) param.result = true
                        }
                    }
                )
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ GnssStatus hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: GnssStatus not found")
        }
    }

    // ========================================
    // GnssStatus.Callback Hook
    // 拦截卫星状态回调，确保回调数据一致
    // ========================================
    private fun hookGnssStatusCallback(classLoader: ClassLoader) {
        // GnssStatus.Callback 使用上面已安装的 GnssStatus hooks
        // 无需额外hook - Callback接收的GnssStatus对象已被hookGnssStatus()拦截
        XposedBridge.log("$TAG: ✅ GnssStatus.Callback uses GnssStatus hooks (no additional hooking needed)")
    }
}
