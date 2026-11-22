/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
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

package net.sourceforge.servestream.fragment;

import java.math.BigInteger;
import java.security.SecureRandom;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.AboutActivity;
import net.sourceforge.servestream.activity.BluetoothOptionsActivity;
import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.utils.BackupUtils;
import net.sourceforge.servestream.utils.Constants;
import net.sourceforge.servestream.preference.PreferenceConstants;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

public class SettingsFragment extends net.sourceforge.servestream.preference.PreferenceFragment {
	private static final String TAG = SettingsFragment.class.getName();

	
	private static final String PREF_BLUETOOTH_OPTIONS = "bluetooth_options";
	private static final String PREF_BACKUP = "backup";
	private static final String PREF_RESTORE = "restore";
	private static final String PREF_ABOUT = "about";
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		// Always (re)build: after a theme or language change the activity is
		// recreated and the restored fragment starts with an empty screen.
		{
			addPreferencesFromResource(R.xml.preferences);

			findPreference(PREF_BLUETOOTH_OPTIONS).setOnPreferenceClickListener(
					new OnPreferenceClickListener() {

						@Override
						public boolean onPreferenceClick(Preference preference) {
							getActivity().startActivity(new Intent(
									getActivity(), BluetoothOptionsActivity.class));
							return true;
						}

					});

			findPreference(PREF_ABOUT).setOnPreferenceClickListener(
					new OnPreferenceClickListener() {

						@Override
						public boolean onPreferenceClick(Preference preference) {
							getActivity().startActivity(new Intent(
									getActivity(), AboutActivity.class));
							return true;
						}

					});

			android.preference.ListPreference themePref = (android.preference.ListPreference)
					findPreference(net.sourceforge.servestream.utils.AppearanceSettings.KEY_THEME);
			themePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					int wasRetro = net.sourceforge.servestream.utils.AppearanceSettings.retroStyle();
					// switched when the user leaves the app: doing it now would close this screen
					net.sourceforge.servestream.utils.AppearanceSettings.requestIcon(getActivity(), (String) newValue);
					boolean modeChanged = net.sourceforge.servestream.utils.AppearanceSettings.applyTheme((String) newValue);
					if (wasRetro != net.sourceforge.servestream.utils.AppearanceSettings.retroStyle() && !modeChanged) {
						// same day/night mode: redraw this screen ourselves (after the choice is saved)
						final android.app.Activity activity = getActivity();
						new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
							public void run() {
								if (activity != null && !activity.isFinishing()) activity.recreate();
							}
						});
					}
					return true;
				}
			});

			android.preference.ListPreference languagePref = (android.preference.ListPreference)
					findPreference(net.sourceforge.servestream.utils.AppearanceSettings.KEY_LANGUAGE);
			languagePref.setValue(net.sourceforge.servestream.utils.AppearanceSettings.currentLanguage());
			languagePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					net.sourceforge.servestream.utils.AppearanceSettings.applyLanguage((String) newValue);
					return true;
				}
			});

			/*findPreference(PreferenceConstants.THEME).setOnPreferenceChangeListener(
                    new OnPreferenceChangeListener() {

                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                			Intent intent = new Intent(getActivity(), MainActivity.class);
                			intent.putExtra("restart_app", true);
                			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                			startActivity(intent);
                			getActivity().finish();
                            return true;
                        }
                    });*/
		}
	}

	private static final int REQUEST_BACKUP = 0x5b01;
	private static final int REQUEST_RESTORE = 0x5b02;

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ((requestCode == REQUEST_BACKUP || requestCode == REQUEST_RESTORE)) {
			if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
				if (requestCode == REQUEST_BACKUP) {
					BackupUtils.backup(getActivity(), data.getData());
				} else {
					BackupUtils.restore(getActivity(), data.getData());
				}
			}
			return;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen, Preference preference) {
		if (preference.getKey() != null) {
			if (preference.getKey().equals(PREF_BACKUP)) {
				Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
						.addCategory(Intent.CATEGORY_OPENABLE)
						.setType("application/json")
						.putExtra(Intent.EXTRA_TITLE, BackupUtils.SUGGESTED_NAME);
				startActivityForResult(intent, REQUEST_BACKUP);
			} else if (preference.getKey().equals(PREF_RESTORE)) {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
						.addCategory(Intent.CATEGORY_OPENABLE)
						.setType("*/*");
				startActivityForResult(intent, REQUEST_RESTORE);
			} 
		}
		
		return true;
	}

}