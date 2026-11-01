package userInterface.intentHandler;

import android.Manifest;
import android.app.Dialog;
import android.text.InputType;
import android.widget.EditText;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.nextgen.R;

import coreUtils.library.process.IntentLinkHelper;
import sysModules.crashedHandler.CrashLogger;
import sysModules.downloadSys.DownloadService;

/**
 * Opens when a link is shared to the app from YouTube, Instagram, browsers, etc.
 * Shows a bottom sheet with the video, the available qualities and a Download button.
 * The activity itself is transparent; everything is drawn by the bottom sheet.
 */
public class IntentHandlerActivity extends AppCompatActivity {

	private static final int BG = Color.parseColor("#1C1C1C");
	private static final int ROW_BG = Color.parseColor("#262626");
	private static final int ROW_SELECTED_BG = Color.parseColor("#2B2118");
	private static final int ORANGE = Color.parseColor("#FF8A00");
	private static final int TEXT = Color.WHITE;
	private static final int TEXT_DIM = Color.parseColor("#B5B5B5");

	private static final class Choice {
		String label;
		String detail;
		boolean audio;
		int height;
		long totalBytes;
	}

	private final ExecutorService io = Executors.newSingleThreadExecutor();
	private final List<Choice> choices = new ArrayList<>();
	private final List<View> rowViews = new ArrayList<>();

	private Dialog sheet;
	private String url;
	private String title;
	private int selected = -1;
	private int loadToken = 0;

	private ImageView thumbView;
	private TextView titleView;
	private TextView subtitleView;
	private ImageView renameButton;
	private LinearLayout statusBox;
	private com.airbnb.lottie.LottieAnimationView statusSpinner;
	private TextView statusText;
	private Button retryButton;
	private TextView listTitleView;
	private ScrollView listScroll;
	private GridLayout listBox;
	private Button downloadButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sysModules.downloadSys.LinkInfoFetcher.prewarm(this);
		requestNotificationPermissionIfNeeded();
		handle(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
		handle(intent);
	}

	@Override
	protected void onDestroy() {
		loadToken++;
		io.shutdownNow();
		if (sheet != null && sheet.isShowing()) {
			sheet.setOnDismissListener(null);
			sheet.dismiss();
		}
		super.onDestroy();
	}

	// ── Flow ─────────────────────────────────────────────────────────

	private void handle(Intent intent) {
		String shared = IntentLinkHelper.getIntentDataURI(intent);
		String link = IntentUrlParser.extract(shared);
		if (link == null) {
			coreUtils.library.views.AppToast.show(this, "No video link found in the shared text", true);
			finish();
			return;
		}
		url = link;
		title = link;
		if (sheet != null && sheet.isShowing()) {
			sheet.setOnDismissListener(null);
			sheet.dismiss();
		}
		buildSheet();
		loadInfo();
	}

	private void loadInfo() {
		final int token = ++loadToken;
		choices.clear();
		rowViews.clear();
		listBox.removeAllViews();
		selected = -1;
		downloadButton.setEnabled(false);
		downloadButton.setAlpha(0.5f);
		renameButton.setVisibility(View.GONE);
		listScroll.setVisibility(View.GONE);
		listTitleView.setVisibility(View.GONE);
		statusBox.setVisibility(View.VISIBLE);
		statusSpinner.setVisibility(View.VISIBLE);
		retryButton.setVisibility(View.GONE);
		statusText.setText("Getting video details…");

		final String link = url;
		try {
			io.execute(() -> {
				try {
					JSONObject info = sysModules.downloadSys.LinkInfoFetcher.fetch(this, link);
					runOnUiThread(() -> {
						if (token == loadToken && !isFinishing()) showInfo(info);
					});
				} catch (Throwable error) {
					CrashLogger.breadcrumb("fetch info failed: " + link, error);
					runOnUiThread(() -> {
						if (token == loadToken && !isFinishing()) showError(error);
					});
				}
			});
		} catch (Throwable error) {
			showError(error);
		}
	}

	private void showError(Throwable error) {
		String message = error.getMessage() == null ? error.toString() : error.getMessage();
		// yt-dlp prints a lot; the useful part is the line starting with "ERROR:".
		int at = message.lastIndexOf("ERROR:");
		if (at >= 0) message = message.substring(at);
		if (message.length() > 300) message = message.substring(0, 300) + "…";
		statusBox.setVisibility(View.VISIBLE);
		statusSpinner.setVisibility(View.GONE);
		retryButton.setVisibility(View.VISIBLE);
		statusText.setText("Could not read this link.\n" + message);
	}

	private void showInfo(JSONObject info) {
		String name = info.optString("title", "");
		if (!name.isEmpty()) title = name;
		titleView.setText(title);

		StringBuilder subtitle = new StringBuilder();
		String uploader = info.optString("uploader", "");
		if (!uploader.isEmpty() && !"null".equals(uploader)) subtitle.append(uploader);
		long duration = info.optLong("duration", 0);
		if (duration > 0) {
			if (subtitle.length() > 0) subtitle.append("  •  ");
			subtitle.append(formatDuration(duration));
		}
		subtitleView.setText(subtitle);

		String thumb = info.optString("thumbnail", "");
		if (!thumb.isEmpty() && !"null".equals(thumb)) {
			try {
				Glide.with(this).load(thumb).into(thumbView);
			} catch (Throwable ignored) {
			}
		}

		buildChoices(info);
		listBox.removeAllViews();
		rowViews.clear();
		for (int i = 0; i < choices.size(); i++) {
			View row = makeRow(choices.get(i), i);
			rowViews.add(row);
			listBox.addView(row);
		}
		statusBox.setVisibility(View.GONE);
		listScroll.setVisibility(View.VISIBLE);
		listTitleView.setVisibility(View.VISIBLE);
		renameButton.setVisibility(View.VISIBLE);
		select(0);
	}

	private void buildChoices(JSONObject info) {
		TreeMap<Integer, Long> videoSizes = new TreeMap<>();
		long bestAudioSize = 0;
		boolean sawAudioOnly = false;

		JSONArray formats = info.optJSONArray("formats");
		if (formats != null) {
			for (int i = 0; i < formats.length(); i++) {
				JSONObject format = formats.optJSONObject(i);
				if (format == null) continue;
				String vcodec = format.optString("vcodec", "none");
				String acodec = format.optString("acodec", "none");
				int height = format.optInt("height", 0);
				long size = format.optLong("filesize", 0);
				if (size <= 0) size = format.optLong("filesize_approx", 0);
				boolean hasVideo = !"none".equals(vcodec) && !"null".equals(vcodec) && height > 0;
				boolean hasAudio = !"none".equals(acodec) && !"null".equals(acodec);
				if (hasVideo) {
					Long old = videoSizes.get(height);
					if (old == null || size > old) videoSizes.put(height, size);
				} else if (hasAudio) {
					sawAudioOnly = true;
					if (size > bestAudioSize) bestAudioSize = size;
				}
			}
		}

		choices.clear();
		for (Integer height : videoSizes.descendingKeySet()) {
			if (height < 144) continue;
			long videoSize = videoSizes.get(height);
			Choice choice = new Choice();
			choice.height = height;
			choice.label = height + "p";
			choice.detail = videoSize > 0 ? "≈ " + formatSize(videoSize + bestAudioSize) : "";
			choice.totalBytes = videoSize > 0 ? videoSize + bestAudioSize : 0;
			choices.add(choice);
		}
		if (choices.isEmpty()) {
			Choice best = new Choice();
			best.label = "Best quality";
			best.detail = "";
			choices.add(best);
		}
		Choice audio = new Choice();
		audio.audio = true;
		audio.label = "Audio";
		audio.detail = bestAudioSize > 0 ? "M4A ≈ " + formatSize(bestAudioSize) : "M4A";
		audio.totalBytes = bestAudioSize;
		choices.add(audio);
	}

	private void select(int index) {
		selected = index;
		for (int i = 0; i < rowViews.size(); i++) {
			rowViews.get(i).setSelected(i == index);
		}
		downloadButton.setEnabled(true);
		downloadButton.setAlpha(1f);
	}

	private void startDownload() {
		if (selected < 0 || selected >= choices.size()) return;
		Choice choice = choices.get(selected);
		try {
			DownloadService.start(this, url, title, choice.audio,
				choice.height, choice.totalBytes);
			coreUtils.library.views.AppToast.show(this, "Download started. Progress is in the notification.", true);
		} catch (Throwable error) {
			CrashLogger.breadcrumb("could not start download", error);
			coreUtils.library.views.AppToast.show(this, "Could not start the download: " + error.getMessage(), true);
		}
		sheet.dismiss();
	}

	private void requestNotificationPermissionIfNeeded() {
		if (Build.VERSION.SDK_INT >= 33
			&& checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
			!= PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
		}
	}

	private void showRenameDialog() {
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		input.setMaxLines(4);
		input.setText(title);
		input.selectAll();
		LinearLayout holder = new LinearLayout(this);
		holder.setPadding(dp(20), dp(8), dp(20), 0);
		holder.addView(input, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		android.app.Dialog dialog = new coreUtils.library.views.AppDialog.Builder(this)
			.setTitle("Rename")
			.setView(holder)
			.setNegativeButton("Cancel", null)
			.setPositiveButton("Save", (d, which) -> {
				String name = input.getText().toString().trim();
				if (!name.isEmpty()) {
					title = name;
					titleView.setText(name);
				}
			})
			.create();
		dialog.show();
		input.requestFocus();
		if (dialog.getWindow() != null) {
			dialog.getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		}
	}

	// ── Bottom sheet UI (plain views, no layout files needed) ────────

	private void buildSheet() {
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(18), dp(10), dp(18), dp(16));
		root.setBackgroundResource(R.drawable.bg_v2_sheet);

		View handle = new View(this);
		GradientDrawable handleShape = new GradientDrawable();
		handleShape.setColor(Color.parseColor("#555555"));
		handleShape.setCornerRadius(dp(2));
		handle.setBackground(handleShape);
		LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
		handleParams.gravity = Gravity.CENTER_HORIZONTAL;
		handleParams.bottomMargin = dp(14);
		root.addView(handle, handleParams);

		LinearLayout header = new LinearLayout(this);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setBackgroundResource(R.drawable.bg_v2_sheet_header);
		header.setPadding(dp(8), dp(8), dp(8), dp(8));

		thumbView = new ImageView(this);
		thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
		thumbView.setBackgroundResource(R.drawable.bg_v2_thumb);
		thumbView.setClipToOutline(true);
		header.addView(thumbView, new LinearLayout.LayoutParams(dp(112), dp(63)));

		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0,
			ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		textsParams.leftMargin = dp(12);

		titleView = new TextView(this);
		titleView.setTextColor(Color.WHITE);
		titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		titleView.setTypeface(Typeface.DEFAULT_BOLD);
		titleView.setMaxLines(2);
		titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
		titleView.setText(url);
		texts.addView(titleView);

		subtitleView = new TextView(this);
		subtitleView.setTextColor(Color.parseColor("#FFE3C2"));
		subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
		subtitleView.setSingleLine(true);
		subtitleView.setPadding(0, dp(4), 0, 0);
		texts.addView(subtitleView);
		header.addView(texts, textsParams);

		renameButton = new ImageView(this);
		renameButton.setImageResource(R.drawable.ic_v2_edit);
		renameButton.setColorFilter(Color.WHITE);
		renameButton.setPadding(dp(10), dp(10), dp(10), dp(10));
		renameButton.setContentDescription("Rename");
		renameButton.setVisibility(View.GONE);
		renameButton.setOnClickListener(v -> showRenameDialog());
		LinearLayout.LayoutParams renameParams = new LinearLayout.LayoutParams(dp(44), dp(44));
		renameParams.leftMargin = dp(4);
		header.addView(renameButton, renameParams);
		root.addView(header);

		// Loading / error area
		statusBox = new LinearLayout(this);
		statusBox.setOrientation(LinearLayout.VERTICAL);
		statusBox.setGravity(Gravity.CENTER_HORIZONTAL);
		statusBox.setPadding(0, dp(22), 0, dp(10));
		statusSpinner = new com.airbnb.lottie.LottieAnimationView(this);
		statusSpinner.setAnimation(R.raw.animation_circle_loading);
		statusSpinner.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
		statusSpinner.playAnimation();
		statusBox.addView(statusSpinner, new LinearLayout.LayoutParams(dp(72), dp(72)));
		statusText = new TextView(this);
		statusText.setTextColor(TEXT_DIM);
		statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
		statusText.setGravity(Gravity.CENTER);
		statusText.setPadding(0, dp(10), 0, 0);
		statusBox.addView(statusText);
		retryButton = makeButton("Try again", false);
		retryButton.setOnClickListener(v -> loadInfo());
		LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
		retryParams.topMargin = dp(10);
		statusBox.addView(retryButton, retryParams);
		root.addView(statusBox);

		// Quality list
		TextView listTitle = new TextView(this);
		listTitleView = listTitle;
		listTitle.setVisibility(View.GONE);
		listTitle.setText("Choose quality");
		listTitle.setTextColor(ORANGE);
		listTitle.setTypeface(Typeface.DEFAULT_BOLD);
		listTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		listTitle.setPadding(0, dp(16), 0, dp(6));

		listBox = new GridLayout(this);
		listBox.setColumnCount(3);
		listScroll = new ScrollView(this);
		listScroll.addView(listBox);
		listScroll.setVisibility(View.GONE);
		int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.38f);
		root.addView(listTitle);
		root.addView(listScroll, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		listScroll.post(() -> {
			if (listScroll.getHeight() > maxHeight) {
				ViewGroup.LayoutParams params = listScroll.getLayoutParams();
				params.height = maxHeight;
				listScroll.setLayoutParams(params);
			}
		});

		// Buttons
		LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setPadding(0, dp(14), 0, 0);
		Button cancel = makeButton("Cancel", false);
		cancel.setOnClickListener(v -> sheet.dismiss());
		downloadButton = makeButton("Download", true);
		downloadButton.setOnClickListener(v -> startDownload());
		downloadButton.setEnabled(false);
		LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
		cancelParams.rightMargin = dp(10);
		buttons.addView(cancel, cancelParams);
		buttons.addView(downloadButton, new LinearLayout.LayoutParams(0, dp(48), 1.4f));
		root.addView(buttons);

		sheet = new Dialog(this);
		sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
		sheet.setContentView(root);
		sheet.setOnDismissListener(d -> finish());
		sheet.setCanceledOnTouchOutside(true);
		Window window = sheet.getWindow();
		if (window != null) {
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,
				coreUtils.library.views.WideLayout.dp(this, 560)), ViewGroup.LayoutParams.WRAP_CONTENT);
			window.setGravity(Gravity.BOTTOM);
			window.setWindowAnimations(android.R.style.Animation_InputMethod);
			window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			window.setDimAmount(0.55f);
		}
		sheet.show();
	}

	private View makeRow(Choice choice, int index) {
		LinearLayout chip = new LinearLayout(this);
		chip.setOrientation(LinearLayout.VERTICAL);
		chip.setGravity(Gravity.CENTER);
		chip.setPadding(dp(4), dp(10), dp(4), dp(10));
		chip.setBackgroundResource(R.drawable.bg_v2_chip);

		TextView label = new TextView(this);
		label.setText(choice.label);
		label.setTextColor(TEXT);
		label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		label.setTypeface(Typeface.DEFAULT_BOLD);
		label.setGravity(Gravity.CENTER);
		chip.addView(label);

		TextView detail = new TextView(this);
		detail.setText(choice.detail);
		detail.setTextColor(TEXT_DIM);
		detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
		detail.setGravity(Gravity.CENTER);
		detail.setSingleLine(true);
		chip.addView(detail);

		GridLayout.LayoutParams params = new GridLayout.LayoutParams(
			GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
		params.width = 0;
		params.setMargins(dp(4), dp(4), dp(4), dp(4));
		chip.setLayoutParams(params);
		chip.setOnClickListener(v -> select(index));
		return chip;
	}

	private Button makeButton(String text, boolean primary) {
		Button button = new Button(this);
		button.setText(text);
		button.setAllCaps(false);
		button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		button.setTypeface(Typeface.DEFAULT_BOLD);
		button.setTextColor(primary ? Color.parseColor("#111111") : TEXT);
		button.setBackgroundResource(primary ? R.drawable.bg_v2_btn_primary : R.drawable.bg_v2_btn_secondary);
		button.setStateListAnimator(null);
		return button;
	}

	// ── Helpers ──────────────────────────────────────────────────────

	private int dp(int value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
			getResources().getDisplayMetrics());
	}

	private static String formatSize(long bytes) {
		double mb = bytes / (1024.0 * 1024.0);
		if (mb >= 1024) return String.format(Locale.US, "%.1f GB", mb / 1024.0);
		if (mb >= 10) return String.format(Locale.US, "%.0f MB", mb);
		return String.format(Locale.US, "%.1f MB", mb);
	}

	private static String formatDuration(long seconds) {
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
			: String.format(Locale.US, "%d:%02d", m, s);
	}
}
