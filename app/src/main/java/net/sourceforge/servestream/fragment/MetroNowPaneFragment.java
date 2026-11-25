package net.sourceforge.servestream.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MediaPlayerActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.StationImages;

/**
 * Metro wide screens: right pane of the main screen showing the station being
 * played (picture and name). Tapping it opens the player.
 */
public class MetroNowPaneFragment extends Fragment {

    public static final String TAG = "metro_now";

    private ImageView mArt;
    private TextView mName;
    private String mShownUri = "";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refresh();
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_metro_now_pane, container, false);
        mArt = v.findViewById(R.id.metro_now_art);
        mName = v.findViewById(R.id.metro_now_name);
        View.OnClickListener open = new View.OnClickListener() {
            public void onClick(View view) {
                if (MetroHomeFragment.currentStation() != null) {
                    startActivity(new Intent(getActivity(), MediaPlayerActivity.class));
                } else {
                    Toast.makeText(getActivity(), R.string.metro_nothing_playing, Toast.LENGTH_SHORT).show();
                }
            }
        };
        v.setOnClickListener(open);
        v.findViewById(R.id.metro_now_open).setOnClickListener(open);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mShownUri = "";
        mHandler.post(mRefresh);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefresh);
    }

    private void refresh() {
        if (mName == null || getActivity() == null) return;
        String name = MetroHomeFragment.currentStation();
        mName.setText(name != null ? name : getString(R.string.metro_tile_now_playing_none));
        String uri = null;
        try {
            if (MusicUtils.sService != null) uri = MusicUtils.sService.getMediaUri();
        } catch (Exception ignored) {
        }
        String key = uri == null ? "" : uri;
        if (key.equals(mShownUri)) return;
        mShownUri = key;
        Bitmap picture = uri == null ? null : stationPicture(uri);
        if (picture != null) {
            mArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mArt.setImageBitmap(picture);
        } else {
            mArt.setScaleType(ImageView.ScaleType.CENTER);
            mArt.setImageResource(R.drawable.ic_m3_radio);
        }
    }

    /** Picture of the saved station whose address is being played. */
    private Bitmap stationPicture(String uri) {
        StreamDatabase db = new StreamDatabase(getActivity());
        try {
            for (UriBean u : db.getUris()) {
                try {
                    if (uri.equals(u.getUri().toString()) || uri.equals(u.getScrubbedUri().toString())) {
                        return StationImages.load(getActivity(), u.getId());
                    }
                } catch (Exception ignored) {
                }
            }
        } finally {
            db.close();
        }
        return null;
    }
}
