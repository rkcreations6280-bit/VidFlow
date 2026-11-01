package userInterface.intentHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the first web link inside shared text such as "Watch this! https://youtu.be/abc". */
public class IntentUrlParser {

	private static final Pattern URL = Pattern.compile("(https?://[^\\s]+)", Pattern.CASE_INSENSITIVE);

	/** @return the first http(s) link in the text, or null if there is none. */
	public static String extract(String text) {
		if (text == null) return null;
		Matcher matcher = URL.matcher(text);
		if (!matcher.find()) return null;
		String url = matcher.group(1);
		while (!url.isEmpty() && ")>.,;\"'".indexOf(url.charAt(url.length() - 1)) >= 0) {
			url = url.substring(0, url.length() - 1);
		}
		return url.isEmpty() ? null : url;
	}
}
