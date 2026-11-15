package com.dshgo.shellpol;

import java.util.*;
import java.util.regex.Pattern;

/** A conservative danger rule set, not a complete shell security parser. Never executes its input. */
public final class DangerCommands {
    private DangerCommands() { }
    private static final Set<String> DESTRUCTIVE = new HashSet<>(Arrays.asList(
        "dd", "shred", "wipe", "wipefs", "mkfs", "mke2fs", "mkfs.ext4", "mkfs.f2fs", "mkswap",
        "fdisk", "sfdisk", "parted", "sgdisk", "fastboot", "flash_image", "erase_image",
        "reboot", "shutdown", "halt", "poweroff", "setenforce", "setprop", "resetprop",
        "insmod", "rmmod", "modprobe", "mount", "umount", "swapon", "swapoff", "chcon",
        "iptables", "ip6tables", "nft", "magisk", "ksud", "apd", "su", "eval", "exec", "source", "."));
    private static final Set<String> PM_WRITES = new HashSet<>(Arrays.asList(
        "uninstall", "clear", "disable", "disable-user", "grant", "revoke", "hide", "suspend", "reset-permissions"));
    private static final Pattern CRITICAL = Pattern.compile("^/(system|vendor|product|odm|boot|dev/block|sys|proc|data/(system|misc|adb))(/|$)");

    public static boolean isDangerous(String command) { return matchedRule(command) != null; }

    public static String matchedRule(String command) {
        if (command == null || command.trim().isEmpty()) return "empty-command";
        // Expansions and substitutions can turn innocuous tokens into a different command.
        if (command.indexOf('`') >= 0 || command.contains("$(") || command.contains("${") || command.indexOf('$') >= 0)
            return "dynamic-shell-expansion";
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char quote = 0;
        boolean escape = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (escape) {
                if (c != '\n') word.append(c); // Shell line continuation removes backslash-newline.
                escape = false;
                continue;
            }
            if (c == '\\' && quote != '\'') { escape = true; continue; }
            if (quote != 0) {
                if (c == quote) quote = 0; else word.append(c);
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == ';' || c == '|' || c == '&' || c == '\n' || c == '(' || c == ')') {
                flush(words, word);
                String rule = inspect(words);
                if (rule != null) return rule;
                words.clear();
            } else if (c == '>' || c == '<') {
                flush(words, word); words.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) flush(words, word);
            else word.append(c);
        }
        if (quote != 0 || escape) return "unparsed-shell-syntax";
        flush(words, word);
        return inspect(words);
    }

    private static void flush(List<String> words, StringBuilder word) {
        if (word.length() > 0) { words.add(word.toString()); word.setLength(0); }
    }

    private static String inspect(List<String> words) {
        if (words.isEmpty()) return null;
        int i = 0;
        while (i < words.size() && words.get(i).matches("[A-Za-z_][A-Za-z0-9_]*=.*")) i++;
        if (i == words.size()) return null;
        String exe = basename(words.get(i));
        if (Arrays.asList("if", "then", "else", "elif", "while", "until", "for", "do", "case", "function", "{", "!", "builtin").contains(exe))
            return "compound-shell-execution";
        if (">".equals(exe) || "<".equals(exe)) return "leading-shell-redirection";
        if (Arrays.asList("env", "command", "nohup", "timeout", "xargs").contains(exe))
            return "indirect-execution";
        if ("find".equals(exe) && hasAny(words, "-exec", "-execdir", "-delete", "-ok", "-okdir"))
            return "find-mutation-or-execution";
        if ("busybox".equals(exe) || "toybox".equals(exe)) {
            if (++i == words.size()) return null;
            exe = basename(words.get(i));
        }
        if (DESTRUCTIVE.contains(exe) || exe.startsWith("mkfs.")) return "destructive-or-privileged:" + exe;
        List<String> args = words.subList(i + 1, words.size());
        if ("rm".equals(exe) || "rmdir".equals(exe)) {
            for (String a : args) {
                if (a.equals("--recursive") || a.matches("-[A-Za-z]*[rR][A-Za-z]*")) return "recursive-delete";
                if (!a.startsWith("-") && ("/".equals(a) || CRITICAL.matcher(a).find() || a.contains("*"))) return "critical-delete";
            }
        }
        if (Arrays.asList("sh", "bash", "dash", "zsh", "python", "python3", "perl", "ruby", "node").contains(exe))
            return "script-execution";
        if ("pm".equals(exe) && !args.isEmpty() && PM_WRITES.contains(args.get(0))) return "package-data-or-permissions";
        if ("cmd".equals(exe) && args.size() >= 2 && "package".equals(args.get(0)) && PM_WRITES.contains(args.get(1)))
            return "package-data-or-permissions";
        if ("settings".equals(exe) && hasAny(args, "put", "delete", "reset")) return "system-settings-write";
        if ("appops".equals(exe) && hasAny(args, "set", "reset")) return "appops-write";
        if ("am".equals(exe) && hasAny(args, "force-stop", "kill", "kill-all", "broadcast", "instrument")) return "process-or-broadcast";
        if ("kill".equals(exe) || "killall".equals(exe) || "pkill".equals(exe)) return "process-termination";
        if ("svc".equals(exe) && hasAny(args, "disable", "enable", "shutdown", "reboot")) return "service-state-write";
        if (Arrays.asList("chmod", "chown", "chgrp").contains(exe)) {
            for (String a : args)
                if (a.contains("R") || a.equals("777") || a.equals("666") || a.equals("000") || a.matches("[2467][0-7]{3}")) return "broad-permission-change";
        }
        if ("ip".equals(exe) && hasAny(args, "set", "add", "del", "flush")) return "network-state-write";
        if ("sysctl".equals(exe) && hasAny(args, "-w", "--write")) return "kernel-write";
        for (int n = 0; n < words.size() - 1; n++) {
            if (">".equals(words.get(n)) && CRITICAL.matcher(words.get(n + 1)).find()) return "critical-redirection";
        }
        if (Arrays.asList("tee", "cp", "mv", "install", "truncate").contains(exe))
            for (String a : args) if (CRITICAL.matcher(a).find()) return "critical-file-write";
        return null;
    }

    private static boolean hasAny(List<String> args, String... choices) {
        for (String c : choices) if (args.contains(c)) return true;
        return false;
    }
    private static String basename(String s) { return s.substring(s.lastIndexOf('/') + 1); }
}
