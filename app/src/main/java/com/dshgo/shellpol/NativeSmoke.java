package com.dshgo.shellpol;

import java.io.File;
import java.util.*;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

/** Optional read-only app_process smoke test: never launches an Activity or executes target shell commands. */
public final class NativeSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("module-apk target-apk native-cache-dir");
        System.out.println("native_loaded=" + NativeLibrary.load(args[0], new File(args[2]), null));
        try (DexKitBridge bridge = DexKitBridge.create(args[1])) {
            Map<String, MethodData> candidates = new LinkedHashMap<>();
            List<MethodData> gates = bridge.findMethod(new FindMethod().matcher(new MethodMatcher()
                    .usingEqStrings("confirmationDenied", "confirmationUnavailable")));
            for (MethodData gate : gates) {
                check(candidates, gate);
                for (MethodData caller : gate.getCallers()) check(candidates, caller);
            }
            System.out.println("result_gates=" + gates.size() + " shell_candidates=" + candidates.size());
            if (candidates.size() != 1) throw new IllegalStateException("Shell candidate not unique");
            MethodData selected = candidates.values().iterator().next();
            System.out.println("resolved=" + selected.getDescriptor());
            Set<String> enums = new HashSet<>();
            for (org.luckypray.dexkit.result.UsingFieldData use : selected.getUsingFields()) {
                org.luckypray.dexkit.result.FieldData field = use.getField();
                if (!"int[]".equals(field.getType().getName())) continue;
                for (MethodData init : field.getDeclaredClass().getMethods()) {
                    if (!init.isStaticInitializer()) continue;
                    for (org.luckypray.dexkit.result.UsingFieldData mapped : init.getUsingFields()) {
                        org.luckypray.dexkit.result.ClassData type = mapped.getField().getType();
                        for (MethodData ei : type.getMethods()) {
                            if (ei.isStaticInitializer() && new HashSet<>(ei.getUsingStrings()).containsAll(
                                    Arrays.asList("ALLOW_ONCE", "ALLOW_APP_FOR_TASK", "ALLOW_ALL_FOR_TASK",
                                            "ALLOW_VISUAL_FOR_TASK", "DENY", "STOP", "UNAVAILABLE"))) enums.add(type.getName());
                        }
                    }
                }
            }
            System.out.println("linked_enums=" + enums);
            if (enums.size() != 1) throw new IllegalStateException("Linked decision enum not unique");
            System.out.println("NATIVE_DEXKIT_SMOKE_PASS");
        }
    }
    private static void check(Map<String, MethodData> out, MethodData m) {
        List<String> p = m.getParamTypeNames();
        if (p.size() == 5 && p.subList(0,3).equals(Arrays.asList("long", "java.lang.String", "long"))
                && "java.util.Map".equals(m.getReturnTypeName()) && new HashSet<>(m.getUsingStrings())
                .containsAll(Arrays.asList("start", "poll", "jobId", "command"))) out.put(m.getDescriptor(), m);
    }
}
