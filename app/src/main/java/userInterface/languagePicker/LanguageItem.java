package userInterface.languagePicker;

import java.io.Serializable;

/**
 * A simple data model representing a single language option for the language
 * picker UI. Each instance pairs a human-readable language name with its
 * corresponding ISO 639-1 language code. The class is immutable after
 * construction, with both fields exposed only through getters.
 *
 * <p>No {@code equals()}, {@code hashCode()}, or {@code toString()} overrides
 * are provided; identity semantics apply. Both constructor parameters are
 * required and must not be {@code null}.
 *
 */
public class LanguageItem implements Serializable {

    private final String languageName;
    private final String languageCode;

    /**
     * Creates a new language item with the specified display name and code.
     *
     * @param languageName The display name of the language in its native
     *                     script (e.g., "English", "हिन्दी"); must not be null.
     * @param languageCode The ISO 639-1 two-letter language code
     *                     (e.g., "en", "hi"); must not be null.
     */
    public LanguageItem(String languageName, String languageCode) {
        this.languageName = languageName;
        this.languageCode = languageCode;
    }

    /**
     * Returns the ISO 639-1 language code associated with this item.
     *
     * @return The two-letter language code (e.g., "en", "hi", "zh"),
     * never {@code null}.
     */
    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * Returns the display name of the language in its native script.
     *
     * @return The language name (e.g., "English", "हिन्दी", "中文"),
     * never {@code null}.
     */
    public String getLanguageName() {
        return languageName;
    }
}