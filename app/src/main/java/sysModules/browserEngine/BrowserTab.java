package sysModules.browserEngine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.webkit.RenderProcessGoneDetail;

import java.io.ByteArrayInputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;

/**
 * One browser tab = one WebView plus all the privacy / blocking logic.
 * The WebView lives in a {@link MutableContextWrapper} so a tab can survive while
 * the browser screen is closed, without holding on to an Activity.
 */
public final class BrowserTab {

	/** Callbacks into the UI. May be null while the browser screen is not visible. */
	public interface Listener {
		void onTabChanged(BrowserTab tab);
		void onProgress(BrowserTab tab, int progress);
		void onMediaChanged(BrowserTab tab);
		void onNotice(BrowserTab tab, String text, String actionLabel, Runnable action);
		boolean onCreateTab(BrowserTab opener, Message resultMsg);
		void onCloseTab(BrowserTab tab);
		boolean onFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params);
		void onDownload(BrowserTab tab, String url, String userAgent, String contentDisposition,
		                String mime, long length, boolean userGesture);
		void onFullscreenShow(View view, WebChromeClient.CustomViewCallback callback);
		void onFullscreenHide();
		void onJsDialog(BrowserTab tab, int type, String message, String defaultValue,
		                android.webkit.JsResult result, android.webkit.JsPromptResult promptResult);
		void onTabCrashed(BrowserTab tab);
		/** The floating button on a playing video was pressed. {@code src} may be empty or blob:. */
		void onPlayInApp(BrowserTab tab, String src, String title, String tracksJson);
	}

	public static final class Candidate {
		public final String label;
		public final String url;

		Candidate(String label, String url) {
			this.label = label;
			this.url = url;
		}
	}

	private static final String BRIDGE_NAME = "ng" + randomHex();
	private static final long GESTURE_WINDOW_MS = 2500;
	private static int nextId = 1;

	private static String randomHex() {
		byte[] bytes = new byte[6];
		new SecureRandom().nextBytes(bytes);
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
		return sb.toString();
	}

	public final int id = nextId++;
	public final WebView webView;
	private final MutableContextWrapper contextWrapper;
	private final Context appContext;
	private final Handler main = new Handler(Looper.getMainLooper());

	private Listener listener;
	public String title = "";
	public String url = "";
	public int progress = 100;
	public boolean loading = false;
	public Bitmap favicon;
	public boolean startPage = true;
	/** Blocked items on the current page. */
	public int blockedAds, blockedTrackers, blockedOther;

	// Media detection
	private boolean mediaPlaying = false;
	private String mediaDomSrc = "";
	private boolean mediaIsAudio = false;
	private final Set<String> sniffed = new LinkedHashSet<>();

	private long lastTouch = 0;
	private long pageFinishedAt = 0;
	private String userNavUrl = null;
	private String upgradedFrom = null;
	private String allowOnceUrl = null;
	private volatile String tapKind = "";
	private volatile long tapAt = 0;
	private volatile long trustedUntil = 0;
	private boolean destroyed = false;

	@SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface", "ClickableViewAccessibility"})
	public BrowserTab(Context context) {
		appContext = context.getApplicationContext();
		contextWrapper = new MutableContextWrapper(appContext);
		webView = new WebView(contextWrapper);
		webView.setBackgroundColor(Color.parseColor("#111111"));
		webView.addJavascriptInterface(new Bridge(), BRIDGE_NAME);
		webView.setWebViewClient(new Client());
		webView.setWebChromeClient(new Chrome());
		webView.setDownloadListener((u, ua, cd, mime, len) -> {
			boolean gesture = System.currentTimeMillis() - lastTouch < GESTURE_WINDOW_MS;
			if (listener != null) listener.onDownload(this, u, ua, cd, mime, len, gesture);
		});
		webView.setOnTouchListener((v, event) -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP) {
				lastTouch = System.currentTimeMillis();
			}
			return false;
		});
		applySettings();
	}

	// ── Attach / detach ──────────────────────────────────────────────

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void attachActivity(Context activity) {
		contextWrapper.setBaseContext(activity);
	}

	public void detachActivity() {
		contextWrapper.setBaseContext(appContext);
	}

	public void destroy() {
		if (destroyed) return;
		destroyed = true;
		listener = null;
		try {
			webView.stopLoading();
			webView.removeJavascriptInterface(BRIDGE_NAME);
			webView.setWebViewClient(new WebViewClient());
			webView.setWebChromeClient(null);
			android.view.ViewParent parent = webView.getParent();
			if (parent instanceof android.view.ViewGroup) {
				((android.view.ViewGroup) parent).removeView(webView);
			}
			webView.destroy();
		} catch (Throwable ignored) {
		}
	}

	// ── Settings ─────────────────────────────────────────────────────

	@SuppressLint("SetJavaScriptEnabled")
	public void applySettings() {
		AppConfigs cfg = AppConfigsRepo.getConfig();
		WebSettings s = webView.getSettings();
		s.setJavaScriptEnabled(cfg.isJavaScriptEnabled);
		s.setDomStorageEnabled(true);
		s.setAllowFileAccess(false);
		s.setAllowContentAccess(false);
		s.setAllowFileAccessFromFileURLs(false);
		s.setAllowUniversalAccessFromFileURLs(false);
		s.setGeolocationEnabled(false);
		s.setSaveFormData(false);
		s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
		s.setLoadsImagesAutomatically(cfg.isImageLoadingEnabled);
		s.setBlockNetworkImage(!cfg.isImageLoadingEnabled);
		s.setSupportMultipleWindows(true);
		s.setJavaScriptCanOpenWindowsAutomatically(false);
		s.setMediaPlaybackRequiresUserGesture(true);
		s.setSupportZoom(true);
		s.setBuiltInZoomControls(true);
		s.setDisplayZoomControls(false);
		s.setUseWideViewPort(true);
		s.setLoadWithOverviewMode(true);
		s.setSafeBrowsingEnabled(false); // Safe Browsing would send URL hashes to Google.
		s.setCacheMode(cfg.isPrivateBrowsingEnabled ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
		s.setUserAgentString(userAgent(cfg));
		CookieManager cookies = CookieManager.getInstance();
		cookies.setAcceptCookie(true);
		cookies.setAcceptThirdPartyCookies(webView,
			!BrowserPrefs.get(appContext, BrowserPrefs.THIRD_PARTY_COOKIES_BLOCK));
		WebView.setWebContentsDebuggingEnabled(false);
	}

	private String userAgent(AppConfigs cfg) {
		if (cfg.httpUserAgentForBrowser != null && !cfg.httpUserAgentForBrowser.trim().isEmpty()) {
			return cfg.httpUserAgentForBrowser;
		}
		if (cfg.isDesktopBrowsingEnabled) {
			return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
				+ "Chrome/120.0.0.0 Safari/537.36";
		}
		// Look like a normal Chrome (many sites break on the "; wv" WebView marker).
		String ua = WebSettings.getDefaultUserAgent(appContext);
		return ua.replace("; wv)", ")").replaceAll(" Version/\\d+\\.\\d+", "");
	}

	// ── Navigation ───────────────────────────────────────────────────

	public void load(String address) {
		if (address == null || address.trim().isEmpty()) return;
		startPage = false;
		upgradedFrom = null;
		trustedUntil = System.currentTimeMillis() + 10000;
		try {
			Uri parsed = Uri.parse(address);
			String host = parsed.getHost();
			if ("http".equalsIgnoreCase(parsed.getScheme())
				&& BrowserPrefs.get(appContext, BrowserPrefs.HTTPS_UPGRADE) && host != null
				&& !host.equals("localhost") && !host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
				&& !host.endsWith(".local")) {
				upgradedFrom = address;
				address = "https" + address.substring(4);
			}
		} catch (Throwable ignored) {
		}
		url = address;
		userNavUrl = address;
		java.util.Map<String, String> headers = new java.util.HashMap<>();
		headers.put("Sec-GPC", "1");
		headers.put("DNT", "1");
		webView.loadUrl(address, headers);
		notifyChanged();
	}

	public void reload() {
		if (loading) webView.stopLoading(); else webView.reload();
	}

	public boolean canGoBack() {
		return webView.canGoBack();
	}

	public String host() {
		try {
			String h = Uri.parse(url).getHost();
			return h == null ? "" : h;
		} catch (Throwable e) {
			return "";
		}
	}

	public boolean protectionOff() {
		return BrowserPrefs.isSiteAllowed(appContext, host());
	}

	public void allowOnce(String address) {
		allowOnceUrl = address;
	}

	private void notifyChanged() {
		if (listener != null) listener.onTabChanged(this);
	}

	// ── Media detection ──────────────────────────────────────────────

	public boolean mediaActive() {
		return mediaPlaying || !sniffed.isEmpty();
	}

	public boolean isMediaPlaying() {
		return mediaPlaying;
	}

	public boolean isAudio() {
		return mediaIsAudio;
	}

	public static boolean isYouTubeHost(String host) {
		if (host == null) return false;
		host = host.toLowerCase(Locale.US);
		return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com")
			|| host.endsWith(".youtube-nocookie.com") || host.equals("youtube-nocookie.com");
	}

	private static String pathOf(String u) {
		String lower = u.toLowerCase(Locale.US);
		int q = lower.indexOf('?');
		return q > 0 ? lower.substring(0, q) : lower;
	}

	private static boolean isAudioUrl(String u) {
		String p = pathOf(u);
		return p.endsWith(".m4a") || p.endsWith(".mp3") || p.endsWith(".aac") || p.endsWith(".ogg")
			|| p.endsWith(".oga") || p.endsWith(".opus") || p.endsWith(".wav") || p.endsWith(".flac");
	}

	private static int streamScore(String u) {
		String p = pathOf(u);
		boolean audioName = p.contains("audio") || p.contains("/aac") || p.contains("mp4a");
		if (p.endsWith(".m3u8")) {
			if (p.contains("master") || p.contains("index") || p.contains("playlist")) return audioName ? 40 : 100;
			return audioName ? 10 : 80;
		}
		if (p.endsWith(".mpd")) return 90;
		if (p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".m4v") || p.endsWith(".mov")
			|| p.endsWith(".flv")) return audioName ? 20 : 60;
		if (isAudioUrl(p)) return 5;
		return 30;
	}

	/** Best direct address for the player: manifests first, then video files; audio files only for audio. */
	public String bestStreamFor(String domSrc) {
		boolean wantAudio = mediaIsAudio;
		String best = "";
		int bestScore = -1;
		java.util.List<String> all = new ArrayList<>();
		if (domSrc != null && domSrc.startsWith("http")) all.add(domSrc);
		all.addAll(sniffed);
		for (int i = 0; i < all.size(); i++) {
			String u = all.get(i);
			if (!u.startsWith("http")) continue;
			if (!wantAudio && isAudioUrl(u)) continue;
			int score = streamScore(u) + (i == 0 && domSrc != null && u.equals(domSrc) ? 25 : 0) + i / 4;
			if (score > bestScore) {
				bestScore = score;
				best = u;
			}
		}
		return best;
	}

	public static boolean isKnownVideoSite(String host) {
		if (host == null) return false;
		host = host.toLowerCase(Locale.US);
		String[] sites = {"youtube.com", "youtu.be", "instagram.com", "facebook.com", "fb.watch",
			"tiktok.com", "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "reddit.com",
			"twitch.tv", "soundcloud.com", "pinterest.com", "snapchat.com", "likee.video",
			"bilibili.com", "ok.ru", "vk.com", "streamable.com", "imgur.com"};
		for (String site : sites) {
			if (host.equals(site) || host.endsWith("." + site)) return true;
		}
		return false;
	}

	/** Links that can be sent to the download popup, best first. */
	public List<Candidate> candidates() {
		List<Candidate> list = new ArrayList<>();
		if (url.startsWith("http")) list.add(new Candidate("This page  •  " + host(), url));
		if (mediaDomSrc.startsWith("http") && !hasUrl(list, mediaDomSrc)) {
			list.add(new Candidate((mediaIsAudio ? "Playing audio  •  " : "Playing video  •  ")
				+ shortUrl(mediaDomSrc), mediaDomSrc));
		}
		List<String> found = new ArrayList<>(sniffed);
		for (int i = found.size() - 1; i >= 0 && list.size() < 6; i--) {
			String u = found.get(i);
			if (u.startsWith("http") && !hasUrl(list, u)) list.add(new Candidate("Stream  •  " + shortUrl(u), u));
		}
		return list;
	}

	private static boolean hasUrl(List<Candidate> list, String url) {
		for (Candidate c : list) if (c.url.equals(url)) return true;
		return false;
	}

	private static String shortUrl(String u) {
		try {
			Uri uri = Uri.parse(u);
			String path = uri.getLastPathSegment();
			String h = uri.getHost() == null ? "" : uri.getHost();
			String text = path == null ? h : h + "/…/" + path;
			return text.length() > 60 ? text.substring(0, 60) + "…" : text;
		} catch (Throwable e) {
			return u.length() > 60 ? u.substring(0, 60) + "…" : u;
		}
	}

	private void sniff(String requestUrl) {
		String lower = requestUrl.toLowerCase(Locale.US);
		int q = lower.indexOf('?');
		String path = q > 0 ? lower.substring(0, q) : lower;
		if (lower.contains("googlevideo.com")) { // YouTube segments: the page link works better
			if (sniffed.add("yt")) postMedia();
			return;
		}
		boolean media = path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4")
			|| path.endsWith(".webm") || path.endsWith(".m4v") || path.endsWith(".m4a")
			|| path.endsWith(".mp3") || path.endsWith(".ogg") || path.endsWith(".flv")
			|| path.endsWith(".mov") || path.endsWith(".aac");
		if (!media) return;
		if (sniffed.size() >= 12) {
			java.util.Iterator<String> it = sniffed.iterator();
			it.next();
			it.remove();
		}
		if (sniffed.add(requestUrl)) postMedia();
	}

	private void postMedia() {
		main.post(() -> {
			if (listener != null) listener.onMediaChanged(this);
		});
	}

	private void resetPageState() {
		blockedAds = 0;
		blockedTrackers = 0;
		blockedOther = 0;
		mediaPlaying = false;
		mediaDomSrc = "";
		sniffed.clear();
	}

	// ── Script injection ─────────────────────────────────────────────

	private void inject() {
		AppConfigs cfg = AppConfigsRepo.getConfig();
		boolean off = protectionOff();
		boolean popup = cfg.isPopupBlockerEnabled && !off;
		boolean touch = BrowserPrefs.get(appContext, BrowserPrefs.TOUCH_BLOCK) && !off;
		boolean ads = cfg.isAdBlockerEnabled && !off;
		boolean playButton = BrowserPrefs.get(appContext, BrowserPrefs.PLAY_BUTTON)
			&& !isYouTubeHost(host());
		String script = BrowserScripts.build(BRIDGE_NAME, popup, touch, ads, ads, playButton);
		webView.evaluateJavascript(script, null);
	}

	// ── JS bridge (only records state, never sends data anywhere) ────

	private final class Bridge {
		@JavascriptInterface
		public void media(boolean playing, String src, String kind) {
			final String safeSrc = src == null ? "" : (src.length() > 2000 ? "" : src);
			final boolean audio = "audio".equals(kind);
			main.post(() -> {
				mediaPlaying = playing;
				mediaDomSrc = safeSrc;
				mediaIsAudio = audio;
				if (listener != null) listener.onMediaChanged(BrowserTab.this);
			});
		}

		@JavascriptInterface
		public void playInApp(String src, String title, String tracksJson) {
			final String safeSrc = src == null ? "" : (src.length() > 4000 ? "" : src);
			final String safeTitle = title == null ? "" : title;
			final String tracks = tracksJson == null || tracksJson.length() > 20000 ? "[]" : tracksJson;
			main.post(() -> {
				if (listener != null) listener.onPlayInApp(BrowserTab.this, safeSrc, safeTitle, tracks);
			});
		}

		@JavascriptInterface
		public void tap(String kind) {
			tapKind = kind == null ? "" : kind;
			tapAt = System.currentTimeMillis();
		}

		@JavascriptInterface
		public void blocked(String kind) {
			main.post(() -> {
				blockedOther++;
				BrowserPrefs.addBlocked(appContext, 1);
				notifyChanged();
			});
		}
	}

	// ── WebViewClient ────────────────────────────────────────────────

	private final class Client extends WebViewClient {

		@Override
		public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
			Uri uri = request.getUrl();
			String requestUrl = uri.toString();
			String scheme = uri.getScheme();
			if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) return null;
			sniff(requestUrl);

			if (protectionOff()) return null;
			AppConfigs cfg = AppConfigsRepo.getConfig();
			int type = BlockLists.classify(uri.getHost(), requestUrl);
			if (type == BlockLists.NONE) return null;
			boolean block = (type == BlockLists.AD && cfg.isAdBlockerEnabled)
				|| (type == BlockLists.TRACKER && BrowserPrefs.get(appContext, BrowserPrefs.TRACKER_BLOCK));
			if (!block) return null;
			if (request.isForMainFrame() && requestUrl.equals(userNavUrl)) return null; // user typed it

			final int kind = type;
			main.post(() -> {
				if (kind == BlockLists.AD) blockedAds++; else blockedTrackers++;
				BrowserPrefs.addBlocked(appContext, 1);
				notifyChanged();
			});
			return new WebResourceResponse("text/plain", "utf-8", 204, "No Content",
				new java.util.HashMap<>(), new ByteArrayInputStream(new byte[0]));
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
			return handleNavigation(view, request.getUrl().toString(), request.hasGesture(),
				request.isRedirect());
		}

		private boolean handleNavigation(WebView view, String target, boolean gesture, boolean redirect) {
			Uri uri = Uri.parse(target);
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);

			if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")
				|| scheme.equals("javascript")) return false;

			if (scheme.equals("http") || scheme.equals("https")) {
				if (target.equals(allowOnceUrl)) {
					allowOnceUrl = null;
					return false;
				}
				AppConfigs cfg = AppConfigsRepo.getConfig();
				// Upgrade plain http to https.
				if (scheme.equals("http") && BrowserPrefs.get(appContext, BrowserPrefs.HTTPS_UPGRADE)
					&& upgradedFrom == null && !isLocalHost(uri.getHost())) {
					upgradedFrom = target;
					view.loadUrl("https" + target.substring(4));
					return true;
				}
				boolean crossSite = !BlockLists.sameSite(host(), uri.getHost() == null ? "" : uri.getHost());
				long now = System.currentTimeMillis();
				boolean guard = !protectionOff() && cfg.isPopupBlockerEnabled
					&& BrowserPrefs.get(appContext, BrowserPrefs.REDIRECT_BLOCK)
					&& !startPage && pageFinishedAt > 0 && crossSite;
				if (guard) {
					boolean hijackedTap = now - tapAt < GESTURE_WINDOW_MS && "other".equals(tapKind);
					boolean chainRedirect = redirect && now < trustedUntil;
					boolean lateRedirect = redirect && !chainRedirect && !gesture;
					if (hijackedTap || lateRedirect) {
						blockedOther++;
						BrowserPrefs.addBlocked(appContext, 1);
						notifyChanged();
						if (listener != null) {
							final String blockedUrl = target;
							listener.onNotice(BrowserTab.this, "Blocked a redirect to " + uri.getHost(),
								"Allow", () -> {
									allowOnce(blockedUrl);
									webView.loadUrl(blockedUrl);
								});
						}
						return true;
					}
				}
				if (!redirect) trustedUntil = now + 10000;
				// Unwanted redirect: a page jumps to another site without any tap from the user.
				if (!protectionOff() && cfg.isPopupBlockerEnabled
					&& BrowserPrefs.get(appContext, BrowserPrefs.REDIRECT_BLOCK)
					&& !gesture && !redirect && !startPage
					&& System.currentTimeMillis() - pageFinishedAt > 800
					&& pageFinishedAt > 0
					&& !BlockLists.sameSite(host(), uri.getHost() == null ? "" : uri.getHost())
					&& System.currentTimeMillis() - lastTouch > GESTURE_WINDOW_MS) {
					blockedOther++;
					BrowserPrefs.addBlocked(appContext, 1);
					notifyChanged();
					if (listener != null) {
						final String blockedUrl = target;
						listener.onNotice(BrowserTab.this, "Blocked a redirect to " + uri.getHost(),
							"Allow", () -> {
								allowOnce(blockedUrl);
								webView.loadUrl(blockedUrl);
							});
					}
					return true;
				}
				return false;
			}

			// Other schemes (app links, market:, intent:, tel:, mailto: ...).
			if (listener != null && gesture) {
				final String link = target;
				listener.onNotice(BrowserTab.this, "This page wants to open another app", "Open",
					() -> openExternal(link));
			} else if (listener != null) {
				blockedOther++;
				BrowserPrefs.addBlocked(appContext, 1);
				listener.onNotice(BrowserTab.this, "Blocked an automatic app redirect", null, null);
			}
			return true;
		}

		private void openExternal(String link) {
			try {
				Intent intent;
				if (link.startsWith("intent:")) {
					intent = Intent.parseUri(link, Intent.URI_INTENT_SCHEME);
					intent.setComponent(null);
					intent.setSelector(null);
					String fallback = intent.getStringExtra("browser_fallback_url");
					if (intent.resolveActivity(appContext.getPackageManager()) == null
						&& fallback != null && fallback.startsWith("http")) {
						load(fallback);
						return;
					}
				} else {
					intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
				}
				intent.addCategory(Intent.CATEGORY_BROWSABLE);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				appContext.startActivity(intent);
			} catch (Throwable ignored) {
			}
		}

		private boolean isLocalHost(String host) {
			if (host == null) return true;
			return host.equals("localhost") || host.startsWith("192.168.") || host.startsWith("10.")
				|| host.startsWith("127.") || host.endsWith(".local")
				|| host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
		}

		@Override
		public void onPageStarted(WebView view, String pageUrl, Bitmap icon) {
			resetPageState();
			url = pageUrl;
			loading = true;
			startPage = false;
			favicon = null;
			inject();
			notifyChanged();
		}

		@Override
		public void onPageCommitVisible(WebView view, String pageUrl) {
			inject();
		}

		@Override
		public void onPageFinished(WebView view, String pageUrl) {
			loading = false;
			url = pageUrl;
			pageFinishedAt = System.currentTimeMillis();
			upgradedFrom = null;
			inject();
			AppConfigs cfg = AppConfigsRepo.getConfig();
			if (!cfg.isPrivateBrowsingEnabled && pageUrl.startsWith("http")) {
				final String t = view.getTitle();
				BrowserDb db = BrowserDb.get(appContext);
				new Thread(() -> db.addHistory(pageUrl, t)).start();
			}
			notifyChanged();
		}

		@Override
		public void doUpdateVisitedHistory(WebView view, String pageUrl, boolean isReload) {
			url = pageUrl;
			notifyChanged();
		}

		@Override
		public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
			if (request.isForMainFrame() && upgradedFrom != null
				&& request.getUrl().toString().startsWith("https")) {
				// https is not available for this site: fall back to the original http link once.
				String original = upgradedFrom;
				main.post(() -> {
					allowOnce(original);
					webView.loadUrl(original);
				});
			}
		}

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
			handler.cancel(); // never continue on a bad certificate
			if (listener != null) {
				listener.onNotice(BrowserTab.this, "Blocked: this site's security certificate is not valid",
					null, null);
			}
		}

		@Override
		public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
			if (listener != null) listener.onTabCrashed(BrowserTab.this);
			return true;
		}
	}

	// ── WebChromeClient ──────────────────────────────────────────────

	private final class Chrome extends WebChromeClient {

		@Override
		public void onProgressChanged(WebView view, int newProgress) {
			progress = newProgress;
			if (listener != null) listener.onProgress(BrowserTab.this, newProgress);
		}

		@Override
		public void onReceivedTitle(WebView view, String newTitle) {
			title = newTitle == null ? "" : newTitle;
			notifyChanged();
		}

		@Override
		public void onReceivedIcon(WebView view, Bitmap icon) {
			favicon = icon;
		}

		@Override
		public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
		                              Message resultMsg) {
			AppConfigs cfg = AppConfigsRepo.getConfig();
			boolean blockPopups = cfg.isPopupBlockerEnabled && !protectionOff();
			boolean hijacked = System.currentTimeMillis() - tapAt < GESTURE_WINDOW_MS && "other".equals(tapKind);
			if (listener == null || (blockPopups && (!isUserGesture || hijacked))) {
				blockedOther++;
				BrowserPrefs.addBlocked(appContext, 1);
				notifyChanged();
				if (listener != null) listener.onNotice(BrowserTab.this, "Blocked a pop-up", null, null);
				return false;
			}
			return listener.onCreateTab(BrowserTab.this, resultMsg);
		}

		@Override
		public void onCloseWindow(WebView window) {
			if (listener != null) listener.onCloseTab(BrowserTab.this);
		}

		@Override
		public void onShowCustomView(View view, CustomViewCallback callback) {
			if (listener != null) listener.onFullscreenShow(view, callback); else callback.onCustomViewHidden();
		}

		@Override
		public void onHideCustomView() {
			if (listener != null) listener.onFullscreenHide();
		}

		@Override
		public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
		                                 FileChooserParams params) {
			if (listener == null || !listener.onFileChooser(callback, params)) {
				callback.onReceiveValue(null);
			}
			return true;
		}

		@Override
		public void onPermissionRequest(PermissionRequest request) {
			request.deny(); // camera / microphone: never without the user
			if (listener != null) listener.onNotice(BrowserTab.this,
				"Blocked a camera / microphone request", null, null);
		}

		@Override
		public void onGeolocationPermissionsShowPrompt(String origin,
		                                               GeolocationPermissions.Callback callback) {
			callback.invoke(origin, false, false);
			if (listener != null) listener.onNotice(BrowserTab.this,
				"Blocked a location request", null, null);
		}

		@Override
		public boolean onJsAlert(WebView view, String u, String message, android.webkit.JsResult result) {
			if (listener == null) { result.cancel(); return true; }
			listener.onJsDialog(BrowserTab.this, 0, message, null, result, null);
			return true;
		}

		@Override
		public boolean onJsConfirm(WebView view, String u, String message, android.webkit.JsResult result) {
			if (listener == null) { result.cancel(); return true; }
			listener.onJsDialog(BrowserTab.this, 1, message, null, result, null);
			return true;
		}

		@Override
		public boolean onJsPrompt(WebView view, String u, String message, String defaultValue,
		                          android.webkit.JsPromptResult result) {
			if (listener == null) { result.cancel(); return true; }
			listener.onJsDialog(BrowserTab.this, 2, message, defaultValue, result, result);
			return true;
		}
	}
}
