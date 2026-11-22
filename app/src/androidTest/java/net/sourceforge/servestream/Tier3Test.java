package net.sourceforge.servestream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.ListView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import net.sourceforge.servestream.activity.AddUrlActivity;
import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.transport.TransportFactory;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.util.Collection;

/** Adaptive "Tier 3": state survives rotation / folding / resizing (activity recreation). */
@RunWith(AndroidJUnit4.class)
public class Tier3Test {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    private Activity resumed() {
        final Activity[] out = new Activity[1];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                Collection<Activity> c = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
                out[0] = c.isEmpty() ? null : c.iterator().next();
            }
        });
        return out[0];
    }

    private Activity recreate(final Activity a) {
        inst.runOnMainSync(new Runnable() {
            public void run() {
                a.recreate();
            }
        });
        long end = SystemClock.elapsedRealtime() + 10000;
        Activity now = null;
        while (SystemClock.elapsedRealtime() < end) {
            SystemClock.sleep(300);
            now = resumed();
            if (now != null && now != a) break;
        }
        assertNotNull(now);
        assertNotEquals("activity was not recreated", a, now);
        SystemClock.sleep(2500);
        return now;
    }

    private void ensureManyStations() {
        StreamDatabase db = new StreamDatabase(inst.getTargetContext());
        for (int k = 0; k < 25; k++) {
            Uri u = Uri.parse("https://scroll" + k + ".invalid/stream");
            if (TransportFactory.findUri(db, u) == null) {
                UriBean b = TransportFactory.getTransport("https").createUri(u);
                b.setNickname("Scroll Test " + k);
                db.saveUri(b);
            }
        }
        db.close();
    }

    @Test
    public void listScrollPositionSurvivesRecreation() {
        ensureManyStations();
        Intent i = new Intent(inst.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        final Activity a = inst.startActivitySync(i);
        SystemClock.sleep(3000);
        inst.runOnMainSync(new Runnable() {
            public void run() {
                ListView list = a.findViewById(android.R.id.list);
                list.setSelection(15);
            }
        });
        SystemClock.sleep(800);
        Activity b = recreate(a);
        final int[] first = new int[1];
        final ListView[] l = new ListView[1];
        final Activity fb = b;
        inst.runOnMainSync(new Runnable() {
            public void run() {
                l[0] = fb.findViewById(android.R.id.list);
                first[0] = l[0].getFirstVisiblePosition();
            }
        });
        assertTrue("list jumped back to " + first[0], first[0] >= 13 && first[0] <= 17);
        inst.runOnMainSync(new Runnable() {
            public void run() {
                fb.finish();
            }
        });
    }

    @Test
    public void typedTextSurvivesRecreation() {
        Intent i = new Intent(inst.getTargetContext(), AddUrlActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity a = inst.startActivitySync(i);
        SystemClock.sleep(1500);
        inst.runOnMainSync(new Runnable() {
            public void run() {
                ((EditText) a.findViewById(R.id.url_edittext)).setText("https://example.invalid/typed");
                ((EditText) a.findViewById(R.id.nickname_edittext)).setText("Typed name");
            }
        });
        final Activity b = recreate(a);
        final String[] got = new String[2];
        inst.runOnMainSync(new Runnable() {
            public void run() {
                got[0] = ((EditText) b.findViewById(R.id.url_edittext)).getText().toString();
                got[1] = ((EditText) b.findViewById(R.id.nickname_edittext)).getText().toString();
                b.finish();
            }
        });
        assertEquals("https://example.invalid/typed", got[0]);
        assertEquals("Typed name", got[1]);
    }
}
