package userInterface.intentHandler;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import userInterface.watchVideo.PlayRequest;

/**
 * "Share to VidFlow Play": a shared link goes through the engines and opens in the VidFlow
 * player; a shared video / audio file plays as it is. No screen of its own.
 */
public class PlayShareActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			handle(getIntent());
		} catch (Throwable e) {
			android.widget.Toast.makeText(this, "Could not open this", android.widget.Toast.LENGTH_LONG).show();
		}
		finish();
		overridePendingTransition(0, 0);
	}

	@SuppressWarnings("deprecation")
	private void handle(Intent intent) {
		if (intent == null) return;
		Uri stream = null;
		try {
			stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
		} catch (Throwable ignored) {
		}
		if (stream == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
			stream = intent.getClipData().getItemAt(0).getUri();
		}
		String text = intent.getStringExtra(Intent.EXTRA_TEXT);
		String link = IntentUrlParser.extract(text);
		if (link != null) {
			PlayRequest.link(link).start(this);
			return;
		}
		if (stream != null) {
			String type = intent.getType();
			if (type == null) {
				try {
					type = getContentResolver().getType(stream);
				} catch (Throwable ignored) {
				}
			}
			String scheme = stream.getScheme() == null ? "" : stream.getScheme();
			if (scheme.startsWith("http")) {
				PlayRequest.link(stream.toString()).start(this);
			} else {
				PlayRequest.file(stream, PlayRequest.displayName(this, stream),
					type != null && type.startsWith("audio/")).start(this);
			}
			return;
		}
		android.widget.Toast.makeText(this, "No video or link found in what was shared", android.widget.Toast.LENGTH_LONG).show();
	}
}
