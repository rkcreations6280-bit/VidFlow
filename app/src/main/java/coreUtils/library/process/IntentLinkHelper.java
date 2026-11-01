package coreUtils.library.process;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import coreUtils.library.networks.URLUtility;

public final class IntentLinkHelper {

    private static final LoggerUtils logger = LoggerUtils.from(IntentLinkHelper.class);

    private IntentLinkHelper() {}

    public static void openUrlInBrowser(Context context, String url) {
        if (context == null || url == null || url.trim().isEmpty()) return;

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);
        } catch (Exception error) {
            logger.error("Activity not found", error);
        }
    }

    public static String getIntentDataURI(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (extraText != null) return extraText;

            if (intent.getClipData() != null
                    && intent.getClipData().getItemCount() > 0) {

                CharSequence text = intent.getClipData()
                        .getItemAt(0)
                        .getText();

                return text != null ? text.toString() : null;
            }

            return null;
        }

        if (Intent.ACTION_VIEW.equals(action)) {
            return intent.getDataString();
        }
        return null;
    }

    public static void openLinkInSystemBrowser(Context context, String webAddress) {
        openLinkInSystemBrowser(context, webAddress, null);
    }

    public static void openLinkInSystemBrowser(Context context,
                                               String webAddress,
                                               Runnable onFailed) {
        if (context == null
                || webAddress == null
                || webAddress.trim().isEmpty()
                || !URLUtility.isValidURL(webAddress)) {
            if (onFailed != null) onFailed.run();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webAddress));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);
        } catch (Exception error) {
            logger.error("Activity not found", error);
            if (onFailed != null) {
                onFailed.run();
            }
        }
    }
}