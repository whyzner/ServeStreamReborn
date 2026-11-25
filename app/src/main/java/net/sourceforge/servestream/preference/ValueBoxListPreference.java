package net.sourceforge.servestream.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import net.sourceforge.servestream.R;

/**
 * A list preference that, when given the Metro drop-down widget, shows its
 * current choice in that box instead of as a summary line. Without the
 * widget it behaves exactly like a normal ListPreference.
 */
public class ValueBoxListPreference extends android.preference.ListPreference {

    public ValueBoxListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        TextView box = view.findViewById(R.id.metro_value);
        if (box != null) {
            CharSequence entry = getEntry();
            box.setText(entry != null ? entry : "");
            View summary = view.findViewById(android.R.id.summary);
            if (summary != null) summary.setVisibility(View.GONE);
        }
    }
}
