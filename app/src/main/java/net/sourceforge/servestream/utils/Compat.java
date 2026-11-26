package net.sourceforge.servestream.utils;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.core.content.ContextCompat;

/** Helpers for behaviour required by recent target SDK levels. */
public final class Compat {

    private Compat() {
    }

    private static int immutable(int flags) {
        if (Build.VERSION.SDK_INT >= 23 && (flags & PendingIntent.FLAG_MUTABLE) == 0) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    public static PendingIntent activity(Context c, int requestCode, Intent intent, int flags) {
        return PendingIntent.getActivity(c, requestCode, intent, immutable(flags));
    }

    public static PendingIntent broadcast(Context c, int requestCode, Intent intent, int flags) {
        return PendingIntent.getBroadcast(c, requestCode, intent, immutable(flags));
    }

    public static PendingIntent service(Context c, int requestCode, Intent intent, int flags) {
        // notification and widget clicks grant a temporary allowance to start the service
        return PendingIntent.getService(c, requestCode, intent, immutable(flags));
    }

    /** Sends an app-internal broadcast; package-scoped so not-exported receivers get it. */
    public static void sendInternal(Context c, Intent intent) {
        intent.setPackage(c.getPackageName());
        c.sendBroadcast(intent);
    }

    /** Registers a receiver for app-internal and system broadcasts. */
    public static Intent register(Context c, BroadcastReceiver receiver, IntentFilter filter) {
        return ContextCompat.registerReceiver(c, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
