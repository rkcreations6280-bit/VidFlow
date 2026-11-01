package dataRepo.dbManager;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Single SQLite database used by the whole app (replaces the old ObjectBox store).
 * <p>
 * No annotation processor is needed. Tables:
 * <ul>
 * <li>{@code app_configs} - key/value rows of {@code AppConfigs}</li>
 * <li>{@code yt_stream_info} - cached YouTube stream metadata</li>
 * <li>{@code app_crashed_info} - last crash report (single row)</li>
 * </ul>
 * Call {@link #initialize(Context)} once from {@code Application.onCreate()}.
 */
public final class DbHelper extends SQLiteOpenHelper {

	private static final String DB_NAME = "app_data.db";
	private static final int DB_VERSION = 1;

	public static final String TABLE_CONFIGS = "app_configs";
	public static final String TABLE_STREAM_INFO = "yt_stream_info";
	public static final String TABLE_CRASHED_INFO = "app_crashed_info";

	private static volatile DbHelper instance;

	private DbHelper(Context context) {
		super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
	}

	public static void initialize(Context context) {
		if (instance == null) {
			synchronized (DbHelper.class) {
				if (instance == null) instance = new DbHelper(context);
			}
		}
	}

	public static SQLiteDatabase getDb() {
		if (instance == null) {
			throw new IllegalStateException("DbHelper is not initialized");
		}
		return instance.getWritableDatabase();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE_CONFIGS + " ("
			+ "key TEXT PRIMARY KEY NOT NULL, "
			+ "value TEXT)");

		db.execSQL("CREATE TABLE " + TABLE_STREAM_INFO + " ("
			+ "id INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ "url TEXT, "
			+ "stream_id TEXT, "
			+ "custom_category TEXT, "
			+ "name TEXT, "
			+ "description TEXT, "
			+ "upload_date INTEGER DEFAULT 0, "
			+ "duration INTEGER DEFAULT 0, "
			+ "view_counts INTEGER DEFAULT 0, "
			+ "cache_created_at INTEGER DEFAULT 0, "
			+ "cache_expiry_time INTEGER DEFAULT 0, "
			+ "uploader_name TEXT, "
			+ "uploader_url TEXT, "
			+ "uploader_avatar_url TEXT, "
			+ "thumbnail_url TEXT, "
			+ "category TEXT, "
			+ "tags TEXT, "
			+ "stream_type TEXT, "
			+ "service_id INTEGER DEFAULT 0, "
			+ "age_limit INTEGER DEFAULT 0, "
			+ "is_short_form INTEGER DEFAULT 0, "
			+ "has_subtitles INTEGER DEFAULT 0, "
			+ "last_watched_at INTEGER DEFAULT 0, "
			+ "watch_count INTEGER DEFAULT 0, "
			+ "recommendation_reason_ordinal INTEGER DEFAULT -1, "
			+ "content_source_ordinal INTEGER DEFAULT 0)");
		db.execSQL("CREATE INDEX idx_yt_url ON " + TABLE_STREAM_INFO + "(url)");
		db.execSQL("CREATE INDEX idx_yt_stream_id ON " + TABLE_STREAM_INFO + "(stream_id)");
		db.execSQL("CREATE INDEX idx_yt_category ON " + TABLE_STREAM_INFO + "(category)");
		db.execSQL("CREATE INDEX idx_yt_expiry ON " + TABLE_STREAM_INFO + "(cache_expiry_time)");
		db.execSQL("CREATE INDEX idx_yt_last_watched ON " + TABLE_STREAM_INFO + "(last_watched_at)");

		db.execSQL("CREATE TABLE " + TABLE_CRASHED_INFO + " ("
			+ "id INTEGER PRIMARY KEY, "
			+ "device_id TEXT, "
			+ "android_version TEXT, "
			+ "user_country TEXT, "
			+ "user_given_message TEXT, "
			+ "crash_timestamp TEXT, "
			+ "application_version TEXT, "
			+ "stack_trace TEXT, "
			+ "detailed_info TEXT)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Version 1 only. Add ALTER TABLE migrations here when DB_VERSION increases.
	}
}
