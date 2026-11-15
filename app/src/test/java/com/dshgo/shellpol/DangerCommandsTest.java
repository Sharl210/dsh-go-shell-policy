package com.dshgo.shellpol;

import org.junit.Test;
import static org.junit.Assert.*;

/** Strings only: these test cases never run shell commands. */
public class DangerCommandsTest {
    @Test public void ordinaryReadCommandsAreNotPrompted() {
        for (String s : new String[]{"id", "whoami", "ls -al /sdcard", "getprop ro.product.model",
                "dumpsys battery", "pm list packages", "settings get global airplane_mode_on",
                "echo 'rm -rf /'", "printf '%s' 'reboot'", "logcat -d -t 20", "cat /proc/version",
                "toybox ls /sdcard", "printf abc | wc -c", "pwd && id"})
            assertFalse(s + ": " + DangerCommands.matchedRule(s), DangerCommands.isDangerous(s));
    }

    @Test public void destructiveAndSystemMutationCommandsArePrompted() {
        for (String s : new String[]{"rm -rf /data/local/tmp/demo", "rm --recursive /sdcard/demo",
                "/system/bin/rm -R /sdcard/demo", "r\\m -rf /sdcard/demo", "'rm' -rf /sdcard/demo",
                "toybox rm -rf /sdcard/demo", "busybox dd if=a of=b", "reboot", "pm clear com.example.demo",
                "pm uninstall com.example.demo", "cmd package revoke com.example.demo android.permission.CAMERA",
                "settings put global airplane_mode_on 1", "chmod -R 777 /sdcard/demo", "dd if=a of=b",
                "echo 1 > /sys/example", "cat a | sh", "id; reboot", "id && pm clear com.example.demo",
                "su -c id", "$(echo reboot)", "sh /sdcard/example.sh", "iptables -F", "mv a /system/example",
                "appops set com.example.demo CAMERA allow", "setenforce 0", "kill 123"})
            assertTrue(s, DangerCommands.isDangerous(s));
    }

    @Test public void malformedAndDynamicSyntaxNeedsConfirmation() {
        assertTrue(DangerCommands.isDangerous("echo 'unterminated"));
        assertTrue(DangerCommands.isDangerous("$COMMAND"));
        assertTrue(DangerCommands.isDangerous("`id`"));
        assertTrue(DangerCommands.isDangerous(null));
    }
}
