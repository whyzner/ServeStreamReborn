/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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

package net.sourceforge.servestream.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.transport.TransportFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;

import net.sourceforge.servestream.R;

public class BackupUtils {

	private static final String BACKUP_FILE = "backup.json";
	private static final String ROOT_JSON_ELEMENT = "backup";
	private static final String BACKUP_DIRECTORY_PATH = "/ServeStream/backup/";
	/** Station image as base64 JPEG. Older versions simply ignore this key. */
	private static final String FIELD_IMAGE = "image";
	
	private static void showBackupDialog(final Context context, String message) {
    	androidx.appcompat.app.AlertDialog.Builder builder;
    	androidx.appcompat.app.AlertDialog alertDialog;
    	builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(context);
    	builder.setMessage(message)
    		.setCancelable(true)
    		.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int id) {
    				dialog.dismiss();
    			}
    		});
    	alertDialog = builder.create();
    	alertDialog.show();
	}
	
	/** File name suggested in the system "save" dialog. */
	public static final String SUGGESTED_NAME = "servestream-backup.json";

	/** Result of a backup or restore: stations written/added, images written/restored, images in the file. */
	public static final class Counts {
		public int stations;
		public int images;
		public int imagesInFile;
	}

	/** Writes all stations, with their images, as JSON. */
	public synchronized static Counts writeBackup(Context context, OutputStream out) throws IOException, JSONException {
		StreamDatabase streamdb = new StreamDatabase(context);
		List<UriBean> uris = streamdb.getUris();
		streamdb.close();
		Counts counts = new Counts();
		int[] images = new int[1];
		out.write(writeJSON(context, uris, images).getBytes("UTF-8"));
		out.flush();
		counts.stations = uris.size();
		counts.images = images[0];
		counts.imagesInFile = images[0];
		return counts;
	}

	/** Adds the stations of a backup that are missing here and brings back their images. */
	public synchronized static Counts readBackup(Context context, InputStream in) throws IOException, JSONException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[65536];
		int n;
		while ((n = in.read(chunk)) > 0) {
			buffer.write(chunk, 0, n);
		}

		List<String> images = new ArrayList<String>();
		List<UriBean> uris = parseBackup(buffer.toString("UTF-8"), images);
		Counts counts = new Counts();
		StreamDatabase streamdb = new StreamDatabase(context);
		try {
			for (int i = 0; i < uris.size(); i++) {
				String image = images.get(i);
				if (image != null) {
					counts.imagesInFile++;
				}
				UriBean station = TransportFactory.findUri(streamdb, uris.get(i).getUri());
				boolean added = false;
				if (station == null) {
					station = streamdb.saveUri(uris.get(i));
					counts.stations++;
					added = true;
				}
				// a star in the backup is kept for a station that is already here
				if (!added && station != null && uris.get(i).isFavorite() && !station.isFavorite()) {
					streamdb.setFavorite(station, true);
				}
				// a new station has no picture of its own yet: anything on disk under its
				// number is left over from a deleted station. Existing stations keep the
				// picture the user set on this phone.
				if (image != null && station != null && (added || !StationImages.has(context, station.getId()))) {
					try {
						if (StationImages.saveBytes(context, station.getId(),
								android.util.Base64.decode(image, android.util.Base64.DEFAULT))) {
							counts.images++;
						}
					} catch (IllegalArgumentException e) {
						android.util.Log.w("BackupUtils", "damaged image in backup", e);
					}
				}
			}
		} finally {
			streamdb.close();
		}
		return counts;
	}

	public static void backup(Context context, Uri target) {
		String message;
		OutputStream out = null;
		try {
			out = context.getContentResolver().openOutputStream(target, "wt");
			if (out == null) {
				throw new IOException("Cannot open " + target);
			}
			Counts counts = writeBackup(context, out);
			if (counts.stations == 0) {
				message = context.getString(R.string.backup_no_data);
			} else {
				message = context.getString(R.string.backup_done, counts.stations);
				if (counts.images > 0) {
					message += "\n" + context.getString(R.string.backup_images, counts.images);
				}
			}
		} catch (Exception e) {
			message = context.getString(R.string.backup_failed) + "\n" + e.getMessage();
		} finally {
			try {
				if (out != null) out.close();
			} catch (IOException ignored) {
			}
		}

		showBackupDialog(context, message);
	}

	public static void restore(Context context, Uri source) {
		String message;
		InputStream in = null;
		try {
			in = context.getContentResolver().openInputStream(source);
			if (in == null) {
				throw new IOException("Cannot open " + source);
			}
			Counts counts = readBackup(context, in);
			message = context.getString(R.string.restore_done, counts.stations);
			if (counts.images > 0) {
				message += "\n" + context.getString(R.string.restore_images, counts.images);
			} else if (counts.imagesInFile == 0) {
				message += "\n" + context.getString(R.string.restore_no_images);
			}
		} catch (Exception e) {
			message = context.getString(R.string.restore_failed) + "\n" + e.getMessage();
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException ignored) {
			}
		}

		showBackupDialog(context, message);
	}

	private static String writeJSON(Context context, List<UriBean> uris, int[] images) throws JSONException {		
		JSONObject root = new JSONObject();
		JSONArray array = new JSONArray();
		JSONObject js = new JSONObject();
		
		for (int i = 0; i < uris.size(); i++) {
			UriBean uriBean = uris.get(i);				
			
			js = new JSONObject();
			js.put(StreamDatabase.FIELD_STREAM_NICKNAME, uriBean.getNickname());
			js.put(StreamDatabase.FIELD_STREAM_PROTOCOL, uriBean.getProtocol());
			js.put(StreamDatabase.FIELD_STREAM_USERNAME, uriBean.getUsername());
			js.put(StreamDatabase.FIELD_STREAM_PASSWORD, uriBean.getPassword());
			js.put(StreamDatabase.FIELD_STREAM_HOSTNAME, uriBean.getHostname());
			js.put(StreamDatabase.FIELD_STREAM_PORT, uriBean.getPort());
			js.put(StreamDatabase.FIELD_STREAM_PATH, uriBean.getPath());
			js.put(StreamDatabase.FIELD_STREAM_QUERY, uriBean.getQuery());
			js.put(StreamDatabase.FIELD_STREAM_REFERENCE, uriBean.getReference());
			js.put(StreamDatabase.FIELD_STREAM_LASTCONNECT, uriBean.getLastConnect());
			if (uriBean.isFavorite()) {
				// only written when set, so older versions read these files unchanged
				js.put(StreamDatabase.FIELD_STREAM_FAVORITE, true);
			}
			byte[] image = StationImages.readBytes(context, uriBean.getId());
			if (image != null) {
				js.put(FIELD_IMAGE, android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP));
				images[0]++;
			}
			array.put(js);
		}
			
		js = new JSONObject();
		js.put(UriBean.BEAN_NAME, array);
		root.put(ROOT_JSON_ELEMENT, js);
		
		return root.toString();
	}
	
	private static List<UriBean> parseBackup(String json, List<String> images) throws JSONException {
		List<UriBean> uris = new ArrayList<UriBean>();
		{
			JSONObject js = new JSONObject(json);
			JSONArray tableRows = js.getJSONObject(ROOT_JSON_ELEMENT).getJSONArray(UriBean.BEAN_NAME);
			
			for (int i = 0; i < tableRows.length(); i++) {
				JSONObject row = tableRows.getJSONObject(i);
				UriBean uriBean = new UriBean();
				
	      		@SuppressWarnings("unchecked")
				Iterator<String> iterator = row.keys();
        		
	      		while (iterator.hasNext()) {
	      			String name = iterator.next();
	      		
	      			if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_NICKNAME)) {
	    				uriBean.setNickname(row.getString(StreamDatabase.FIELD_STREAM_NICKNAME));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PROTOCOL)) {
	      				uriBean.setProtocol(row.getString(StreamDatabase.FIELD_STREAM_PROTOCOL));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_USERNAME)) {
	    				uriBean.setUsername(row.getString(StreamDatabase.FIELD_STREAM_USERNAME));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PASSWORD)) {
	    				uriBean.setPassword(row.getString(StreamDatabase.FIELD_STREAM_PASSWORD));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_HOSTNAME)) {
	      				uriBean.setHostname(row.getString(StreamDatabase.FIELD_STREAM_HOSTNAME));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PORT)) {
	      				uriBean.setPort(row.getInt(StreamDatabase.FIELD_STREAM_PORT));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PATH)) {
	      				uriBean.setPath(row.getString(StreamDatabase.FIELD_STREAM_PATH));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_QUERY)) {
	      				uriBean.setQuery(row.getString(StreamDatabase.FIELD_STREAM_QUERY));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_REFERENCE)) {
	      				uriBean.setReference(row.getString(StreamDatabase.FIELD_STREAM_REFERENCE));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_LASTCONNECT)) {
	      				uriBean.setLastConnect(row.getLong(StreamDatabase.FIELD_STREAM_LASTCONNECT));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_FAVORITE)) {
	      				uriBean.setFavorite(row.optBoolean(StreamDatabase.FIELD_STREAM_FAVORITE, false));
	      			}
	      		}
	      		
				uris.add(uriBean);
				images.add(row.has(FIELD_IMAGE) ? row.optString(FIELD_IMAGE, null) : null);
			}
		}

		return uris;
	}
}
