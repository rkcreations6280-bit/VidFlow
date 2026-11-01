package coreUtils.library.strings;

import static android.content.ClipData.newHtmlText;
import static android.content.ClipData.newPlainText;
import static android.content.Context.CLIPBOARD_SERVICE;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.Context;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for interacting with the Android {@link ClipboardManager}.
 *
 * <p>This class provides static methods to simplify common clipboard operations such as
 * copying plain text or HTML, retrieving content, clearing the clipboard, and managing
 * {@link OnPrimaryClipChangedListener} instances with safe proxying.
 *
 * <p>All methods handle {@code null} context checks and ensure safe interaction with
 * system services to prevent crashes in non-UI environments or restricted contexts.
 */
public final class ClipboardHelper {

    private ClipboardHelper() {}

    /**
     * A thread-safe registry that maps original {@link OnPrimaryClipChangedListener} instances
     * to their corresponding {@link ListenerProxy} wrappers. This allows the helper to
     * correctly identify and remove the proxy from the system {@link ClipboardManager}
     * when the original listener is unsubscribed.
     */
    private static final Map<OnPrimaryClipChangedListener,
            OnPrimaryClipChangedListener> activeListeners = new ConcurrentHashMap<>();

    /**
     * A proxy wrapper for {@link OnPrimaryClipChangedListener} that holds a {@link WeakReference}
     * to the actual listener. This prevents memory leaks by allowing the original listener
     * to be garbage collected even if the system's {@link ClipboardManager} still holds
     * a reference to this proxy.
     */
    private record ListenerProxy(WeakReference<OnPrimaryClipChangedListener> weakListener)
            implements OnPrimaryClipChangedListener {

        /**
         * Constructs a new ListenerProxy that wraps the provided listener in a {@link WeakReference}.
         * This prevents the proxy from causing memory leaks by allowing the original listener
         * to be garbage collected.
         *
         * @param weakListener The listener to be wrapped and notified of clipboard changes.
         */
        private ListenerProxy(OnPrimaryClipChangedListener weakListener) {
            this(new WeakReference<>(weakListener));
        }

        /**
         * Callback invoked by the system when the primary clip on the clipboard changes.
         * This implementation delegates the event to the wrapped listener if it has
         * not been garbage collected.
         */
        @Override
        public void onPrimaryClipChanged() {
            OnPrimaryClipChangedListener listener = weakListener.get();
            if (listener != null) listener.onPrimaryClipChanged();
        }
    }

    /**
     * Clears the current contents of the system clipboard by setting it to an empty plain text clip.
     *
     * @param context The context used to access the clipboard service.
     * @return The {@link ClipboardManager} instance used to clear the clipboard,
     *         or {@code null} if the service could not be accessed.
     */
    @Nullable
    public static ClipboardManager clearClipboard(Context context) {
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard != null) clipboard.setPrimaryClip(newPlainText("", ""));
        return clipboard;
    }

    /**
     * Checks whether the system clipboard currently contains a non-empty text clip.
     *
     * @param context The context used to access the clipboard service.
     * @return {@code true} if the clipboard contains text with a length greater than zero,
     *         {@code false} otherwise.
     */
    public static boolean hasTextInClipboard(Context context) {
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard == null || clipboard.getPrimaryClip() == null) {
            return false;
        }

        ClipData clip = clipboard.getPrimaryClip();
        return clip != null
                && clip.getItemCount() > 0
                && clip.getItemAt(0).getText() != null
                && clip.getItemAt(0).getText().length() > 0;
    }

    /**
     * Retrieves the HTML formatted text from the system clipboard.
     *
     * @param context The context used to access the system clipboard service.
     */
    public static String getHtmlFromClipboard(Context context) {
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard == null || clipboard.getPrimaryClip() == null) {
            return "";
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            String html = clip.getItemAt(0).getHtmlText();
            return html != null ? html : "";
        }

        return "";
    }

    /**
     * Copies the provided HTML string to the system clipboard.
     * <p>
     * This method sets the primary clip to an HTML-formatted text item. If the HTML string
     * is null or empty, the operation is aborted and null is returned.
     *
     * @param context The context used to access the clipboard service.
     * @param html    The HTML string to be copied.
     * @return The {@link ClipboardManager} instance if the copy was successful, or null if
     *         the input was invalid or the service could not be retrieved.
     */
    @Nullable
    public static ClipboardManager copyHtmlToClipboard(Context context, String html) {
        if (html == null || html.isEmpty()) return null;
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard != null) {
            ClipData htmlClipData = newHtmlText("html_clip", html, html);
            clipboard.setPrimaryClip(htmlClipData);
        }
        return clipboard;
    }

    /**
     * Appends the specified text to the current content of the clipboard.
     * <p>
     * This method retrieves the existing text from the clipboard, concatenates it with
     * the new text, and updates the primary clip. If the clipboard is currently empty,
     * it performs a standard copy operation.
     *
     * @param context The context used to access the clipboard service.
     * @param text    The text string to append to the existing clipboard content.
     */
    @Nullable
    public static ClipboardManager appendTextToClipboard(Context context, String text) {
        if (text == null || text.isEmpty()) return null;
        String currentText = getTextFromClipboard(context);
        return copyTextToClipboard(context, currentText + text);
    }

    /**
     * Retrieves the plain text content from the system clipboard.
     *
     * @param context The context used to access the clipboard service.
     * @return The text content of the first item in the primary clip if available;
     *         otherwise, an empty string.
     */
    public static String getTextFromClipboard(Context context) {
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard == null || clipboard.getPrimaryClip() == null) return "";
        ClipData clip = clipboard.getPrimaryClip();

        if (clip != null && clip.getItemCount() > 0) {
            CharSequence text = clip.getItemAt(0).getText();
            return text != null ? text.toString() : "";
        }

        return "";
    }

    /**
     * Copies the provided plain text to the system clipboard.
     *
     * @param context The context used to access the clipboard service.
     * @param text    The string to be copied to the clipboard. If null or empty, the operation is aborted.
     * @return The {@link ClipboardManager} instance if the copy was successful, or {@code null}
     *         if the text was empty/null or the clipboard service could not be accessed.
     */
    @Nullable
    public static ClipboardManager copyTextToClipboard(Context context, String text) {
        if (text == null || text.isEmpty()) return null;
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard != null) clipboard.setPrimaryClip(newPlainText("text_clip", text));
        return clipboard;
    }

    /**
     * Registers a listener to be notified when the primary clip on the clipboard changes.
     * <p>
     * This method wraps the provided listener in a proxy that uses a {@link WeakReference}
     * to help prevent memory leaks, and stores the mapping internally to allow for later removal
     * via {@link #removeClipboardListener(Context, OnPrimaryClipChangedListener)}.
     * </p>
     *
     * @param context  The context used to access the clipboard service.
     * @param listener The listener to be added. If null, the method returns immediately.
     */
    public static void setClipboardListener(Context context,
                                            OnPrimaryClipChangedListener listener) {
        if (listener == null) return;
        ClipboardManager clipboard = getClipboard(context);

        if (clipboard == null) return;
        ListenerProxy proxy = new ListenerProxy(listener);
        clipboard.addPrimaryClipChangedListener(proxy);
        activeListeners.put(listener, proxy);
    }

    /**
     * Removes a previously registered clipboard listener.
     * <p>
     * This method unregisters the proxy associated with the provided {@code listener}
     * from the system {@link ClipboardManager} and removes it from the internal
     * tracking map to prevent memory leaks.
     *
     * @param context  The context used to access the clipboard service.
     * @param listener The listener to be removed.
     */
    public static void removeClipboardListener(Context context,
                                               OnPrimaryClipChangedListener listener) {
        if (listener == null) return;
        ClipboardManager clipboard = getClipboard(context);
        if (clipboard == null) return;
        OnPrimaryClipChangedListener proxy = activeListeners.remove(listener);
        if (proxy != null) clipboard.removePrimaryClipChangedListener(proxy);
    }

    /**
     * Safely retrieves the {@link ClipboardManager} system service.
     *
     * @param context the context used to access the system service.
     * @return the {@link ClipboardManager} instance, or {@code null} if the context is null
     *         or the service is not available.
     */
    @Nullable
    private static ClipboardManager getClipboard(Context context) {
        if (context == null) return null;
        Object service = context.getSystemService(CLIPBOARD_SERVICE);
        if (service instanceof ClipboardManager) return (ClipboardManager) service;
        return null;
    }
}