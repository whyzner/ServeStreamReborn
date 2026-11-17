package net.sourceforge.servestream.utils;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Android 15+ always draws apps edge-to-edge. This pads every activity's root
 * view by the system bar / cutout / keyboard insets so no content ends up
 * under the status or navigation bar, while the bars show the window background.
 */
public final class EdgeToEdge implements Application.ActivityLifecycleCallbacks {

    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new EdgeToEdge());
    }

    private static void apply(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup) || ((ViewGroup) decor).getChildCount() == 0) {
            return;
        }
        View root = ((ViewGroup) decor).getChildAt(0);
        if (root.getTag(net.sourceforge.servestream.R.id.edge_to_edge_applied) != null) {
            return;
        }
        root.setTag(net.sourceforge.servestream.R.id.edge_to_edge_applied, Boolean.TRUE);

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        boolean night = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), decor);
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        apply(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
