package sysModules.browserEngine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local history + bookmarks. Stored only on this phone, never uploaded. */
public final class BrowserDb extends SQLiteOpenHelper {

	public static final class Entry {
		public long id;
		public String title;
		public String url;
		public long time;
	}

	private static volatile BrowserDb instance;

	public static BrowserDb get(Context context) {
		if (instance == null) {
			synchronized (BrowserDb.class) {
				if (instance == null) instance = new BrowserDb(context.getApplicationContext());
			}
		}
		return instance;
	}

	private BrowserDb(Context context) {
		super(context, "browser_data.db", null, 1);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT UNIQUE, "
			+ "title TEXT, time INTEGER)");
		db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT UNIQUE, "
			+ "title TEXT, time INTEGER)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	// ── History ──────────────────────────────────────────────────────

	public synchronized void addHistory(String url, String title) {
		try {
			ContentValues values = new ContentValues();
			values.put("url", url);
			values.put("title", title == null || title.isEmpty() ? url : title);
			values.put("time", System.currentTimeMillis());
			getWritableDatabase().insertWithOnConflict("history", null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
			getWritableDatabase().execSQL("DELETE FROM history WHERE id NOT IN "
				+ "(SELECT id FROM history ORDER BY time DESC LIMIT 1000)");
		} catch (Throwable ignored) {
		}
	}

	public synchronized List<Entry> history(int limit) {
		return read("history", null, null, limit);
	}

	public synchronized void deleteHistory(long id) {
		try {
			getWritableDatabase().delete("history", "id=?", new String[]{String.valueOf(id)});
		} catch (Throwable ignored) {
		}
	}

	public synchronized void clearHistory() {
		try {
			getWritableDatabase().delete("history", null, null);
		} catch (Throwable ignored) {
		}
	}

	// ── Bookmarks ────────────────────────────────────────────────────

	public synchronized void addBookmark(String url, String title) {
		try {
			ContentValues values = new ContentValues();
			values.put("url", url);
			values.put("title", title == null || title.isEmpty() ? url : title);
			values.put("time", System.currentTimeMillis());
			getWritableDatabase().insertWithOnConflict("bookmarks", null, values,
				SQLiteDatabase.CONFLICT_REPLACE);
		} catch (Throwable ignored) {
		}
	}

	public synchronized void removeBookmark(String url) {
		try {
			getWritableDatabase().delete("bookmarks", "url=?", new String[]{url});
		} catch (Throwable ignored) {
		}
	}

	public synchronized boolean isBookmarked(String url) {
		if (url == null) return false;
		try (Cursor c = getReadableDatabase().rawQuery(
			"SELECT 1 FROM bookmarks WHERE url=? LIMIT 1", new String[]{url})) {
			return c.moveToFirst();
		} catch (Throwable e) {
			return false;
		}
	}

	public synchronized List<Entry> bookmarks() {
		return read("bookmarks", null, null, 500);
	}

	/** Matches in history + bookmarks for the address bar suggestions. */
	public synchronized List<Entry> search(String text, int limit) {
		List<Entry> result = new ArrayList<>();
		if (text == null || text.trim().isEmpty()) return result;
		String like = "%" + text.trim().replace("%", "").replace("_", "") + "%";
		result.addAll(read("bookmarks", "url LIKE ? OR title LIKE ?", new String[]{like, like}, limit));
		for (Entry e : read("history", "url LIKE ? OR title LIKE ?", new String[]{like, like}, limit)) {
			boolean dup = false;
			for (Entry r : result) if (r.url.equals(e.url)) dup = true;
			if (!dup) result.add(e);
			if (result.size() >= limit) break;
		}
		return result.size() > limit ? result.subList(0, limit) : result;
	}

	private List<Entry> read(String table, String where, String[] args, int limit) {
		List<Entry> list = new ArrayList<>();
		try (Cursor c = getReadableDatabase().query(table, new String[]{"id", "url", "title", "time"},
			where, args, null, null, "time DESC", String.valueOf(limit))) {
			while (c.moveToNext()) {
				Entry e = new Entry();
				e.id = c.getLong(0);
				e.url = c.getString(1);
				e.title = c.getString(2);
				e.time = c.getLong(3);
				list.add(e);
			}
		} catch (Throwable ignored) {
		}
		return list;
	}
}
