package sysModules.downloadSys;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small list of finished downloads, kept in SharedPreferences. */
public final class DownloadHistory {

	public static final class Item {
		public String title;
		public String uri;
		public boolean audio;
		public long size;
		public long time;
	}

	private static final String PREFS = "download_history";
	private static final String KEY = "items";
	private static final int MAX_ITEMS = 200;

	private DownloadHistory() {}

	public static synchronized void add(Context context, Item item) {
		try {
			JSONArray old = read(context);
			JSONArray fresh = new JSONArray();
			JSONObject json = new JSONObject();
			json.put("title", item.title);
			json.put("uri", item.uri);
			json.put("audio", item.audio);
			json.put("size", item.size);
			json.put("time", item.time);
			fresh.put(json);
			for (int i = 0; i < old.length() && fresh.length() < MAX_ITEMS; i++) {
				fresh.put(old.get(i));
			}
			prefs(context).edit().putString(KEY, fresh.toString()).apply();
		} catch (Exception ignored) {
		}
	}

	/** Newest first. {@code filter}: "all", "audio" or "video". */
	public static synchronized List<Item> list(Context context, String filter) {
		List<Item> result = new ArrayList<>();
		try {
			JSONArray array = read(context);
			for (int i = 0; i < array.length(); i++) {
				JSONObject json = array.getJSONObject(i);
				Item item = new Item();
				item.title = json.optString("title", "Video");
				item.uri = json.optString("uri", "");
				item.audio = json.optBoolean("audio", false);
				item.size = json.optLong("size", 0);
				item.time = json.optLong("time", 0);
				if ("audio".equals(filter) && !item.audio) continue;
				if ("video".equals(filter) && item.audio) continue;
				result.add(item);
			}
		} catch (Exception ignored) {
		}
		return result;
	}

	public static synchronized void remove(Context context, String uri) {
		try {
			JSONArray old = read(context);
			JSONArray fresh = new JSONArray();
			for (int i = 0; i < old.length(); i++) {
				JSONObject json = old.getJSONObject(i);
				if (!uri.equals(json.optString("uri"))) fresh.put(json);
			}
			prefs(context).edit().putString(KEY, fresh.toString()).apply();
		} catch (Exception ignored) {
		}
	}

	public static synchronized void rename(Context context, String uri, String newTitle) {
		try {
			JSONArray old = read(context);
			for (int i = 0; i < old.length(); i++) {
				JSONObject json = old.getJSONObject(i);
				if (uri.equals(json.optString("uri"))) json.put("title", newTitle);
			}
			prefs(context).edit().putString(KEY, old.toString()).apply();
		} catch (Exception ignored) {
		}
	}

	private static JSONArray read(Context context) throws Exception {
		return new JSONArray(prefs(context).getString(KEY, "[]"));
	}

	private static SharedPreferences prefs(Context context) {
		return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}
}
