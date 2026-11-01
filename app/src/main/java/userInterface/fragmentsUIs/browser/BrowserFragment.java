package userInterface.fragmentsUIs.browser;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nextgen.R;
import com.nextgen.databinding.FragBrowser1Binding;
import com.nextgen.databinding.ItemBrowserEntry1Binding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import coreUtils.base.BaseFragment;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import sysModules.browserEngine.BrowserDb;
import sysModules.browserEngine.BrowserPrefs;
import sysModules.browserEngine.BrowserSession;
import sysModules.browserEngine.BrowserTab;
import userInterface.fragmentsUIs.homepage.HomeActions;
import userInterface.watchVideo.PlayRequest;

/**
 * The in-app browser. All blocking / privacy logic is in sysModules.browserEngine;
 * this class is only the screen.
 */
public class BrowserFragment extends BaseFragment<FragBrowser1Binding> implements BrowserTab.Listener {

	@Override
	protected int maxContentWidthDp() {
		return 0; // the browser uses the whole screen
	}


	private final BrowserSession session = BrowserSession.get();
	private final Handler main = new Handler(Looper.getMainLooper());
	private BrowserTab tab;
	private boolean editing = false;
	private ObjectAnimator pulse;
	private boolean fabWasActive = false;

	// Fullscreen video
	private View customView;
	private WebChromeClient.CustomViewCallback customCallback;
	private FrameLayout fullscreenHolder;
	private int savedOrientation;
	private int savedUiFlags;

	private ValueCallback<Uri[]> fileCallback;
	private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(
		new ActivityResultContracts.StartActivityForResult(), result -> {
			if (fileCallback == null) return;
			Uri[] uris = null;
			if (result.getData() != null) {
				uris = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData());
			}
			fileCallback.onReceiveValue(uris);
			fileCallback = null;
		});

	private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
		@Override
		public void handleOnBackPressed() {
			if (customView != null) {
				hideFullscreen();
			} else if (editing) {
				stopEditing();
			} else if (tab != null && tab.canGoBack()) {
				tab.webView.goBack();
			} else if (session.count() > 1 && tab != null) {
				closeTab(tab);
			} else if (tab != null && !tab.startPage) {
				// Back to the start page.
				tab.startPage = true;
				tab.webView.loadUrl("about:blank");
				tab.url = "";
				tab.title = "";
				updateUi();
			}
		}
	};

	@Override
	protected FragBrowser1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
		return FragBrowser1Binding.inflate(inflater, container, false);
	}

	@Override
	protected void onLoadedLayout() {
		requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

		binding.btnShield.setOnClickListener(v -> {
			if (tab != null) BrowserSheets.showProtection(requireContext(), tab, this::reloadCurrent);
		});
		binding.urlPill.setOnClickListener(v -> startEditing());
		binding.tvUrl.setOnClickListener(v -> startEditing());
		binding.btnReloadStop.setOnClickListener(v -> {
			if (tab != null && !tab.startPage) tab.reload();
		});
		binding.btnTabs.setOnClickListener(v -> showTabs());
		binding.btnMenu.setOnClickListener(v -> showMenu());
		binding.fabDownload.setOnClickListener(v -> onFabClick(false));
		binding.fabDownload.setOnLongClickListener(v -> {
			onFabClick(true);
			return true;
		});
		binding.etUrl.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
				|| actionId == EditorInfo.IME_NULL) {
				submit(binding.etUrl.getText().toString());
				return true;
			}
			return false;
		});
		binding.etUrl.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) {
				if (editing) refreshSuggestions(s.toString());
			}
		});

		buildStartGrid();
		placeFab();

		if (session.count() == 0) session.newTab(requireContext());
		attachTab(session.current());
		consumePending();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (tab != null) tab.webView.onResume();
		consumePending();
		if (binding != null) updateUi();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (tab != null) tab.webView.onPause();
		try {
			CookieManager.getInstance().flush();
		} catch (Throwable ignored) {
		}
	}

	@Override
	public void onDestroyView() {
		hideFullscreen();
		stopPulse();
		main.removeCallbacksAndMessages(null);
		detachTab();
		super.onDestroyView();
	}

	private void consumePending() {
		if (binding == null) return;
		String sheet = session.pendingSheet;
		String url = session.pendingUrl;
		session.pendingSheet = null;
		session.pendingUrl = null;
		if (url != null && tab != null) tab.load(url);
		if ("history".equals(sheet)) showHistory();
		else if ("bookmarks".equals(sheet)) showBookmarks();
	}

	// ── Tabs ─────────────────────────────────────────────────────────

	private void attachTab(BrowserTab next) {
		if (next == null) return;
		detachTab();
		tab = next;
		session.select(tab);
		if (tab.webView.getParent() instanceof ViewGroup) {
			((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
		}
		tab.attachActivity(requireActivity());
		tab.setListener(this);
		binding.webContainer.addView(tab.webView, 0, new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		tab.webView.setOnLongClickListener(v -> onLongPress());
		tab.webView.onResume();
		tab.applySettings();
		updateUi();
	}

	private void detachTab() {
		if (tab == null) return;
		tab.setListener(null);
		tab.webView.setOnLongClickListener(null);
		if (tab.webView.getParent() instanceof ViewGroup) {
			((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
		}
		tab.webView.onPause();
		tab.detachActivity();
		tab = null;
	}

	private void closeTab(BrowserTab closing) {
		boolean wasCurrent = closing == tab;
		if (wasCurrent) detachTab();
		BrowserTab now = session.close(closing);
		if (session.count() == 0) now = session.newTab(requireContext());
		if (wasCurrent || tab == null) attachTab(now);
		else updateUi();
	}

	private void newTab() {
		BrowserTab fresh = session.newTab(requireContext());
		attachTab(fresh);
		startEditing();
	}

	private void showTabs() {
		List<BrowserSheets.Item> items = new ArrayList<>();
		for (BrowserTab t : session.tabs()) {
			String name = t.title == null || t.title.isEmpty()
				? (t.startPage ? "New tab" : BrowserSheets.displayUrl(t.url)) : t.title;
			items.add(new BrowserSheets.Item(t.id, name, t.startPage ? "" : t.url));
		}
		final BottomSheetDialog[] holder = new BottomSheetDialog[1];
		holder[0] = BrowserSheets.showList(requireContext(),
			session.count() + (session.count() == 1 ? " tab" : " tabs"), "+ New tab",
			() -> {
				holder[0].dismiss();
				newTab();
			}, items, "No tabs", new BrowserSheets.EntryListener() {
				@Override
				public void onOpen(BrowserSheets.Item entry) {
					for (BrowserTab t : session.tabs()) {
						if (t.id == entry.id) {
							attachTab(t);
							return;
						}
					}
				}

				@Override
				public void onAction(BrowserSheets.Item entry) {
					for (BrowserTab t : new ArrayList<>(session.tabs())) {
						if (t.id == entry.id) {
							closeTab(t);
							return;
						}
					}
				}
			});
	}

	// ── Address bar ──────────────────────────────────────────────────

	private void startEditing() {
		if (editing || tab == null) return;
		editing = true;
		binding.tvUrl.setVisibility(View.GONE);
		binding.etUrl.setVisibility(View.VISIBLE);
		binding.etUrl.setText(tab.startPage ? "" : tab.url);
		binding.etUrl.requestFocus();
		binding.etUrl.selectAll();
		binding.suggestScroll.setVisibility(View.VISIBLE);
		refreshSuggestions(binding.etUrl.getText().toString());
		InputMethodManager imm = (InputMethodManager) requireContext()
			.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.showSoftInput(binding.etUrl, InputMethodManager.SHOW_IMPLICIT);
		updateUi();
	}

	private void stopEditing() {
		if (!editing) return;
		editing = false;
		binding.etUrl.setVisibility(View.GONE);
		binding.tvUrl.setVisibility(View.VISIBLE);
		binding.suggestScroll.setVisibility(View.GONE);
		binding.suggestBox.removeAllViews();
		InputMethodManager imm = (InputMethodManager) requireContext()
			.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) imm.hideSoftInputFromWindow(binding.etUrl.getWindowToken(), 0);
		binding.etUrl.clearFocus();
		updateUi();
	}

	private void refreshSuggestions(String text) {
		binding.suggestBox.removeAllViews();
		LayoutInflater inflater = LayoutInflater.from(requireContext());
		String typed = text.trim();
		if (!typed.isEmpty()) {
			addSuggestion(inflater, "Search or open", typed, typed, "→");
		}
		List<BrowserDb.Entry> found = typed.isEmpty()
			? BrowserDb.get(requireContext()).bookmarks() : BrowserDb.get(requireContext()).search(typed, 6);
		int count = 0;
		for (BrowserDb.Entry e : found) {
			if (count++ >= 6) break;
			addSuggestion(inflater, e.title, e.url, e.url, "↖");
		}
	}

	private void addSuggestion(LayoutInflater inflater, String title, String subtitle, String target,
	                           String letter) {
		ItemBrowserEntry1Binding row = ItemBrowserEntry1Binding.inflate(inflater, binding.suggestBox, false);
		row.tvTitle.setText(title);
		row.tvUrl.setText(subtitle);
		row.tvLetter.setText(letter);
		row.btnAction.setVisibility(View.GONE);
		row.itemRoot.setOnClickListener(v -> submit(target));
		binding.suggestBox.addView(row.getRoot());
	}

	private void submit(String input) {
		String text = input == null ? "" : input.trim();
		if (text.isEmpty() || tab == null) return;
		String lower = text.toLowerCase(Locale.US);
		if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("content:")
			|| lower.startsWith("data:") || lower.startsWith("intent:")) {
			coreUtils.library.views.AppToast.show(requireContext(), "This kind of address is not allowed");
			return;
		}
		String target;
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			target = text;
		} else if (!text.contains(" ") && (text.contains(".") || lower.startsWith("localhost"))
			&& !text.endsWith(".")) {
			target = "https://" + text;
		} else {
			target = BrowserPrefs.searchUrl(requireContext(), text);
		}
		stopEditing();
		tab.load(target);
	}

	// ── UI state ─────────────────────────────────────────────────────

	private void updateUi() {
		if (binding == null || tab == null) return;
		Context ctx = requireContext();
		boolean start = tab.startPage;

		binding.startPage.setVisibility(start ? View.VISIBLE : View.GONE);
		binding.tvStartBlocked.setText(BrowserPrefs.blockedTotal(ctx) + " ads & trackers blocked so far");

		if (start) {
			binding.tvUrl.setText(R.string.v2_search_hint);
			binding.tvUrl.setTextColor(ContextCompat.getColor(ctx, R.color.style_color_text_hint));
		} else {
			binding.tvUrl.setText(BrowserSheets.displayUrl(tab.url));
			binding.tvUrl.setTextColor(ContextCompat.getColor(ctx, R.color.style_color_text_primary));
		}
		boolean secure = tab.url.startsWith("https://");
		binding.imgLock.setImageResource(R.drawable.ic_v2_lock);
		binding.imgLock.setColorFilter(start ? ContextCompat.getColor(ctx, R.color.style_color_text_secondary)
			: (secure ? Color.parseColor("#4ADE80") : Color.parseColor("#FF6B6B")));
		binding.btnReloadStop.setImageResource(tab.loading ? R.drawable.ic_v2_close : R.drawable.ic_v2_refresh);
		binding.btnReloadStop.setVisibility(start || editing ? View.GONE : View.VISIBLE);

		binding.progressPage.setVisibility(tab.loading && tab.progress < 100 ? View.VISIBLE : View.INVISIBLE);
		binding.progressPage.setProgress(tab.progress);

		int blocked = tab.blockedAds + tab.blockedTrackers + tab.blockedOther;
		if (blocked > 0 && !start) {
			binding.tvShieldCount.setVisibility(View.VISIBLE);
			binding.tvShieldCount.setText(blocked > 99 ? "99+" : String.valueOf(blocked));
		} else {
			binding.tvShieldCount.setVisibility(View.GONE);
		}
		binding.imgShield.setColorFilter(tab.protectionOff()
			? ContextCompat.getColor(ctx, R.color.style_color_text_hint)
			: ContextCompat.getColor(ctx, R.color.style_color_primary));

		binding.tvTabCount.setText(String.valueOf(session.count()));
		updateFab();
		backCallback.setEnabled(customView != null || editing || tab.canGoBack()
			|| session.count() > 1 || !start);
	}

	private void placeFab() {
		binding.getRoot().post(() -> {
			if (binding == null) return;
			WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(binding.getRoot());
			int nav = insets == null ? 0 : insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
			ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.fabDownload.getLayoutParams();
			lp.bottomMargin = nav + dp(96);
			binding.fabDownload.setLayoutParams(lp);
		});
	}

	// ── Download button (video / audio detector) ─────────────────────

	private void updateFab() {
		if (binding == null) return;
		AppConfigs cfg = AppConfigsRepo.getConfig();
		boolean show = cfg.isVideoGrabberEnabled && tab != null && !tab.startPage && !editing
			&& customView == null;
		binding.fabDownload.setVisibility(show ? View.VISIBLE : View.GONE);
		boolean active = show && tab.mediaActive();
		if (active == fabWasActive && pulse != null == active) return;
		fabWasActive = active;
		if (active) {
			binding.fabDownload.setBackgroundResource(R.drawable.bg_v2_fab_active);
			binding.fabDownload.setColorFilter(Color.parseColor("#2A1600"));
			startPulse();
		} else {
			binding.fabDownload.setBackgroundResource(R.drawable.bg_v2_fab_idle);
			binding.fabDownload.setColorFilter(ContextCompat.getColor(requireContext(), R.color.style_color_primary));
			stopPulse();
		}
	}

	private void startPulse() {
		stopPulse();
		pulse = ObjectAnimator.ofPropertyValuesHolder(binding.fabDownload,
			PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.14f),
			PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.14f));
		pulse.setDuration(700);
		pulse.setRepeatCount(ObjectAnimator.INFINITE);
		pulse.setRepeatMode(ObjectAnimator.REVERSE);
		pulse.start();
	}

	private void stopPulse() {
		if (pulse != null) {
			pulse.cancel();
			pulse = null;
		}
		if (binding != null) {
			binding.fabDownload.setScaleX(1f);
			binding.fabDownload.setScaleY(1f);
		}
	}

	private void onFabClick(boolean force) {
		if (tab == null) return;
		if (!tab.mediaActive() && !force) {
			coreUtils.library.views.AppToast.show(requireContext(), "Play a video or audio first. This button lights up when it is detected. "
					+ "Long-press to fetch this page anyway.", true);
			return;
		}
		List<BrowserTab.Candidate> list = tab.candidates();
		if (list.isEmpty()) {
			coreUtils.library.views.AppToast.show(requireContext(), "Nothing to download on this page");
			return;
		}
		// Known sites (YouTube, Instagram, ...): the page link is the best input for the downloader.
		if (list.size() == 1 || BrowserTab.isKnownVideoSite(tab.host())) {
			HomeActions.fetch(requireContext(), list.get(0).url);
			return;
		}
		String[] labels = new String[list.size()];
		for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).label;
		new coreUtils.library.views.AppDialog.Builder(requireContext())
			.setTitle("Download which one?")
			.setItems(labels, (d, which) -> HomeActions.fetch(requireContext(), list.get(which).url))
			.setNegativeButton("Cancel", null)
			.show();
	}

	// ── Menu ─────────────────────────────────────────────────────────

	private void showMenu() {
		if (tab == null) return;
		Context ctx = requireContext();
		BottomSheetDialog dialog = coreUtils.library.views.WideLayout.sheet(new BottomSheetDialog(ctx));
		LinearLayout body = BrowserSheets.sheetBody(ctx);
		android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
		scroll.addView(body);
		dialog.setContentView(scroll);

		LinearLayout bar = new LinearLayout(ctx);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER);
		boolean bookmarked = !tab.startPage && BrowserDb.get(ctx).isBookmarked(tab.url);
		addBarButton(bar, R.drawable.ic_v2_back, false, () -> {
			dialog.dismiss();
			if (tab.canGoBack()) tab.webView.goBack();
		});
		addBarButton(bar, R.drawable.ic_v2_forward, false, () -> {
			dialog.dismiss();
			if (tab.webView.canGoForward()) tab.webView.goForward();
		});
		addBarButton(bar, R.drawable.ic_v2_refresh, false, () -> {
			dialog.dismiss();
			if (!tab.startPage) tab.webView.reload();
		});
		addBarButton(bar, R.drawable.ic_v2_star, bookmarked, () -> {
			dialog.dismiss();
			if (tab.startPage) return;
			if (bookmarked) {
				BrowserDb.get(ctx).removeBookmark(tab.url);
				coreUtils.library.views.AppToast.show(ctx, "Bookmark removed");
			} else {
				BrowserDb.get(ctx).addBookmark(tab.url, tab.title);
				coreUtils.library.views.AppToast.show(ctx, "Bookmarked");
			}
		});
		addBarButton(bar, R.drawable.ic_v2_share, false, () -> {
			dialog.dismiss();
			if (tab.startPage) return;
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("text/plain");
			send.putExtra(Intent.EXTRA_TEXT, tab.url);
			startActivity(Intent.createChooser(send, "Share link"));
		});
		body.addView(bar);

		BrowserSheets.row(ctx, body, R.drawable.ic_v2_add, "New tab", null, () -> {
			dialog.dismiss();
			newTab();
		});
		BrowserSheets.row(ctx, body, R.drawable.ic_v2_bookmark, "Bookmarks", null, () -> {
			dialog.dismiss();
			showBookmarks();
		});
		BrowserSheets.row(ctx, body, R.drawable.ic_v2_history, "History", null, () -> {
			dialog.dismiss();
			showHistory();
		});
		BrowserSheets.row(ctx, body, R.drawable.ic_v2_shield, "Privacy & protection",
			"Ads, trackers, pop-ups, redirects, downloads", () -> {
				dialog.dismiss();
				BrowserSheets.showProtection(ctx, tab, this::reloadCurrent);
			});
		BrowserSheets.row(ctx, body, R.drawable.ic_v2_link, "Copy link", null, () -> {
			dialog.dismiss();
			if (tab.startPage) return;
			ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
			if (cm != null) {
				cm.setPrimaryClip(ClipData.newPlainText("link", tab.url));
				coreUtils.library.views.AppToast.show(ctx, "Copied");
			}
		});
		BrowserSheets.row(ctx, body, R.drawable.ic_v2_search, "Search engine",
			BrowserPrefs.ENGINE_NAMES[BrowserPrefs.engine(ctx)], () -> {
				dialog.dismiss();
				new coreUtils.library.views.AppDialog.Builder(ctx)
					.setTitle("Search engine")
					.setSingleChoiceItems(BrowserPrefs.ENGINE_NAMES, BrowserPrefs.engine(ctx), (d, which) -> {
						BrowserPrefs.setEngine(ctx, which);
						d.dismiss();
					})
					.show();
			});
		BrowserSheets.row(ctx, body, R.drawable.ic_v2_trash, "Clear browsing data",
			"History, cookies, cache, site data", () -> {
				dialog.dismiss();
				new coreUtils.library.views.AppDialog.Builder(ctx)
					.setTitle("Clear browsing data?")
					.setMessage("This deletes history, cookies, cache and saved site data. "
						+ "Bookmarks are kept. You will be signed out of websites.")
					.setPositiveButton("Clear", (d, w) -> {
						session.clearAllData(ctx);
						coreUtils.library.views.AppToast.show(ctx, "Browsing data cleared");
					})
					.setNegativeButton("Cancel", null)
					.show();
			});
		dialog.show();
	}

	private void addBarButton(LinearLayout bar, int icon, boolean accent, Runnable action) {
		android.widget.ImageView button = BrowserSheets.iconButton(requireContext(), icon, accent,
			v -> action.run());
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
		lp.setMargins(dp(4), dp(4), dp(4), dp(10));
		bar.addView(button, lp);
	}

	private void showBookmarks() {
		Context ctx = requireContext();
		List<BrowserSheets.Item> items = new ArrayList<>();
		for (BrowserDb.Entry e : BrowserDb.get(ctx).bookmarks()) {
			items.add(new BrowserSheets.Item(e.id, e.title, e.url));
		}
		BrowserSheets.showList(ctx, "Bookmarks", null, null, items, "No bookmarks yet.\n"
			+ "Open a page, tap ⋮ and then the star.", new BrowserSheets.EntryListener() {
			@Override
			public void onOpen(BrowserSheets.Item entry) {
				openUrl(entry.url);
			}

			@Override
			public void onAction(BrowserSheets.Item entry) {
				BrowserDb.get(ctx).removeBookmark(entry.url);
			}
		});
	}

	private void showHistory() {
		Context ctx = requireContext();
		List<BrowserSheets.Item> items = new ArrayList<>();
		for (BrowserDb.Entry e : BrowserDb.get(ctx).history(300)) {
			items.add(new BrowserSheets.Item(e.id, e.title, e.url));
		}
		final BottomSheetDialog[] holder = new BottomSheetDialog[1];
		holder[0] = BrowserSheets.showList(ctx, "History", "Clear all", () -> {
			BrowserDb.get(ctx).clearHistory();
			holder[0].dismiss();
			coreUtils.library.views.AppToast.show(ctx, "History cleared");
		}, items, AppConfigsRepo.getConfig().isPrivateBrowsingEnabled
			? "Private mode is on: history is not saved." : "No history yet.",
			new BrowserSheets.EntryListener() {
				@Override
				public void onOpen(BrowserSheets.Item entry) {
					openUrl(entry.url);
				}

				@Override
				public void onAction(BrowserSheets.Item entry) {
					BrowserDb.get(ctx).deleteHistory(entry.id);
				}
			});
	}

	private void openUrl(String url) {
		if (tab == null) return;
		tab.load(url);
	}

	private void reloadCurrent() {
		if (tab != null && !tab.startPage) tab.webView.reload();
	}

	// ── Start page ───────────────────────────────────────────────────

	private void buildStartGrid() {
		Context ctx = requireContext();
		GridLayout grid = binding.startGrid;
		grid.removeAllViews();
		for (QuickSites.Site site : QuickSites.ALL) {
			LinearLayout cell = new LinearLayout(ctx);
			cell.setOrientation(LinearLayout.VERTICAL);
			cell.setGravity(Gravity.CENTER_HORIZONTAL);
			cell.setPadding(0, dp(8), 0, dp(8));

			TextView icon = new TextView(ctx);
			icon.setText(site.name.substring(0, 1));
			icon.setGravity(Gravity.CENTER);
			icon.setTextColor(ContextCompat.getColor(ctx, R.color.style_color_primary));
			icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
			icon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
			icon.setBackgroundResource(R.drawable.bg_v2_icon_btn);
			cell.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

			TextView label = new TextView(ctx);
			label.setText(site.name);
			label.setMaxLines(1);
			label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
			label.setTextColor(ContextCompat.getColor(ctx, R.color.style_color_text_secondary));
			label.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = dp(4);
			cell.addView(label, lp);
			cell.setOnClickListener(v -> openUrl(site.url));

			GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
				GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
			glp.width = 0;
			grid.addView(cell, glp);
		}
	}

	// ── Long press on links / images ─────────────────────────────────

	private boolean onLongPress() {
		if (tab == null) return false;
		WebView.HitTestResult hit = tab.webView.getHitTestResult();
		final String extra = hit.getExtra();
		int type = hit.getType();
		if (extra == null || !extra.startsWith("http")) return false;
		boolean isImage = type == WebView.HitTestResult.IMAGE_TYPE;
		boolean isLink = type == WebView.HitTestResult.SRC_ANCHOR_TYPE
			|| type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
		if (!isImage && !isLink) return false;

		List<String> labels = new ArrayList<>();
		List<Runnable> actions = new ArrayList<>();
		Context ctx = requireContext();
		if (isLink) {
			labels.add("Open in new tab");
			actions.add(() -> {
				BrowserTab fresh = session.newTab(ctx);
				attachTab(fresh);
				fresh.load(extra);
			});
			labels.add("Download with VidFlow");
			actions.add(() -> HomeActions.fetch(ctx, extra));
		}
		if (isImage) {
			labels.add("Save image");
			actions.add(() -> enqueueDownload(extra, tab.webView.getSettings().getUserAgentString(),
				null, "image/*"));
		}
		labels.add("Copy link");
		actions.add(() -> {
			ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
			if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("link", extra));
		});
		labels.add("Share link");
		actions.add(() -> {
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType("text/plain");
			send.putExtra(Intent.EXTRA_TEXT, extra);
			startActivity(Intent.createChooser(send, "Share link"));
		});
		new coreUtils.library.views.AppDialog.Builder(ctx)
			.setTitle(BrowserSheets.displayUrl(extra))
			.setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
			.show();
		return true;
	}

	// ── Downloads from pages (always asks first) ─────────────────────

	private void enqueueDownload(String url, String userAgent, String contentDisposition, String mime) {
		try {
			Context ctx = requireContext();
			String name = URLUtil.guessFileName(url, contentDisposition, mime)
				.replaceAll("[\\\\/:*?\"<>|]", "_");
			DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
			if (mime != null && !mime.isEmpty()) request.setMimeType(mime);
			String cookie = CookieManager.getInstance().getCookie(url);
			if (cookie != null) request.addRequestHeader("Cookie", cookie);
			if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NextGen/" + name);
			DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
			if (dm != null) dm.enqueue(request);
			coreUtils.library.views.AppToast.show(ctx, "Downloading " + name);
		} catch (Throwable e) {
			coreUtils.library.views.AppToast.show(requireContext(), "Could not start the download");
		}
	}

	private void askDownload(String url, String userAgent, String contentDisposition, String mime,
	                         long length) {
		if (!url.startsWith("http")) {
			coreUtils.library.views.AppToast.show(requireContext(), "This download type is not supported here");
			return;
		}
		String name = URLUtil.guessFileName(url, contentDisposition, mime);
		String size = length > 0 ? android.text.format.Formatter.formatShortFileSize(requireContext(), length)
			: "unknown size";
		boolean media = mime != null && (mime.startsWith("video/") || mime.startsWith("audio/")
			|| mime.contains("mpegurl"));
		coreUtils.library.views.AppDialog.Builder builder = new coreUtils.library.views.AppDialog.Builder(requireContext())
			.setTitle("Download file?")
			.setMessage(name + "\n" + size + "\nFrom: " + Uri.parse(url).getHost())
			.setPositiveButton("Download", (d, w) -> enqueueDownload(url, userAgent, contentDisposition, mime))
			.setNegativeButton("Cancel", null);
		if (media) {
			builder.setNeutralButton("VidFlow",
				(d, w) -> HomeActions.fetch(requireContext(), url));
		}
		builder.show();
	}

	// ── BrowserTab.Listener ──────────────────────────────────────────

	@Override
	public void onTabChanged(BrowserTab changed) {
		if (changed == tab) updateUi();
	}

	@Override
	public void onProgress(BrowserTab changed, int progress) {
		if (changed != tab || binding == null) return;
		binding.progressPage.setVisibility(progress < 100 ? View.VISIBLE : View.INVISIBLE);
		binding.progressPage.setProgress(progress);
		binding.btnReloadStop.setImageResource(progress < 100 ? R.drawable.ic_v2_close : R.drawable.ic_v2_refresh);
	}

	@Override
	public void onMediaChanged(BrowserTab changed) {
		if (changed == tab) updateFab();
	}

	@Override
	public void onNotice(BrowserTab source, String text, String actionLabel, Runnable action) {
		if (source != tab || binding == null) return;
		binding.tvBanner.setText(text);
		if (actionLabel == null) {
			binding.tvBannerAction.setVisibility(View.GONE);
		} else {
			binding.tvBannerAction.setVisibility(View.VISIBLE);
			binding.tvBannerAction.setText(actionLabel);
			binding.tvBannerAction.setOnClickListener(v -> {
				binding.bannerBox.setVisibility(View.GONE);
				if (action != null) action.run();
			});
		}
		binding.bannerBox.setVisibility(View.VISIBLE);
		main.removeCallbacks(hideBanner);
		main.postDelayed(hideBanner, 6000);
	}

	private final Runnable hideBanner = () -> {
		if (binding != null) binding.bannerBox.setVisibility(View.GONE);
	};

	@Override
	public boolean onCreateTab(BrowserTab opener, Message resultMsg) {
		if (resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) return false;
		BrowserTab fresh = session.newTab(requireContext());
		fresh.startPage = false;
		WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
		transport.setWebView(fresh.webView);
		resultMsg.sendToTarget();
		attachTab(fresh);
		return true;
	}

	@Override
	public void onCloseTab(BrowserTab closing) {
		closeTab(closing);
	}

	@Override
	public boolean onFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
		if (fileCallback != null) fileCallback.onReceiveValue(null);
		fileCallback = callback;
		try {
			fileLauncher.launch(params.createIntent());
			return true;
		} catch (Throwable e) {
			fileCallback = null;
			return false;
		}
	}

	@Override
	public void onDownload(BrowserTab source, String url, String userAgent, String contentDisposition,
	                       String mime, long length, boolean userGesture) {
		if (source != tab) return;
		boolean prevent = BrowserPrefs.get(requireContext(), BrowserPrefs.DOWNLOAD_PREVENT)
			&& !source.protectionOff();
		if (prevent && !userGesture) {
			onNotice(source, "Blocked an automatic download", "Allow",
				() -> askDownload(url, userAgent, contentDisposition, mime, length));
			return;
		}
		askDownload(url, userAgent, contentDisposition, mime, length);
	}

	@Override
	public void onFullscreenShow(View view, WebChromeClient.CustomViewCallback callback) {
		if (customView != null) {
			callback.onCustomViewHidden();
			return;
		}
		customView = view;
		customCallback = callback;
		ViewGroup content = requireActivity().findViewById(android.R.id.content);
		fullscreenHolder = new FrameLayout(requireContext());
		fullscreenHolder.setBackgroundColor(Color.BLACK);
		fullscreenHolder.setClickable(true);
		fullscreenHolder.addView(view, new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		content.addView(fullscreenHolder, new ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		savedOrientation = requireActivity().getRequestedOrientation();
		savedUiFlags = requireActivity().getWindow().getDecorView().getSystemUiVisibility();
		requireActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
		requireActivity().getWindow().getDecorView().setSystemUiVisibility(
			View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		backCallback.setEnabled(true);
		updateFab();
	}

	@Override
	public void onFullscreenHide() {
		hideFullscreen();
	}

	private void hideFullscreen() {
		if (customView == null) return;
		android.app.Activity activity = getActivity();
		if (fullscreenHolder != null && fullscreenHolder.getParent() instanceof ViewGroup) {
			((ViewGroup) fullscreenHolder.getParent()).removeView(fullscreenHolder);
		}
		if (fullscreenHolder != null) fullscreenHolder.removeAllViews();
		fullscreenHolder = null;
		customView = null;
		if (customCallback != null) customCallback.onCustomViewHidden();
		customCallback = null;
		if (activity != null) {
			activity.setRequestedOrientation(savedOrientation);
			activity.getWindow().getDecorView().setSystemUiVisibility(savedUiFlags);
			activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		if (binding != null) updateUi();
	}

	@Override
	public void onJsDialog(BrowserTab source, int type, String message, String defaultValue,
	                       JsResult result, JsPromptResult promptResult) {
		if (getContext() == null) {
			result.cancel();
			return;
		}
		coreUtils.library.views.AppDialog.Builder builder = new coreUtils.library.views.AppDialog.Builder(requireContext())
			.setTitle(source.host())
			.setOnCancelListener(d -> result.cancel());
		if (type == 2 && promptResult != null) {
			EditText input = new EditText(requireContext());
			input.setText(defaultValue == null ? "" : defaultValue);
			builder.setMessage(message).setView(input)
				.setPositiveButton("OK", (d, w) -> promptResult.confirm(input.getText().toString()))
				.setNegativeButton("Cancel", (d, w) -> promptResult.cancel());
		} else if (type == 1) {
			builder.setMessage(message)
				.setPositiveButton("OK", (d, w) -> result.confirm())
				.setNegativeButton("Cancel", (d, w) -> result.cancel());
		} else {
			builder.setMessage(message).setPositiveButton("OK", (d, w) -> result.confirm());
		}
		builder.show();
	}

	@Override
	public void onPlayInApp(BrowserTab source, String src, String title, String tracksJson) {
		if (source != tab || getContext() == null) return;
		String stream = source.bestStreamFor(src);
		String pageUrl = source.url;
		String ua = source.webView.getSettings().getUserAgentString();
		PlayRequest request;
		if (!stream.isEmpty()) {
			String cookie = null;
			try {
				cookie = CookieManager.getInstance().getCookie(stream);
			} catch (Throwable ignored) {
			}
			request = PlayRequest.stream(stream, title, pageUrl, pageUrl, ua, cookie, false);
		} else {
			// blob: / MediaSource videos have no raw address: the page link goes to the extractor.
			request = PlayRequest.link(pageUrl);
			request.title = title == null ? "" : title;
		}
		try {
			org.json.JSONArray tracks = new org.json.JSONArray(tracksJson);
			for (int i = 0; i < tracks.length() && i < 30; i++) {
				org.json.JSONObject t = tracks.optJSONObject(i);
				if (t == null) continue;
				String link = t.optString("src", "");
				if (link.startsWith("http")) {
					request.subs.add(new PlayRequest.Sub(link, t.optString("label", ""), t.optString("lang", "")));
				}
			}
		} catch (Throwable ignored) {
		}
		request.start(requireContext());
	}

	@Override
	public void onTabCrashed(BrowserTab crashed) {
		coreUtils.library.views.AppToast.show(requireContext(), "The page crashed and was closed");
		closeTab(crashed);
	}

	private int dp(int value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
			getResources().getDisplayMetrics());
	}
}
