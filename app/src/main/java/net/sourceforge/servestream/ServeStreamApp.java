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

package net.sourceforge.servestream;

import net.sourceforge.servestream.preference.UserPreferences;
import android.app.Application;

/** Main application class. */
public class ServeStreamApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		net.sourceforge.servestream.utils.CrashReporter.install(this);
		net.sourceforge.servestream.utils.EdgeToEdge.install(this);
		// wallpaper colours, except in the classic look which has fixed colours
		com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this,
				new com.google.android.material.color.DynamicColorsOptions.Builder()
						.setPrecondition(new com.google.android.material.color.DynamicColors.Precondition() {
							@Override
							public boolean shouldApplyDynamicColors(android.app.Activity activity, int theme) {
								return !net.sourceforge.servestream.utils.AppearanceSettings.isClassic();
							}
						}).build());
		net.sourceforge.servestream.utils.AppearanceSettings.install(this);
		UserPreferences.createInstance(this);
		net.sourceforge.servestream.utils.AppearanceSettings.applyTheme(
				android.preference.PreferenceManager.getDefaultSharedPreferences(this).getString(
						net.sourceforge.servestream.utils.AppearanceSettings.KEY_THEME, "system"));
	}
}
