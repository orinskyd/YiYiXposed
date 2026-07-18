package de.robv.android.xposed;

/**
 * Xposed API stub - compile time only
 * Real implementation provided by Xposed/LSPosed/VA at runtime
 */
public abstract class XC_MethodHook {

    public class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;

        public void setResult(Object result) {
            this.result = result;
        }

        public Object getResult() {
            return result;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }

    public class Unhook {
        public void unhook() {}
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
