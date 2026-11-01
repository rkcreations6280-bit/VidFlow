package coreUtils.library.process;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Locale;

import coreUtils.base.BaseActivity;

/**
 * A utility class for managing application-wide language and locale settings.
 *
 * <p>This class provides functionality to persist the user's language preference using
 * {@link SharedPreferences}, update the {@link Configuration}
 * for the application and activity contexts, and dynamically switch languages at runtime.</p>
 *
 * <p>Common use cases include:</p>
 * <ul>
 *     <li>Saving a selected language code (e.g., "en", "es").</li>
 *     <li>Applying a saved locale to a {@link Context} during activity initialization.</li>
 *     <li>Changing the language programmatically and recreating the activity to reflect changes.</li>
 * </ul>
 */
public final class LocaleHelper {

    /**
     * The name of the SharedPreferences file used to persist the user's language preference.
     */
    private static final String PREF_NAME = "language_manager_pref";

    /**
     * SharedPreferences key used to store the user's selected language code.
     */
    private static final String KEY_LANGUAGE = "selected_language";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private LocaleHelper() {}

    /**
     * Changes the application language and refreshes the current activity to apply the changes.
     * This method updates the language settings globally and triggers a recreation of the
     * provided activity instance to ensure the UI reflects the new locale.
     *
     * @param languageCode The ISO 639-1 language code to be applied (e.g., "en", "es").
     * @param baseActivity The activity instance that will be recreated.
     */
    public static void changeLanguage(@NonNull String languageCode,
                                      @NonNull BaseActivity<?> baseActivity) {
        LocaleHelper.setLanguage(baseActivity, languageCode);
        baseActivity.recreate();
    }

    /**
     * Updates the application's language settings by persisting the provided language code
     * and applying the new locale configuration to both the local context and the
     * application-wide context.
     *
     * @param context      The context used to save preferences and apply configuration.
     * @param languageCode The ISO 639-1 language code to apply (e.g., "en", "es").
     */
    public static void setLanguage(@NonNull Context context,
                                   @NonNull String languageCode) {
        saveLanguage(context, languageCode);
        applyLanguage(context, languageCode);
        applyLanguage(context.getApplicationContext(), languageCode);
    }

    /**
     * Applies the previously saved language preference to the given context.
     * If no language is found in the preferences, it defaults to the system's current language.
     *
     * @param context The context to which the language configuration will be applied.
     * @return A new context object with the updated configuration.
     */
    @NonNull
    public static Context applySavedLanguage(@NonNull Context context) {
        String language = getSavedLanguage(context);
        if (language.isEmpty()) language = Locale.getDefault().getLanguage();
        return applyLanguage(context, language);
    }

    /**
     * Applies the specified language to the given context and returns a new context with
     * the updated configuration. This method also updates the system's default locale.
     *
     * @param context      The context to which the language should be applied.
     * @param languageCode The ISO language code (e.g., "en", "fr") to set.
     * @return A new {@link Context} object with the applied locale configuration.
     */
    @NonNull
    public static Context applyLanguage(@NonNull Context context,
                                        @NonNull String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration resourcesConfiguration = resources.getConfiguration();
        Configuration configuration = new Configuration(resourcesConfiguration);
        configuration.setLocale(locale);
        configuration.uiMode = resources.getConfiguration().uiMode;
        return context.createConfigurationContext(configuration);
    }

    /**
     * Retrieves the language code currently saved in the application's preferences.
     *
     * @param context The context used to access SharedPreferences.
     * @return The saved language code string, or an empty string if no language has been saved.
     */
    @NonNull
    public static String getSavedLanguage(@NonNull Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        preferences.getString(KEY_LANGUAGE, "");
        return preferences.getString(KEY_LANGUAGE, "");
    }

    /**
     * Saves the selected language code to the application's shared preferences.
     *
     * @param context      The context used to access {@link SharedPreferences}.
     * @param languageCode The ISO language code to persist.
     */
    private static void saveLanguage(@NonNull Context context,
                                     @NonNull String languageCode) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

}