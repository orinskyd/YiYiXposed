package de.robv.android.xposed;

import java.io.File;

/**
 * Xposed API stub - compile time only
 * Real implementation provided by Xposed/LSPosed/VA at runtime
 *
 * Used to read SharedPreferences written by the Xposed module's UI app
 * from within the hooked target app's process.
 */
public class XSharedPreferences {

    public File file;

    public XSharedPreferences(String packageName, String prefName) {}

    public XSharedPreferences(String packageName) {}

    public void makeWorldReadable() {}

    public void reload() {}

    public File getFile() {
        return file;
    }

    public boolean getBoolean(String key, boolean defValue) {
        return defValue;
    }

    public int getInt(String key, int defValue) {
        return defValue;
    }

    public long getLong(String key, long defValue) {
        return defValue;
    }

    public float getFloat(String key, float defValue) {
        return defValue;
    }

    public String getString(String key, String defValue) {
        return defValue;
    }

    public boolean contains(String key) {
        return false;
    }

    public java.util.Map<String, ?> getAll() {
        return new java.util.HashMap<>();
    }
}
