package userInterface.fragmentsUIs.homepage;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import userInterface.intentHandler.IntentHandlerActivity;
import userInterface.watchVideo.PlayRequest;

/**
 * Everything the Home screen buttons DO lives here, so you can change behaviour
 * (for example open your own video player) without touching the fragment.
 */
public final class HomeActions {

	private HomeActions() {}

	/**
	 * Play something in the app's own player: a link (http/https) is extracted and streamed,
	 * anything else (content:// or file://) is played as a local file.
	 */
	public static void play(Context context, Uri uri) {
		try {
			String scheme = uri.getScheme();
			if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
				PlayRequest.link(uri.toString()).start(context);
				return;
			}
			String type = null;
			try {
				type = context.getContentResolver().getType(uri);
			} catch (Throwable ignored) {
			}
			playFile(context, uri, displayName(context, uri), type != null && type.startsWith("audio/"));
		} catch (Throwable e) {
			coreUtils.library.views.AppToast.show(context, "Could not open this");
		}
	}

	/** Opens a link in the app's own browser tab. */
	public static void openInBrowser(Context context, String link) {
		Context c = context;
		for (int i = 0; i < 6 && c instanceof android.content.ContextWrapper; i++) {
			if (c instanceof userInterface.mainScreen.MainActivity) break;
			c = ((android.content.ContextWrapper) c).getBaseContext();
		}
		if (c instanceof userInterface.mainScreen.MainActivity) {
			sysModules.browserEngine.BrowserSession.get().pendingUrl = link;
			((userInterface.mainScreen.MainActivity) c).openTab(
				userInterface.mainScreen.NavigationTabs.BROWSER_TABS);
		} else {
			coreUtils.library.process.IntentLinkHelper.openUrlInBrowser(context, link);
		}
	}

	/** Play a downloaded or picked file. */
	public static void playFile(Context context, Uri uri, String title, boolean audio) {
		PlayRequest.file(uri, title, audio).start(context);
	}

	/** Extract and stream a page link (YouTube and other sites). */
	public static void playLink(Context context, String link) {
		PlayRequest.link(link).start(context);
	}

	private static String displayName(Context context, Uri uri) {
		try (android.database.Cursor c = context.getContentResolver().query(uri,
			new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
			if (c != null && c.moveToFirst()) {
				String name = c.getString(0);
				if (name != null && !name.isEmpty()) {
					int dot = name.lastIndexOf('.');
					return dot > 0 ? name.substring(0, dot) : name;
				}
			}
		} catch (Throwable ignored) {
		}
		String last = uri.getLastPathSegment();
		return last == null ? "" : last;
	}

	/** Opens the download popup (quality list + Download button) for a link. */
	public static void fetch(Context context, String link) {
		Intent intent = new Intent(context, IntentHandlerActivity.class);
		intent.setAction(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, link);
		context.startActivity(intent);
	}

	public static void comingSoon(Context context) {
		coreUtils.library.views.AppToast.show(context, "Coming soon");
	}
}
