package sysModules.newPipeLib.cache;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import dataRepo.dbManager.DbHelper;

/**
 * SQLite-backed cache of {@link YtStreamInfo}. Call {@link #initialize()} after
 * {@link DbHelper#initialize(android.content.Context)}.
 */
public final class YtStreamInfoRepo {
    private static final LoggerUtils logger = LoggerUtils.from(YtStreamInfoRepo.class);
    private static final String TABLE = DbHelper.TABLE_STREAM_INFO;
    private static volatile boolean initialized = false;

    private YtStreamInfoRepo() {}

    public static void initialize() {
        initialized = true;
        pruneOldCache();
    }

    public static YtStreamInfo findStreamInfo(String url) {
        if (!initialized || url == null) return null;

        String streamId = YtStreamInfo.extractVideoId(url);
        if (streamId == null) return null;

        YtStreamInfo cached = getByStreamId(streamId);
        if (cached != null) {
            if (cached.isExpired()) {
                logger.debug("Cache expired. Refreshing background...");
                refreshInBackground(cached);
            }
            return cached;
        }
        return null;
    }

    private static void refreshInBackground(YtStreamInfo expiredInfo) {
        new ThreadTask.Builder<YtStreamInfo, Void>()
                .withBackgroundTask(callback -> {
                    expiredInfo.updateStreamDetails();
                    return expiredInfo;
                })
                .withResultTask(updatedInfo -> {
                    if (updatedInfo != null) {
                        save(updatedInfo);
                        String message = "Background refresh complete for: " +
                                updatedInfo.name;
                        logger.debug(message);
                    }
                })
                .withErrorTask(error -> {
                    String message1 = "Failed to refresh cache in background";
                    logger.error(message1, error);
                })
                .build()
                .start();
    }

    public static void pruneOldCache() {
        if (!initialized) return;
        long twoWeekAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L);
        try {
            DbHelper.getDb().delete(TABLE, "cache_expiry_time < ?",
                    new String[]{String.valueOf(twoWeekAgo)});
        } catch (Exception error) {
            logger.error("Failed pruning old cache", error);
        }
    }

    public static synchronized void save(YtStreamInfo info) {
        if (info == null || !initialized) return;
        try {
            saveInternal(DbHelper.getDb(), info);
        } catch (Exception error) {
            logger.error("Failed saving stream info", error);
        }
    }

    public static synchronized void saveAll(List<YtStreamInfo> list) {
        if (!initialized || list == null || list.isEmpty()) return;
        SQLiteDatabase db = DbHelper.getDb();
        db.beginTransaction();
        try {
            for (YtStreamInfo info : list) saveInternal(db, info);
            db.setTransactionSuccessful();
        } catch (Exception error) {
            logger.error("Failed saving stream info list", error);
        } finally {
            db.endTransaction();
        }
    }

    private static void saveInternal(SQLiteDatabase db, YtStreamInfo info) {
        if (info == null) return;
        if (info.streamId != null) {
            YtStreamInfo existing = getByStreamId(info.streamId);
            if (existing != null) info.dbId = existing.dbId;
        }
        long rowId = db.insertWithOnConflict(TABLE, null, toValues(info),
                SQLiteDatabase.CONFLICT_REPLACE);
        if (rowId > 0) info.dbId = rowId;
    }

    public static void delete(YtStreamInfo info) {
        if (info == null) return;
        deleteById(info.dbId);
    }

    public static void deleteById(long id) {
        if (!initialized) return;
        DbHelper.getDb().delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
    }

    public static void clearAll() {
        if (!initialized) return;
        DbHelper.getDb().delete(TABLE, null, null);
    }

    public static YtStreamInfo getByUrl(String url) {
        if (!initialized || url == null) return null;
        return queryFirst("url = ?", new String[]{url}, null);
    }

    public static YtStreamInfo getByStreamId(String streamId) {
        if (!initialized || streamId == null) return null;
        return queryFirst("stream_id = ?", new String[]{streamId}, null);
    }

    public static List<YtStreamInfo> getByCategory(String category, int limit) {
        if (!initialized || category == null) return null;
        return queryList("category = ?", new String[]{category},
                "cache_created_at DESC", String.valueOf(limit));
    }

    public static List<YtStreamInfo> getRecentlyWatched(int limit) {
        if (!initialized) return null;
        return queryList("last_watched_at > 0", null,
                "last_watched_at DESC", String.valueOf(limit));
    }

    public static void recordWatchEvent(String url) {
        ThreadTask.executeInBackground(() -> {
            YtStreamInfo info = getByUrl(url);
            if (info == null) {
                return;
            }
            info.lastWatchedAt = System.currentTimeMillis();
            info.watchCount++;
            save(info);
            logger.debug("Watch event recorded for: " + info.name);
        });
    }

    public static List<YtStreamInfo> getExpiredCache() {
        if (!initialized) return null;
        return queryList("cache_expiry_time < ?",
                new String[]{String.valueOf(System.currentTimeMillis())}, null, null);
    }

    // ── SQLite helpers ───────────────────────────────────────────────

    private static YtStreamInfo queryFirst(String where, String[] args, String orderBy) {
        List<YtStreamInfo> list = queryList(where, args, orderBy, "1");
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<YtStreamInfo> queryList(String where, String[] args,
                                                String orderBy, String limit) {
        List<YtStreamInfo> result = new ArrayList<>();
        try (Cursor c = DbHelper.getDb().query(TABLE, null, where, args,
                null, null, orderBy, limit)) {
            while (c.moveToNext()) result.add(fromCursor(c));
        } catch (Exception error) {
            logger.error("Failed querying stream info", error);
        }
        return result;
    }

    private static ContentValues toValues(YtStreamInfo i) {
        ContentValues v = new ContentValues();
        if (i.dbId > 0) v.put("id", i.dbId);
        v.put("url", i.url);
        v.put("stream_id", i.streamId);
        v.put("custom_category", i.customCategory);
        v.put("name", i.name);
        v.put("description", i.description);
        v.put("upload_date", i.uploadDate);
        v.put("duration", i.duration);
        v.put("view_counts", i.viewCounts);
        v.put("cache_created_at", i.cacheCreatedAt);
        v.put("cache_expiry_time", i.cacheExpiryTime);
        v.put("uploader_name", i.uploaderName);
        v.put("uploader_url", i.uploaderUrl);
        v.put("uploader_avatar_url", i.uploaderAvatarUrl);
        v.put("thumbnail_url", i.thumbnailUrl);
        v.put("category", i.category);
        v.put("tags", i.tags);
        v.put("stream_type", i.streamType);
        v.put("service_id", i.serviceId);
        v.put("age_limit", i.ageLimit);
        v.put("is_short_form", i.isShortFormContent ? 1 : 0);
        v.put("has_subtitles", i.hasSubtitles ? 1 : 0);
        v.put("last_watched_at", i.lastWatchedAt);
        v.put("watch_count", i.watchCount);
        v.put("recommendation_reason_ordinal", i.recommendationReasonOrdinal);
        v.put("content_source_ordinal", i.contentSourceOrdinal);
        return v;
    }

    private static YtStreamInfo fromCursor(Cursor c) {
        YtStreamInfo i = new YtStreamInfo();
        i.dbId = c.getLong(c.getColumnIndexOrThrow("id"));
        i.url = c.getString(c.getColumnIndexOrThrow("url"));
        i.streamId = c.getString(c.getColumnIndexOrThrow("stream_id"));
        i.customCategory = c.getString(c.getColumnIndexOrThrow("custom_category"));
        i.name = c.getString(c.getColumnIndexOrThrow("name"));
        i.description = c.getString(c.getColumnIndexOrThrow("description"));
        i.uploadDate = c.getLong(c.getColumnIndexOrThrow("upload_date"));
        i.duration = c.getLong(c.getColumnIndexOrThrow("duration"));
        i.viewCounts = c.getLong(c.getColumnIndexOrThrow("view_counts"));
        i.cacheCreatedAt = c.getLong(c.getColumnIndexOrThrow("cache_created_at"));
        i.cacheExpiryTime = c.getLong(c.getColumnIndexOrThrow("cache_expiry_time"));
        i.uploaderName = c.getString(c.getColumnIndexOrThrow("uploader_name"));
        i.uploaderUrl = c.getString(c.getColumnIndexOrThrow("uploader_url"));
        i.uploaderAvatarUrl = c.getString(c.getColumnIndexOrThrow("uploader_avatar_url"));
        i.thumbnailUrl = c.getString(c.getColumnIndexOrThrow("thumbnail_url"));
        i.category = c.getString(c.getColumnIndexOrThrow("category"));
        i.tags = c.getString(c.getColumnIndexOrThrow("tags"));
        i.streamType = c.getString(c.getColumnIndexOrThrow("stream_type"));
        i.serviceId = c.getInt(c.getColumnIndexOrThrow("service_id"));
        i.ageLimit = c.getInt(c.getColumnIndexOrThrow("age_limit"));
        i.isShortFormContent = c.getInt(c.getColumnIndexOrThrow("is_short_form")) == 1;
        i.hasSubtitles = c.getInt(c.getColumnIndexOrThrow("has_subtitles")) == 1;
        i.lastWatchedAt = c.getLong(c.getColumnIndexOrThrow("last_watched_at"));
        i.watchCount = c.getInt(c.getColumnIndexOrThrow("watch_count"));
        i.recommendationReasonOrdinal =
                c.getInt(c.getColumnIndexOrThrow("recommendation_reason_ordinal"));
        i.contentSourceOrdinal = c.getInt(c.getColumnIndexOrThrow("content_source_ordinal"));
        return i;
    }
}
