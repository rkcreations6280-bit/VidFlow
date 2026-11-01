package sysModules.mediaEngine;

import android.net.Uri;
import android.util.Base64;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The first and last engines of the player pipeline, both without a WebView:
 * <ul>
 *   <li>{@link #probe}: is this link already a media file / playlist? Then ExoPlayer gets it as is.</li>
 *   <li>{@link #scanPage}: look inside the page source (and embedded player pages) for a media address.</li>
 * </ul>
 */
public final class MediaProbe {

	public static final String UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
		+ "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

	/** A media address and the page it was found on. */
	public static final class Found {
		public String url;
		public String pageUrl;
		public String title = "";
		public String how = "";
	}

	private static final Pattern MEDIA = Pattern.compile(
		"https?:(?:\\\\?/){2}[^\\s\"'<>\\\\)\\]}]+?\\.(?:m3u8|mpd|mp4|webm|m4v|mov|mkv|flv|mp3|m4a|aac|ogg|opus|wav|flac)(?:\\?[^\\s\"'<>\\\\)\\]}]*)?",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern IFRAME = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
	private static final Pattern META = Pattern.compile(
		"<meta[^>]+(?:og:video(?::url|:secure_url)?|og:audio(?::url|:secure_url)?|twitter:player:stream)[^>]+content=[\"']([^\"']+)[\"']",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TAG_SRC = Pattern.compile(
		"<(?:video|audio|source)[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
	private static final Pattern ATOB = Pattern.compile("atob\\(\\s*[\"']([A-Za-z0-9+/=]{16,})[\"']\\s*\\)");
	private static final Pattern TITLE = Pattern.compile("<title[^>]*>([^<]{1,200})</title>", Pattern.CASE_INSENSITIVE);

	private MediaProbe() {}

	// ── helpers ──────────────────────────────────────────────────────

	public static String pathOf(String url) {
		try {
			String p = Uri.parse(url).getPath();
			return p == null ? "" : p.toLowerCase(Locale.US);
		} catch (Throwable e) {
			return "";
		}
	}

	/** Extension tells it is a media file (video, audio or playlist). */
	public static boolean looksLikeMedia(String url) {
		String p = pathOf(url);
		String[] ext = {".mp4", ".m4v", ".webm", ".mkv", ".mov", ".flv", ".avi", ".3gp", ".ts", ".m3u8", ".mpd",
			".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac", ".weba"};
		for (String e : ext) if (p.endsWith(e)) return true;
		return false;
	}

	public static boolean looksLikeAudio(String url) {
		String p = pathOf(url);
		String[] ext = {".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".wav", ".flac", ".weba"};
		for (String e : ext) if (p.endsWith(e)) return true;
		return false;
	}

	private static int score(String u) {
		String l = u.toLowerCase(Locale.US);
		int s = 0;
		if (l.contains(".m3u8") || l.contains(".mpd")) s += 30;
		if (l.contains(".mp4")) s += 20;
		if (l.contains("master") || l.contains("index")) s += 5;
		if (l.contains("thumb") || l.contains("preview") || l.contains("sample") || l.contains("trailer")
			|| l.contains("ads") || l.contains("sprite")) s -= 40;
		return s;
	}

	// ── 1. direct link ───────────────────────────────────────────────

	/**
	 * Asks the server for the first bytes. Returns a short description ("video/mp4", "hls playlist"...)
	 * when the link really is media, otherwise null. Run on a worker thread.
	 */
	public static String probe(String url, String referer) {
		try {
			HttpURLConnection c = open(url, referer);
			c.setRequestProperty("Range", "bytes=0-1023");
			try {
				int code = c.getResponseCode();
				if (code != 200 && code != 206) return null;
				String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.US);
				if (type.startsWith("video/") || type.startsWith("audio/") || type.contains("mpegurl")
					|| type.contains("dash+xml")) return type.split(";")[0];
				if (type.startsWith("application/octet-stream") || type.startsWith("binary/")
					|| type.startsWith("text/plain") || type.isEmpty()) {
					byte[] head = readUpTo(c.getInputStream(), 1024);
					String s = new String(head, "ISO-8859-1");
					if (s.startsWith("#EXTM3U")) return "hls playlist";
					if (s.length() > 8 && s.substring(4, 8).equals("ftyp")) return "mp4";
					if (head.length > 4 && head[0] == 0x1A && head[1] == 0x45 && (head[2] & 0xFF) == 0xDF) return "matroska";
					if (s.startsWith("ID3") || s.startsWith("OggS") || s.startsWith("fLaC")) return "audio";
				}
				return null;
			} finally {
				c.disconnect();
			}
		} catch (Throwable e) {
			return null;
		}
	}

	// ── 2. page source ───────────────────────────────────────────────

	/** Looks for a media address inside the page and the player pages embedded in it. */
	public static Found scanPage(String pageUrl, StringBuilder log) {
		try {
			return scan(pageUrl, pageUrl, 0, new LinkedHashSet<>(), log);
		} catch (Throwable e) {
			log.append("page scan: ").append(e).append('\n');
			return null;
		}
	}

	private static Found scan(String pageUrl, String referer, int depth, Set<String> visited,
	                          StringBuilder log) throws Exception {
		if (!visited.add(pageUrl) || visited.size() > 6) return null;
		String html = fetchText(pageUrl, referer);
		if (html == null || html.isEmpty()) {
			log.append("empty page: ").append(pageUrl).append('\n');
			return null;
		}
		log.append("scanned ").append(pageUrl).append(" (").append(html.length()).append(" chars)\n");
		String title = "";
		Matcher tm = TITLE.matcher(html);
		if (tm.find()) title = tm.group(1).trim();

		List<String> urls = new ArrayList<>();
		collect(html, pageUrl, urls);
		Matcher am = ATOB.matcher(html);
		while (am.find() && urls.size() < 60) {
			try {
				collect(new String(Base64.decode(am.group(1), Base64.DEFAULT), "UTF-8"), pageUrl, urls);
			} catch (Throwable ignored) {
			}
		}
		urls.sort((a, b) -> score(b) - score(a));
		int tried = 0;
		for (String u : urls) {
			if (tried++ >= 8) break;
			String type = probe(u, pageUrl);
			if (type != null) {
				Found f = new Found();
				f.url = u;
				f.pageUrl = pageUrl;
				f.title = title;
				f.how = "page source (" + type + ")";
				return f;
			}
			log.append("not playable: ").append(u).append('\n');
		}
		if (depth < 2) {
			Matcher im = IFRAME.matcher(html);
			int n = 0;
			while (im.find() && n++ < 4) {
				String src = resolve(pageUrl, im.group(1));
				if (src == null || !src.startsWith("http")) continue;
				Found inner = scan(src, pageUrl, depth + 1, visited, log);
				if (inner != null) {
					if (inner.title.isEmpty()) inner.title = title;
					return inner;
				}
			}
		}
		return null;
	}

	private static void collect(String text, String base, List<String> out) {
		String clean = text.replace("\\u0026", "&").replace("&amp;", "&").replace("\\/", "/");
		Matcher mm = META.matcher(clean);
		while (mm.find()) add(resolve(base, mm.group(1)), out);
		Matcher ts = TAG_SRC.matcher(clean);
		while (ts.find()) add(resolve(base, ts.group(1)), out);
		Matcher m = MEDIA.matcher(clean);
		while (m.find() && out.size() < 80) add(m.group(), out);
	}

	private static void add(String u, List<String> out) {
		if (u == null || !u.startsWith("http") || out.contains(u)) return;
		out.add(u);
	}

	private static String resolve(String base, String ref) {
		try {
			return new URL(new URL(base), ref.replace("&amp;", "&")).toString();
		} catch (Throwable e) {
			return null;
		}
	}

	private static String fetchText(String url, String referer) throws Exception {
		HttpURLConnection c = open(url, referer);
		try {
			int code = c.getResponseCode();
			if (code < 200 || code >= 300) return null;
			String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.US);
			if (!(type.contains("html") || type.contains("json") || type.contains("javascript")
				|| type.contains("text") || type.contains("xml") || type.isEmpty())) return null;
			return new String(readUpTo(c.getInputStream(), 2_000_000), "UTF-8");
		} finally {
			c.disconnect();
		}
	}

	private static HttpURLConnection open(String url, String referer) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setInstanceFollowRedirects(true);
		c.setConnectTimeout(8000);
		c.setReadTimeout(10000);
		c.setRequestProperty("User-Agent", UA);
		c.setRequestProperty("Accept", "*/*");
		c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
		if (referer != null) c.setRequestProperty("Referer", referer);
		try {
			String cookie = CookieManager.getInstance().getCookie(url);
			if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
		} catch (Throwable ignored) {
		}
		return c;
	}

	private static byte[] readUpTo(InputStream in, int max) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		try {
			while (out.size() < max && (n = in.read(buf, 0, Math.min(buf.length, max - out.size()))) > 0) {
				out.write(buf, 0, n);
			}
		} finally {
			in.close();
		}
		return out.toByteArray();
	}
}
