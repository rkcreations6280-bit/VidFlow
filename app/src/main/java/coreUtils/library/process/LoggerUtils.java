package coreUtils.library.process;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

import coreUtils.base.BaseApplication;

/**
 * A utility class for handling Android logging with automatic tag generation and debug-mode filtering.
 * <p>
 * This class wraps the standard {@link Log} methods and provides the following features:
 * <ul>
 *     <li>Automatic Tagging: Uses the simple name of the provided class as the log tag.</li>
 *     <li>Debug Filtering: Logs are only processed if the application is in debug mode,
 *     as determined by {@link BaseApplication#isDebugBuild()}.</li>
 *     <li>Throwable Formatting: Provides helper methods to convert {@link Throwable} stack traces into strings.</li>
 *     <li>Null Safety: Handles null message strings gracefully by providing a default fallback.</li>
 * </ul>
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * private static final Logger logger = Logger.from(MyActivity.class);
 *
 * logger.d("Initialize components");
 * try {
 *     // some code
 * } catch (Exception e) {
 *     logger.e("Operation failed", e);
 * }
 * </pre>
 */
public final class LoggerUtils implements Serializable {

    /**
     * The class source used to generate the tag for Android log messages.
     */
    private final Class<?> class_;
    /**
     * Indicates whether the logger is in debugging mode. When {@code false},
     * all logging methods will return early without outputting any logs.
     */
    private final boolean isDebuggingMode;

    /**
     * Private constructor to initialize the logger with a specific class context.
     * Sets the debugging mode based on the application's current build configuration.
     *
     * @param class_ The class instance used as a tag for log messages.
     */
    private LoggerUtils(Class<?> class_) {
        this.class_ = class_;
        this.isDebuggingMode = BaseApplication.isDebugBuild();
    }

    /**
     * Creates a new Logger instance for the specified class.
     *
     * @param class_ The class whose simple name will be used as the log tag.
     * @return A new Logger instance associated with the provided class.
     */
    @NonNull
    public static LoggerUtils from(@NonNull Class<?> class_) {
        return new LoggerUtils(class_);
    }

    /**
     * Converts the stack trace of a {@link Throwable} into a {@link String}.
     *
     * @param throwable The throwable to be converted.
     * @return A string representation of the throwable's stack trace.
     */
    @NonNull
    public static String toString(@NonNull Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Logs an error message at the ERROR level.
     * <p>
     * The log is only processed if the application is currently in debugging mode.
     *
     * @param message The error message to be logged.
     */
    public void error(@NonNull String message) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.e(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs the stack trace of a {@link Throwable} at the error level.
     * <p>
     * This log is only written if the application is in debug mode.
     *
     * @param error The exception or error to log.
     */
    public void error(@NonNull Throwable error) {
        if (!isDebuggingMode) return;
        String msg = toString(error);
        Log.e(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs an error message and the associated stack trace to Logcat.
     * Execution is skipped if the application is not in debug mode.
     *
     * @param message   The error message to be logged.
     * @param throwable The exception or error to log.
     */
    public void error(@NonNull String message,
                      @NonNull Throwable throwable) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.e(class_.getSimpleName(), msg, throwable);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs a debug message to the Android Logcat if the application is in debugging mode.
     *
     * @param message The message to be logged.
     */
    public void debug(@NonNull String message) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.d(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs the stack trace of a {@link Throwable} at the DEBUG level.
     * The log is only processed if the application is in debugging mode.
     *
     * @param err The throwable/exception to log.
     */
    public void debug(@NonNull Throwable err) {
        if (!isDebuggingMode) return;
        String msg = toString(err);
        Log.d(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Send a debug log message that includes the method name.
     *
     * @param methodName The name of the method where the log is triggered.
     * @param message    The message you would like logged.
     */
    public void debug(@NonNull String methodName,
                      @NonNull String message) {
        debug(methodName + message);
    }

    /**
     * Logs an informational message. The log will only be processed if the application
     * is in debugging mode.
     *
     * @param message The message to be logged.
     */
    public void info(@NonNull String message) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.i(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs the stack trace of a {@link Throwable} at the INFO level.
     * <p>
     * This log is only emitted if the application is in debugging mode.
     *
     * @param err The throwable to be logged.
     */
    public void info(@NonNull Throwable err) {
        if (!isDebuggingMode) return;
        String msg = toString(err);
        Log.i(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs an informational message including the name of the method where the log was generated.
     * This log will only be processed if the application is in debug mode.
     *
     * @param methodName The name of the method originating the log.
     * @param message    The informational message to be logged.
     */
    public void info(@NonNull String methodName,
                     @NonNull String message) {
        info(methodName + message);
    }

    /**
     * Logs a verbose message if the application is in debugging mode.
     *
     * @param message The message to be logged.
     */
    public void verbose(@NonNull String message) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.v(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs a verbose message containing the stack trace of the provided {@link Throwable}.
     * This log is only processed if the application is in debug mode.
     *
     * @param err The exception or error to log.
     */
    public void verbose(@NonNull Throwable err) {
        if (!isDebuggingMode) return;
        String msg = toString(err);
        Log.v(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs a verbose message combining a method name and a specific message.
     * The log is only triggered if the application is in debugging mode.
     *
     * @param methodName The name of the method where the log originates.
     * @param message    The message to be logged.
     */
    public void verbose(@NonNull String methodName,
                        @NonNull String message) {
        verbose(methodName + message);
    }

    /**
     * Logs a warning message if the application is in debug mode.
     *
     * @param message The warning message to be logged.
     */
    public void warning(@NonNull String message) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.w(class_.getSimpleName(), msg);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Logs a warning message and the associated throwable.
     * The log is only output if the application is in debug mode.
     *
     * @param message   The warning message to be logged.
     * @param throwable The exception or error to be logged.
     */
    public void warning(@NonNull String message,
                        @NonNull Throwable throwable) {
        if (!isDebuggingMode) return;
        String msg = toMessage(message);
        Log.w(class_.getSimpleName(), msg, throwable);
        CrashLogWriter.record(class_.getSimpleName(), msg);
    }

    /**
     * Normalizes the log message by providing a fallback string if the input is null.
     *
     * @param message The message to be logged.
     * @return The original message, or a default error indicator if the input is null.
     */
    private String toMessage(String message) {
        return message == null ? "Error Message = NULL!!" : message;
    }
}