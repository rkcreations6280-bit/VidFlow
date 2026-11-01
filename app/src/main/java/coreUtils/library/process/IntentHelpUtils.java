package coreUtils.library.process;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.Collections;
import java.util.List;

/**
 * Utility class for handling Android {@link Intent} operations, activity resolution,
 * and deep-linking into common social media and multimedia applications.
 *
 * <p>This class provides helper methods to:
 * <ul>
 *     <li>Query and validate activities that can handle specific intents.</li>
 *     <li>Extract data from incoming intents (ACTION_SEND and ACTION_VIEW).</li>
 *     <li>Safely launch external applications like Facebook, YouTube, Instagram, WhatsApp, etc.</li>
 *     <li>Handle failure cases via {@link Runnable} callbacks when an application is not installed.</li>
 * </ul>
 *
 * <p>All methods are static, and the class cannot be instantiated.</p>
 */
public final class IntentHelpUtils {

    /**
     * Internal logger instance used for tracking errors and diagnostic information
     * within this utility class.
     */
    private static final LoggerUtils logger = LoggerUtils.from(IntentHelpUtils.class);

    private IntentHelpUtils() {}

    /**
     * Retrieves a list of all activities that can be performed for the given intent.
     *
     * @param activity The activity context used to access the package manager.
     * @param intent   The intent to be resolved.
     * @return A list of {@link ResolveInfo} objects containing one entry for each matching activity.
     *         Returns an empty list if the intent or activity is null, or if no matching activities are found.
     */
    public static List<ResolveInfo> getMatchingActivities(Activity activity, Intent intent) {
        if (intent == null || activity == null) return Collections.emptyList();
        return activity.getPackageManager().queryIntentActivities(intent, 0);
    }

    /**
     * Extracts data from the Intent that started the given Activity.
     *
     * <p>Specifically handles:
     * <ul>
     *     <li>{@link Intent#ACTION_SEND}: Returns the text shared via {@link Intent#EXTRA_TEXT}.</li>
     *     <li>{@link Intent#ACTION_VIEW}: Returns the data URI string via {@link Intent#getDataString()}.</li>
     * </ul>
     *
     * @param activity The activity from which to retrieve the intent data.
     * @return The extracted string data if the action matches and data is present;
     *         {@code null} if the activity or intent is null, or if the action is unsupported.
     */
    public static String getIntentData(Activity activity) {
        if (activity == null) return null;
        Intent intent = activity.getIntent();
        if (intent == null) return null;

        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            return intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            return intent.getDataString();
        }
        return null;
    }

    /**
     * Checks whether there is at least one activity on the device that can handle the given intent.
     *
     * @param activity The activity context used to access the PackageManager.
     * @param intent   The intent to be resolved.
     * @return {@code true} if there is at least one activity capable of handling the intent;
     *         {@code false} if the intent or activity is null, or if no matching activities are found.
     */
    public static boolean canHandleIntent(Activity activity, Intent intent) {
        if (intent == null || activity == null) return false;
        PackageManager packageManager = activity.getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        return !activities.isEmpty();
    }

    /**
     * Attempts to start an activity for the provided intent after verifying that an application
     * on the device can handle it.
     *
     * @param activity The activity context used to start the intent.
     * @param intent   The intent to be executed.
     * @return {@code true} if the intent was successfully started; {@code false} if the activity
     *         or intent was null, or if no handler for the intent was found.
     */
    public static boolean startActivityIfPossible(Activity activity, Intent intent) {
        if (intent == null || activity == null) return false;
        if (canHandleIntent(activity, intent)) {
            activity.startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * Retrieves the package name of the first activity capable of handling the specified intent.
     *
     * @param activity The activity context used to access the PackageManager.
     * @param intent   The intent to resolve.
     * @return The package name of the resolving activity if found; otherwise, an empty string.
     */
    public static String getPackageNameForIntent(Activity activity, Intent intent) {
        if (intent == null || activity == null) return "";
        PackageManager packageManager = activity.getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(intent, 0);
        if (!activities.isEmpty()) return activities.get(0).activityInfo.packageName;
        return "";
    }

    /**
     * Attempts to open the Facebook application with a specific URL or the default homepage.
     *
     * @param context   The context used to resolve the activity and start the intent.
     * @param targetUrl The specific Facebook URL to open (e.g., a profile or post).
     *                  Defaults to "https://www.facebook.com" if null.
     * @param onError   A {@link Runnable} to be executed if the Facebook app is not installed
     *                  or the intent fails to resolve.
     * @return {@code true} if the Facebook app was successfully opened, {@code false} otherwise.
     */
    public static boolean openFacebookApp(Context context, String targetUrl, Runnable onError) {
        try {
            String url = (targetUrl != null) ? targetUrl : "https://www.facebook.com";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.facebook.katana");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return true;
            } else {
                if (onError != null) onError.run();
                return false;
            }
        } catch (Exception e) {
            if (onError != null) onError.run();
            return false;
        }
    }

    /**
     * Attempts to open the YouTube application with a specific URL.
     * If the YouTube app is not installed, the provided error callback is executed.
     *
     * @param context The application or activity context.
     * @param url     The YouTube URL to open (e.g., a video or channel link).
     *                If null, defaults to "https://www.youtube.com".
     * @param onError A {@link Runnable} to be executed if the YouTube app cannot be
     *                resolved or an error occurs.
     */
    public static void openYouTubeApp(Context context, String url, Runnable onError) {
        openAppWithUrl(context, (url != null ? url : "https://www.youtube.com"),
                "com.google.android.youtube", onError);
    }

    /**
     * Opens the Instagram application to a specific URL or the default home page.
     *
     * @param context The context used to start the activity.
     * @param url     The specific Instagram URL to navigate to (e.g., a profile or post).
     *                If null, it defaults to "http://instagram.com".
     * @param onError A {@link Runnable} to be executed if the Instagram app is not
     *                installed or the intent cannot be resolved.
     */
    public static void openInstagramApp(Context context, String url, Runnable onError) {
        openAppWithUrl(context, (url != null ? url : "http://instagram.com"),
                "com.instagram.android", onError);
    }

    /**
     * Attempts to launch the WhatsApp application on the device.
     *
     * @param context The context used to access the PackageManager and start the activity.
     * @param onError A {@link Runnable} to be executed if WhatsApp is not installed or cannot be opened.
     */
    public static void openWhatsappApp(Context context, Runnable onError) {
        launchPackage(context, "com.whatsapp", "whatsapp", onError);
    }

    /**
     * Attempts to open the YouTube Music application.
     *
     * @param context The context used to retrieve the package manager and start the activity.
     * @param onError A {@link Runnable} to be executed if the app is not installed or cannot be opened.
     */
    public static void openYouTubeMusicApp(Context context, Runnable onError) {
        launchPackage(context, "com.google.android.apps.youtube.music", "youtube music", onError);
    }

    /**
     * Attempts to launch the SoundCloud application on the device.
     *
     * @param context The context used to access the package manager and start the activity.
     * @param onError A {@link Runnable} to be executed if the SoundCloud app is not installed
     *                or cannot be opened.
     */
    public static void openSoundCloudApp(Context context, Runnable onError) {
        launchPackage(context, "com.soundcloud.android", "soundcloud", onError);
    }

    /**
     * Attempts to open the Pinterest application.
     *
     * @param context the context used to retrieve the package manager and start the activity
     * @param onError a {@link Runnable} to be executed if the app is not installed or cannot be opened
     */
    public static void openPinterestApp(Context context, Runnable onError) {
        launchPackage(context, "com.pinterest", "pinterest", onError);
    }

    /**
     * Attempts to open the TikTok application on the device.
     *
     * @param context The context used to access the PackageManager and start the activity.
     * @param onError A callback to be executed if the TikTok app is not installed or cannot be opened.
     */
    public static void openTikTokApp(Context context, Runnable onError) {
        launchPackage(context, "com.zhiliaoapp.musically", "tiktok", onError);
    }

    /**
     * Attempts to open the Dailymotion application.
     *
     * @param context The context used to access the PackageManager and start the activity.
     * @param onError A {@link Runnable} to be executed if the app is not installed or cannot be opened.
     */
    public static void openDailymotionApp(Context context, Runnable onError) {
        launchPackage(context, "com.dailymotion.dailymotion", "dailymotion", onError);
    }

    /**
     * Attempts to open the Reddit application.
     *
     * @param context The context used to retrieve the package manager and start the activity.
     * @param onError A {@link Runnable} to be executed if the Reddit app is not installed
     *                 or fails to launch.
     */
    public static void openRedditApp(Context context, Runnable onError) {
        launchPackage(context, "com.reddit.frontpage", "reddit", onError);
    }

    /**
     * Attempts to open the X (formerly Twitter) application on the device.
     *
     * @param context The context used to access the PackageManager and start the activity.
     * @param onError A {@link Runnable} to be executed if the app is not installed or cannot be opened.
     */
    public static void openXApp(Context context, Runnable onError) {
        launchPackage(context, "com.twitter.android", "twitter", onError);
    }

    /**
     * Attempts to open the TED Talks application.
     *
     * @param context the context used to retrieve the package manager and start the activity
     * @param onError a {@link Runnable} to be executed if the app is not installed or cannot be launched
     */
    public static void openTedTalksApp(Context context, Runnable onError) {
        launchPackage(context, "com.ted.android", "ted-talk", onError);
    }

    /**
     * Attempts to open a specific application using a URL.
     *
     */
    private static void openAppWithUrl(Context context, String url,
                                       String packageName, Runnable onError) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else if (onError != null) {
                onError.run();
            }
        } catch (Exception e) {
            if (onError != null) onError.run();
        }
    }

    /**
     * Launches an application using its unique package name.
     *
     * @param context     The context used to access the package manager and start the activity.
     * @param packageName The full package name of the application to be launched (e.g., "com.whatsapp").
     * @param logName     A descriptive name of the application for error logging purposes.
     * @param onError     A callback to be executed if the application is not installed or fails to launch.
     */
    private static void launchPackage(Context context, String packageName,
                                      String logName, Runnable onError) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else if (onError != null) {
                onError.run();
            }
        } catch (Exception error) {
            logger.error("Error in opening " + logName + ":", error);
            if (onError != null) onError.run();
        }
    }
}