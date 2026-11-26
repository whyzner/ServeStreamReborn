package net.sourceforge.servestream.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.fragment.AlarmClockFragment;
import net.sourceforge.servestream.fragment.BrowseFragment;
import net.sourceforge.servestream.fragment.MetroNowPlayingFragment;
import net.sourceforge.servestream.fragment.MetroRowsFragment;
import net.sourceforge.servestream.fragment.UrlListFragment;
import net.sourceforge.servestream.utils.MetroSwipe;
import net.sourceforge.servestream.utils.MusicUtils;

/**
 * Metro Dark / Metro Light main screen: a Windows Phone style panorama.
 *
 * Sections: stations, now playing, explore, alarms, settings. Move between them
 * by swiping sideways or by tapping a section title. Phones show one section at
 * a time; wide windows (unfolded foldables, tablets) show the station list and
 * now playing side by side, the other sections full width.
 *
 * Every section lives in its own container (list on the left, now playing on
 * the right, the rest in a single container), so a fragment never has to move
 * when the window changes size: only which containers are visible changes.
 */
class MetroPanorama {

    static final int STATIONS = 0, NOW_PLAYING = 1, EXPLORE = 2, ALARMS = 3, SETTINGS = 4;
    private static final int[] TITLES = {
            R.string.metro_pivot_stations, R.string.metro_pivot_now_playing, R.string.metro_pivot_explore,
            R.string.metro_pivot_alarms, R.string.metro_pivot_settings};

    static final String STATIONS_TAG = "metro_sec_stations";
    static final String ALARMS_TAG = "metro_sec_alarms";
    static final String BROWSE_TAG = "metro_sec_browse";
    /** The whole settings screen, right in the panorama (one settings window). */
    static final String SETTINGS_TAG = "metro_sec_prefs";
    private static final String STATE_SECTION = "metro_section";
    private static final String STATE_SEARCH = "metro_search";

    /** For tests: the section shown. */
    static volatile int sShown = -1;

    private final MainActivity mActivity;
    private final FragmentManager mFm;
    private final boolean mWide;
    private int mSection = STATIONS;
    private boolean mSearching;
    private final TextView[] mTitles = new TextView[TITLES.length];
    private MetroSwipe mSwipe;

    MetroPanorama(MainActivity activity, Bundle saved, String targetUri) {
        mActivity = activity;
        mFm = activity.getSupportFragmentManager();
        Configuration c = activity.getResources().getConfiguration();
        mWide = c.screenWidthDp >= 600 && c.screenHeightDp >= 480;

        // screens restored from before the panorama (or from another theme) are dropped
        FragmentTransaction drop = mFm.beginTransaction();
        boolean any = false;
        // (2.9.1 - 2.9.2 had a list of settings groups here instead of the settings themselves)
        for (String tag : new String[] {"0", "1", "2", "metro_home", "metro_rail", "metro_now", MetroRowsFragment.SETTINGS_TAG}) {
            Fragment f = mFm.findFragmentByTag(tag);
            if (f != null) {
                drop.remove(f);
                any = true;
            }
        }
        if (any) drop.commitNow();

        if (mFm.findFragmentByTag(STATIONS_TAG) == null) {
            UrlListFragment list = new UrlListFragment();
            Bundle args = new Bundle();
            args.putString(UrlListFragment.ARG_TARGET_URI, targetUri);
            list.setArguments(args);
            mFm.beginTransaction().add(R.id.metro_left, list, STATIONS_TAG).commitNow();
        } else if (targetUri != null) {
            openUri(targetUri);
        }
        if (mFm.findFragmentByTag(MetroNowPlayingFragment.TAG) == null) {
            mFm.beginTransaction().add(R.id.metro_right, new MetroNowPlayingFragment(), MetroNowPlayingFragment.TAG).commitNow();
        }

        if (saved != null) {
            mSection = saved.getInt(STATE_SECTION, STATIONS);
            mSearching = saved.getBoolean(STATE_SEARCH, false);
        }
        buildTitles();
        setUpSearch();
        show(mSection, 0);
    }

    void save(Bundle out) {
        out.putInt(STATE_SECTION, mSection);
        out.putBoolean(STATE_SEARCH, mSearching);
    }

    boolean isWide() {
        return mWide;
    }

    // ---- sections ----

    /** Station list and now playing share one place on wide windows. */
    private boolean dual(int section) {
        return section == STATIONS || section == NOW_PLAYING;
    }

    void show(int section, int direction) {
        mSection = section;
        sShown = section;
        View dual = mActivity.findViewById(R.id.metro_dual);
        View single = mActivity.findViewById(R.id.metro_single);
        View left = mActivity.findViewById(R.id.metro_left);
        View right = mActivity.findViewById(R.id.metro_right);
        View line = mActivity.findViewById(R.id.metro_split_line);
        View shown;

        FragmentTransaction ft = mFm.beginTransaction();
        String singleTag = null;
        if (!dual(section)) {
            singleTag = section == EXPLORE ? (mFm.findFragmentByTag(BROWSE_TAG) != null ? BROWSE_TAG : MetroRowsFragment.EXPLORE_TAG)
                    : section == ALARMS ? ALARMS_TAG : SETTINGS_TAG;
            if (mFm.findFragmentByTag(singleTag) == null) {
                Fragment f = section == EXPLORE ? MetroRowsFragment.explore()
                        : section == ALARMS ? new AlarmClockFragment()
                        : new net.sourceforge.servestream.fragment.SettingsFragment();
                ft.add(R.id.metro_single, f, singleTag);
            }
        }
        for (String tag : new String[] {MetroRowsFragment.EXPLORE_TAG, BROWSE_TAG, ALARMS_TAG, SETTINGS_TAG}) {
            Fragment f = mFm.findFragmentByTag(tag);
            if (f == null) continue;
            if (tag.equals(singleTag)) ft.show(f); else ft.hide(f);
        }
        ft.commitNow();

        if (dual(section)) {
            dual.setVisibility(View.VISIBLE);
            single.setVisibility(View.GONE);
            if (mWide) {
                left.setVisibility(View.VISIBLE);
                right.setVisibility(View.VISIBLE);
                line.setVisibility(View.VISIBLE);
                shown = dual;
            } else {
                left.setVisibility(section == STATIONS ? View.VISIBLE : View.GONE);
                right.setVisibility(section == NOW_PLAYING ? View.VISIBLE : View.GONE);
                line.setVisibility(View.GONE);
                shown = section == STATIONS ? left : right;
            }
        } else {
            dual.setVisibility(View.GONE);
            single.setVisibility(View.VISIBLE);
            shown = single;
        }
        if (direction != 0) slideIn(shown, direction);

        mActivity.findViewById(R.id.metro_search).setVisibility(
                mSearching && (section == STATIONS || (mWide && dual(section))) ? View.VISIBLE : View.GONE);
        highlightTitle();
        buildAppBar();
        mActivity.refreshBackCallback();
    }

    /** A restrained horizontal slide, as panorama sections move. */
    private void slideIn(View v, int direction) {
        float d = 48 * mActivity.getResources().getDisplayMetrics().density;
        v.setTranslationX(direction * d);
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** Next / previous section in swipe order; on wide windows list and now playing count as one. */
    private void step(int direction) {
        int[] order = mWide ? new int[] {STATIONS, EXPLORE, ALARMS, SETTINGS}
                : new int[] {STATIONS, NOW_PLAYING, EXPLORE, ALARMS, SETTINGS};
        int at = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == mSection || (mWide && dual(mSection) && order[i] == STATIONS)) at = i;
        }
        int next = (at + direction + order.length) % order.length;
        show(order[next], direction);
    }

    void showNowPlaying() {
        if (mSection != NOW_PLAYING) show(NOW_PLAYING, 1);
    }

    void openUri(String uri) {
        show(STATIONS, 0);
        Fragment list = mFm.findFragmentByTag(STATIONS_TAG);
        if (list instanceof UrlListFragment) ((UrlListFragment) list).openMetroUri(uri);
    }

    /** A station address turned out to be a folder: browse it inside explore. */
    void browse(String uri) {
        Fragment old = mFm.findFragmentByTag(BROWSE_TAG);
        FragmentTransaction ft = mFm.beginTransaction();
        if (old != null) ft.remove(old);
        BrowseFragment b = new BrowseFragment();
        Bundle args = new Bundle();
        args.putString(UrlListFragment.ARG_TARGET_URI, uri);
        b.setArguments(args);
        ft.add(R.id.metro_single, b, BROWSE_TAG).commitNow();
        show(EXPLORE, 1);
    }

    Fragment browseFragment() {
        return mFm.findFragmentByTag(BROWSE_TAG);
    }

    // ---- section titles ----

    private void buildTitles() {
        LinearLayout row = mActivity.findViewById(R.id.metro_pivot);
        row.removeAllViews();
        float dp = mActivity.getResources().getDisplayMetrics().density;
        for (int i = 0; i < TITLES.length; i++) {
            final int section = i;
            TextView t = new TextView(mActivity);
            t.setText(TITLES[i]);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, mWide ? 24 : 26);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            t.setPadding((int) (6 * dp), (int) (4 * dp), (int) (6 * dp), (int) (8 * dp));
            t.setMinHeight((int) (48 * dp));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setSingleLine(true);
            t.setFocusable(true);
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (section != mSection) show(section, section > mSection ? 1 : -1);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) (12 * dp));
            row.addView(t, lp);
            mTitles[i] = t;
        }
    }

    private void highlightTitle() {
        int on = color(com.google.android.material.R.attr.colorOnSurface);
        int off = color(com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (int i = 0; i < mTitles.length; i++) {
            boolean selected = i == mSection;
            mTitles[i].setTextColor(selected ? on : off);
            mTitles[i].setAlpha(selected ? 1f : 0.7f);
            mTitles[i].setSelected(selected);
            mTitles[i].setBackgroundResource(selected ? R.drawable.metro_pivot_selected : 0);
        }
        final TextView sel = mTitles[mSection];
        final HorizontalScrollView scroll = mActivity.findViewById(R.id.metro_pivot_scroll);
        scroll.post(new Runnable() {
            public void run() {
                scroll.smoothScrollTo(Math.max(0, sel.getLeft() - sel.getPaddingLeft()), 0);
            }
        });
    }

    private int color(int attr) {
        TypedValue tv = new TypedValue();
        mActivity.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    // ---- search ----

    private void setUpSearch() {
        EditText box = mActivity.findViewById(R.id.metro_search);
        box.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                Fragment list = mFm.findFragmentByTag(STATIONS_TAG);
                if (list instanceof UrlListFragment) ((UrlListFragment) list).setMetroQuery(s.toString());
            }
        });
    }

    void search(boolean on) {
        mSearching = on;
        EditText box = mActivity.findViewById(R.id.metro_search);
        if (!on) {
            box.setText("");
        }
        if (mSection != STATIONS && !(mWide && dual(mSection))) {
            show(STATIONS, -1);
        } else {
            show(mSection, 0);
        }
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(MainActivity.INPUT_METHOD_SERVICE);
        if (on) {
            box.requestFocus();
            imm.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        }
    }

    void showFavorites() {
        show(STATIONS, -1);
        Fragment list = mFm.findFragmentByTag(STATIONS_TAG);
        if (list instanceof UrlListFragment) ((UrlListFragment) list).selectMetroTab(UrlListFragment.TAB_FAVORITES);
    }

    // ---- round-icon app bar ----

    private void buildAppBar() {
        LinearLayout bar = mActivity.findViewById(R.id.metro_appbar);
        bar.removeAllViews();
        boolean listShown = mSection == STATIONS || (mWide && dual(mSection));
        if (listShown) {
            addBarButton(bar, R.drawable.ic_m3_add, R.string.add_new_url_label, new View.OnClickListener() {
                public void onClick(View v) {
                    mActivity.startActivity(new Intent(mActivity, AddUrlActivity.class));
                }
            });
            addBarButton(bar, R.drawable.ic_m3_search, R.string.metro_search_stations, new View.OnClickListener() {
                public void onClick(View v) {
                    search(!mSearching);
                }
            });
        }
        if (mSection == NOW_PLAYING || (mWide && dual(mSection))) {
            addBarButton(bar, R.drawable.ic_m3_volume_up, R.string.metro_volume, new View.OnClickListener() {
                public void onClick(View v) {
                    AudioManager am = (AudioManager) mActivity.getSystemService(MainActivity.AUDIO_SERVICE);
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                }
            });
        }
        if (mSection == ALARMS) {
            addBarButton(bar, R.drawable.ic_m3_add, R.string.alarm_clock_label, new View.OnClickListener() {
                public void onClick(View v) {
                    mActivity.startActivity(new Intent(mActivity, SetAlarmActivity.class));
                }
            });
        }
        addBarButton(bar, R.drawable.ic_m3_more_vert, R.string.metro_more, new View.OnClickListener() {
            public void onClick(View v) {
                more(v);
            }
        });
    }

    private void addBarButton(LinearLayout bar, int icon, int description, View.OnClickListener click) {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        ImageButton b = new ImageButton(mActivity);
        b.setImageResource(icon);
        b.setBackgroundResource(R.drawable.metro_circle_outline);
        b.setColorFilter(color(com.google.android.material.R.attr.colorOnSurface));
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        b.setContentDescription(mActivity.getString(description));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (44 * dp), (int) (44 * dp));
        lp.setMarginStart((int) (14 * dp));
        lp.setMarginEnd((int) (14 * dp));
        bar.addView(b, lp);
    }

    private void more(View anchor) {
        PopupMenu menu = new PopupMenu(mActivity, anchor);
        menu.getMenuInflater().inflate(R.menu.main, menu.getMenu());
        if (mSection == NOW_PLAYING || (mWide && dual(mSection))) {
            menu.getMenu().add(0, R.id.menu_play_queue, 0, R.string.now_playing_label);
            menu.getMenu().add(0, R.id.menu_item_stop, 0, R.string.stop);
        }
        menu.getMenu().add(0, R.id.metro_menu_about, 0, R.string.metro_tile_about);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_play_queue) {
                    mActivity.startActivity(new Intent(mActivity, NowPlayingActivity.class));
                } else if (id == R.id.menu_item_stop) {
                    try {
                        if (MusicUtils.sService != null) MusicUtils.sService.stop();
                    } catch (Exception ignored) {
                    }
                } else if (id == R.id.metro_menu_about) {
                    mActivity.startActivity(new Intent(mActivity, AboutActivity.class));
                } else {
                    return mActivity.onOptionsItemSelected(item);
                }
                return true;
            }
        });
        menu.show();
    }

    // ---- back and swipe ----

    /** Is there something for back to do here (rather than leaving the app)? */
    boolean wantsBack() {
        return mSearching || browseFragment() != null || mSection != STATIONS;
    }

    void back() {
        Fragment browse = browseFragment();
        if (mSearching) {
            search(false);
        } else if (browse instanceof BrowseFragment && mSection == EXPLORE && ((BrowseFragment) browse).canGoUp()) {
            ((BrowseFragment) browse).onBackKeyPressed();
        } else if (browse != null && mSection == EXPLORE) {
            mFm.beginTransaction().remove(browse).commitNow();
            show(STATIONS, -1);
        } else {
            show(STATIONS, -1);
        }
    }

    void onTouch(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // views that move sideways themselves are left alone
            mSwipe = new MetroSwipe(mActivity.getWindow().getDecorView(),
                    mActivity.findViewById(R.id.metro_np_seek),
                    mActivity.findViewById(R.id.metro_tabs),
                    mActivity.findViewById(R.id.metro_pivot_scroll),
                    mActivity.findViewById(R.id.metro_search));
        }
        if (mSwipe == null) return;
        int d = mSwipe.onTouchEvent(ev);
        if (d == MetroSwipe.LEFT) step(1);
        else if (d == MetroSwipe.RIGHT) step(-1);
    }
}
