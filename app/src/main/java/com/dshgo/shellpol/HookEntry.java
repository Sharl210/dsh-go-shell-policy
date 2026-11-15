package com.dshgo.shellpol;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Resolve current-APK semantic anchors, never persisted obfuscated symbols. */
public final class HookEntry implements IXposedHookLoadPackage {
    public static final String TAG = "DSHGoShellPolicy";
    public static final String TARGET_PKG = "io.github.ccch1mneyyy.dsh_app";
    public static final String MODULE_PKG = "com.dshgo.shellpol";
    public static final String POLICY_URI = "content://com.dshgo.shellpol.policy";
    public static final String KEY_MODE = "mode";
    public static final int MODE_ASK = 0, MODE_ALLOW = 1, MODE_DANGER = 2;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET_PKG.equals(lp.packageName)) return;
        try {
            System.loadLibrary("dexkit");
            try (DexKitBridge bridge = DexKitBridge.create(lp.appInfo.sourceDir)) {
                // R8 may inline confirmationError into execute, or leave it as a callee.
                List<MethodData> gates = bridge.findMethod(new FindMethod().matcher(
                    new MethodMatcher().usingEqStrings("confirmationDenied", "confirmationUnavailable")));
                Map<String, MethodData> accepted = new LinkedHashMap<>();
                for (MethodData gate : gates) {
                    if (executeShape(gate)) accepted.put(gate.getDescriptor(), gate);
                    for (MethodData caller : gate.getCallers())
                        if (executeShape(caller)) accepted.put(caller.getDescriptor(), caller);
                }
                // Eliminate unrelated same-shape callers before requiring uniqueness.
                Iterator<MethodData> candidates = accepted.values().iterator();
                while (candidates.hasNext()) {
                    MethodData c = candidates.next();
                    Set<String> strings = new HashSet<>(c.getUsingStrings());
                    if (!strings.containsAll(Arrays.asList("start", "poll", "jobId", "command"))) candidates.remove();
                }
                XposedBridge.log(TAG + ": result_gates=" + gates.size() + " shell_candidates=" + accepted.size());
                if (accepted.size() != 1) throw new IllegalStateException("shell candidate count=" + accepted.size());
                MethodData selected = accepted.values().iterator().next();
                Method method = selected.getMethodInstance(lp.classLoader);
                Class<?>[] types = method.getParameterTypes();
                if (!types[3].isInterface() || !types[4].isInterface())
                    throw new IllegalStateException("callback parameter shapes changed");
                Method callbackMethod = callbackMethod(types[4], 2);
                callbackMethod(types[3], 0);
                Set<Class<?>> decisions = new HashSet<>();
                for (MethodData gate : gates) {
                    boolean linked = gate.getDescriptor().equals(selected.getDescriptor());
                    for (MethodData caller : gate.getCallers())
                        if (caller.getDescriptor().equals(selected.getDescriptor())) linked = true;
                    if (!linked) continue;
                    // Non-inlined confirmationError(Decision) establishes the concrete consumed type.
                    for (ClassData p : gate.getParamTypes()) tryAddDecision(decisions, p, lp.classLoader);
                    if (decisions.isEmpty()) collectLinkedEnums(gate, decisions, lp.classLoader);
                }
                if (decisions.size() != 1) throw new IllegalStateException("linked decision count=" + decisions.size());
                Class<?> decision = decisions.iterator().next();
                Object once = Enum.valueOf((Class)decision, "ALLOW_ONCE");
                XposedBridge.hookMethod(method, new ExecuteHook(once, types[4], callbackMethod));
                XposedBridge.log(TAG + ": installed " + selected.getDescriptor());
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": unresolved; original confirmation retained: " + t);
        }
    }

    private static Method callbackMethod(Class<?> type, int argc) {
        Method selected = null;
        for (Method m : type.getMethods()) {
            if (!java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
            if (m.getParameterCount() != argc || m.getReturnType() != Object.class) continue;
            if (selected != null) throw new IllegalStateException("callback method ambiguous");
            selected = m;
        }
        if (selected == null) throw new IllegalStateException("callback method missing");
        return selected;
    }

    private static void tryAddDecision(Set<Class<?>> out, ClassData data, ClassLoader loader) {
        try { addDecision(out, data.getInstance(loader)); }
        catch (Throwable t) { XposedBridge.log(TAG + ": optional enum relation skipped: " + data.getName()); }
    }

    private static void addDecision(Set<Class<?>> out, Class<?> c) {
        if (!c.isEnum()) return;
        Set<String> names = new HashSet<>();
        for (Object e : c.getEnumConstants()) names.add(((Enum<?>)e).name());
        if (names.containsAll(Arrays.asList("ALLOW_ONCE", "ALLOW_APP_FOR_TASK", "ALLOW_ALL_FOR_TASK",
                "ALLOW_VISUAL_FOR_TASK", "DENY", "STOP", "UNAVAILABLE"))) out.add(c);
    }

    private static void collectLinkedEnums(MethodData gate, Set<Class<?>> out, ClassLoader loader) throws Exception {
        for (MethodData call : gate.getInvokes()) {
            // ordinal/value inspection is a Java enum operation, not a host member name.
            if ("ordinal".equals(call.getMethodName()) || "name".equals(call.getMethodName()))
                tryAddDecision(out, call.getDeclaredClass(), loader);
        }
        for (org.luckypray.dexkit.result.UsingFieldData use : gate.getUsingFields()) {
            org.luckypray.dexkit.result.FieldData field = use.getField();
            tryAddDecision(out, field.getType(), loader);
            if (!"int[]".equals(field.getType().getName())) continue;
            // Kotlin when mappings: follow only this consumed mapping's initializer, never a global enum scan.
            for (MethodData init : field.getDeclaredClass().getMethods()) {
                if (!init.isStaticInitializer()) continue;
                for (org.luckypray.dexkit.result.UsingFieldData mapped : init.getUsingFields())
                    tryAddDecision(out, mapped.getField().getType(), loader);
            }
        }
    }

    private static boolean executeShape(MethodData m) {
        List<String> p = m.getParamTypeNames();
        return p.size() == 5 && "long".equals(p.get(0)) && "java.lang.String".equals(p.get(1))
            && "long".equals(p.get(2)) && "java.util.Map".equals(m.getReturnTypeName());
    }

    static int readMode(Context context) {
        if (context == null) return MODE_ASK;
        try {
            Bundle b = context.getContentResolver().call(Uri.parse(POLICY_URI), "readPolicy", null, null);
            if (b == null) return MODE_ASK;
            int mode = b.getInt("mode", MODE_ASK);
            return mode == MODE_ALLOW || mode == MODE_DANGER ? mode : MODE_ASK;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": config unavailable; ask retained: " + t.getClass().getSimpleName());
            return MODE_ASK;
        }
    }

    static Context findTargetContext(Object controller) {
        for (Class<?> c = controller.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Context.class.isAssignableFrom(f.getType()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Context ctx = (Context)f.get(controller);
                    if (ctx != null && TARGET_PKG.equals(ctx.getPackageName())) return ctx;
                } catch (Throwable ignored) { }
            }
        }
        return null;
    }
}
