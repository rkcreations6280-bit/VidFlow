package sysModules.browserEngine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Small local settings store for the browser. Everything stays on the phone.
 * (Ad blocker, popup blocker, JavaScript, images, desktop mode and private mode
 * live in AppConfigs, as before.)
 */
public final class BrowserPrefs {

	public static final String TRACKER_BLOCK = "tracker_block";
	public static final String TOUCH_BLOCK = "touch_block";
	public static final String REDIRECT_BLOCK = "redirect_block";
	public static final String DOWNLOAD_PREVENT = "download_prevent";
	public static final String HTTPS_UPGRADE = "https_upgrade";
	public static final String THIRD_PARTY_COOKIES_BLOCK = "tp_cookies_block";
	/** Small floating button on playing videos that opens them in the app's own player. */
	public static final String PLAY_BUTTON = "play_button";

	private static final String PREFS = "browser_prefs";
	private static final String KEY_ENGINE = "search_engine";
	private static final String KEY_ALLOWED = "allowed_sites";
	private static final String KEY_BLOCKED_TOTAL = "blocked_total";

	public static final String[] ENGINE_NAMES = {"DuckDuckGo", "Brave Search", "Startpage"};
	private static final String[] ENGINE_URLS = {
		"https://duckduckgo.com/?q=",
		"https://search.brave.com/search?q=",
		"https://www.startpage.com/do/search?query="
	};

	private BrowserPrefs() {}

	private static SharedPreferences prefs(Context context) {
		return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	/** All protections are ON unless the user turns them off. */
	public static boolean get(Context context, String key) {
		return prefs(context).getBoolean(key, true);
	}

	public static void set(Context context, String key, boolean value) {
		prefs(context).edit().putBoolean(key, value).apply();
	}

	public static int engine(Context context) {
		int value = prefs(context).getInt(KEY_ENGINE, 0);
		return value < 0 || value >= ENGINE_URLS.length ? 0 : value;
	}

	public static void setEngine(Context context, int index) {
		prefs(context).edit().putInt(KEY_ENGINE, index).apply();
	}

	public static String searchUrl(Context context, String query) {
		return ENGINE_URLS[engine(context)] + android.net.Uri.encode(query);
	}

	// Sites where the user switched protection off.

	public static boolean isSiteAllowed(Context context, String host) {
		if (host == null) return false;
		return prefs(context).getStringSet(KEY_ALLOWED, new HashSet<>()).contains(host);
	}

	public static void setSiteAllowed(Context context, String host, boolean allowed) {
		if (host == null) return;
		Set<String> sites = new HashSet<>(prefs(context).getStringSet(KEY_ALLOWED, new HashSet<>()));
		if (allowed) sites.add(host); else sites.remove(host);
		prefs(context).edit().putStringSet(KEY_ALLOWED, sites).apply();
	}

	public static long blockedTotal(Context context) {
		return prefs(context).getLong(KEY_BLOCKED_TOTAL, 0);
	}

	public static void addBlocked(Context context, int count) {
		SharedPreferences p = prefs(context);
		p.edit().putLong(KEY_BLOCKED_TOTAL, p.getLong(KEY_BLOCKED_TOTAL, 0) + count).apply();
	}
}
