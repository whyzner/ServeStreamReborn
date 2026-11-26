package net.sourceforge.servestream.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.AboutActivity;
import net.sourceforge.servestream.activity.AddUrlActivity;
import net.sourceforge.servestream.activity.SettingsActivity;

/**
 * Metro panorama "explore" and "settings" sections: rows with an outlined
 * icon, a title and a smaller line, Windows Phone style.
 *
 * explore: add a URL, search stations, favourites.
 * settings: the groups of the settings screen (each opens it), app info.
 */
public class MetroRowsFragment extends Fragment {

    public static final String EXPLORE_TAG = "metro_sec_explore";
    public static final String SETTINGS_TAG = "metro_sec_settings";
    private static final String ARG_KIND = "kind";
    private static final int EXPLORE = 0, SETTINGS = 1;

    /** What the panorama does for rows that stay inside it. */
    public interface Host {
        void metroSearchStations();
        void metroShowFavorites();
    }

    public static MetroRowsFragment explore() {
        return make(EXPLORE);
    }

    public static MetroRowsFragment settings() {
        return make(SETTINGS);
    }

    private static MetroRowsFragment make(int kind) {
        MetroRowsFragment f = new MetroRowsFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_KIND, kind);
        f.setArguments(b);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(getActivity());
        LinearLayout rows = new LinearLayout(getActivity());
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(rows);
        int kind = getArguments() != null ? getArguments().getInt(ARG_KIND) : EXPLORE;
        if (kind == EXPLORE) {
            add(inflater, rows, R.drawable.ic_m3_add, R.string.add_new_url_label, R.string.metro_explore_add_sub,
                    new Intent(getActivity(), AddUrlActivity.class), null);
            add(inflater, rows, R.drawable.ic_m3_search, R.string.metro_search_stations, R.string.metro_explore_search_sub,
                    null, new Runnable() {
                        public void run() {
                            if (getActivity() instanceof Host) ((Host) getActivity()).metroSearchStations();
                        }
                    });
            add(inflater, rows, R.drawable.ic_m3_star_border, R.string.metro_tab_favorites, R.string.metro_explore_fav_sub,
                    null, new Runnable() {
                        public void run() {
                            if (getActivity() instanceof Host) ((Host) getActivity()).metroShowFavorites();
                        }
                    });
        } else {
            Intent settings = new Intent(getActivity(), SettingsActivity.class);
            add(inflater, rows, R.drawable.ic_m3_settings, R.string.pref_ui_category, R.string.metro_set_display_sub, settings, null);
            add(inflater, rows, R.drawable.ic_m3_settings, R.string.pref_general_settings_category, R.string.metro_set_general_sub, settings, null);
            add(inflater, rows, R.drawable.ic_m3_play, R.string.pref_playback_category, R.string.metro_set_playback_sub, settings, null);
            add(inflater, rows, R.drawable.ic_m3_settings, R.string.pref_bluetooth_category, R.string.metro_set_bluetooth_sub, settings, null);
            add(inflater, rows, R.drawable.ic_m3_settings, R.string.pref_social_media_category, R.string.metro_set_social_sub, settings, null);
            add(inflater, rows, R.drawable.ic_m3_folder, R.string.pref_backup_and_restore, R.string.metro_set_backup_sub, settings, null);
            add(inflater, rows, R.drawable.ic_m3_info, R.string.metro_tile_about, R.string.metro_set_about_sub,
                    new Intent(getActivity(), AboutActivity.class), null);
        }
        return scroll;
    }

    private void add(LayoutInflater inflater, LinearLayout rows, int icon, int title, int sub,
                     final Intent intent, final Runnable action) {
        View row = inflater.inflate(R.layout.metro_row, rows, false);
        ((ImageView) row.findViewById(R.id.metro_row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.metro_row_title)).setText(title);
        ((TextView) row.findViewById(R.id.metro_row_sub)).setText(sub);
        row.setContentDescription(getString(title) + ", " + getString(sub));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (intent != null) startActivity(intent);
                if (action != null) action.run();
            }
        });
        rows.addView(row);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
