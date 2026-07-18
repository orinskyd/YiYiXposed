package com.yiyi.xposed.hooks

import android.os.Debug
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.yiyi.xposed.util.PrefsHelper
import java.io.File

/**
 * 反检测Hook
 * 隐藏VirtualApp和Xposed框架的特征
 * 绕过钉钉的安全检测机制
 *
 * 钉钉检测项：
 * 1. 检查已安装应用列表中是否含VA/Xposed相关包名
 * 2. 检查文件系统中是否有VA特征文件
 * 3. 检查是否处于调试模式
 * 4. 检查Root状态
 * 5. 检查系统属性中是否有hook框架特征
 */
object AntiDetectHook {

    private const val TAG = "YiYi-AntiDetect"

    // 需要隐藏的包名列表
    private val hiddenPackages = setOf(
        "com.yiyi.xposed",
        "com.yiyi.mock",
        "com.ningning.mock",
        // VA框架相关
        "io.virtualapp",
        "com.lody.virtual",
        "com.github.android",
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        // 常见hook工具
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "me.weishu.kernelsu"
    )

    // 需要隐藏的文件路径特征
    private val hiddenPaths = setOf(
        "/data/data/io.virtualapp",
        "/data/data/com.lody.virtual",
        "/data/data/de.robv.android.xposed.installer",
        "/data/data/org.lsposed.manager",
        "/data/app/io.virtualapp",
        "/data/app/com.lody.virtual",
        "/sbin/.magisk",
        "/system/xbin/su",
        "/system/bin/su",
        "/data/local/su",
        "/system/app/Superuser.apk",
        "/data/data/com.topjohnwu.magisk",
        "/data/adb/magisk",
        "/data/adb/modules"
    )

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookPackageManager(lpparam.classLoader)
        hookFileCheck(lpparam.classLoader)
        hookDebugCheck(lpparam.classLoader)
        hookSystemProperties(lpparam.classLoader)
        hookRuntimeExec(lpparam.classLoader)
        hookStackTrace(lpparam.classLoader)
    }

    // ========================================
    // 1. 隐藏VA/Xposed包名
    // ========================================
    private fun hookPackageManager(classLoader: ClassLoader) {
        try {
            val pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader)

            // getInstalledPackages() → 过滤掉VA/Xposed相关包名
            try {
                XposedHelpers.findAndHookMethod(
                    pmClass, "getInstalledPackages",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            @Suppress("UNCHECKED_CAST")
                            val packages = param.result as? MutableList<Any> ?: return
                            packages.removeAll { pkg ->
                                val pkgName = try {
                                    XposedHelpers.callMethod(pkg, "packageName") as? String
                                } catch (e: Throwable) { null }
                                pkgName in hiddenPackages
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            // getInstalledApplications() → 同上
            try {
                XposedHelpers.findAndHookMethod(
                    pmClass, "getInstalledApplications",
                    Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            @Suppress("UNCHECKED_CALL")
                            val apps = param.result as? MutableList<Any> ?: return
                            apps.removeAll { app ->
                                val pkgName = try {
                                    XposedHelpers.callMethod(app, "packageName") as? String
                                } catch (e: Throwable) { null }
                                pkgName in hiddenPackages
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            // getPackageInfo() → 对VA/Xposed包名返回NameNotFoundException
            try {
                val nameNotFoundExceptionClass = XposedHelpers.findClass(
                    "android.content.pm.PackageManager\$NameNotFoundException", classLoader
                )
                XposedHelpers.findAndHookMethod(
                    pmClass, "getPackageInfo",
                    String::class.java, Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkgName = param.args[0] as? String ?: return
                            if (pkgName in hiddenPackages) {
                                throw XposedHelpers.newInstance(nameNotFoundExceptionClass, pkgName) as Throwable as Throwable
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            // getApplicationInfo() → 同上
            try {
                val nameNotFoundExceptionClass = XposedHelpers.findClass(
                    "android.content.pm.PackageManager\$NameNotFoundException", classLoader
                )
                XposedHelpers.findAndHookMethod(
                    pmClass, "getApplicationInfo",
                    String::class.java, Integer::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val pkgName = param.args[0] as? String ?: return
                            if (pkgName in hiddenPackages) {
                                throw XposedHelpers.newInstance(nameNotFoundExceptionClass, pkgName) as Throwable as Throwable
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ PackageManager hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ PackageManager hook failed: ${e.message}")
        }
    }

    // ========================================
    // 2. 隐藏VA特征文件
    // ========================================
    private fun hookFileCheck(classLoader: ClassLoader) {
        try {
            // File.exists() → 对VA特征文件返回false
            XposedHelpers.findAndHookMethod(
                File::class.java, "exists",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val file = param.thisObject as File
                        val path = file.absolutePath

                        for (hiddenPath in hiddenPaths) {
                            if (path.contains(hiddenPath) || path.startsWith(hiddenPath)) {
                                param.result = false
                                return
                            }
                        }

                        // 检查xposed相关文件
                        if (path.contains("xposed") || path.contains("XposedBridge")) {
                            param.result = false
                            return
                        }

                        // 检查VA相关文件
                        if (path.contains("virtualapp") || path.contains("lody")) {
                            param.result = false
                            return
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ File.exists hook installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ File.exists hook failed: ${e.message}")
        }
    }

    // ========================================
    // 3. 隐藏调试状态
    // ========================================
    private fun hookDebugCheck(classLoader: ClassLoader) {
        try {
            // Debug.isDebuggerConnected() → false
            XposedHelpers.findAndHookMethod(
                Debug::class.java, "isDebuggerConnected",
                XC_MethodReplacement.returnConstant(false)
            )

            // Debug.waitForDebugger() → 空操作
            try {
                XposedHelpers.findAndHookMethod(
                    Debug::class.java, "waitForDebugger",
                    XC_MethodReplacement.returnConstant(null)
                )
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ Debug hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ Debug hook failed: ${e.message}")
        }
    }

    // ========================================
    // 4. 隐藏系统属性中的hook特征
    // ========================================
    private fun hookSystemProperties(classLoader: ClassLoader) {
        try {
            val spClass = XposedHelpers.findClass("android.os.SystemProperties", classLoader)

            // get(String) → 过滤xposed/va相关属性
            try {
                XposedHelpers.findAndHookMethod(
                    spClass, "get",
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val key = param.args[0] as? String ?: return
                            val result = param.result as? String ?: return

                            // 隐藏xposed相关属性
                            if (key.contains("xposed") || key.contains("vapp")) {
                                param.result = ""
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            // get(String, String) → 同上
            try {
                XposedHelpers.findAndHookMethod(
                    spClass, "get",
                    String::class.java, String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val key = param.args[0] as? String ?: return
                            if (key.contains("xposed") || key.contains("vapp")) {
                                param.result = param.args[1] // 返回默认值
                            }
                        }
                    }
                )
            } catch (e: Throwable) {}

            XposedBridge.log("$TAG: ✅ SystemProperties hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ SystemProperties hook failed: ${e.message}")
        }
    }

    // ========================================
    // 5. 拦截Runtime.exec - 隐藏su/root检测
    // ========================================
    private fun hookRuntimeExec(classLoader: ClassLoader) {
        try {
            // Runtime.exec(String) → 对su相关命令返回异常
            XposedHelpers.findAndHookMethod(
                Runtime::class.java, "exec",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cmd = param.args[0] as? String ?: return
                        if (cmd.contains("su") || cmd.contains("which su") || cmd.contains("magisk")) {
                            // 抛出IOException模拟su不存在
                            val ioExceptionClass = XposedHelpers.findClass("java.io.IOException", classLoader)
                            throw XposedHelpers.newInstance(ioExceptionClass, "No such file or directory") as Throwable as Throwable
                        }
                    }
                }
            )

            // Runtime.exec(String[])
            XposedHelpers.findAndHookMethod(
                Runtime::class.java, "exec",
                Array<String>::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val cmds = param.args[0] as? Array<String> ?: return
                        for (cmd in cmds) {
                            if (cmd.contains("su") || cmd.contains("magisk")) {
                                val ioExceptionClass = XposedHelpers.findClass("java.io.IOException", classLoader)
                                throw XposedHelpers.newInstance(ioExceptionClass, "No such file or directory") as Throwable as Throwable
                            }
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: ✅ Runtime.exec hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ Runtime.exec hook failed: ${e.message}")
        }
    }

    // ========================================
    // 6. 清理调用栈中的Xposed痕迹
    // ========================================
    private fun hookStackTrace(classLoader: ClassLoader) {
        try {
            // Throwable.getStackTrace() → 过滤Xposed相关栈帧
            XposedHelpers.findAndHookMethod(
                Throwable::class.java, "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val stackTrace = param.result as? Array<Any> ?: return
                        val filtered = stackTrace.filter { frame ->
                            val className = try {
                                XposedHelpers.callMethod(frame, "getClassName") as? String ?: ""
                            } catch (e: Throwable) { "" }

                            // 过滤Xposed/VirtualApp相关栈帧
                            !className.contains("de.robv.android.xposed") &&
                            !className.contains("com.lody") &&
                            !className.contains("io.virtualapp") &&
                            !className.contains("com.yiyi.xposed.hooks")
                        }.toTypedArray()

                        param.result = filtered
                    }
                }
            )
            XposedBridge.log("$TAG: ✅ StackTrace hook installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ❌ StackTrace hook failed: ${e.message}")
        }
    }
}
