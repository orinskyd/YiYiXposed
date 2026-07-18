package de.robv.android.xposed;

/**
 * Xposed API stub - compile time only
 * Real implementation provided by Xposed/LSPosed/VA at runtime
 */
public abstract class XC_MethodReplacement extends XC_MethodHook {

    public static XC_MethodReplacement returnConstant(final Object value) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                return value;
            }
        };
    }

    public static XC_MethodReplacement DO_NOTHING = new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            return null;
        }
    };

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        try {
            param.setResult(replaceHookedMethod(param));
        } catch (RuntimeException re) {
            param.setThrowable(re);
        } catch (Throwable t) {
            param.setThrowable(t);
        }
    }
}
