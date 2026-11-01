package userInterface.fragmentsUIs.browser;

/** Shortcut sites on the browser start page. Edit this table to change them. */
public final class QuickSites {

	public static final class Site {
		public final String name;
		public final String url;

		Site(String name, String url) {
			this.name = name;
			this.url = url;
		}
	}

	public static final Site[] ALL = new Site[]{
		new Site("YouTube", "https://m.youtube.com"),
		new Site("Instagram", "https://www.instagram.com"),
		new Site("Facebook", "https://m.facebook.com"),
		new Site("TikTok", "https://www.tiktok.com"),
		new Site("X", "https://x.com"),
		new Site("Vimeo", "https://vimeo.com"),
		new Site("Dailymotion", "https://www.dailymotion.com"),
		new Site("Reddit", "https://www.reddit.com"),
		new Site("Twitch", "https://m.twitch.tv"),
		new Site("SoundCloud", "https://soundcloud.com"),
		new Site("Wikipedia", "https://en.m.wikipedia.org"),
		new Site("DuckDuckGo", "https://duckduckgo.com"),
	};

	private QuickSites() {}
}
