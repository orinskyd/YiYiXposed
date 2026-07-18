package de.robv.android.xposed.callbacks;

/**
 * Xposed API stub - compile time only
 * Real implementation provided by Xposed/LSPosed/VA at runtime
 */
public class XC_LoadPackage {

    public static class LoadPackageParam {
        public String packageName;
        public Object appInfo;
        public boolean isFirstApplication;
        public ClassLoader classLoader;
    }
}
