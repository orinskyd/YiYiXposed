package com.yiyi.xposed.hooks

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper
import com.yiyi.xposed.model.FakeLocationConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * 核心定位Hook
 * 拦截Android系统LocationManager和Location的所有相关API
 *
 * 关键Hook点：
 * 1. Location.isFromMockProvider() → false （最重要！绕过钉钉的mock检测）
 * 2. Location.isMock() → false (Android 12+)
 * 3. LocationManager.getLastKnownLocation() → 返回伪造位置
 * 4. LocationManager.getCurrentLocation() → 返回伪造位置
 * 5. LocationManager.requestLocationUpdates() → 持续投递伪造位置
 * 6. LocationManager.isProviderEnabled() → GPS/NETWORK始终可用
 */
object LocationHook {

    private const val TAG = "YiYi-LocationHook"

    // 用于持续推送伪造位置的HandlerThread
    private var pushThread: HandlerThread? = null
    private var pushHandler: Handler? = null

    // 存储活跃的LocationListener，用于持续推送
    private val activeListeners = ConcurrentHashMap<String, MutableList<LocationListener>>()
    private val activePendingIntents = mutableListOf<Any>()

    // 当前配置缓存
    @Volatile
    private var currentConfig: FakeLocationConfig? = null

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: 开始Hook定位API，目标包名: ${lpparam.packageName}")

        hookLocationMockDetection(lpparam.classLoader)
        hookLocationGetters(lpparam.classLoader)
        hookLocationManager(lpparam.classLoader)

        // 启动后台推送线程
        ensurePushThread()
    }

    // ========================================
    // 1. Hook mock检测标志 - 最关键！
    // ========================================
    @SuppressLint("NewApi")
    private fun hookLocationMockDetection(classLoader: ClassLoader) {
        try {
            // Location.isFromMockProvider() → false (API 18+)
            XposedHelpers.findAndHookMethod(
                Location::class.java, "isFromMockProvider",
                XC_MethodReplacement.returnConstant(false)
            )
            XposedBridge.log("$TAG: ✅ Hooked Location.isFromMockProvider()")

            // Location.isMock() → false (API 31+ / Android 12)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                XposedHelpers.findAndHookMethod(
                    Location::class.java, "isMock",
                    XC_MethodReplacement.returnConstant(false)
                )
                XposedBridge.log("$TAG: ✅ Hooked Location.isMock()")
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookLocationMockDetection failed: ${e.message}")
        }
    }

    // ========================================
    // 2. Hook Location getter方法
    //    在Location对象被返回给App时修改其坐标
    // ========================================
    private fun hookLocationGetters(classLoader: ClassLoader) {
        try {
            // 标记方法：检查当前Location是否需要替换坐标
            // 只在LocationManager返回的Location上修改，避免影响系统内部逻辑
            val shouldSpoof = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val config = getConfig()
                    if (!config.enabled) return
                    if (param.thisObject !is Location) return

                    val loc = param.thisObject as Location
                    // 只修改从GPS/NETWORK provider返回的位置
                    val provider = loc.provider ?: return
                    if (provider == "gps" || provider == "network" || provider == "fused" || provider == "passive") {
                        spoofLocation(loc, config)
                    }
                }
            }

            // Hook getLatitude/getLongitude
            // 注意：不全局hook这些getter，因为可能影响系统内部逻辑
            // 而是在LocationManager返回的Location上直接修改值

        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookLocationGetters failed: ${e.message}")
        }
    }

    // ========================================
    // 3. Hook LocationManager - 核心定位服务
    // ========================================
    private fun hookLocationManager(classLoader: ClassLoader) {
        try {
            val lmClass = XposedHelpers.findClass("android.location.LocationManager", classLoader)

            // --- getLastKnownLocation(String provider) → 返回伪造Location ---
            hookGetLastKnownLocation(lmClass)

            // --- getCurrentLocation (API 30+) → 返回伪造Location ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookGetCurrentLocation(lmClass)
            }

            // --- requestLocationUpdates (多个重载) → 持续投递伪造位置 ---
            hookRequestLocationUpdates(lmClass)

            // --- isProviderEnabled → GPS/NETWORK始终返回true ---
            hookIsProviderEnabled(lmClass)

            // --- getProviders → 返回正常provider列表 ---
            hookGetProviders(lmClass)

            // --- addGnssStatusListener / registerGnssStatusCallback → 拦截卫星状态 ---
            // 在GnssHook中处理

            XposedBridge.log("$TAG: ✅ LocationManager hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookLocationManager failed: ${e.message}")
        }
    }

    // ========================================
    // getLastKnownLocation Hook
    // ========================================
    private fun hookGetLastKnownLocation(lmClass: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                lmClass, "getLastKnownLocation",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return

                        val provider = param.args[0] as String
                        val fakeLocation = config.createFakeLocation(provider)
                        param.result = fakeLocation
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked getLastKnownLocation")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookGetLastKnownLocation failed: ${e.message}")
        }
    }

    // ========================================
    // getCurrentLocation Hook (API 30+)
    // ========================================
    private fun hookGetCurrentLocation(lmClass: Class<*>) {
        try {
            // getCurrentLocation(String, CancellationSignal, Executor, Consumer)
            val consumerClass = XposedHelpers.findClass("java.util.function.Consumer", lmClass.classLoader)
            val executorClass = XposedHelpers.findClass("java.util.concurrent.Executor", lmClass.classLoader)
            val cancellationClass = XposedHelpers.findClass("android.os.CancellationSignal", lmClass.classLoader)

            XposedHelpers.findAndHookMethod(
                lmClass, "getCurrentLocation",
                String::class.java, cancellationClass, executorClass, consumerClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return

                        val provider = param.args[0] as String
                        val consumer = param.args[3] ?: return

                        val fakeLocation = config.createFakeLocation(provider)
                        // 调用Consumer.accept(fakeLocation)
                        XposedHelpers.callMethod(consumer, "accept", fakeLocation)
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked getCurrentLocation")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookGetCurrentLocation failed: ${e.message}")
        }
    }

    // ========================================
    // requestLocationUpdates Hook
    // 拦截所有重载版本，持续投递伪造位置
    // ========================================
    @SuppressLint("NewApi")
    private fun hookRequestLocationUpdates(lmClass: Class<*>) {
        // 版本1: requestLocationUpdates(String, long, float, LocationListener)
        hookRLU_v1(lmClass)

        // 版本2: requestLocationUpdates(String, long, float, LocationListener, Looper)
        hookRLU_v2(lmClass)

        // 版本3: requestLocationUpdates(String, long, float, PendingIntent)
        hookRLU_v3(lmClass)

        // 版本4: requestLocationUpdates(long, Criteria, float, LocationListener) (deprecated)
        hookRLU_v4(lmClass)

        // 版本5: requestLocationUpdates(long, Criteria, float, PendingIntent) (deprecated)
        hookRLU_v5(lmClass)

        // removeUpdates - 清理listener
        hookRemoveUpdates(lmClass)
    }

    private fun hookRLU_v1(lmClass: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                lmClass, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                LocationListener::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return

                        val provider = param.args[0] as String
                        val listener = param.args[3] as? LocationListener ?: return

                        // 立即投递一次伪造位置
                        val fakeLocation = config.createFakeLocation(provider)
                        try {
                            listener.onLocationChanged(fakeLocation)
                        } catch (e: Throwable) {
                            XposedBridge.log("$TAG: listener.onLocationChanged failed: ${e.message}")
                        }

                        // 注册持续推送
                        registerListener(provider, listener)
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked requestLocationUpdates v1 (String, long, float, LocationListener)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookRLU_v1 failed: ${e.message}")
        }
    }

    private fun hookRLU_v2(lmClass: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                lmClass, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                LocationListener::class.java, Looper::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return

                        val provider = param.args[0] as String
                        val listener = param.args[3] as? LocationListener ?: return

                        val fakeLocation = config.createFakeLocation(provider)
                        try { listener.onLocationChanged(fakeLocation) } catch (e: Throwable) {}

                        registerListener(provider, listener)
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked requestLocationUpdates v2 (String, long, float, LocationListener, Looper)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ⚠️ hookRLU_v2 not found: ${e.message}")
        }
    }

    private fun hookRLU_v3(lmClass: Class<*>) {
        try {
            val piClass = XposedHelpers.findClass("android.app.PendingIntent", lmClass.classLoader)
            XposedHelpers.findAndHookMethod(
                lmClass, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType, Float::class.javaPrimitiveType,
                piClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return
                        // PendingIntent方式暂不处理，需要send广播
                        XposedBridge.log("$TAG: requestLocationUpdates v3 (PendingIntent) - skipped")
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked requestLocationUpdates v3 (PendingIntent)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ⚠️ hookRLU_v3 not found: ${e.message}")
        }
    }

    private fun hookRLU_v4(lmClass: Class<*>) {
        try {
            val criteriaClass = XposedHelpers.findClass("android.location.Criteria", lmClass.classLoader)
            XposedHelpers.findAndHookMethod(
                lmClass, "requestLocationUpdates",
                Long::class.javaPrimitiveType, criteriaClass, Float::class.javaPrimitiveType,
                LocationListener::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return

                        val listener = param.args[3] as? LocationListener ?: return
                        val fakeLocation = config.createFakeLocation("gps")
                        try { listener.onLocationChanged(fakeLocation) } catch (e: Throwable) {}
                        registerListener("gps", listener)
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked requestLocationUpdates v4 (long, Criteria, float, LocationListener)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ⚠️ hookRLU_v4 not found: ${e.message}")
        }
    }

    private fun hookRLU_v5(lmClass: Class<*>) {
        try {
            val criteriaClass = XposedHelpers.findClass("android.location.Criteria", lmClass.classLoader)
            val piClass = XposedHelpers.findClass("android.app.PendingIntent", lmClass.classLoader)
            XposedHelpers.findAndHookMethod(
                lmClass, "requestLocationUpdates",
                Long::class.javaPrimitiveType, criteriaClass, Float::class.javaPrimitiveType,
                piClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // PendingIntent方式暂不处理
                    }
                }
            )
        } catch (e: Throwable) {
            // 这个重载可能不存在，忽略
        }
    }

    private fun hookRemoveUpdates(lmClass: Class<*>) {
        try {
            // removeUpdates(LocationListener)
            XposedHelpers.findAndHookMethod(
                lmClass, "removeUpdates",
                LocationListener::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val listener = param.args[0] as? LocationListener ?: return
                        unregisterListener(listener)
                    }
                }
            )

            // removeUpdates(PendingIntent)
            try {
                val piClass = XposedHelpers.findClass("android.app.PendingIntent", lmClass.classLoader)
                XposedHelpers.findAndHookMethod(
                    lmClass, "removeUpdates",
                    piClass,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 清理PendingIntent相关
                        }
                    }
                )
            } catch (e: Throwable) {}
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookRemoveUpdates failed: ${e.message}")
        }
    }

    // ========================================
    // isProviderEnabled Hook
    // ========================================
    private fun hookIsProviderEnabled(lmClass: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                lmClass, "isProviderEnabled",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val config = getConfig()
                        if (!config.enabled) return

                        val provider = param.args[0] as String
                        if (provider == LocationManager.GPS_PROVIDER ||
                            provider == LocationManager.NETWORK_PROVIDER ||
                            provider == LocationManager.FUSED_PROVIDER
                        ) {
                            param.result = true
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ Hooked isProviderEnabled")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookIsProviderEnabled failed: ${e.message}")
        }
    }

    // ========================================
    // getProviders Hook
    // ========================================
    private fun hookGetProviders(lmClass: Class<*>) {
        try {
            XposedHelpers.findAndHookMethod(
                lmClass, "getProviders",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // 确保返回的列表包含gps和network
                        @Suppress("UNCHECKED_CAST")
                        val result = param.result as? MutableList<String>
                        if (result != null) {
                            if (!result.contains(LocationManager.GPS_PROVIDER)) {
                                result.add(LocationManager.GPS_PROVIDER)
                            }
                            if (!result.contains(LocationManager.NETWORK_PROVIDER)) {
                                result.add(LocationManager.NETWORK_PROVIDER)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ hookGetProviders failed: ${e.message}")
        }
    }

    // ========================================
    // 工具方法
    // ========================================

    /**
     * 获取当前配置（带缓存）
     */
    private fun getConfig(): FakeLocationConfig {
        val cached = currentConfig
        if (cached != null) return cached

        val config = PrefsHelper.load()
        currentConfig = config
        return config
    }

    /**
     * 刷新配置缓存
     */
    fun refreshConfig() {
        currentConfig = PrefsHelper.load()
    }

    /**
     * 修改Location对象的坐标值
     */
    private fun spoofLocation(loc: Location, config: FakeLocationConfig) {
        val (lat, lon) = applyOffset(config)
        loc.latitude = lat
        loc.longitude = lon
        loc.altitude = config.altitude
        loc.accuracy = config.accuracy
        loc.bearing = config.bearing
        loc.speed = config.speed
        loc.time = System.currentTimeMillis()
        loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loc.verticalAccuracyMeters = config.accuracy * 1.5f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loc.setMock(false)
        }
    }

    private fun applyOffset(config: FakeLocationConfig): Pair<Double, Double> {
        if (config.randomRadius <= 0) return Pair(config.latitude, config.longitude)
        val offsetLat = (Math.random() - 0.5) * config.randomRadius / 111000.0
        val offsetLon = (Math.random() - 0.5) * config.randomRadius / 111000.0 * Math.cos(Math.toRadians(config.latitude))
        return Pair(config.latitude + offsetLat, config.longitude + offsetLon)
    }

    // ========================================
    // 持续推送机制
    // ========================================

    private fun ensurePushThread() {
        if (pushThread == null) {
            pushThread = HandlerThread("YiYiLocationPush", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY).also {
                it.start()
                pushHandler = Handler(it.looper)
            }
            // 启动周期性推送
            startPeriodicPush()
        }
    }

    private fun startPeriodicPush() {
        val handler = pushHandler ?: return
        handler.postDelayed(object : Runnable {
            override fun run() {
                pushFakeLocations()
                handler.postDelayed(this, 1000) // 每秒推送一次
            }
        }, 1000)
    }

    private fun pushFakeLocations() {
        val config = getConfig()
        if (!config.enabled) return
        if (activeListeners.isEmpty()) return

        // 刷新配置（每30秒刷新一次配置缓存）
        refreshConfig()

        for ((provider, listeners) in activeListeners) {
            val fakeLocation = config.createFakeLocation(provider)
            for (listener in listeners) {
                try {
                    listener.onLocationChanged(fakeLocation)
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: push listener failed: ${e.message}")
                }
            }
        }
    }

    private fun registerListener(provider: String, listener: LocationListener) {
        val list = activeListeners.getOrPut(provider) { mutableListOf() }
        if (!list.contains(listener)) {
            list.add(listener)
        }
    }

    private fun unregisterListener(listener: LocationListener) {
        for ((_, list) in activeListeners) {
            list.remove(listener)
        }
    }
}
