package coreUtils.library.strings;

import static android.text.Html.FROM_HTML_MODE_COMPACT;
import static android.text.Html.fromHtml;

import android.content.Context;
import android.content.res.Resources;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Random;

import coreUtils.base.BaseApplication;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.LocaleHelper;

/**
 * A utility class providing a comprehensive set of static methods for string manipulation,
 * resource handling, and formatting within an Android environment.
 *
 * <p>This class includes functionality for:
 * <ul>
 *     <li>Cleanup operations (removing duplicate slashes, stripping empty lines).</li>
 *     <li>Localization and resource retrieval (fetching localized strings and HTML raw files).</li>
 *     <li>Random string generation.</li>
 *     <li>Safe string truncation based on code points.</li>
 *     <li>Text transformations (capitalization, joining, reversing).</li>
 *     <li>HTML processing (converting HTML strings to {@link Spanned}).</li>
 *     <li>Numerical formatting for display (e.g., view count abbreviations like K, M, B).</li>
 * </ul>
 *
 * <p>The class is final and cannot be instantiated.</p>
 */
public final class StringHelper {

    /**
     * Logger instance for this class, used to log diagnostic messages and errors.
     */
    private static final LoggerUtils logger = LoggerUtils.from(StringHelper.class);

    private StringHelper() {}

    /**
     * Removes duplicate forward slashes from the provided string, replacing multiple
     * consecutive slashes with a single slash.
     *
     * @param input The string to process.
     * @return A string with all occurrences of multiple slashes reduced to a single slash,
     *         or {@code null} if the input is {@code null}.
     */
    @Nullable
    public static String removeDuplicateSlashes(@Nullable String input) {
        if (input == null) return null;
        return input.replaceAll("/{2,}", "/");
    }

    /**
     * Retrieves a localized string from the application resources based on the
     * current language settings managed by {@link LocaleHelper}.
     *
     * @param resId The resource ID of the string to retrieve.
     * @return The localized string associated with the resource ID.
     */
    @NonNull
    public static String getText(@StringRes int resId) {
        BaseApplication appContext = BaseApplication.getInstance();
        Context localizedContext = LocaleHelper.applySavedLanguage(appContext);
        return localizedContext.getString(resId);
    }

    /**
     * Generates a random string of a specified length using characters from a provided source string.
     *
     * @param length The length of the string to be generated.
     * @param characters A string containing the set of characters to choose from.
     * @return A randomly generated string of the given length.
     */
    @NonNull
    public static String generateRandomString(int length, @NonNull String characters) {
        StringBuilder builder = new StringBuilder(length);
        Random random = new Random();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            builder.append(characters.charAt(index));
        }
        return builder.toString();
    }


    /**
     * Safely truncates a string to a specified maximum number of code points.
     * <p>
     * After cutting, the method trims the end of the string to remove any trailing whitespace
     * or characters deemed invalid by {@link #isValidCharacter(char)}. This ensures the
     * resulting string does not end with partial or unwanted formatting characters.
     * </p>
     *
     * @param input     The source string to be truncated. Can be null.
     * @param maxLength The maximum allowed length in code points.
     * @return The truncated and cleaned string, or {@code null} if the input was null.
     */
    @Nullable
    public static String safeCutString(@Nullable String input, int maxLength) {
        if (input == null) return null;
        int codePointCount = input.codePointCount(0, input.length());
        if (codePointCount <= maxLength) return input;

        int endIndex = input.offsetByCodePoints(0, maxLength);
        String result = input.substring(0, endIndex);

        while (!result.isEmpty()) {
            char last = result.charAt(result.length() - 1);
            if (!Character.isWhitespace(last) && isValidCharacter(last)) break;
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    /**
     * Determines whether the specified character is considered valid.
     * <p>
     * A character is valid if it is a letter, a digit, or one of the following
     * special characters: {@code _}, {@code -}, {@code .}, {@code @}, {@code  } (space),
     * {@code [}, {@code ]}, {@code (}, or {@code )}.
     * </p>
     *
     * @param character the character to be tested.
     * @return {@code true} if the character is a letter, digit, or an allowed special character;
     * {@code false} otherwise.
     */
    public static boolean isValidCharacter(char character) {
        return Character.isLetterOrDigit(character)
                || character == '_'
                || character == '-'
                || character == '.'
                || character == '@'
                || character == ' '
                || character == '['
                || character == ']'
                || character == '('
                || character == ')';
    }

    /**
     * Joins the provided elements into a single string, separated by the specified delimiter.
     *
     * @param delimiter the separator to be placed between each element
     * @param elements  the strings to be joined
     * @return a concatenated string of the elements separated by the delimiter,
     *         or an empty string if no elements are provided
     */
    @NonNull
    public static String join(@NonNull String delimiter, @NonNull String... elements) {
        if (elements.length == 0) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < elements.length; i++) {
            builder.append(elements[i]);
            if (i < elements.length - 1) builder.append(delimiter);
        }
        return builder.toString();
    }

    /**
     * Reverses the characters in the given string.
     *
     * @param input The string to be reversed, may be null.
     * @return The reversed string, or {@code null} if the input was null.
     */
    @Nullable
    public static String reverse(@Nullable String input) {
        if (input == null) return null;
        return new StringBuilder(input).reverse().toString();
    }

    /**
     * Capitalizes the first letter of the given string.
     * <p>
     * If the string is null or empty, it returns null. If the first character
     * is already uppercase, the original string is returned.
     *
     * @param input The string to be capitalized.
     * @return The string with the first letter in uppercase, or null if the input is null or empty.
     */
    @Nullable
    public static String capitalizeFirstLetter(@Nullable String input) {
        if (input == null || input.isEmpty()) return null;
        char first = input.charAt(0);
        if (Character.isUpperCase(first)) return input;
        return Character.toUpperCase(first)
                + input.substring(1);
    }

    /**
     * Capitalizes the first letter of each word in the provided string.
     * Words are identified as sequences of characters separated by whitespace.
     * The method trims leading/trailing whitespace and reduces multiple internal
     * spaces to a single space in the output.
     *
     * @param input The string to process.
     * @return A string with every word capitalized, or the original input if it is null or empty.
     */
    @Nullable
    public static String capitalizeWords(@Nullable String input) {
        if (input == null || input.trim().isEmpty()) return input;
        String[] words = input.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                char first = word.charAt(0);
                builder.append(Character.toUpperCase(first));
                if (word.length() > 1) {
                    builder.append(word.substring(1));
                }
            }
            builder.append(" ");
        }

        return builder.toString().trim();
    }

    /**
     * Converts an HTML-formatted string into a {@link Spanned} object for display in UI components.
     * <p>
     * This method uses the {@code FROM_HTML_MODE_COMPACT} flag, which separates block-level
     * elements with a single newline.
     *
     * @param htmlString The string containing HTML markup to be parsed.
     * @return A {@link Spanned} object containing the formatted text.
     */
    @NonNull
    public static Spanned fromHtmlStringToSpanned(@NonNull String htmlString) {
        return fromHtml(htmlString, FROM_HTML_MODE_COMPACT);
    }

    /**
     * Retrieves the content of an HTML file stored in the raw resources folder as a string.
     *
     * @param resId The resource ID of the HTML file (e.g., R.raw.filename).
     * @return A string containing the text content of the raw resource.
     */
    @NonNull
    public static String getHtmlString(int resId) {
        return convertRawHtmlFileToString(resId);
    }

    /**
     * Reads the content of a raw resource file (typically an HTML file) and converts it into a single String.
     *
     * @param resourceId The resource identifier of the raw file to be read (e.g., R.raw.filename).
     * @return A String containing the full content of the file, or an empty string if an error occurs
     *         during the reading process.
     */
    @NonNull
    public static String convertRawHtmlFileToString(int resourceId) {
        StringBuilder builder = new StringBuilder();
        BaseApplication appContext = BaseApplication.AppContext;
        Resources resources = appContext.getResources();
        try (
                InputStream inputStream = resources.openRawResource(resourceId);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader reader = new BufferedReader(inputStreamReader)
        ) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        } catch (Exception error) {
            logger.error("Error converting raw html file to string.", error);
        }

        return builder.toString();
    }

    /**
     * Counts the number of times a specific character appears within a given string.
     *
     * @param input     The string to be searched. If null, the method returns 0.
     * @param character The character to search for. If null, the method returns 0.
     * @return The number of occurrences of the character in the input string.
     */
    public static int countOccurrences(@Nullable String input,
                                       @Nullable Character character) {
        if (input == null || character == null) return 0;
        int count = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == character) count++;
        }

        return count;
    }

    /**
     * Removes all empty or whitespace-only lines from the given string.
     *
     * @param input The string to process.
     * @return The processed string with blank lines removed, or {@code null} if
     *         the input is {@code null} or empty.
     */
    @Nullable
    public static String removeEmptyLines(@Nullable String input) {
        if (input == null || input.isEmpty()) return null;
        String[] lines = input.split("\n");
        StringBuilder builder = new StringBuilder();

        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                builder.append(line).append("\n");
            }
        }

        return builder.toString().trim();
    }

    /**
     * Formats a large numeric count (such as view counts) into a human-readable string with units.
     * <p>
     * For example:
     * <ul>
     *     <li>999 becomes "999"</li>
     *     <li>1000 becomes "1K"</li>
     *     <li>1500 becomes "1.5K"</li>
     *     <li>2000000 becomes "2M"</li>
     * </ul>
     * Units supported: K (thousands), M (millions), B (billions), T (trillions).
     *
     * @param count The raw numeric count to be formatted.
     * @return A formatted string representation of the count with a unit suffix.
     */
    @NonNull
    public static String formatViewCounts(long count) {
        if (count < 1000) return String.valueOf(count);
        String[] units = {"K", "M", "B", "T"};

        double value = count;
        int unitIndex = -1;

        while (value >= 1000 && unitIndex < units.length - 1) {
            value /= 1000;
            unitIndex++;
        }

        if (value % 1 == 0) return ((int) value) + units[unitIndex];
        Locale locale = Locale.getDefault();
        return String.format(locale, "%.1f%s", value, units[unitIndex]);
    }
}