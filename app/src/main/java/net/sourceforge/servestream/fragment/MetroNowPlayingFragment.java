package net.sourceforge.servestream.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.NowPlayingActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.StationImages;

/**
 * Metro panorama "now playing" section (Windows Phone Xbox Music look).
 * Drives the playback service directly, like the mini controller does; the
 * full player screen (MediaPlayerActivity) is untouched.
 * Live radio (no known length) shows LIVE and the time listened, never a
 * made-up remaining time.
 */
public class MetroNowPlayingFragment extends Fragment {

    public static final String TAG = "metro_sec_now";

    private TextView mTitle, mBy, mLive, mElapsed, mRemaining, mQueuePos;
    private ImageView mArt;
    private ImageButton mPlay, mShuffle, mRepeat;
    private SeekBar mSeek;
    private boolean mDragging;
    private String mArtUri;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRefresh = new Runnable() {
        public void run() {
            refresh();
            mHandler.postDelayed(this, 500);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_metro_now_playing, container, false);
        mTitle = v.findViewById(R.id.metro_np_title);
        mBy = v.findViewById(R.id.metro_np_by);
        mArt = v.findViewById(R.id.metro_np_art);
        mLive = v.findViewById(R.id.metro_np_live);
        mElapsed = v.findViewById(R.id.metro_np_elapsed);
        mRemaining = v.findViewById(R.id.metro_np_remaining);
        mQueuePos = v.findViewById(R.id.metro_np_queue_pos);
        mSeek = v.findViewById(R.id.metro_np_seek);
        mPlay = v.findViewById(R.id.metro_np_play);
        mShuffle = v.findViewById(R.id.metro_np_shuffle);
        mRepeat = v.findViewById(R.id.metro_np_repeat);

        mPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                IMediaPlaybackService s = MusicUtils.sService;
                if (s == null) return;
                try {
                    if (s.isPlaying()) s.pause(); else s.play();
                } catch (Exception ignored) {
                }
                refresh();
            }
        });
        v.findViewById(R.id.metro_np_prev).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    if (MusicUtils.sService != null) MusicUtils.sService.prev();
                } catch (Exception ignored) {
                }
            }
        });
        v.findViewById(R.id.metro_np_next).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    if (MusicUtils.sService != null) MusicUtils.sService.next();
                } catch (Exception ignored) {
                }
            }
        });
        mShuffle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    IMediaPlaybackService s = MusicUtils.sService;
                    if (s == null) return;
                    s.setShuffleMode(s.getShuffleMode() == MediaPlaybackService.SHUFFLE_NONE
                            ? MediaPlaybackService.SHUFFLE_ON : MediaPlaybackService.SHUFFLE_NONE);
                    view.announceForAccessibility(getString(s.getShuffleMode() == MediaPlaybackService.SHUFFLE_NONE
                            ? R.string.shuffle_off_notif : R.string.shuffle_on_notif));
                } catch (Exception ignored) {
                }
                refresh();
            }
        });
        mRepeat.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    IMediaPlaybackService s = MusicUtils.sService;
                    if (s == null) return;
                    int mode = s.getRepeatMode();
                    // off -> all -> current -> off, as on the player screen
                    int next = mode == MediaPlaybackService.REPEAT_NONE ? MediaPlaybackService.REPEAT_ALL
                            : mode == MediaPlaybackService.REPEAT_ALL ? MediaPlaybackService.REPEAT_CURRENT
                            : MediaPlaybackService.REPEAT_NONE;
                    s.setRepeatMode(next);
                    view.announceForAccessibility(getString(repeatText(next)));
                } catch (Exception ignored) {
                }
                refresh();
            }
        });
        v.findViewById(R.id.metro_np_queue).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), NowPlayingActivity.class));
            }
        });
        mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            public void onStartTrackingTouch(SeekBar bar) {
                mDragging = true;
            }
            public void onStopTrackingTouch(SeekBar bar) {
                mDragging = false;
                try {
                    IMediaPlaybackService s = MusicUtils.sService;
                    if (s != null && s.duration() > 0) s.seek(s.duration() * bar.getProgress() / 1000);
                } catch (Exception ignored) {
                }
            }
        });
        return v;
    }

    private static int repeatText(int mode) {
        return mode == MediaPlaybackService.REPEAT_ALL ? R.string.repeat_all_notif
                : mode == MediaPlaybackService.REPEAT_CURRENT ? R.string.repeat_current_notif
                : R.string.repeat_off_notif;
    }

    @Override
    public void onResume() {
        super.onResume();
        mArtUri = null;
        mHandler.post(mRefresh);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefresh);
    }

    private void refresh() {
        if (mTitle == null || getActivity() == null) return;
        IMediaPlaybackService s = MusicUtils.sService;
        String path = null, title = null, artist = null, uri = null;
        boolean playing = false;
        long pos = 0, dur = 0;
        int shuffle = 0, repeat = 0, qpos = 0, qlen = 0;
        try {
            if (s != null) {
                path = s.getPath();
                if (path != null) {
                    title = s.getTrackName();
                    artist = s.getArtistName();
                    uri = s.getMediaUri();
                    playing = s.isPlaying();
                    pos = s.position();
                    dur = s.duration();
                    shuffle = s.getShuffleMode();
                    repeat = s.getRepeatMode();
                    qpos = s.getQueuePosition();
                    long[] q = s.getQueue();
                    qlen = q == null ? 0 : q.length;
                }
            }
        } catch (Exception ignored) {
            path = null;
        }
        boolean active = path != null;
        mTitle.setText(active ? (title != null && title.length() > 0 ? title : path)
                : getString(R.string.metro_tile_now_playing_none));
        mBy.setText(active ? (artist != null && artist.length() > 0 && !"<unknown>".equals(artist)
                ? getString(R.string.metro_by, artist) : "") : getString(R.string.metro_np_hint));

        mPlay.setImageResource(playing ? R.drawable.metro_pause_line : R.drawable.metro_play_line);
        mPlay.setContentDescription(getString(playing ? R.string.pause : R.string.play));
        mPlay.setEnabled(active);
        mPlay.setAlpha(active ? 1f : 0.4f);

        mShuffle.setImageResource(shuffle != MediaPlaybackService.SHUFFLE_NONE ? R.drawable.ic_av_shuffle_selected : R.drawable.ic_m3_shuffle);
        mShuffle.setContentDescription(getString(shuffle != MediaPlaybackService.SHUFFLE_NONE ? R.string.shuffle_on_notif : R.string.shuffle_off_notif));
        mRepeat.setImageResource(repeat == MediaPlaybackService.REPEAT_ALL ? R.drawable.ic_av_repeat_selected
                : repeat == MediaPlaybackService.REPEAT_CURRENT ? R.drawable.ic_av_repeat_one_selected : R.drawable.ic_m3_repeat);
        mRepeat.setContentDescription(getString(repeatText(repeat)));

        boolean live = active && dur <= 0;
        mLive.setVisibility(live ? View.VISIBLE : View.GONE);
        mSeek.setVisibility(live || !active ? View.INVISIBLE : View.VISIBLE);
        mSeek.setEnabled(!live && active);
        mElapsed.setText(active ? MusicUtils.makeTimeString(getActivity(), Math.max(0, pos) / 1000) : "");
        mRemaining.setText(active && !live ? "-" + MusicUtils.makeTimeString(getActivity(), Math.max(0, dur - pos) / 1000) : "");
        if (!live && active && dur > 0 && !mDragging) mSeek.setProgress((int) (1000 * pos / dur));
        mQueuePos.setText(active && qlen > 0 ? (qpos + 1) + " / " + qlen : "");

        String key = uri == null ? "" : uri;
        if (!key.equals(mArtUri)) {
            mArtUri = key;
            Bitmap picture = uri == null ? null : stationPicture(uri);
            if (picture != null) {
                mArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                mArt.clearColorFilter();
                mArt.setPadding(0, 0, 0, 0);
                mArt.setImageBitmap(picture);
            } else {
                mArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                android.util.TypedValue tv = new android.util.TypedValue();
                requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true);
                mArt.setColorFilter(tv.data);
                mArt.setImageResource(R.drawable.ic_m3_radio);
                mArt.post(new Runnable() {
                    public void run() {
                        int p = mArt.getWidth() / 4;
                        mArt.setPadding(p, p, p, p);
                    }
                });
            }
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
