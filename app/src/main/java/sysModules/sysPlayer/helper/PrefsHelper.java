package sysModules.sysPlayer.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

public final class PrefsHelper {
    private PrefsHelper() {}

    private static final String KEY_SPEED = "player_speed";
    private static final String KEY_PITCH = "player_pitch";
    private static final String KEY_CAPTION_LANG = "caption_user_set_language";
    private static final String KEY_QUALITY = "player_quality";

    public static float getSpeed(@NonNull Context context) {
        return getPrefs(context).getFloat(KEY_SPEED, 1.0f);
    }

    public static void setSpeed(@NonNull Context context, float speed) {
        getPrefs(context).edit().putFloat(KEY_SPEED, speed).apply();
    }

    public static float getPitch(@NonNull Context context) {
        return getPrefs(context).getFloat(KEY_PITCH, 1.0f);
    }

    public static void setPitch(@NonNull Context context, float pitch) {
        getPrefs(context).edit().putFloat(KEY_PITCH, pitch).apply();
    }

    @Nullable
    public static String getCaptionLanguage(@NonNull Context context) {
        String lang = getPrefs(context).getString(KEY_CAPTION_LANG, null);
        if ("".equals(lang)) return null;
        return lang;
    }

    public static void setCaptionLanguage(@NonNull Context context, @Nullable String language) {
        getPrefs(context).edit().putString(KEY_CAPTION_LANG, language).apply();
    }

    @Nullable
    public static String getVideoQuality(@NonNull Context context) {
        return getPrefs(context).getString(KEY_QUALITY, null);
    }

    public static void setVideoQuality(@NonNull Context context, @Nullable String quality) {
        getPrefs(context).edit().putString(KEY_QUALITY, quality).apply();
    }

    private static SharedPreferences getPrefs(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
