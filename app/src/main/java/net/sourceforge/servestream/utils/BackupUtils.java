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

	public synchronized static void backup(Context context, Uri target) {
		String message;
		StreamDatabase streamdb = new StreamDatabase(context);
		List<UriBean> uris = streamdb.getUris();
		streamdb.close();

		if (uris.size() == 0) {
			message = context.getString(R.string.backup_no_data);
		} else {
			OutputStream out = null;
			try {
				out = context.getContentResolver().openOutputStream(target, "wt");
				if (out == null) {
					throw new IOException("Cannot open " + target);
				}
				out.write(writeJSON(uris).getBytes("UTF-8"));
				message = context.getString(R.string.backup_done, uris.size());
			} catch (Exception e) {
				message = context.getString(R.string.backup_failed) + "\n" + e.getMessage();
			} finally {
				try {
					if (out != null) out.close();
				} catch (IOException ignored) {
				}
			}
		}

		showBackupDialog(context, message);
	}

	public synchronized static void restore(Context context, Uri source) {
		String message;
		StreamDatabase streamdb = new StreamDatabase(context);
		InputStream in = null;

		try {
			in = context.getContentResolver().openInputStream(source);
			if (in == null) {
				throw new IOException("Cannot open " + source);
			}
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] chunk = new byte[8192];
			int n;
			while ((n = in.read(chunk)) > 0) {
				buffer.write(chunk, 0, n);
			}

			List<UriBean> uris = parseBackup(buffer.toString("UTF-8"));
			int added = 0;
			for (int i = 0; i < uris.size(); i++) {
				if (TransportFactory.findUri(streamdb, uris.get(i).getUri()) == null) {
					streamdb.saveUri(uris.get(i));
					added++;
				}
			}
			message = context.getString(R.string.restore_done, added);
		} catch (Exception e) {
			message = context.getString(R.string.restore_failed) + "\n" + e.getMessage();
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException ignored) {
			}
			streamdb.close();
		}

		showBackupDialog(context, message);
	}

	private static String writeJSON(List<UriBean> uris) throws JSONException {		
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
			array.put(js);
		}
			
		js = new JSONObject();
		js.put(UriBean.BEAN_NAME, array);
		root.put(ROOT_JSON_ELEMENT, js);
		
		return root.toString();
	}
	
	private static List<UriBean> parseBackup(String json) throws JSONException {
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
	      			}
	      		}
	      		
				uris.add(uriBean);
			}
		}

		return uris;
	}
}
