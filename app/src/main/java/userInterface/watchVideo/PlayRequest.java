package userInterface.watchVideo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * What the player should play. Three kinds:
 * <ul>
 *   <li>FILE: a local video / audio (downloaded file or picked from the phone)</li>
 *   <li>LINK: a page link (YouTube, Instagram, ...). The app extracts the real stream.</li>
 *   <li>STREAM: a direct media address captured from a web page (with its referer / cookies).</li>
 * </ul>
 * Build one with the static helpers and call {@link #start(Context)}.
 */
public final class PlayRequest {

	public enum Kind {FILE, LINK, STREAM}

	/** One subtitle file offered by a page. */
	public static final class Sub {
		public final String url;
		public final String label;
		public final String lang;

		public Sub(String url, String label, String lang) {
			this.url = url;
			this.label = label == null ? "" : label;
			this.lang = lang == null ? "" : lang;
		}
	}

	private static final String X_KIND = "pr_kind";
	private static final String X_URL = "pr_url";
	private static final String X_TITLE = "pr_title";
	private static final String X_REFERER = "pr_referer";
	private static final String X_UA = "pr_ua";
	private static final String X_COOKIE = "pr_cookie";
	private static final String X_PAGE = "pr_page";
	private static final String X_AUDIO = "pr_audio";
	private static final String X_SUBS = "pr_subs";
	private static final String X_RESUME = "pr_resume";
	private static final char SEP = '\u0001';

	public Kind kind = Kind.LINK;
	public String url = "";
	public String title = "";
	public String referer = "";
	public String userAgent = "";
	public String cookie = "";
	/** The web page a STREAM came from. Used as a fallback link if the raw stream fails. */
	public String pageUrl = "";
	public boolean audio = false;
	public final List<Sub> subs = new ArrayList<>();

	public static PlayRequest file(Uri uri, String title, boolean audio) {
		PlayRequest r = new PlayRequest();
		r.kind = Kind.FILE;
		r.url = uri.toString();
		r.title = title == null ? "" : title;
		r.audio = audio;
		return r;
	}

	public static PlayRequest link(String link) {
		PlayRequest r = new PlayRequest();
		r.kind = Kind.LINK;
		r.url = link;
		r.title = link;
		return r;
	}

	public static PlayRequest stream(String streamUrl, String title, String pageUrl, String referer,
	                                 String userAgent, String cookie, boolean audio) {
		PlayRequest r = new PlayRequest();
		r.kind = Kind.STREAM;
		r.url = streamUrl;
		r.title = title == null ? "" : title;
		r.pageUrl = pageUrl == null ? "" : pageUrl;
		r.referer = referer == null ? "" : referer;
		r.userAgent = userAgent == null ? "" : userAgent;
		r.cookie = cookie == null ? "" : cookie;
		r.audio = audio;
		return r;
	}

	public PlayRequest copy() {
		PlayRequest r = new PlayRequest();
		r.kind = kind;
		r.url = url;
		r.title = title;
		r.referer = referer;
		r.userAgent = userAgent;
		r.cookie = cookie;
		r.pageUrl = pageUrl;
		r.audio = audio;
		r.subs.addAll(subs);
		return r;
	}

	public Intent toIntent(Context context) {
		Intent intent = new Intent(context, WatchVideoActivity.class);
		intent.putExtra(X_KIND, kind.name());
		intent.putExtra(X_URL, url);
		intent.putExtra(X_TITLE, title);
		intent.putExtra(X_REFERER, referer);
		intent.putExtra(X_UA, userAgent);
		intent.putExtra(X_COOKIE, cookie);
		intent.putExtra(X_PAGE, pageUrl);
		intent.putExtra(X_AUDIO, audio);
		String[] encoded = new String[subs.size()];
		for (int i = 0; i < encoded.length; i++) {
			Sub s = subs.get(i);
			encoded[i] = s.url + SEP + s.label + SEP + s.lang;
		}
		intent.putExtra(X_SUBS, encoded);
		if (kind == Kind.FILE && url.startsWith("content:")) {
			// keeps read access to a shared file alive for the player screen
			intent.setClipData(android.content.ClipData.newRawUri("", Uri.parse(url)));
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		}
		return intent;
	}

	/** Opens the player screen on the session that keeps playing in the background. */
	public static Intent resumeIntent(Context context) {
		Intent intent = new Intent(context, WatchVideoActivity.class);
		intent.putExtra(X_RESUME, true);
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		return intent;
	}

	public static boolean isResume(Intent intent) {
		return intent != null && intent.getBooleanExtra(X_RESUME, false);
	}

	/**
	 * Another app asked us to play something ("Play with" / ACTION_VIEW): a web link is run through
	 * the engines, a file or content address is played as it is.
	 */
	public static PlayRequest fromView(Context context, Intent intent) {
		if (intent == null) return null;
		Uri data = intent.getData();
		if (data == null) return null;
		String scheme = data.getScheme() == null ? "" : data.getScheme().toLowerCase(java.util.Locale.US);
		if (scheme.equals("http") || scheme.equals("https")) return link(data.toString());
		String type = intent.getType();
		if (type == null) {
			try {
				type = context.getContentResolver().getType(data);
			} catch (Throwable ignored) {
			}
		}
		boolean audio = type != null && type.startsWith("audio/");
		return file(data, displayName(context, data), audio);
	}

	public static String displayName(Context context, Uri uri) {
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

	public void start(Context context) {
		Intent intent = toIntent(context);
		if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}

	public static PlayRequest from(Intent intent) {
		if (intent == null || !intent.hasExtra(X_URL)) return null;
		PlayRequest r = new PlayRequest();
		try {
			r.kind = Kind.valueOf(String.valueOf(intent.getStringExtra(X_KIND)));
		} catch (Throwable e) {
			r.kind = Kind.LINK;
		}
		r.url = nz(intent.getStringExtra(X_URL));
		r.title = nz(intent.getStringExtra(X_TITLE));
		r.referer = nz(intent.getStringExtra(X_REFERER));
		r.userAgent = nz(intent.getStringExtra(X_UA));
		r.cookie = nz(intent.getStringExtra(X_COOKIE));
		r.pageUrl = nz(intent.getStringExtra(X_PAGE));
		r.audio = intent.getBooleanExtra(X_AUDIO, false);
		String[] encoded = intent.getStringArrayExtra(X_SUBS);
		if (encoded != null) {
			for (String line : encoded) {
				String[] parts = line.split(String.valueOf(SEP), -1);
				if (parts.length >= 3 && parts[0].startsWith("http")) {
					r.subs.add(new Sub(parts[0], parts[1], parts[2]));
				}
			}
		}
		return r.url.isEmpty() ? null : r;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
