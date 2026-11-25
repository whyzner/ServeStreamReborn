/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream.adapter;

import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.utils.OverflowClickListener;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
 
public class UrlListAdapter extends ArrayAdapter<UriBean> {
    private Context mContext;
    private List<UriBean> mRowItems;
    private OverflowClickListener mListener;
    private Runnable mFavoriteChanged;
    private String mPlaying;

    /** Called after a star was switched on or off (Metro). */
    public void setOnFavoriteChanged(Runnable r) {
        mFavoriteChanged = r;
    }

    @Override
    public void notifyDataSetChanged() {
        mPlaying = playingUri();
        super.notifyDataSetChanged();
    }

    private static String playingUri() {
        try {
            if (net.sourceforge.servestream.utils.MusicUtils.sService != null) {
                return net.sourceforge.servestream.utils.MusicUtils.sService.getMediaUri();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isPlaying(UriBean uri) {
        if (mPlaying == null) return false;
        try {
            return mPlaying.equals(uri.getUri().toString()) || mPlaying.equals(uri.getScrubbedUri().toString());
        } catch (Exception e) {
            return false;
        }
    }
 
    public UrlListAdapter(Context context, List<UriBean> rowItems, OverflowClickListener listener) {
		super(context, R.layout.list_item_uri, rowItems);
        mContext = context;
        mRowItems = rowItems;
        mListener = listener;
    }
 
    /*private view holder class*/
    private class ViewHolder {
        TextView nickname;
        TextView caption;
        ImageView overflowMenuButton;
        ImageView image;
        ImageView favorite;
    }
 
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
 
        LayoutInflater mInflater = (LayoutInflater)
        		mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.list_item_uri, null);
            holder = new ViewHolder();
			holder.nickname = (TextView) convertView.findViewById(android.R.id.text1);
			holder.caption = (TextView) convertView.findViewById(android.R.id.text2);
			holder.overflowMenuButton = (ImageView) convertView.findViewById(R.id.overflow_menu_button);
			holder.image = (ImageView) convertView.findViewById(R.id.station_image);
			holder.favorite = (ImageView) convertView.findViewById(R.id.favorite_button);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
 
        final UriBean uri = mRowItems.get(position);

		holder.nickname.setText(uri.getNickname());

		if (holder.image != null) {
			android.graphics.Bitmap bmp = net.sourceforge.servestream.utils.StationImages.load(mContext, uri.getId());
			if (bmp != null) {
				holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
				holder.image.setImageBitmap(bmp);
			} else {
				holder.image.setScaleType(ImageView.ScaleType.CENTER);
				holder.image.setImageResource(R.drawable.ic_m3_radio);
			}
		}

		long now = System.currentTimeMillis() / 1000;

		String lastConnect = mContext.getString(R.string.bind_never);
		if (uri.getLastConnect() > 0) {
			int minutes = (int)((now - uri.getLastConnect()) / 60);
			if (minutes >= 60) {
				int hours = (minutes / 60);
				if (hours >= 24) {
					int days = (hours / 24);
					lastConnect = mContext.getString(R.string.bind_days, days);
				} else
					lastConnect = mContext.getString(R.string.bind_hours, hours);
			} else
				lastConnect = mContext.getString(R.string.bind_minutes, minutes);
		}

		holder.caption.setText(lastConnect);

		boolean metro = net.sourceforge.servestream.utils.AppearanceSettings.isMetro();
		if (holder.favorite != null) {
			holder.favorite.setVisibility(metro ? View.VISIBLE : View.GONE);
			if (metro) {
				bindStar(holder.favorite, uri);
				holder.favorite.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						net.sourceforge.servestream.database.StreamDatabase db =
								new net.sourceforge.servestream.database.StreamDatabase(mContext);
						try {
							db.setFavorite(uri, !uri.isFavorite());
						} finally {
							db.close();
						}
						bindStar((ImageView) v, uri);
						v.announceForAccessibility(v.getContentDescription());
						if (mFavoriteChanged != null) mFavoriteChanged.run();
					}
				});
			}
		}
		// Metro: the station being played is shown on a coloured row
		if (metro && isPlaying(uri)) {
			android.util.TypedValue tv = new android.util.TypedValue();
			mContext.getTheme().resolveAttribute(R.attr.metroSelectedRow, tv, true);
			convertView.setBackgroundColor(tv.data);
		} else {
			convertView.setBackground(null);
		}
		if (holder.overflowMenuButton != null) {
			holder.overflowMenuButton.setOnClickListener(new View.OnClickListener() {
			
				@Override
				public void onClick(View v) {
					if (mListener != null) {
						mListener.onClick(v, uri);
					}
				}
			
			});
		}

        return convertView;
    }

    private void bindStar(ImageView star, UriBean uri) {
        android.util.TypedValue tv = new android.util.TypedValue();
        mContext.getTheme().resolveAttribute(uri.isFavorite()
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true);
        star.setImageResource(uri.isFavorite() ? R.drawable.ic_m3_star : R.drawable.ic_m3_star_border);
        star.setColorFilter(tv.data);
        star.setContentDescription(mContext.getString(uri.isFavorite()
                ? R.string.metro_favorite_remove : R.string.metro_favorite_add));
    }
}