# Xposed module classes
-keep class com.yiyi.xposed.XposedEntry { *; }
-keep class com.yiyi.xposed.hooks.** { *; }
-keep class com.yiyi.xposed.model.** { *; }
-keep class com.yiyi.xposed.util.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Keep Xposed interface
-keep interface de.robv.android.xposed.IXposedHookLoadPackage
-keep interface de.robv.android.xposed.IXposedHookZygoteInit
