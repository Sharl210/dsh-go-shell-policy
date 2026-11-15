package com.dshgo.shellpol;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Read-only policy bridge; only the target package UID and module UID may read the mode. */
public final class PolicyProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"readPolicy".equals(method) || getContext() == null) return null;
        int uid = Binder.getCallingUid();
        if (uid != Process.myUid() && !isTargetUid(uid)) return null;
        int mode = getContext().getSharedPreferences("dshgo_shell_policy", 0).getInt("mode", 0);
        Bundle result = new Bundle();
        result.putInt("mode", mode);
        return result;
    }

    private boolean isTargetUid(int uid) {
        try {
            String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
            if (packages == null) return false;
            for (String p : packages) if (HookEntry.TARGET_PKG.equals(p)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.dshgo.shell-policy"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("read only"); }
}
