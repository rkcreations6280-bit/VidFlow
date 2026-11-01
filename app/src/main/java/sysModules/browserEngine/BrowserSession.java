package sysModules.browserEngine;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the open tabs alive while the user moves between the bottom tabs.
 * Lives only in memory: nothing about open tabs is written to disk.
 */
public final class BrowserSession {

	public static final int MAX_TABS = 10;

	private static final BrowserSession INSTANCE = new BrowserSession();

	public static BrowserSession get() {
		return INSTANCE;
	}

	private final List<BrowserTab> tabs = new ArrayList<>();
	private int current = -1;

	/** Set by other screens (Home) to ask the browser to open something when it appears. */
	public String pendingSheet; // "history" or "bookmarks"
	public String pendingUrl;

	private BrowserSession() {}

	public List<BrowserTab> tabs() {
		return tabs;
	}

	public int count() {
		return tabs.size();
	}

	public BrowserTab current() {
		return current >= 0 && current < tabs.size() ? tabs.get(current) : null;
	}

	public BrowserTab newTab(Context context) {
		if (tabs.size() >= MAX_TABS) {
			int victim = current == 0 ? 1 : 0; // never close the tab we are opening from
			BrowserTab oldest = tabs.remove(victim);
			if (victim < current) current--;
			oldest.destroy();
		}
		BrowserTab tab = new BrowserTab(context);
		tabs.add(tab);
		current = tabs.size() - 1;
		return tab;
	}

	public void select(BrowserTab tab) {
		int index = tabs.indexOf(tab);
		if (index >= 0) current = index;
	}

	/** Closes a tab. Returns the tab that is now current (may be null if none are left). */
	public BrowserTab close(BrowserTab tab) {
		int index = tabs.indexOf(tab);
		if (index < 0) return current();
		tabs.remove(index);
		tab.destroy();
		if (tabs.isEmpty()) {
			current = -1;
		} else if (current >= tabs.size()) {
			current = tabs.size() - 1;
		} else if (index < current) {
			current--;
		} else if (index == current) {
			current = Math.min(index, tabs.size() - 1);
		}
		return current();
	}

	public void closeAll() {
		for (BrowserTab tab : tabs) tab.destroy();
		tabs.clear();
		current = -1;
	}

	/** Wipes cookies, site storage, cache and history. Must run on the main thread. */
	public void clearAllData(Context context) {
		try {
			CookieManager.getInstance().removeAllCookies(null);
			CookieManager.getInstance().flush();
			WebStorage.getInstance().deleteAllData();
		} catch (Throwable ignored) {
		}
		for (BrowserTab tab : tabs) {
			try {
				tab.webView.clearCache(true);
				tab.webView.clearFormData();
				tab.webView.clearHistory();
			} catch (Throwable ignored) {
			}
		}
		BrowserDb.get(context).clearHistory();
	}
}
