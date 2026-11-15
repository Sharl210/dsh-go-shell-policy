package com.dshgo.shellpol;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Replaces only the shell confirmation callback; unresolved shapes keep native behavior. */
public final class ExecuteHook extends XC_MethodHook {
    private final Object allowOnce;
    private final Class<?> callback;
    private final Method callbackMethod;
    public ExecuteHook(Object allowOnce, Class<?> callback, Method callbackMethod) {
        this.allowOnce = allowOnce;
        this.callback = callback;
        this.callbackMethod = callbackMethod;
    }

    @Override protected void beforeHookedMethod(MethodHookParam param) {
        try {
            if (param.args == null || param.args.length != 5 || !(param.args[1] instanceof String)) return;
            String command = (String) param.args[1];
            Object original = param.args[4];
            if (original == null) return;
            Context context = HookEntry.findTargetContext(param.thisObject);
            int mode = HookEntry.readMode(context);
            if (PolicyLogic.shouldAsk(mode, command)) {
                if (mode == HookEntry.MODE_DANGER)
                    XposedBridge.log(HookEntry.TAG + ": danger_prompt rule=" + DangerCommands.matchedRule(command));
                return;
            }
            if (!callback.isInstance(original)) {
                XposedBridge.log(HookEntry.TAG + ": callback no longer implements resolved interface");
                return;
            }
            param.args[4] = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                    new ConfirmDecision(original, allowOnce, callbackMethod));
            XposedBridge.log(HookEntry.TAG + ": callback_wrapped mode=" + mode + " command_bytes="
                    + command.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        } catch (Throwable t) {
            XposedBridge.log(HookEntry.TAG + ": callback replacement failed; native path retained: " + t);
        }
    }

    private static final class ConfirmDecision implements InvocationHandler {
        private final Object original;
        private final Object allowOnce;
        private final Method callbackMethod;
        ConfirmDecision(Object original, Object allowOnce, Method callbackMethod) {
            this.original = original; this.allowOnce = allowOnce; this.callbackMethod = callbackMethod;
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    case "toString": return "ShellPolicyDecisionProxy";
                    default: break;
                }
            }
            if (method.equals(callbackMethod)) {
                // Preserve the original backend/UID boundary before authorizing this callback.
                String backend = args != null && args.length == 2 && args[0] instanceof String ? (String)args[0] : "";
                int uid = args != null && args.length == 2 && args[1] instanceof Integer ? (Integer)args[1] : -1;
                if (PolicyLogic.validIdentity(backend, uid)) {
                    XposedBridge.log(HookEntry.TAG + ": decision_auto_allow backend=" + backend + " uid=" + uid);
                    return allowOnce;
                }
            }
            try { method.setAccessible(true); return method.invoke(original, args); }
            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        }
    }
}
