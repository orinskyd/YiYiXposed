package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Xposed API stub - compile time only
 * Real implementation provided by Xposed/LSPosed/VA at runtime
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(LoadPackageParam lpparam);
}
