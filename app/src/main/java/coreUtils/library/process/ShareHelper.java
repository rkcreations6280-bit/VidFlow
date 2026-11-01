package coreUtils.library.process;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_STREAM;
import static android.content.Intent.EXTRA_TEXT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.nextgen.R;

import java.io.File;

import coreUtils.base.BaseActivity;
import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.strings.StringHelper;

/**
 * A utility class providing helper methods to simplify sharing content and opening files via Android Intents.
 *
 * <p>This class handles the complexities of:
 * <ul>
 *   <li>Generating {@link FileProvider} URIs for secure file sharing.</li>
 *   <li>Automatically detecting MIME types based on file extensions or system content resolvers.</li>
 *   <li>Launching system choosers for sharing text, URLs, and media files.</li>
 *   <li>Handling {@link Intent#ACTION_VIEW} operations for documents and APK installations.</li>
 * </ul>
 *
 * <p><b>Note:</b> Methods involving {@link File} require a provider defined in the
 * AndroidManifest with the authority format {@code "${applicationId}.provider"}.
 */
public final class ShareHelper {

    /**
     * Logger instance used for logging error messages and diagnostic information within this class.
     */
    private static final LoggerUtils logger = LoggerUtils.from(DeviceInfoUtils.class);

    private ShareHelper() {}

    /**
     * Determines the MIME type of given file or URI.
     * <p>
     * The method first attempts to resolve the type using the {@link android.content.ContentResolver}.
     * If that fails, it tries to infer the MIME type from the file's extension using {@link MimeTypeMap}.
     * If no type can be determined, it defaults to "*&#47;*".
     *
     * @param context The application context used to access the ContentResolver.
     * @param uri     The URI of the content to check.
     * @param file    The {@link File} object, used for extension-based detection if the URI resolution fails.
     * @return A {@link String} representing the MIME type, or "*&#47;*" if unknown.
     */
    private static String getMimeType(Context context, Uri uri, File file) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType != null) return mimeType;
        if (file != null) {
            String extension = FileStorageUtility.getFileExtension(file.getName());
            if (extension != null && !extension.isEmpty()) {
                MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
                String detectedType = mimeTypeMap.getMimeTypeFromExtension(extension);
                if (detectedType != null) return detectedType;
            }
        }

        return "*/*";
    }

    /**
     * Shares a URL string using the system's default share sheet with a default title.
     *
     * @param context The context used to start the share activity.
     * @param fileURL The URL string to be shared.
     */
    public static void shareUrl(Context context, String fileURL) {
        shareUrl(context, fileURL, "Share");
    }

    /**
     * Shares a URL string via an Android Intent chooser.
     * <p>
     * This method creates an {@link Intent#ACTION_SEND} intent with a
     * {@text "text/plain"} MIME type, allowing the user to share the provided URL
     * through any compatible application installed on the device.
     * </p>
     *
     * @param context The context used to start the chooser activity.
     * @param fileURL The URL string to be shared. If null or empty, the method returns without action.
     * @param title   The title to be displayed in the system's app chooser dialog.
     */
    public static void shareUrl(Context context, String fileURL, String title) {
        if (fileURL == null || fileURL.trim().isEmpty()) return;
        Intent intent = new Intent(ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(EXTRA_TEXT, fileURL);
        context.startActivity(Intent.createChooser(intent, title));
    }

    /**
     * Shares a {@link DocumentFile} using the default "Share" title.
     *
     * @param context      The context used to start the chooser activity.
     * @param documentFile The document file to be shared.
     */
    public static void shareDocumentFile(Context context, DocumentFile documentFile) {
        shareDocumentFile(context, documentFile, "Share");
    }

    /**
     * Shares a {@link DocumentFile} using the system's share sheet.
     * <p>
     * This method resolves the MIME type from the document file, defaults to "application/octet-stream"
     * if unknown, and grants temporary read URI permission to the receiving application.
     * </p>
     *
     * @param context      The context used to start the activity.
     * @param documentFile The document file to be shared.
     * @param title        The title to be displayed in the application chooser.
     */
    public static void shareDocumentFile(Context context, DocumentFile documentFile, String title) {
        Intent intent = new Intent(ACTION_SEND);
        boolean isTypeKnown = documentFile.getType() != null;
        intent.setType(isTypeKnown ? documentFile.getType() : "application/octet-stream");

        intent.putExtra(EXTRA_STREAM, documentFile.getUri());
        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, title));
    }

    /**
     * Shares a media file using the default system share sheet.
     * This method generates a content URI for the file and attempts to detect its MIME type.
     *
     * @param context The context used to access the FileProvider and start the activity.
     * @param file    The file to be shared.
     */
    public static void shareMediaFile(Context context, File file) {
        shareMediaFile(context, file, null);
    }

    /**
     * Shares a media file by creating a content URI via FileProvider and launching the system share chooser.
     *
     * @param context The context used to resolve the FileProvider authority and start the activity.
     * @param file    The {@link File} object representing the media to be shared.
     * @param title   The title to display in the share chooser. If null, a default string resource is used.
     */
    public static void shareMediaFile(Context context, File file, String title) {
        try {
            String authority = context.getPackageName() + ".provider";
            Uri fileUri = FileProvider.getUriForFile(context, authority, file);

            Intent intent = new Intent(ACTION_SEND);
            intent.setType(getMimeType(context, fileUri, file));
            intent.putExtra(EXTRA_STREAM, fileUri);
            intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);

            String chooserTitle = title != null
                    ? title
                    : StringHelper.getText(R.string.label_sharing_media_file);

            context.startActivity(Intent.createChooser(intent, chooserTitle));

        } catch (Exception error) {
            logger.error("Error sharing media file", error);
        }
    }

    /**
     * Opens a file using an appropriate external application based on its MIME type.
     * <p>
     * This method generates a content URI via {@link FileProvider}, determines the
     * file's MIME type, and starts an {@link Intent#ACTION_VIEW} activity.
     * It automatically grants read URI permissions to the receiving application.
     * </p>
     *
     * @param context The context used to resolve the FileProvider authority and start the activity.
     * @param file    The {@link File} object to be opened.
     * @throws ActivityNotFoundException if no application is installed that can handle the file type.
     */
    public static void openFile(Context context, File file) {
        try {
            String authority = context.getPackageName() + ".provider";
            Uri fileUri = FileProvider.getUriForFile(context, authority, file);
            String mimeType = getMimeType(context, fileUri, file);

            Intent intent = new Intent(ACTION_VIEW);

            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);

            if (!(context instanceof BaseActivity<?>)) {
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(intent);
        } catch (ActivityNotFoundException error) {
            logger.error("Activity not found to open file", error);
        }
    }

    /**
     * Shares the provided text content using the default system share sheet with a default title.
     *
     * @param context The context used to start the sharing activity.
     * @param text    The text string to be shared.
     */
    public static void shareText(Context context, String text) {
        shareText(context, text, "Share");
    }

    /**
     * Shares plain text using the system's share sheet.
     *
     * @param context The context used to start the sharing activity.
     * @param text    The text content to be shared. If the text is null or empty, no action is taken.
     * @param title   The title to be displayed in the system chooser dialog.
     */
    public static void shareText(Context context, String text, String title) {
        if (text == null || text.trim().isEmpty()) return;
        Intent intent = new Intent(ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, title));
    }
}