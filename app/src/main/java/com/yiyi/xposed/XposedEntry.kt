package com.yiyi.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.hooks.*

/**
 * Xposed模块入口点
 * 当目标App被加载时，此类的handleLoadPackage方法会被调用
 *
 * 在VirtualApp的Xposed设置中启用本模块后：
 * - 目标App(钉钉等)启动时 → 安装所有定位hook
 * - 系统框架(android)启动时 → 安装系统级hook
 */
class XposedEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "YiYi-Entry"

        // 目标App包名列表
        private val targetPackages = setOf(
            "com.alibaba.android.rimet",   // 钉钉
            "com.tencent.wework",          // 企业微信
            "com.tencent.mobileqq",        // QQ
            "android",                      // 系统框架
            "com.android.phone"             // 电话进程
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName

        // 只对目标App进行hook
        if (packageName !in targetPackages) return

        XposedBridge.log("$TAG: === 依依助手开始Hook: $packageName ===")

        try {
            // 1. 核心：Android系统定位API hook
            LocationHook.hook(lpparam)
            XposedBridge.log("$TAG: ✅ LocationHook installed")

            // 2. Google Play Services定位 hook
            GoogleLocationHook.hook(lpparam)
            XposedBridge.log("$TAG: ✅ GoogleLocationHook installed")

            // 3. 高德地图定位 hook
            AMapHook.hook(lpparam)
            XposedBridge.log("$TAG: ✅ AMapHook installed")

            // 4. 百度地图定位 hook
            BaiduHook.hook(lpparam)
            XposedBridge.log("$TAG: ✅ BaiduHook installed")

            // 5. WiFi/基站 hook
            WifiCellHook.hook(lpparam)
            XposedBridge.log("$TAG: ✅ WifiCellHook installed")

            // 6. GNSS卫星状态 hook
            GnssHook.hook(lpparam)
            XposedBridge.log("$TAG: ✅ GnssHook installed")

            // 7. 反检测 hook (仅对应用层，不对系统框架)
            if (packageName != "android" && packageName != "com.android.phone") {
                AntiDetectHook.hook(lpparam)
                XposedBridge.log("$TAG: ✅ AntiDetectHook installed")
            }

            XposedBridge.log("$TAG: === 所有Hook安装完成: $packageName ===")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ Hook安装失败: ${e.message}")
            XposedBridge.log(e)
        }
    }
}
