package sysModules.downloadSys;

import android.content.Context;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Thin helper around youtubedl-android.
 * <p>
 * The library's {@code execute(...)} signature changed between versions (Java callback interface
 * vs Kotlin function type), so it is called through reflection. That keeps this code compiling
 * and working with whichever version is on the classpath.
 */
public final class YtDlpRunner {

	public interface Progress {
		void onProgress(float percent, long etaSeconds, String line);
	}

	private YtDlpRunner() {}

	/** Safe to call many times; the library ignores repeated init calls. */
	public static void ensureInit(Context context) throws Exception {
		Context app = context.getApplicationContext();
		YoutubeDL.getInstance().init(app);
		try {
			FFmpeg.getInstance().init(app);
		} catch (Throwable ignored) {
		}
	}

	/** Runs yt-dlp and returns its stdout. {@code progress} and {@code processId} may be null. */
	public static String execute(Context context, YoutubeDLRequest request,
	                             String processId, Progress progress) throws Exception {
		ensureInit(context);
		YoutubeDL yt = YoutubeDL.getInstance();

		Method target = null;
		for (Method method : YoutubeDL.class.getMethods()) {
			Class<?>[] types = method.getParameterTypes();
			if (method.getName().equals("execute") && types.length == 3
				&& types[0] == YoutubeDLRequest.class && types[1] == String.class) {
				target = method;
				break;
			}
		}

		try {
			Object response;
			if (target != null) {
				Class<?> callbackType = target.getParameterTypes()[2];
				Object callback = null;
				if (progress != null) {
					callback = Proxy.newProxyInstance(callbackType.getClassLoader(),
						new Class<?>[]{callbackType}, (proxy, method, args) -> {
							if (method.getDeclaringClass() == Object.class) {
								switch (method.getName()) {
									case "hashCode": return System.identityHashCode(proxy);
									case "equals": return proxy == args[0];
									default: return "ProgressProxy";
								}
							}
							if (args != null && args.length == 3) {
								try {
									progress.onProgress(((Number) args[0]).floatValue(),
										((Number) args[1]).longValue(), String.valueOf(args[2]));
								} catch (Throwable ignored) {
								}
							}
							return method.getReturnType() == void.class ? null : kotlinUnit();
						});
				}
				response = target.invoke(yt, request, processId, callback);
			} else {
				Method single = YoutubeDL.class.getMethod("execute", YoutubeDLRequest.class);
				response = single.invoke(yt, request);
			}
			Object out = response.getClass().getMethod("getOut").invoke(response);
			return out == null ? "" : out.toString();
		} catch (InvocationTargetException wrapped) {
			Throwable cause = wrapped.getCause();
			if (cause instanceof Exception) throw (Exception) cause;
			if (cause != null) throw new Exception(cause);
			throw wrapped;
		}
	}

	/** Gets title, thumbnail and formats for a link without downloading it. */
	public static JSONObject fetchInfo(Context context, String url) throws Exception {
		return fetchInfo(context, url, null);
	}

	/** Same, with extra yt-dlp arguments, e.g. {"--extractor-args", "youtube:player_client=tv"}. */
	public static JSONObject fetchInfo(Context context, String url, String[] extra) throws Exception {
		YoutubeDLRequest request = new YoutubeDLRequest(url);
		if (extra != null) {
			for (int i = 0; i + 1 < extra.length; i += 2) request.addOption(extra[i], extra[i + 1]);
		}
		request.addOption("-J");
		request.addOption("--no-playlist");
		request.addOption("--no-warnings");
		String out = execute(context, request, null, null);
		int start = out.indexOf('{');
		int end = out.lastIndexOf('}');
		if (start < 0 || end <= start) throw new Exception("No video information was returned.");
		return new JSONObject(out.substring(start, end + 1));
	}

	/** Stops a running download started with the given process id. */
	public static void cancel(String processId) {
		try {
			Method method = YoutubeDL.class.getMethod("destroyProcessById", String.class);
			method.invoke(YoutubeDL.getInstance(), processId);
		} catch (Throwable ignored) {
		}
	}

	private static Object kotlinUnit() {
		try {
			return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
		} catch (Throwable error) {
			return null;
		}
	}
}
