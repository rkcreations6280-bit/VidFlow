package sysModules.crashedHandler;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import dataRepo.dbManager.DbHelper;

/**
 * Stores the last crash report (single row, id = {@link AppCrashedInfo#APP_CRASHED_OBJECT_BOX_ID})
 * in SQLite so it can be shown on the next launch.
 */
public final class AppCrashedRepo {

	private AppCrashedRepo() {}

	public static void saveReplacing(AppCrashedInfo info) {
		SQLiteDatabase db = DbHelper.getDb();
		ContentValues v = new ContentValues();
		v.put("id", AppCrashedInfo.APP_CRASHED_OBJECT_BOX_ID);
		v.put("device_id", info.getDeviceId());
		v.put("android_version", info.getAndroidVersion());
		v.put("user_country", info.getUserCountry());
		v.put("user_given_message", info.getUserGivenMessage());
		v.put("crash_timestamp", info.getCrashTimestamp());
		v.put("application_version", info.getApplicationVersion());
		v.put("stack_trace", info.getStackStraceInfo());
		v.put("detailed_info", info.getDetailedInfo());
		db.insertWithOnConflict(DbHelper.TABLE_CRASHED_INFO, null, v,
			SQLiteDatabase.CONFLICT_REPLACE);
	}

	@Nullable
	public static AppCrashedInfo get() {
		try (Cursor c = DbHelper.getDb().query(DbHelper.TABLE_CRASHED_INFO, null,
			"id = ?", new String[]{String.valueOf(AppCrashedInfo.APP_CRASHED_OBJECT_BOX_ID)},
			null, null, null)) {
			if (!c.moveToFirst()) return null;
			AppCrashedInfo info = new AppCrashedInfo();
			info.id = AppCrashedInfo.APP_CRASHED_OBJECT_BOX_ID;
			info.setDeviceId(c.getString(c.getColumnIndexOrThrow("device_id")));
			info.setAndroidVersion(c.getString(c.getColumnIndexOrThrow("android_version")));
			info.setUserCountry(c.getString(c.getColumnIndexOrThrow("user_country")));
			info.setUserGivenMessage(c.getString(c.getColumnIndexOrThrow("user_given_message")));
			info.setCrashTimestamp(c.getString(c.getColumnIndexOrThrow("crash_timestamp")));
			info.setApplicationVersion(c.getString(c.getColumnIndexOrThrow("application_version")));
			info.setStackStraceInfo(c.getString(c.getColumnIndexOrThrow("stack_trace")));
			info.setDetailedInfo(c.getString(c.getColumnIndexOrThrow("detailed_info")));
			return info;
		}
	}

	public static void clear() {
		DbHelper.getDb().delete(DbHelper.TABLE_CRASHED_INFO, null, null);
	}
}
