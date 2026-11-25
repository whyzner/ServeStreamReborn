package net.sourceforge.servestream.utils;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/**
 * Metro panorama-style swipe: a quick, mostly horizontal swipe across the
 * screen. Decided by distance and time rather than fling speed (speed is
 * unreliable when the last move and the lift come in late). Swipes that start
 * at the screen edges (system back gesture) or on views that take horizontal
 * drags themselves (seek bar, tabs) are left alone.
 */
public class MetroSwipe {

    public static final int NONE = 0, LEFT = -1, RIGHT = 1;

    private final View mRoot;
    private final View[] mSkip;
    private final float mDp;
    private float mX, mY;
    private long mTime;
    private boolean mTracking;

    public MetroSwipe(View root, View... skip) {
        mRoot = root;
        mSkip = skip;
        mDp = root.getResources().getDisplayMetrics().density;
    }

    /** Feed every touch event; returns LEFT or RIGHT when a swipe has just finished. */
    public int onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mX = ev.getRawX();
                mY = ev.getRawY();
                mTime = ev.getEventTime();
                int width = mRoot.getWidth();
                mTracking = mX > 32 * mDp && mX < width - 32 * mDp && !onSkipped(mX, mY);
                return NONE;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                mTracking = false;
                return NONE;
            case MotionEvent.ACTION_UP:
                if (!mTracking) return NONE;
                mTracking = false;
                float dx = ev.getRawX() - mX, dy = ev.getRawY() - mY;
                long took = ev.getEventTime() - mTime;
                if (Math.abs(dx) > 120 * mDp && Math.abs(dy) < Math.abs(dx) / 2 && took < 700) {
                    return dx < 0 ? LEFT : RIGHT;
                }
                return NONE;
            default:
                return NONE;
        }
    }

    private boolean onSkipped(float x, float y) {
        Rect r = new Rect();
        for (View v : mSkip) {
            if (v != null && v.isShown() && v.getGlobalVisibleRect(r) && r.contains((int) x, (int) y)) {
                return true;
            }
        }
        return false;
    }
}
