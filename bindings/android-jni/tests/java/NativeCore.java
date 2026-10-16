// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect.nativecore;

/** Host JVM smoke test of the exact JNI names and handle contract (not Android APIs). */
public final class NativeCore {
    private static native String greeting();
    private static native long create(String dir, String name, String key);
    private static native void stop(long handle);
    private static native void command(long handle, String json);
    private static native String snapshot(long handle);
    private static native String events(long handle);

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("missing JNI IllegalStateException");
        } catch (IllegalStateException expected) { /* required JNI exception */ }
    }

    public static void main(String[] args) {
        System.load(args[0]);
        if (!greeting().equals("Hello from Lava Connect Core")) throw new AssertionError("JNI greeting");
        expectIllegalState(() -> create(null, "JNI smoke", "ab".repeat(32)));
        long handle = create(args[1], "JNI smoke \u0000 🚀 汉字", "ab".repeat(32));
        if (handle <= 0) throw new AssertionError("invalid handle");
        String snapshot = snapshot(handle);
        var matcher = java.util.regex.Pattern.compile("\"device_id\":\"([0-9a-f]{64})\"").matcher(snapshot);
        if (!matcher.find()) throw new AssertionError("missing identity");
        String id = matcher.group(1);
        if (!snapshot.contains("\"running\":true")) throw new AssertionError("core not running");
        if (!snapshot.contains("\\u0000") || !snapshot.contains("🚀 汉字")) throw new AssertionError("JNI Unicode conversion");
        final long activeHandle = handle;
        expectIllegalState(() -> command(activeHandle, null));
        expectIllegalState(() -> command(activeHandle, "not JSON"));
        expectIllegalState(() -> command(activeHandle, "{\"type\":\"send_clipboard\",\"mime_type\":\"text/plain\",\"data\":\"!\"}"));
        command(handle, "{\"type\":\"mark_clipboard_applied\",\"content_hash\":\"" + "00".repeat(32) + "\"}");
        if (!events(handle).startsWith("[")) throw new AssertionError("event contract");
        stop(handle);
        try {
            snapshot(handle);
            throw new AssertionError("stopped handle accepted");
        } catch (IllegalStateException expected) { /* required JNI exception */ }
        handle = create(args[1], "JNI smoke restarted", "ab".repeat(32));
        if (!snapshot(handle).contains("\"device_id\":\"" + id + "\"")) throw new AssertionError("identity changed");
        stop(handle);
        System.out.println("JNI host smoke passed");
    }
}
