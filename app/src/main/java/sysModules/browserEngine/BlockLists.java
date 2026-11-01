package sysModules.browserEngine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in block lists (host based, runs fully offline, nothing is downloaded).
 * A host matches when it or any parent domain is in the set
 * (ads.example.com matches "example.com").
 */
public final class BlockLists {

	public static final int NONE = 0;
	public static final int AD = 1;
	public static final int TRACKER = 2;

	private static final Set<String> ADS = new HashSet<>(Arrays.asList(
		"doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
		"pagead2.googlesyndication.com", "2mdn.net", "adsafeprotected.com", "moatads.com",
		"amazon-adsystem.com", "adnxs.com", "adsrvr.org", "advertising.com", "criteo.com",
		"criteo.net", "rubiconproject.com", "pubmatic.com", "openx.net", "openx.com",
		"casalemedia.com", "indexww.com", "smartadserver.com", "yieldmo.com", "media.net",
		"taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "zedo.com",
		"adform.net", "adition.com", "teads.tv", "sharethrough.com", "triplelift.com",
		"33across.com", "bidswitch.net", "contextweb.com", "lijit.com", "sovrn.com",
		"spotxchange.com", "spotx.tv", "springserve.com", "tremorhub.com", "innovid.com",
		"serving-sys.com", "sizmek.com", "adcolony.com", "applovin.com", "unityads.unity3d.com",
		"inmobi.com", "vungle.com", "chartboost.com", "ironsrc.com", "supersonicads.com",
		"popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net",
		"exoclick.com", "exosrv.com", "exdynsrv.com", "juicyads.com", "trafficjunky.net",
		"trafficfactory.biz", "clickadu.com", "adsterra.com", "adsterratools.com", "hilltopads.net",
		"hilltopads.com", "richpush.co", "pushwoosh.com", "pushengage.com", 
		"a-ads.com", "ad-maven.com", "admaven.com", "adcash.com", "bidvertiser.com",
		"adskeeper.com", "adspyglass.com", "ero-advertising.com", "plugrush.com", "realsrv.com",
		"tsyndicate.com", "syndication.exoclick.com", "mc.yandex.ru", "an.yandex.ru",
		"yandexadexchange.net", "adriver.ru", "begun.ru", "ads.yahoo.com", "adtechus.com",
		"advertising.aol.com", "ads.twitter.com", "ads-twitter.com", "ads.linkedin.com",
		"ads.pinterest.com", "ads.tiktok.com", "business-api.tiktok.com", "adsmeasurement.com",
		"admob.com", "adnetwork.vn", "adtival.network", "ad.gt", "adbrite.com", "adbooth.com",
		"admixer.net", "adocean.pl", "adscale.de", "adzerk.net", "carbonads.com", "carbonads.net",
		"buysellads.com", "bsa.network", "perfectaudience.com", "gumgum.com", "undertone.com",
		"yllix.com", "trafficstars.com", "tsyndicate.com", "mobiads.com", "mobfox.com", "smaato.net",
		"tapjoy.com", "fyber.com", "startappservice.com", "startapp.com", "mopub.com",
		"flurry.com", "adjust.com", "kochava.com", "tenjin.com", "singular.net"
	));

	private static final Set<String> TRACKERS = new HashSet<>(Arrays.asList(
		"google-analytics.com", "analytics.google.com", "googletagmanager.com",
		"googletagservices.com", "stats.g.doubleclick.net", "app-measurement.com",
		"firebaseinstallations.googleapis.com", "crashlytics.com", "firebase-settings.crashlytics.com",
		"connect.facebook.net", "pixel.facebook.com", "an.facebook.com",
		"analytics.tiktok.com", "analytics.twitter.com", "static.ads-twitter.com",
		"snap.licdn.com", "px.ads.linkedin.com", "tr.snapchat.com", "sc-static.net",
		"scorecardresearch.com", "quantserve.com", "quantcount.com", "comscore.com",
		"hotjar.com", "hotjar.io", "mouseflow.com", "fullstory.com", "crazyegg.com",
		"luckyorange.com", "inspectlet.com", "clarity.ms", "heap.io", "heapanalytics.com",
		"mixpanel.com", "mxpnl.com", "segment.io", "segment.com", "amplitude.com",
		"kissmetrics.com", "optimizely.com", "newrelic.com", "nr-data.net", "bugsnag.com",
		"sentry.io", "sentry-cdn.com", "rollbar.com", "loggly.com", "datadoghq.com",
		"bluekai.com", "demdex.net", "omtrdc.net", "everesttech.net", "2o7.net",
		"krxd.net", "exelator.com", "agkn.com", "rlcdn.com", "liveramp.com", "tapad.com",
		"mathtag.com", "turn.com", "bounceexchange.com", "chartbeat.com", "chartbeat.net",
		"parsely.com", "parse.ly", "permutive.com", "permutive.app", "id5-sync.com",
		"adsymptotic.com", "eyeota.net", "nexac.com", "pippio.com", "semasio.net",
		"ioam.de", "xiti.com", "atinternet-solutions.com", "webtrekk.net",
		"matomo.cloud", "piwik.pro", "statcounter.com", "histats.com", "hit.ua",
		"counter.yadro.ru", "top.mail.ru", "top-fwz1.mail.ru", "metrika.yandex.ru",
		"appsflyer.com", 		"adobedtm.com", "tealiumiq.com", "tags.tiqcdn.com", "ensighten.com", "mpulse.net",
		"akamai-mpulse.net", "go-mpulse.net", "browser-intake-datadoghq.com", "pingdom.net",
		"hs-analytics.net", "hs-scripts.com", "hsadspixel.net", "marketo.net", "mktoresp.com", "pardot.com", "bat.bing.com", "c.bing.com",
		"ct.pinterest.com", "log.pinterest.com", "trk.pinterest.com", "events.reddit.com",
		"pixel.reddit.com", "alb.reddit.com", "pixel-config.reddit.com"
	));

	/** Path fragments of ads served from normal (first party) hosts. */
	private static final String[] AD_PATHS = {
		"/pagead/", "/api/stats/ads", "/ptracking", "/adservice", "/ads/js/", "/adsbygoogle.js",
		"/doubleclick/", "/googleads", "/adserver", "/ad_frame", "/ads.js", "/advert.js"
	};

	private BlockLists() {}

	/** Returns {@link #NONE}, {@link #AD} or {@link #TRACKER}. */
	public static int classify(String host, String url) {
		if (host == null) return NONE;
		host = host.toLowerCase(java.util.Locale.US);
		String h = host;
		while (true) {
			if (ADS.contains(h)) return AD;
			if (TRACKERS.contains(h)) return TRACKER;
			int dot = h.indexOf('.');
			if (dot < 0) break;
			String parent = h.substring(dot + 1);
			if (parent.indexOf('.') < 0) break; // parent is only a TLD
			h = parent;
		}
		if (url != null) {
			String lower = url.toLowerCase(java.util.Locale.US);
			for (String path : AD_PATHS) {
				if (lower.contains(path)) return AD;
			}
		}
		return NONE;
	}

	/** Same registrable-ish site? (last two labels, good enough for redirect checks). */
	public static boolean sameSite(String hostA, String hostB) {
		if (hostA == null || hostB == null) return false;
		return siteOf(hostA).equals(siteOf(hostB));
	}

	public static String siteOf(String host) {
		String[] parts = host.toLowerCase(java.util.Locale.US).split("\\.");
		if (parts.length <= 2) return host.toLowerCase(java.util.Locale.US);
		String last = parts[parts.length - 1];
		String second = parts[parts.length - 2];
		boolean twoLevelTld = last.length() == 2 && second.length() <= 3
			&& (second.equals("co") || second.equals("com") || second.equals("org")
			|| second.equals("net") || second.equals("gov") || second.equals("ac"));
		int keep = twoLevelTld ? 3 : 2;
		StringBuilder sb = new StringBuilder();
		for (int i = parts.length - keep; i < parts.length; i++) {
			if (sb.length() > 0) sb.append('.');
			sb.append(parts[i]);
		}
		return sb.toString();
	}
}
