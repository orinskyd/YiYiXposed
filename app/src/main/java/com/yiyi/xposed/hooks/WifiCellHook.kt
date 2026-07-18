package com.yiyi.xposed.hooks

import android.annotation.SuppressLint
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper
import java.util.UUID

/**
 * WiFi和基站信息Hook
 * 钉钉通过WiFi BSSID和基站信息交叉验证定位真实性
 * 需要伪造WiFi扫描结果和基站信息
 */
object WifiCellHook {

    private const val TAG = "YiYi-WifiCellHook"

    // 伪造的WiFi BSSID列表
    private val fakeBssids = listOf(
        "00:1A:2B:3C:4D:5E",
        "00:1A:2B:3C:4D:5F",
        "00:1A:2B:3C:4D:60",
        "AC:DE:48:00:11:22",
        "AC:DE:48:00:11:23"
    )

    // 伪造的WiFi SSID
    private const val FAKE_SSID = "ChinaNet-Home"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookWifiManager(lpparam.classLoader)
        hookTelephonyManager(lpparam.classLoader)
    }

    // ========================================
    // WifiManager Hook
    // ========================================
    @SuppressLint("NewApi")
    private fun hookWifiManager(classLoader: ClassLoader) {
        try {
            val wmClass = XposedHelpers.findClass("android.net.wifi.WifiManager", classLoader)

            // getScanResults() → 返回伪造的WiFi扫描结果
            try {
                XposedHelpers.findAndHookMethod(wmClass, "getScanResults", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled || !config.wifiSpoof) return

                        @Suppress("UNCHECKED_CAST")
                        val original = param.result as? MutableList<Any> ?: mutableListOf()

                        // 如果有原始结果，修改BSSID；否则创建伪造结果
                        if (original.isNotEmpty()) {
                            for (i in original.indices) {
                                val scanResult = original[i]
                                try {
                                    val bssid = fakeBssids[i % fakeBssids.size]
                                    XposedHelpers.callMethod(scanResult, "BSSID", bssid)

                                    // 修改SSID
                                    try {
                                        val ssidField = scanResult.javaClass.getDeclaredField("SSID")
                                        ssidField.isAccessible = true
                                        ssidField.set(scanResult, FAKE_SSID)
                                    } catch (e: Throwable) {}

                                    // 修改信号强度
                                    try {
                                        val levelField = scanResult.javaClass.getDeclaredField("level")
                                        levelField.isAccessible = true
                                        levelField.set(scanResult, -50 - (Math.random() * 20).toInt())
                                    } catch (e: Throwable) {}
                                } catch (e: Throwable) {}
                            }
                        } else {
                            // 创建伪造的ScanResult
                            try {
                                val srClass = XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader)
                                for (i in 0 until 3) {
                                    val sr = srClass.getDeclaredConstructor().newInstance()
                                    try {
                                        val bssidField = sr.javaClass.getDeclaredField("BSSID")
                                        bssidField.isAccessible = true
                                        bssidField.set(sr, fakeBssids[i])

                                        val ssidField = sr.javaClass.getDeclaredField("SSID")
                                        ssidField.isAccessible = true
                                        ssidField.set(sr, FAKE_SSID)

                                        val levelField = sr.javaClass.getDeclaredField("level")
                                        levelField.isAccessible = true
                                        levelField.set(sr, -50 - i * 5)
                                    } catch (e: Throwable) {}
                                    original.add(sr)
                                }
                                param.result = original
                            } catch (e: Throwable) {
                                XposedBridge.log("$TAG: create fake ScanResult failed: ${e.message}")
                            }
                        }
                    }
                })
                XposedBridge.log("$TAG: ✅ Hooked getScanResults")
            } catch (e: Throwable) {}

            // getConnectionInfo() → 修改WiFi连接信息
            try {
                XposedHelpers.findAndHookMethod(wmClass, "getConnectionInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled || !config.wifiSpoof) return

                        val wifiInfo = param.result ?: return
                        try {
                            // 修改BSSID
                            val bssidField = wifiInfo.javaClass.getDeclaredField("mBSSID")
                            bssidField.isAccessible = true
                            bssidField.set(wifiInfo, fakeBssids[0])
                        } catch (e: Throwable) {
                            try {
                                XposedHelpers.callMethod(wifiInfo, "setBSSID", fakeBssids[0])
                            } catch (e2: Throwable) {}
                        }

                        try {
                            // 修改SSID
                            val ssidField = wifiInfo.javaClass.getDeclaredField("mWifiSsid")
                            ssidField.isAccessible = true
                            val wifiSsidClass = XposedHelpers.findClass("android.net.wifi.WifiSsid", classLoader)
                            val fromUtf8Text = wifiSsidClass.getMethod("fromUtf8Text", String::class.java)
                            ssidField.set(wifiInfo, fromUtf8Text.invoke(null, FAKE_SSID))
                        } catch (e: Throwable) {}
                    }
                })
                XposedBridge.log("$TAG: ✅ Hooked getConnectionInfo")
            } catch (e: Throwable) {}

            // startScan() → 返回true
            try {
                XposedHelpers.findAndHookMethod(wmClass, "startScan", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (PrefsHelper.load().enabled) param.result = true
                    }
                })
            } catch (e: Throwable) {}

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: WifiManager not found")
        }
    }

    // ========================================
    // TelephonyManager Hook - 基站信息
    // ========================================
    @SuppressLint("NewApi")
    private fun hookTelephonyManager(classLoader: ClassLoader) {
        try {
            val tmClass = XposedHelpers.findClass("android.telephony.TelephonyManager", classLoader)

            // getCellLocation() → 返回伪造基站
            try {
                XposedHelpers.findAndHookMethod(tmClass, "getCellLocation", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled || !config.cellSpoof) return

                        try {
                            val gsmCellLocClass = XposedHelpers.findClass(
                                "android.telephony.gsm.GsmCellLocation", classLoader
                            )
                            val gsmLoc = gsmCellLocClass.getDeclaredConstructor().newInstance()
                            XposedHelpers.callMethod(gsmLoc, "setLacAndCid", 5009, 1285)
                            param.result = gsmLoc
                        } catch (e: Throwable) {
                            XposedBridge.log("$TAG: getCellLocation fake failed: ${e.message}")
                        }
                    }
                })
                XposedBridge.log("$TAG: ✅ Hooked getCellLocation")
            } catch (e: Throwable) {}

            // getAllCellInfo() → 返回伪造基站列表
            try {
                XposedHelpers.findAndHookMethod(tmClass, "getAllCellInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (!config.enabled || !config.cellSpoof) return

                        @Suppress("UNCHECKED_CAST")
                        val original = param.result as? MutableList<Any> ?: mutableListOf()
                        // 保留原始列表，只修改cellId和lac
                        for (cellInfo in original) {
                            try {
                                val cellInfoClass = cellInfo.javaClass
                                val cellIdentityField = cellInfoClass.getDeclaredField("mCellIdentity")
                                cellIdentityField.isAccessible = true
                                val cellIdentity = cellIdentityField.get(cellInfo)

                                // 设置合理的基站参数
                                try { XposedHelpers.callMethod(cellIdentity, "setMcc", 460) } catch (e: Throwable) {}
                                try { XposedHelpers.callMethod(cellIdentity, "setMnc", 0) } catch (e: Throwable) {}
                            } catch (e: Throwable) {}
                        }
                    }
                })
                XposedBridge.log("$TAG: ✅ Hooked getAllCellInfo")
            } catch (e: Throwable) {}

            // getNeighboringCellInfo() → 返回空列表（避免暴露真实基站）
            try {
                XposedHelpers.findAndHookMethod(tmClass, "getNeighboringCellInfo", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = PrefsHelper.load()
                        if (config.enabled && config.cellSpoof) {
                            param.result = emptyList<Any>()
                        }
                    }
                })
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ TelephonyManager hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: TelephonyManager not found")
        }
    }
}
