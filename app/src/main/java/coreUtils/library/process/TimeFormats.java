package coreUtils.library.process;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A comprehensive utility class for date and time operations, providing methods for
 * formatting, parsing, and calculating differences between dates and times.
 * <p>
 * This class leverages the {@code java.time} API for thread-safe operations and maintains
 * an internal cache of {@link DateTimeFormatter} instances to optimize
 * performance for frequently used patterns.
 * </p>
 *
 * <p>Key features include:</p>
 * <ul>
 *     <li>Current date and time retrieval in various formats.</li>
 *     <li>Conversion between epoch timestamps (milliseconds) and formatted strings.</li>
 *     <li>Duration formatting for media (e.g., video duration as "H:mm:ss" or "m:ss").</li>
 *     <li>Ordinal suffix generation (e.g., 1st, 2nd, 3rd) for calendar days.</li>
 *     <li>Calculation of day differences and time range validation.</li>
 * </ul>
 */
public final class TimeFormats {
    /**
     * Logger instance for this class, used to record error messages and diagnostic information
     * regarding date-time parsing and formatting operations.
     */
    private static final LoggerUtils logger = LoggerUtils.from(TimeFormats.class);
    public static final String APP_DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String APP_DEFAULT_TIMESTAMP_PATTERN = "%02d:%02d:%02d";
    public static final String APP_DEFAULT_DURATION_PATTERN = "%d:%02d:%02d";
    public static final String APP_DEFAULT_TIME_PATTERN = "%02d:%02d";
    
    public static final String APP_TIME_FORMAT_12_HOUR = "hh:mm a";
    public static final String APP_TIME_FORMAT_24_HOUR = "HH:mm";
    public static final String APP_TIME_FORMAT_DAY = "d";
    public static final String APP_TIME_FORMAT_MONTH = "MMM";
    public static final String APP_TIME_EMPTY = "00:00";

    /**
     * A thread-safe cache for {@link DateTimeFormatter} instances to avoid the overhead
     * of repeatedly parsing and creating formatters for the same patterns and locales.
     * The key is a composite string consisting of the pattern, language, and country.
     */
    private static final ConcurrentHashMap<String, DateTimeFormatter>
            FORMATTER_CACHE = new ConcurrentHashMap<>();

    /**
     * The default date-time formatter used by the application for general date-time operations.
     * <p>
     * This formatter is initialized using the {@code APP_DEFAULT_DATE_TIME_FORMAT} pattern
     * and {@code Locale.ENGLISH}. It is used as the primary formatter for methods such as
     * {@link #getCurrentDateTime()}.
     */
    private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
            getOrCreateFormatter(APP_DEFAULT_DATE_TIME_FORMAT, Locale.ENGLISH);

    /**
     * A thread-safe {@link DateTimeFormatter} for 12-hour clock time formatting.
     * <p>
     * This formatter uses the pattern defined by {@code APP_TIME_FORMAT_12_HOUR}
     * and is fixed to the {@link Locale#ENGLISH} locale to ensure consistent
     * AM/PM markers.
     */
    private static final DateTimeFormatter TIME_12H_FORMATTER =
            getOrCreateFormatter(APP_TIME_FORMAT_12_HOUR, Locale.ENGLISH);

    /**
     * Predefined formatter for 24-hour time strings (e.g., "14:30").
     * Uses the application's default 24-hour pattern and {@link Locale#ENGLISH}.
     */
    private static final DateTimeFormatter TIME_24H_FORMATTER =
            getOrCreateFormatter(APP_TIME_FORMAT_24_HOUR, Locale.ENGLISH);

    private TimeFormats() {}

    /**
     * Retrieves a cached {@link DateTimeFormatter} for the specified pattern or creates a new one
     * using the system's default locale if it does not exist in the cache.
     *
     * @param pattern the date-time pattern to use
     * @return a {@link DateTimeFormatter} instance for the given pattern
     */
    private static DateTimeFormatter getOrCreateFormatter(String pattern) {
        return getOrCreateFormatter(pattern, Locale.getDefault());
    }

    /**
     * Retrieves an existing {@link DateTimeFormatter} from the cache or creates a new one
     * if it does not already exist for the given pattern and locale.
     * <p>
     * This method ensures thread-safe access to formatters and improves performance by
     * reusing instances. If the provided pattern is null, the application's default
     * date-time format is used.
     *
     * @param pattern the date-time pattern to use; if {@code null},
     *                uses {@code APP_DEFAULT_DATE_TIME_FORMAT}
     * @param locale  the locale to be applied to the formatter
     * @return a cached or newly created {@link DateTimeFormatter}
     */
    private static DateTimeFormatter getOrCreateFormatter(String pattern, Locale locale) {
        String safePattern = pattern != null ? pattern : APP_DEFAULT_DATE_TIME_FORMAT;
        String key = safePattern + "-" + locale.getLanguage() + "-" + locale.getCountry();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(safePattern, locale);

        return FORMATTER_CACHE.computeIfAbsent(key, k -> dateTimeFormatter);
    }

    /**
     * Returns the current system date and time formatted according to the application's
     * default date-time pattern and the English locale.
     *
     * @return a formatted string representing the current date and time
     */
    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(DEFAULT_DATE_FORMATTER);
    }

    /**
     * Gets the current system date and time formatted according to the specified pattern.
     * <p>
     * This method retrieves the current date-time from the system clock and applies
     * a {@link DateTimeFormatter} based on the provided format string.
     *
     * @param format the date-time pattern to use (e.g., "yyyy-MM-dd" or "dd/MM/yyyy")
     * @return a formatted string representing the current date and time
     */
    public static String getCurrentDate(String format) {
        return LocalDateTime.now().format(getOrCreateFormatter(format));
    }

    /**
     * Returns the current system time as a string formatted according to the specified pattern.
     * <p>
     * The method uses {@link LocalDateTime#now()} and retrieves or creates a
     * {@link DateTimeFormatter} for the provided format string.
     *
     * @param format the date-time pattern to use (e.g., "HH:mm:ss" or "h:mm a")
     * @return a formatted string representing the current time
     */
    public static String getCurrentTime(String format) {
        return LocalDateTime.now().format(getOrCreateFormatter(format));
    }

    /**
     * Converts a millisecond timestamp into a formatted date string using the specified pattern.
     * <p>
     * The conversion uses the system's default time zone to interpret the timestamp.
     *
     * @param timestamp the epoch milliseconds to be formatted
     * @param format    the date-time pattern to use (e.g., "yyyy-MM-dd", "HH:mm:ss")
     * @return a formatted date string representing the timestamp
     */
    public static String timestampToDateString(long timestamp, String format) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZonedDateTime dt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dt.format(getOrCreateFormatter(format));
    }

    /**
     * Converts a date string into epoch milliseconds using a specified format pattern.
     * <p>
     * This method parses the {@code dateString} using the provided {@code format} and
     * converts it to a timestamp based on the system's default time zone. If the input
     * string is null, empty, or fails to parse, the error is logged and 0 is returned.
     *
     * @param dateString the date-time string to be converted
     * @param format     the date-time pattern matching the input string (e.g., "yyyy-MM-dd HH:mm:ss")
     * @return the equivalent epoch milliseconds, or {@code 0L} if the input is invalid or parsing fails
     */
    public static long dateStringToTimestamp(String dateString, String format) {
        if (dateString == null || dateString.isEmpty()) return 0L;

        try {
            DateTimeFormatter formatter = getOrCreateFormatter(format);
            LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);

            return dateTime.atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception error) {
            logger.error("Error converting date string to timestamp", error);
            return 0L;
        }
    }

    /**
     * Calculates the number of full days between two date strings.
     * <p>
     * The difference is calculated as {@code endDate - startDate}. Both date strings
     * must follow the provided {@code format} pattern. If any input is null, empty,
     * or if parsing fails, the method returns 0.
     *
     * @param startDate the starting date string
     * @param endDate   the ending date string
     * @param format    the date-time pattern used to parse both strings
     * @return the number of full days between the two dates; may be negative if
     * {@code endDate} is before {@code startDate}
     */
    public static long getDaysDifference(String startDate, String endDate, String format) {
        if (startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
            return 0L;
        }

        try {
            DateTimeFormatter formatter = getOrCreateFormatter(format);
            LocalDateTime start = LocalDateTime.parse(startDate, formatter);
            LocalDateTime end = LocalDateTime.parse(endDate, formatter);

            return Duration.between(start, end).toDays();
        } catch (Exception error) {
            logger.error("Error getting days difference", error);
            return 0L;
        }
    }

    /**
     * Formats a millisecond timestamp into a human-readable string including the day with its
     * ordinal suffix, the full month name, and the time in 24-hour format.
     * <p>
     * Example output: "12th January (14:30)"
     *
     * @param timestamp the epoch milliseconds to format
     * @return a formatted string in the style of "d{suffix} Month (HH:mm)"
     */
    public static String formatDateWithSuffix(long timestamp) {
        Instant instant = Instant.ofEpochMilli(timestamp);
        ZonedDateTime dt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());
        int day = dt.getDayOfMonth();

        String month = dt.format(getOrCreateFormatter(APP_TIME_FORMAT_MONTH, Locale.US));
        String time = dt.format(getOrCreateFormatter(APP_TIME_FORMAT_24_HOUR, Locale.US));
        return day + getOrdinalSuffix(day) + " " + month + " (" + time + ")";
    }

    /**
     * Checks if the current system time falls strictly between the specified start and end times.
     * <p>
     * The method parses the provided time strings using the given format and compares them
     * against {@link LocalDateTime#now()}. All parameters must be non-null and non-empty,
     * and must match the provided pattern.
     *
     * @param startTime the lower bound of the time range (exclusive)
     * @param endTime   the upper bound of the time range (exclusive)
     * @param format    the date-time pattern used to parse the start and end strings
     * @return {@code true} if the current time is after the start time and before the end time;
     * {@code false} otherwise or if any input is invalid/parsing fails
     */
    public static boolean isCurrentTimeInRange(String startTime, String endTime, String format) {
        if (startTime == null || startTime.isEmpty() || endTime == null || endTime.isEmpty()) {
            return false;
        }

        try {
            DateTimeFormatter formatter = getOrCreateFormatter(format);
            LocalDateTime start = LocalDateTime.parse(startTime, formatter);
            LocalDateTime end = LocalDateTime.parse(endTime, formatter);
            LocalDateTime now = LocalDateTime.now();

            return now.isAfter(start) && now.isBefore(end);
        } catch (Exception error) {
            logger.error("Error checking time range", error);
            return false;
        }
    }

    /**
     * Converts a date string from one format to another.
     * <p>
     * This method parses the input {@code dateString} using the {@code fromFormat}
     * and re-formats it into the {@code toFormat}. If the input string is null,
     * empty, or if parsing fails, it returns {@code null}.
     *
     * @param dateString the date string to be reformatted
     * @param fromFormat the date-time pattern of the source string (e.g., "yyyy-MM-dd HH:mm:ss")
     * @param toFormat   the desired date-time pattern for the output string
     * @return the reformatted date string, or {@code null} if the input is invalid or parsing fails
     */
    public static String convertDateFormat(String dateString, String fromFormat, String toFormat) {
        if (dateString == null || dateString.isEmpty()) return null;
        try {
            DateTimeFormatter formatter = getOrCreateFormatter(fromFormat);
            LocalDateTime dateTime = LocalDateTime.parse(dateString, formatter);

            return dateTime.format(getOrCreateFormatter(toFormat));
        } catch (Exception error) {
            logger.error("Error formatting date string", error);
            return null;
        }
    }

    /**
     * Gets the current system time formatted according to the 12-hour clock convention.
     * The format typically includes hours, minutes, and an AM/PM designator,
     * as defined by the application's 12-hour time pattern.
     *
     * @return a formatted string representing the current time in 12-hour format
     */
    public static String getCurrentTimeIn12HourFormat() {
        return LocalDateTime.now().format(TIME_12H_FORMATTER);
    }

    /**
     * Returns the current system time formatted in a 24-hour format (e.g., "14:30").
     * <p>
     * This method uses the predefined {@code TIME_24H_FORMATTER} which is based on
     * the application's default 24-hour time pattern.
     *
     * @return a string representing the current time in 24-hour format
     */
    public static String getCurrentTimeIn24HourFormat() {
        return LocalDateTime.now().format(TIME_24H_FORMATTER);
    }

    /**
     * Formats a millisecond timestamp into a string representing the day of the month
     * with its ordinal suffix and the full month name (e.g., "12th January").
     *
     * @param lastModifiedTimeDate the timestamp in milliseconds to format
     * @return a formatted string containing the day with a suffix and the month
     */
    public static String formatLastModifiedDate(long lastModifiedTimeDate) {
        Instant instant = Instant.ofEpochMilli(lastModifiedTimeDate);
        ZonedDateTime dt = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault());

        int day = dt.getDayOfMonth();
        String month = dt.format(getOrCreateFormatter(APP_TIME_FORMAT_MONTH));
        return day + getOrdinalSuffix(day) + " " + month;
    }

    /**
     * Returns the appropriate English ordinal suffix (st, nd, rd, or th) for a given day of the month.
     * <p>
     * For example: 1 yields "st", 2 yields "nd", 3 yields "rd", and 11 yields "th".
     *
     * @param day the day of the month (typically 1-31)
     * @return the ordinal suffix for the day
     */
    public static String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) return "th";

        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    /**
     * Composes a human-readable duration string from the total number of seconds.
     * <p>
     * If the duration is one hour or longer, it returns a string formatted with hours,
     * minutes, and seconds (using {@code APP_DEFAULT_TIMESTAMP_PATTERN}).
     * Otherwise, it returns a string formatted with only minutes and seconds
     * (using {@code APP_DEFAULT_TIME_PATTERN}).
     *
     * @param totalSeconds the total duration in seconds
     * @return a formatted duration string (e.g., "H:mm:ss" or "m:ss")
     */
    public static String toHumanReadableTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(),
                    APP_DEFAULT_TIMESTAMP_PATTERN, hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(),
                    APP_DEFAULT_TIME_PATTERN, minutes, seconds);
        }
    }

    /**
     * Formats a video duration provided in milliseconds into a human-readable string.
     * If the duration is null or non-positive, it returns a default empty time indicator.
     * The output format is typically "HH:mm:ss" if hours are present, otherwise "mm:ss".
     *
     * @param durationMs the video duration in milliseconds
     * @return a formatted duration string or {@code APP_TIME_EMPTY} if the input is invalid
     */
    public static String formatVideoDuration(Long durationMs) {
        if (durationMs == null || durationMs <= 0) return APP_TIME_EMPTY;
        return toHumanReadableTime(durationMs / 1000);
    }

    /**
     * Formats a duration in milliseconds into a human-readable time string (e.g., HH:mm:ss or mm:ss).
     * This is a convenience method that calls {@link #formatDurationFromMillisecond(long, String)} with no suffix.
     *
     * @param milliseconds the duration to format in milliseconds
     * @return the formatted time string
     */
    public static String formatDurationFromMillisecond(long milliseconds) {
        return formatDurationFromMillisecond(milliseconds, "");
    }

    /**
     * Formats a duration given in seconds into a human
     */
    public static String formatDurationFromSecond(long seconds) {
        if (seconds <= 0) return "00:00";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s);
        } else {
            return String.format(Locale.ENGLISH, "%02d:%02d", m, s);
        }
    }

    /**
     * Formats a duration in milliseconds into a human-readable time string,
     * optionally appending a specified suffix.
     *
     * @param milliseconds  the duration to format in milliseconds
     * @param includeSuffix the suffix to append to the formatted time string (e.g., "remaining")
     * @return the formatted time string, with the suffix appended if it is not null or empty
     */
    public static String formatDurationFromMillisecond(long milliseconds, String includeSuffix) {
        String time = toHumanReadableTime(milliseconds / 1000);
        if (includeSuffix == null || includeSuffix.isEmpty()) return time;
        return time + " " + includeSuffix;
    }

    /**
     * Calculates the number of full days that have passed from a given timestamp
     * until the current system time.
     *
     * @param lastModifiedTime the starting point in milliseconds (epoch time)
     * @return the number of full days elapsed since the provided timestamp
     */
    public static long getDaysPassedSince(long lastModifiedTime) {
        long timeDifference = System.currentTimeMillis() - lastModifiedTime;
        return TimeUnit.MILLISECONDS.toDays(timeDifference);
    }

    /**
     * Converts a millisecond timestamp to a formatted date-time string using the
     * application's default date-time format and the system's default time zone.
     *
     * @param millis the milliseconds since the epoch (1970-01-01T00:00:00Z)
     * @return a formatted date-time string
     */
    public static String millisToDateTimeString(long millis) {
        Instant ofEpochMilli = Instant.ofEpochMilli(millis);
        ZonedDateTime dt = ZonedDateTime.ofInstant(ofEpochMilli, ZoneId.systemDefault());
        return dt.format(getOrCreateFormatter(APP_DEFAULT_DATE_TIME_FORMAT));
    }

    /**
     * Converts a date-time string into epoch milliseconds using the application's default date-time format.
     * <p>
     * This method uses the system's default time zone to interpret the parsed date-time.
     * If the input string is null, empty, or doesn't match the expected format
     * (defined by {@code APP_DEFAULT_DATE_TIME_FORMAT}), the error is logged and 0 is returned.
     *
     * @param dateTimeString The date-time string to be converted.
     * @return The equivalent epoch milliseconds, or 0L if parsing fails.
     */
    public static long dateTimeStringToMillis(String dateTimeString) {
        try {
            DateTimeFormatter formatter = getOrCreateFormatter(APP_DEFAULT_DATE_TIME_FORMAT);
            LocalDateTime dt = LocalDateTime.parse(dateTimeString, formatter);

            return dt.atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception error) {
            logger.error("Error converting date string to millis", error);
            return 0L;
        }
    }

    /**
     * Calculates and formats a duration from milliseconds into a human-readable time string.
     * This method is a convenience wrapper that calls {@link #calculateTime(float, String)}
     * with an empty suffix.
     *
     * @param millis The duration in milliseconds as a float.
     * @return A formatted time string (e.g., "HH:mm:ss" or "mm:ss").
     */
    public static String calculateTime(float millis) {
        return calculateTime(millis, "");
    }

    /**
     * Converts a duration in milliseconds to a formatted string (HH:mm:ss or mm:ss)
     * and appends an optional suffix.
     *
     * @param millis The duration in milliseconds to be formatted.
     * @param suffix An optional string to append to the end of the formatted time (e.g., "left", "ago").
     * @return A formatted time string, optionally followed by the specified suffix.
     */
    public static String calculateTime(float millis, String suffix) {
        String time = toHumanReadableTime((long) (millis / 1000));
        if (suffix == null || suffix.isEmpty()) return time;
        return time + " " + suffix;
    }

    /**
     * Formats a duration given in milliseconds into a human-readable string representation.
     * The output format (typically HH:mm:ss or mm:ss) is determined by the internal
     * composition logic based on the magnitude of the duration.
     *
     * @param durationMillis the duration to format in milliseconds
     * @return a formatted string representing the duration
     */
    public static String formatTimeDurationToString(long durationMillis) {
        return toHumanReadableTime(durationMillis / 1000);
    }
}