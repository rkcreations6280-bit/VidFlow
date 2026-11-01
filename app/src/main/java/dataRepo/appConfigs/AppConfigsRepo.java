package dataRepo.appConfigs;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import coreUtils.library.process.LoggerUtils;
import dataRepo.dbManager.DbHelper;

/**
 * Stores the single {@link AppConfigs} object in SQLite as key/value rows
 * (one row per public instance field). New fields added to {@link AppConfigs}
 * work automatically; missing rows fall back to the field's default value.
 * <p>
 * Field names are the DB keys, so {@code AppConfigs} must not be obfuscated
 * (see the keep rule in proguard-rules.pro).
 */
public class AppConfigsRepo {

	private static final LoggerUtils logger = LoggerUtils.from(AppConfigsRepo.class);

	private static final Set<AppConfigsObserver> observers = new CopyOnWriteArraySet<>();
	private static final Map<String, Field> fields = new LinkedHashMap<>();
	private static final Map<String, String> lastSaved = new HashMap<>();
	private static volatile AppConfigs activeConfig;

	static {
		for (Field field : AppConfigs.class.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)
				|| Modifier.isTransient(modifiers) || field.isSynthetic()) continue;
			field.setAccessible(true);
			fields.put(field.getName(), field);
		}
	}

	private AppConfigsRepo() {}

	/** Loads configs from SQLite (creating defaults on first run). Needs DbHelper first. */
	public static synchronized void initialize() {
		AppConfigs config = new AppConfigs();
		try {
			SQLiteDatabase db = DbHelper.getDb();
			try (Cursor cursor = db.query(DbHelper.TABLE_CONFIGS,
				new String[]{"key", "value"}, null, null, null, null, null)) {
				while (cursor.moveToNext()) {
					String key = cursor.getString(0);
					String value = cursor.getString(1);
					Field field = fields.get(key);
					if (field == null || value == null) continue;
					try {
						setFieldValue(field, config, value);
						lastSaved.put(key, value);
					} catch (Exception error) {
						logger.error("Bad config value for: " + key, error);
					}
				}
			}
		} catch (Exception error) {
			logger.error("Error while loading app configs.", error);
		}
		activeConfig = config;
		persist(config);
	}

	/** Never returns null: falls back to defaults if the database could not be read. */
	public static AppConfigs getConfig() {
		AppConfigs config = activeConfig;
		if (config == null) {
			synchronized (AppConfigsRepo.class) {
				if (activeConfig == null) activeConfig = new AppConfigs();
				config = activeConfig;
			}
		}
		return config;
	}

	public static void save(AppConfigs config) {
		activeConfig = config;
		persist(config);
		notifyObservers(config);
	}

	private static synchronized void persist(AppConfigs config) {
		SQLiteDatabase db;
		try {
			db = DbHelper.getDb();
		} catch (Exception error) {
			logger.error("Database unavailable, configs not saved.", error);
			return;
		}
		db.beginTransaction();
		try {
			for (Map.Entry<String, Field> entry : fields.entrySet()) {
				String key = entry.getKey();
				String value;
				try {
					Object raw = entry.getValue().get(config);
					value = raw == null ? "" : String.valueOf(raw);
				} catch (IllegalAccessException error) {
					continue;
				}
				if (value.equals(lastSaved.get(key))) continue;
				ContentValues row = new ContentValues();
				row.put("key", key);
				row.put("value", value);
				db.insertWithOnConflict(DbHelper.TABLE_CONFIGS, null, row,
					SQLiteDatabase.CONFLICT_REPLACE);
				lastSaved.put(key, value);
			}
			db.setTransactionSuccessful();
		} catch (Exception error) {
			logger.error("Error while saving app configs.", error);
			lastSaved.clear();
		} finally {
			db.endTransaction();
		}
	}

	private static void setFieldValue(Field field, Object target, String value)
		throws IllegalAccessException {
		Class<?> type = field.getType();
		if (type == boolean.class) field.setBoolean(target, Boolean.parseBoolean(value));
		else if (type == int.class) field.setInt(target, Integer.parseInt(value));
		else if (type == long.class) field.setLong(target, Long.parseLong(value));
		else if (type == float.class) field.setFloat(target, Float.parseFloat(value));
		else if (type == double.class) field.setDouble(target, Double.parseDouble(value));
		else if (type == String.class) field.set(target, value);
	}

	private static void notifyObservers(AppConfigs config) {
		for (AppConfigsObserver observer : observers) {
			try {
				observer.onConfigChanged(config);
			} catch (Exception error) {
				logger.error("Error while notifying observers.", error);
			}
		}
	}

	public static void registerObserver(AppConfigsObserver observer) {
		observers.add(observer);
	}

	public static void unregisterObserver(AppConfigsObserver observer) {
		observers.remove(observer);
	}

	public static void release() {
		observers.clear();
	}
}
