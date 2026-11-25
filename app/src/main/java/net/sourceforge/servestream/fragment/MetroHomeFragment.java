package net.sourceforge.servestream.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.utils.MusicUtils;

/**
 * Metro home screen: coloured tiles for stations, browse, alarm, now playing,
 * settings and about. The now playing tile shows the current station.
 */
public class MetroHomeFragment extends Fragment {

    public static final String TAG = "metro_home";

    public static final int STATIONS = 0, BROWSE = 1, ALARM = 2, NOW_PLAYING = 3, SETTINGS = 4, ABOUT = 5;

    private TextView mNowPlaying;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refreshNowPlaying();
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_metro_home, container, false);
        mNowPlaying = v.findViewById(R.id.metro_now_playing_name);
        bind(v, R.id.metro_tile_stations, STATIONS);
        bind(v, R.id.metro_tile_browse, BROWSE);
        bind(v, R.id.metro_tile_alarm, ALARM);
        bind(v, R.id.metro_tile_now_playing, NOW_PLAYING);
        bind(v, R.id.metro_tile_settings, SETTINGS);
        bind(v, R.id.metro_tile_about, ABOUT);
        return v;
    }

    private void bind(View root, int id, final int which) {
        root.findViewById(id).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openMetroTile(which);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.post(mRefresh);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefresh);
    }

    /** Name of the station being played, or null. */
    public static String currentStation() {
        try {
            if (MusicUtils.sService != null && MusicUtils.sService.getPath() != null) {
                String name = MusicUtils.sService.getTrackName();
                if (name == null || name.length() == 0) name = MusicUtils.sService.getPath();
                return name;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void refreshNowPlaying() {
        if (mNowPlaying == null) return;
        String name = currentStation();
        mNowPlaying.setText(name != null ? name : getString(R.string.metro_tile_now_playing_none));
        View tile = (View) mNowPlaying.getParent();
        tile.setContentDescription(getString(R.string.metro_tile_now_playing) + ", " + mNowPlaying.getText());
    }
}
