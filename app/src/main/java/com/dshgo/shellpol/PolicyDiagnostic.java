package com.dshgo.shellpol;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Standalone read-only diagnostic. Does not launch an Activity or execute target shell commands. */
public final class PolicyDiagnostic {
    public static void main(String[] args) throws Exception {
        System.out.println("diagnostic_uid=" + Process.myUid());
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context system = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        Context target = system.createPackageContext("io.github.ccch1mneyyy.dsh_app", Context.CONTEXT_IGNORE_SECURITY);
        ProviderInfo info = target.getPackageManager().resolveContentProvider("com.dshgo.shellpol.policy", 0);
        System.out.println("context=" + target.getPackageName() + " provider=" + (info == null ? "null" : info.packageName + ":uid=" + info.applicationInfo.uid));
        try (Cursor c = target.getContentResolver().query(Uri.parse("content://com.dshgo.shellpol.policy/mode"), new String[]{"mode"}, null, null, null)) {
            System.out.println("query=" + (c == null ? "null" : c.moveToFirst() ? "mode=" + c.getInt(c.getColumnIndexOrThrow("mode")) : "empty"));
        } catch (Throwable e) { System.out.println("POLICY_QUERY_EXCEPTION"); e.printStackTrace(System.out); }
        List<String> unavailable = new ArrayList<>(); int own = 0;
        File[] dirs = new File("/proc").listFiles();
        if (dirs == null) { System.out.println("PROC_ENUMERATION_FAILED"); return; }
        for (File p : dirs) {
            if (!p.getName().matches("[0-9]+") || p.getName().equals(Integer.toString(Process.myPid()))) continue;
            try {
                int uid = android.system.Os.stat(p.getAbsolutePath()).st_uid;
                if (uid != Process.myUid()) continue;
                own++;
                for (String name : new String[]{"stat", "cmdline", "environ", "cwd"}) {
                    try { if (name.equals("cwd")) android.system.Os.readlink(new File(p, name).getAbsolutePath()); else Files.readAllBytes(new File(p, name).toPath()); }
                    catch (Throwable e) { unavailable.add(p.getName() + "/" + name + ":" + e); }
                }
            } catch (Throwable e) { unavailable.add(p.getName() + "/owner:" + e); }
        }
        System.out.println("same_uid_processes=" + own + " unavailable=" + unavailable.size());
        for (String item : unavailable) System.out.println("PROC_UNAVAILABLE " + item);
        System.out.println("READ_ONLY_DIAGNOSTIC_DONE");
    }
}
