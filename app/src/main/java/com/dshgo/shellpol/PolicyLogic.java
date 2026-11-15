package com.dshgo.shellpol;

/** Pure logic shared by the runtime hook and JVM tests. */
public final class PolicyLogic {
    private PolicyLogic() { }
    public static boolean shouldAsk(int mode, String command) {
        if (mode == 1) return false;
        if (mode == 2) return DangerCommands.isDangerous(command);
        return true;
    }
    public static boolean validIdentity(String backend, int uid) {
        return ("adb".equals(backend) && uid == 2000) ||
            ("shizuku".equals(backend) && (uid == 0 || uid == 2000));
    }
}
