package sysModules.crashedHandler;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Process;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shows the crash / hang / black-screen report written by {@link CrashLogger}.
 * <p>
 * Runs in its own process (see the manifest) so it stays alive when the main app process
 * dies, and it uses only plain framework views (no AppCompat, no ViewBinding, no app theme),
 * so it cannot be broken by the same problem it reports.
 */
public class CrashLogActivity extends Activity {

	private static final int MAX_SHARE_CHARS = 150_000;

	private TextView reportView;
	private String reportText = "";
	private int mainPid = -1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		buildUi();
		showReport(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		showReport(intent);
	}

	private void buildUi() {
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(Color.parseColor("#101114"));
		root.setPadding(dp(14), dp(14), dp(14), dp(14));
		root.setFitsSystemWindows(true);

		TextView title = new TextView(this);
		title.setText("Crash / problem report");
		title.setTextColor(Color.WHITE);
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		root.addView(title);

		TextView hint = new TextView(this);
		hint.setText("Copy or share this text with the developer. It shows the real reason.");
		hint.setTextColor(Color.parseColor("#9AA0A6"));
		hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		hint.setPadding(0, dp(4), 0, dp(10));
		root.addView(hint);

		LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setGravity(Gravity.CENTER_VERTICAL);
		buttons.addView(makeButton("Copy", v -> copyReport()));
		buttons.addView(makeButton("Share", v -> shareReport()));
		buttons.addView(makeButton("Close app", v -> closeEverything()));
		root.addView(buttons);

		ScrollView scroll = new ScrollView(this);
		LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
		scrollParams.topMargin = dp(10);
		scroll.setLayoutParams(scrollParams);

		reportView = new TextView(this);
		reportView.setTextColor(Color.parseColor("#E8EAED"));
		reportView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		reportView.setTypeface(Typeface.MONOSPACE);
		reportView.setTextIsSelectable(true);
		scroll.addView(reportView);
		root.addView(scroll);

		setContentView(root);
	}

	private Button makeButton(String text, View.OnClickListener listener) {
		Button button = new Button(this);
		button.setText(text);
		button.setAllCaps(false);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		params.rightMargin = dp(6);
		button.setLayoutParams(params);
		button.setOnClickListener(listener);
		return button;
	}

	private void showReport(Intent intent) {
		String path = intent == null ? null : intent.getStringExtra(CrashLogger.EXTRA_REPORT_PATH);
		mainPid = intent == null ? -1 : intent.getIntExtra(CrashLogger.EXTRA_MAIN_PID, -1);

		if (path == null) {
			reportText = "No report path was given.";
		} else {
			File file = new File(path);
			reportText = readFile(file);
			// Seen now, so it will not be shown again on the next launch.
			//noinspection ResultOfMethodCallIgnored
			new File(file.getParentFile(), CrashLogger.PENDING_FLAG_NAME).delete();
		}
		reportView.setText(reportText);
	}

	private String readFile(File file) {
		try (InputStream in = new FileInputStream(file)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} catch (Throwable error) {
			return "Could not read the report file:\n" + path(file) + "\n" + error;
		}
	}

	private String path(File file) {
		return file == null ? "(null)" : file.getAbsolutePath();
	}

	private String limited() {
		if (reportText.length() <= MAX_SHARE_CHARS) return reportText;
		return reportText.substring(0, 20_000) + "\n...\n"
			+ reportText.substring(reportText.length() - (MAX_SHARE_CHARS - 20_000));
	}

	private void copyReport() {
		try {
			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setPrimaryClip(ClipData.newPlainText("crash report", limited()));
			coreUtils.library.views.AppToast.show(this, "Copied");
		} catch (Throwable error) {
			coreUtils.library.views.AppToast.show(this, "Copy failed, long-press the text to select it", true);
		}
	}

	private void shareReport() {
		try {
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("text/plain");
			send.putExtra(Intent.EXTRA_SUBJECT, "Crash report");
			send.putExtra(Intent.EXTRA_TEXT, limited());
			startActivity(Intent.createChooser(send, "Share report"));
		} catch (Throwable error) {
			coreUtils.library.views.AppToast.show(this, "Share failed, use Copy instead", true);
		}
	}

	private void closeEverything() {
		try {
			if (mainPid > 0 && mainPid != Process.myPid()) Process.killProcess(mainPid);
		} catch (Throwable ignored) {
		}
		finishAndRemoveTask();
		Process.killProcess(Process.myPid());
	}

	private int dp(int value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
			getResources().getDisplayMetrics());
	}
}
