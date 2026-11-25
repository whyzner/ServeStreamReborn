/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2016 William Seemann
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

package net.sourceforge.servestream.activity;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.adapter.NavigationDrawerAdapter.ItemAccess;
import net.sourceforge.servestream.fragment.AlarmClockFragment;
import net.sourceforge.servestream.fragment.BrowseFragment;
import net.sourceforge.servestream.fragment.UrlListFragment;
import net.sourceforge.servestream.fragment.MetroHomeFragment;
import net.sourceforge.servestream.fragment.MetroNowPaneFragment;
import net.sourceforge.servestream.fragment.UrlListFragment.BrowseIntentListener;
import net.sourceforge.servestream.preference.UserPreferences;
import net.sourceforge.servestream.utils.DownloadScannerDialog;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import com.google.android.material.navigation.NavigationView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity implements
			ServiceConnection,
			BrowseIntentListener, ItemAccess,
			NavigationView.OnNavigationItemSelectedListener,
			MetroHomeFragment.Host {
	
	private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";
	
	private final static String DOWNLOAD_SCANNER_DIALOG = "download_scanner_dialog";

	private final static int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;

	private String mTag;
	
    private CharSequence mTitle;

	private ServiceToken mToken;
    
    private int mCurrentSelectedPosition = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		mMetroWide = isMetro() && metroWideWindow(this);
		setContentView(mMetroWide ? R.layout.activity_main_metro_wide : R.layout.activity_main);
		setUpMetroPanes();
		if (savedInstanceState == null) {
			// pictures of stations deleted by older versions would be picked up by new stations
			final android.content.Context app = getApplicationContext();
			new Thread(new Runnable() {
				public void run() {
					net.sourceforge.servestream.utils.StationImages.removeOrphans(app);
				}
			}, "StationImageCleanup").start();
		}
		if (savedInstanceState == null) {
			net.sourceforge.servestream.utils.CrashReporter.showIfAny(this);
		}
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		androidx.appcompat.app.ActionBarDrawerToggle toggle = new androidx.appcompat.app.ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.setDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		if (savedInstanceState == null) {
			int section = getIntent() != null ? getIntent().getIntExtra(EXTRA_METRO_SECTION, -1) : -1;
			String uri = getUri();
			if (section == 2) {
				selectItem(2);
			} else if (uri == null && isMetro() && !mMetroWide) {
				showMetroHome();
			} else {
				openUri(uri);
			}

			navigationView = (NavigationView) findViewById(R.id.nav_view);
			navigationView.setCheckedItem(R.id.nav_urls);
			//selectItem(0);
		}

		getOnBackPressedDispatcher().addCallback(this, mBackCallback);
		drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
			@Override public void onDrawerOpened(View v) { refreshBackCallback(); }
			@Override public void onDrawerClosed(View v) { refreshBackCallback(); }
		});
		setUpMetroSwipe();
		refreshBackCallback();

		mToken = MusicUtils.bindToService(this, this);

		requestPermission(this);
	}

    @Override
    public void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
		
        setIntent(intent);
    	
    	if (intent.hasExtra("restart_app")) {
    		Intent i = getIntent();
    		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		finish();
    		startActivity(i);
    	} else if (intent.getIntExtra(EXTRA_METRO_SECTION, -1) == 2) {
    		selectItem(2);
    	} else if (intent.getIntExtra(EXTRA_METRO_SECTION, -1) == 0) {
    		selectItem(0);
    	} else {
    		openUri(getUri());
    	}
    }
    
	@Override
	public void onDestroy() {
		super.onDestroy();

        MusicUtils.unbindFromService(mToken);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		// Restore the previously serialized current dropdown position.
		if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
			mTag = savedInstanceState.getString(STATE_SELECTED_NAVIGATION_ITEM);
		}
		updateMetroChrome();
	}

	/** Opens a section of the main screen from another screen (Metro tile menu). */
	public static final String EXTRA_METRO_SECTION = "metro_section";

	private boolean mMetroWide;

	/** Width class: tile menu column from 600dp, plus the now playing pane from 700dp. */
	public static boolean metroWideWindow(Activity a) {
		android.content.res.Configuration c = a.getResources().getConfiguration();
		return c.screenWidthDp >= 600 && c.screenHeightDp >= 480;
	}

	public static boolean metroThreePanes(Activity a) {
		android.content.res.Configuration c = a.getResources().getConfiguration();
		return c.screenWidthDp >= 700 && c.screenHeightDp >= 480;
	}

	/**
	 * Metro wide screens: tile menu column and (with room) the now playing pane.
	 * When the window gets narrow again (folding), panes restored from before are
	 * dropped so they do not look for a container that is gone.
	 */
	private void setUpMetroPanes() {
		FragmentManager fm = getSupportFragmentManager();
		Fragment rail = fm.findFragmentByTag(MetroHomeFragment.RAIL_TAG);
		Fragment now = fm.findFragmentByTag(MetroNowPaneFragment.TAG);
		if (mMetroWide) {
			if (rail == null) {
				fm.beginTransaction().replace(R.id.metro_rail_pane, MetroHomeFragment.rail(), MetroHomeFragment.RAIL_TAG).commitNow();
			}
			boolean three = metroThreePanes(this);
			findViewById(R.id.metro_now_pane).setVisibility(three ? View.VISIBLE : View.GONE);
			if (three && now == null) {
				fm.beginTransaction().replace(R.id.metro_now_pane, new MetroNowPaneFragment(), MetroNowPaneFragment.TAG).commitNow();
			} else if (!three && now != null) {
				fm.beginTransaction().remove(now).commitNow();
			}
			// the tiles are on screen already: no separate tile home here
			Fragment home = fm.findFragmentByTag(MetroHomeFragment.TAG);
			if (home != null) fm.beginTransaction().remove(home).commitNow();
		} else {
			FragmentTransaction ft = fm.beginTransaction();
			if (rail != null) ft.remove(rail);
			if (now != null) ft.remove(now);
			if (rail != null || now != null) ft.commitNow();
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// restored on a wide screen while the tile home was shown: show the list instead
		if (mMetroWide && MetroHomeFragment.TAG.equals(mTag)) {
			mTag = null;
			selectItem(0);
		}
	}

	private static boolean isMetro() {
		return net.sourceforge.servestream.utils.AppearanceSettings.isMetro();
	}

	/** Metro: shows the tile home in place of the current section. */
	public void showMetroHome() {
		FragmentManager fm = getSupportFragmentManager();
		Fragment current = fm.findFragmentByTag(mTag);
		FragmentTransaction ft = fm.beginTransaction();
		if (current != null && !MetroHomeFragment.TAG.equals(mTag)) {
			ft.detach(current);
		}
		Fragment home = fm.findFragmentByTag(MetroHomeFragment.TAG);
		if (home == null) {
			ft.add(R.id.content_frame, new MetroHomeFragment(), MetroHomeFragment.TAG);
		} else {
			ft.attach(home);
		}
		ft.commit();
		mTag = MetroHomeFragment.TAG;
		updateMetroChrome();
	}

	/** Leaves the Metro home (if shown) so another section can take its place. */
	private void leaveMetroHome() {
		if (!MetroHomeFragment.TAG.equals(mTag)) return;
		Fragment home = getSupportFragmentManager().findFragmentByTag(MetroHomeFragment.TAG);
		if (home != null) {
			getSupportFragmentManager().beginTransaction().detach(home).commit();
		}
		mTag = null;
	}

	/** The home has its own title row: no tool bar, no drawer while it is shown. */
	private void updateMetroChrome() {
		boolean home = MetroHomeFragment.TAG.equals(mTag);
		View bar = findViewById(R.id.app_bar);
		if (bar != null) bar.setVisibility(home ? View.GONE : View.VISIBLE);
		View frame = findViewById(R.id.content_frame);
		if (frame != null) {
			android.util.TypedValue tv = new android.util.TypedValue();
			int top = 0;
			if (!home && getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
				top = android.util.TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
			}
			frame.setPadding(frame.getPaddingLeft(), top, frame.getPaddingRight(), frame.getPaddingBottom());
		}
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		if (drawer != null) {
			drawer.setDrawerLockMode(home ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
		}
		refreshBackCallback();
		// Metro: the title bar names the section (Stations, Browse, Alarm)
		if (isMetro() && getSupportActionBar() != null) {
			if ("0".equals(mTag)) getSupportActionBar().setTitle(R.string.metro_tile_stations);
			else if ("1".equals(mTag)) getSupportActionBar().setTitle(R.string.browse_label);
			else if ("2".equals(mTag)) getSupportActionBar().setTitle(R.string.metro_tile_alarm);
		}
	}

	/** Metro home tile pressed. */
	public void openMetroTile(int which) {
		NavigationView nav = (NavigationView) findViewById(R.id.nav_view);
		switch (which) {
			case MetroHomeFragment.STATIONS:
				leaveMetroHome();
				selectItem(0);
				nav.setCheckedItem(R.id.nav_urls);
				break;
			case MetroHomeFragment.BROWSE:
				startActivity(new Intent(this, AddUrlActivity.class));
				break;
			case MetroHomeFragment.ALARM:
				leaveMetroHome();
				selectItem(2);
				nav.setCheckedItem(R.id.nav_alarm_clock);
				break;
			case MetroHomeFragment.NOW_PLAYING:
				if (MetroHomeFragment.currentStation() != null) {
					startActivity(new Intent(this, MediaPlayerActivity.class));
				} else {
					android.widget.Toast.makeText(this, R.string.metro_nothing_playing, android.widget.Toast.LENGTH_SHORT).show();
				}
				break;
			case MetroHomeFragment.SETTINGS:
				startActivity(new Intent(this, SettingsActivity.class));
				break;
			case MetroHomeFragment.ABOUT:
				startActivity(new Intent(this, AboutActivity.class));
				break;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Serialize the current dropdown position.
		outState.putString(STATE_SELECTED_NAVIGATION_ITEM, mTag);
	}
	
    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
         // The action bar home/up action should open or close the drawer.
         // ActionBarDrawerToggle will take care of this.

        // Handle action buttons
        switch (item.getItemId()) {
			case (R.id.menu_item_settings):
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case (R.id.menu_item_scan):
				try {
					Intent intent = new Intent("com.google.zxing.client.android.SCAN");
					intent.setPackage("com.google.zxing.client.android");
					intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
					startActivityForResult(intent, 0);
				} catch (ActivityNotFoundException ex) {
					showDialog(DOWNLOAD_SCANNER_DIALOG);
				}
        		return true;
			default:
				return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void startActivity(Intent intent) {      
        // check if search intent
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
        	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
        	if (fragment != null && fragment instanceof BrowseFragment) {
    			intent.putParcelableArrayListExtra("uris", ((BrowseFragment) fragment).getUris());
        	}
        }

        super.startActivity(intent);
    }
    
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
	    // hand results on to fragments (e.g. the station image picker)
	    super.onActivityResult(requestCode, resultCode, intent);
	    if (requestCode == 0) {
	        if (resultCode == RESULT_OK) {
	            String contents = intent.getStringExtra("SCAN_RESULT");
	            // Handle successful scan
	            openUri(contents);
	        } else if (resultCode == RESULT_CANCELED) {
	            // Handle cancel
	        }
	    }
	}
	
    private void selectItem(int position) {
    	leaveMetroHome();
    	mCurrentSelectedPosition = position;
    	
    	FragmentManager fragmentManager = getSupportFragmentManager();
    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
    	
    	if (fragment != null) {
    		fragmentManager.beginTransaction().detach(fragment).commit();
    	}
    	
    	String tag = String.valueOf(position);
    	fragment = getSupportFragmentManager().findFragmentByTag(tag);
    	
    	if (fragment == null) {
            if (position == 0) {
            	fragment = new UrlListFragment();
            	fragment.setArguments(new Bundle());
            } else if (position == 1) {
            	fragment = new BrowseFragment();
            	fragment.setArguments(new Bundle());
            } else if (position == 2) {
            	fragment = new AlarmClockFragment();
            }
    		
    		fragmentManager.beginTransaction().add(R.id.content_frame, fragment, tag).commit();
    	} else {
    		fragmentManager.beginTransaction().attach(fragment).commit();
    	}
    	
    	mTag = tag;
    	updateMetroChrome();
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
			return;
		}
		String storage = Build.VERSION.SDK_INT >= 33 ?
				Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
		for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
			if (storage.equals(permissions[i])) {
				SharedPreferences preferences = getSharedPreferences("Music", MODE_PRIVATE);
				preferences.edit().putBoolean("read_external_storage",
						grantResults[i] == PackageManager.PERMISSION_GRANTED).apply();
			}
		}
	}

	private void requestPermission(Activity activity) {
		java.util.ArrayList<String> wanted = new java.util.ArrayList<String>();
		if (Build.VERSION.SDK_INT >= 33) {
			wanted.add(Manifest.permission.POST_NOTIFICATIONS);
			wanted.add(Manifest.permission.READ_MEDIA_AUDIO);
		} else {
			wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE);
		}

		java.util.ArrayList<String> missing = new java.util.ArrayList<String>();
		for (String p : wanted) {
			if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
				missing.add(p);
			}
		}

		if (!missing.isEmpty()) {
			ActivityCompat.requestPermissions(activity, missing.toArray(new String[0]),
					MainActivity.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
		}
	}

    private String getUri() {
		String intentUri = null;
		String contentType = null;
		
		Intent intent = getIntent();
		
		if (intent == null) {
			return null;
		}
		
        // check to see if we were called from a home screen shortcut
		if ((contentType = intent.getType()) != null) {
			if (contentType.contains("net.sourceforge.servestream/")) {
				intentUri = intent.getType().toString().replace("net.sourceforge.servestream/", "");
				setIntent(null);
				return intentUri;
			}
		}
		
		// check to see if we were called by clicking on a URL
		if (intent.getData() != null) {
			intentUri = intent.getData().toString();
		}
		
		// check to see if the application was opened from a share intent
		if (intent.getExtras() != null && intent.getExtras().getCharSequence(Intent.EXTRA_TEXT) != null) {
			intentUri = intent.getExtras().getCharSequence(Intent.EXTRA_TEXT).toString();
		}

		setIntent(null);
		
		return intentUri;
    }
    
	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		//MusicUtils.updateNowPlaying(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		finish();
	}

	private void openUri(String uri) {
		leaveMetroHome();
		mCurrentSelectedPosition = 0;
		
		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, uri);
    	
    	FragmentManager fragmentManager = getSupportFragmentManager();
    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
    	
    	if (fragment != null) {
    		fragmentManager.beginTransaction().detach(fragment).commit();
    	}
    	
    	String tag = String.valueOf(0);
    	fragment = getSupportFragmentManager().findFragmentByTag(tag);
    	
    	if (fragment == null) {
           	fragment = new UrlListFragment();
           	fragment.setArguments(args);
    		fragmentManager.beginTransaction().add(R.id.content_frame, fragment, tag).commit();
    	} else {
           	fragment.getArguments().putString(UrlListFragment.ARG_TARGET_URI, uri);
    		fragmentManager.beginTransaction().attach(fragment).commit();
    	}
    	
    	mTag = tag;
    	updateMetroChrome();
	}
	
	@Override
	public void browseToUri(Uri uri) {
		leaveMetroHome();
		mCurrentSelectedPosition = 1;
		
		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, uri.toString());
    	
    	FragmentManager fragmentManager = getSupportFragmentManager();
    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
    	
    	if (fragment != null) {
    		fragmentManager.beginTransaction().detach(fragment).commit();
    	}
    	
    	String tag = String.valueOf(1);
    	fragment = getSupportFragmentManager().findFragmentByTag(tag);
    	
    	if (fragment == null) {
           	fragment = new BrowseFragment();
           	fragment.setArguments(args);
    		fragmentManager.beginTransaction().add(R.id.content_frame, fragment, tag).commit();
    	} else {
           	fragment.getArguments().putString(UrlListFragment.ARG_TARGET_URI, uri.toString());
    		fragmentManager.beginTransaction().attach(fragment).commit();
    	}
    	
    	mTag = tag;
    	updateMetroChrome();

		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setCheckedItem(R.id.nav_browse);
	}
	
	private void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction.  We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		// Create and show the dialog.
		newFragment = DownloadScannerDialog.newInstance();

		ft.add(0, newFragment, tag);
		ft.commit();
	}

	@Override
	public int getSelectedItemIndex() {
		return mCurrentSelectedPosition;
	}

	/**
	 * Back (key, button or gesture). Registered with the back dispatcher so the
	 * predictive back gesture works: this callback is only switched on while
	 * back has something to do here (drawer open, a folder being browsed,
	 * Metro section over the tile home); otherwise the system shows its
	 * back-to-home preview and closes the screen.
	 */
	private final androidx.activity.OnBackPressedCallback mBackCallback =
			new androidx.activity.OnBackPressedCallback(false) {
		@Override
		public void handleOnBackPressed() {
			DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
			Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
			if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
				drawer.closeDrawer(GravityCompat.START);
			} else if (fragment instanceof BrowseFragment) {
				((BrowseFragment) fragment).onBackKeyPressed();
			} else if (isMetro() && !mMetroWide && !MetroHomeFragment.TAG.equals(mTag)) {
				// Metro: back from a section goes to the tile home
				showMetroHome();
			} else {
				setEnabled(false);
				getOnBackPressedDispatcher().onBackPressed();
			}
			refreshBackCallback();
		}
	};

	private void refreshBackCallback() {
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
		boolean busy = (drawer != null && drawer.isDrawerOpen(GravityCompat.START))
				|| fragment instanceof BrowseFragment
				|| (isMetro() && !mMetroWide && mTag != null && !MetroHomeFragment.TAG.equals(mTag));
		mBackCallback.setEnabled(busy);
	}

	// ---- Metro: swipe from the station list to the player (panorama style) ----

	private android.view.GestureDetector mSwipe;

	private void setUpMetroSwipe() {
		if (!isMetro() || mMetroWide) return;
		final float dp = getResources().getDisplayMetrics().density;
		mSwipe = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
				if (e1 == null || !"0".equals(mTag)) return false;
				int width = getWindow().getDecorView().getWidth();
				// keep clear of the system back gesture at the screen edges
				if (e1.getX() < 32 * dp || e1.getX() > width - 32 * dp) return false;
				float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
				if (dx < -120 * dp && Math.abs(dy) < Math.abs(dx) / 2 && Math.abs(vx) > 600 * dp
						&& MetroHomeFragment.currentStation() != null) {
					startActivity(new Intent(MainActivity.this, MediaPlayerActivity.class));
					overridePendingTransition(R.anim.metro_slide_in_right, R.anim.metro_slide_out_left);
					return true;
				}
				return false;
			}
		});
	}

	@Override
	public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
		if (mSwipe != null) mSwipe.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}

	@SuppressWarnings("StatementWithEmptyBody")
	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();

		if (id == R.id.nav_urls) {
			selectItem(0);
		} else if (id == R.id.nav_browse) {
			selectItem(1);
		} else if (id == R.id.nav_alarm_clock) {
			selectItem(2);
		}

		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}
}