package userInterface.fragmentsUIs.browser;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nextgen.R;
import com.nextgen.databinding.ItemBrowserEntry1Binding;
import com.nextgen.databinding.ItemSettingsRow1Binding;
import com.nextgen.databinding.SheetBrowserList1Binding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import sysModules.browserEngine.BrowserPrefs;
import sysModules.browserEngine.BrowserTab;

/** Reusable bottom sheets for the browser (lists, menu rows, protection panel). */
final class BrowserSheets {

	private BrowserSheets() {}

	interface EntryListener {
		void onOpen(Item entry);

		/** The small button on the right (delete / close). */
		void onAction(Item entry);
	}

	/** One row in a list sheet. */
	static final class Item {
		final long id;
		final String title;
		final String url;

		Item(long id, String title, String url) {
			this.id = id;
			this.title = title;
			this.url = url;
		}
	}

	// ── List sheet (tabs / history / bookmarks) ──────────────────────

	static BottomSheetDialog showList(Context ctx, String title, String actionLabel,
	                                  Runnable onHeaderAction, List<Item> items,
	                                  String emptyText, EntryListener listener) {
		BottomSheetDialog dialog = coreUtils.library.views.WideLayout.sheet(new BottomSheetDialog(ctx));
		SheetBrowserList1Binding b = SheetBrowserList1Binding.inflate(LayoutInflater.from(ctx));
		dialog.setContentView(b.getRoot());
		b.tvSheetTitle.setText(title);
		if (actionLabel == null) {
			b.tvSheetAction.setVisibility(View.GONE);
		} else {
			b.tvSheetAction.setText(actionLabel);
			b.tvSheetAction.setOnClickListener(v -> {
				if (onHeaderAction != null) onHeaderAction.run();
			});
		}
		final List<Item> data = new ArrayList<>(items);
		b.tvSheetEmpty.setText(emptyText);
		Runnable refreshEmpty = () -> {
			b.tvSheetEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
			b.rvEntries.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
		};
		refreshEmpty.run();

		final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
			new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
				@NonNull
				@Override
				public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
					ItemBrowserEntry1Binding e = ItemBrowserEntry1Binding
						.inflate(LayoutInflater.from(parent.getContext()), parent, false);
					return new RecyclerView.ViewHolder(e.getRoot()) {};
				}

				@Override
				public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
					ItemBrowserEntry1Binding e = ItemBrowserEntry1Binding.bind(holder.itemView);
					Item entry = data.get(position);
					String label = entry.title == null || entry.title.isEmpty() ? entry.url : entry.title;
					e.tvTitle.setText(label);
					e.tvUrl.setText(displayUrl(entry.url));
					e.tvLetter.setText(label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase());
					e.itemRoot.setOnClickListener(v -> {
						dialog.dismiss();
						listener.onOpen(entry);
					});
					e.btnAction.setOnClickListener(v -> {
						int index = data.indexOf(entry);
						if (index < 0) return;
						data.remove(index);
						notifyItemRemoved(index);
						refreshEmpty.run();
						listener.onAction(entry);
					});
				}

				@Override
				public int getItemCount() {
					return data.size();
				}
			};
		b.rvEntries.setLayoutManager(new LinearLayoutManager(ctx));
		b.rvEntries.setAdapter(adapter);
		int max = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.55f);
		b.rvEntries.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
		b.rvEntries.post(() -> {
			if (b.rvEntries.getHeight() > max) {
				b.rvEntries.getLayoutParams().height = max;
				b.rvEntries.requestLayout();
			}
		});
		dialog.show();
		return dialog;
	}

	static String displayUrl(String url) {
		if (url == null) return "";
		String shown = url;
		if (shown.startsWith("https://")) shown = shown.substring(8);
		else if (shown.startsWith("http://")) shown = shown.substring(7);
		if (shown.startsWith("www.")) shown = shown.substring(4);
		if (shown.endsWith("/")) shown = shown.substring(0, shown.length() - 1);
		return shown;
	}

	// ── Rows (same look as the Settings screen) ──────────────────────

	static void row(Context ctx, LinearLayout parent, int icon, String title, String value,
	                Runnable action) {
		ItemSettingsRow1Binding r = ItemSettingsRow1Binding.inflate(LayoutInflater.from(ctx), parent, false);
		r.imgIcon.setImageResource(icon);
		r.tvTitle.setText(title);
		if (value != null) {
			r.tvValue.setVisibility(View.VISIBLE);
			r.tvValue.setText(value);
		}
		r.imgTrailing.setVisibility(View.GONE);
		r.rowRoot.setOnClickListener(v -> action.run());
		parent.addView(r.getRoot());
	}

	static void toggle(Context ctx, LinearLayout parent, int icon, String title, String hint,
	                   Supplier<Boolean> getter, Consumer<Boolean> setter, Runnable changed) {
		ItemSettingsRow1Binding r = ItemSettingsRow1Binding.inflate(LayoutInflater.from(ctx), parent, false);
		r.imgIcon.setImageResource(icon);
		r.tvTitle.setText(title);
		r.imgTrailing.setImageResource(R.drawable.ic_v2_check);
		Runnable render = () -> {
			boolean on = getter.get();
			r.tvValue.setVisibility(View.VISIBLE);
			r.tvValue.setText((on ? "On" : "Off") + (hint == null ? "" : "  •  " + hint));
			r.imgTrailing.setAlpha(on ? 1f : 0.2f);
		};
		render.run();
		r.rowRoot.setOnClickListener(v -> {
			setter.accept(!getter.get());
			render.run();
			if (changed != null) changed.run();
		});
		parent.addView(r.getRoot());
	}

	static LinearLayout sheetBody(Context ctx) {
		LinearLayout body = new LinearLayout(ctx);
		body.setOrientation(LinearLayout.VERTICAL);
		body.setBackgroundResource(R.drawable.bg_v2_sheet);
		int pad = (int) (12 * ctx.getResources().getDisplayMetrics().density);
		body.setPadding(pad, pad, pad, pad);
		return body;
	}

	static TextView sectionTitle(Context ctx, LinearLayout parent, String text) {
		TextView tv = new TextView(ctx);
		tv.setText(text);
		tv.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.style_color_primary));
		tv.setTextSize(13);
		tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		float d = ctx.getResources().getDisplayMetrics().density;
		tv.setPadding((int) (8 * d), (int) (10 * d), 0, (int) (4 * d));
		parent.addView(tv);
		return tv;
	}

	// ── Protection panel ─────────────────────────────────────────────

	static void showProtection(Context ctx, BrowserTab tab, Runnable onChanged) {
		BottomSheetDialog dialog = coreUtils.library.views.WideLayout.sheet(new BottomSheetDialog(ctx));
		LinearLayout body = sheetBody(ctx);
		ScrollView scroll = new ScrollView(ctx);
		scroll.addView(body);
		dialog.setContentView(scroll);

		final boolean[] changed = {false};
		Runnable mark = () -> {
			changed[0] = true;
			for (BrowserTab t : sysModules.browserEngine.BrowserSession.get().tabs()) t.applySettings();
		};
		dialog.setOnDismissListener(d -> {
			if (changed[0] && onChanged != null) onChanged.run();
		});

		String host = tab.host();
		TextView head = new TextView(ctx);
		head.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.style_color_text_primary));
		head.setTextSize(18);
		head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		head.setText(tab.startPage || host.isEmpty() ? "Privacy & protection" : host);
		float d = ctx.getResources().getDisplayMetrics().density;
		head.setPadding((int) (8 * d), 0, 0, (int) (2 * d));
		body.addView(head);

		TextView stats = new TextView(ctx);
		stats.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.style_color_text_secondary));
		stats.setTextSize(13);
		stats.setText("Blocked on this page:  " + tab.blockedAds + " ads  •  " + tab.blockedTrackers
			+ " trackers  •  " + tab.blockedOther + " other\nBlocked in total:  "
			+ BrowserPrefs.blockedTotal(ctx));
		stats.setPadding((int) (8 * d), 0, 0, (int) (8 * d));
		body.addView(stats);

		if (!tab.startPage && !host.isEmpty()) {
			toggle(ctx, body, R.drawable.ic_v2_shield, "Protection on this site", host,
				() -> !BrowserPrefs.isSiteAllowed(ctx, host),
				v -> BrowserPrefs.setSiteAllowed(ctx, host, !v), mark);
		}

		sectionTitle(ctx, body, "Blocking");
		toggle(ctx, body, R.drawable.ic_v2_block, "Ad blocker", null,
			() -> AppConfigsRepo.getConfig().isAdBlockerEnabled,
			v -> save(c -> c.isAdBlockerEnabled = v), mark);
		toggle(ctx, body, R.drawable.ic_v2_privacy, "Tracker blocker", null,
			() -> BrowserPrefs.get(ctx, BrowserPrefs.TRACKER_BLOCK),
			v -> BrowserPrefs.set(ctx, BrowserPrefs.TRACKER_BLOCK, v), mark);
		toggle(ctx, body, R.drawable.ic_v2_touch, "Hidden touch blocker", "invisible tap-catching layers",
			() -> BrowserPrefs.get(ctx, BrowserPrefs.TOUCH_BLOCK),
			v -> BrowserPrefs.set(ctx, BrowserPrefs.TOUCH_BLOCK, v), mark);
		toggle(ctx, body, R.drawable.ic_v2_block, "Pop-up blocker", null,
			() -> AppConfigsRepo.getConfig().isPopupBlockerEnabled,
			v -> save(c -> c.isPopupBlockerEnabled = v), mark);
		toggle(ctx, body, R.drawable.ic_v2_link, "Unwanted redirect blocker", null,
			() -> BrowserPrefs.get(ctx, BrowserPrefs.REDIRECT_BLOCK),
			v -> BrowserPrefs.set(ctx, BrowserPrefs.REDIRECT_BLOCK, v), mark);
		toggle(ctx, body, R.drawable.ic_v2_download, "Download prevention", "ask before any file saves",
			() -> BrowserPrefs.get(ctx, BrowserPrefs.DOWNLOAD_PREVENT),
			v -> BrowserPrefs.set(ctx, BrowserPrefs.DOWNLOAD_PREVENT, v), mark);

		sectionTitle(ctx, body, "Privacy");
		toggle(ctx, body, R.drawable.ic_v2_lock, "Upgrade to HTTPS", null,
			() -> BrowserPrefs.get(ctx, BrowserPrefs.HTTPS_UPGRADE),
			v -> BrowserPrefs.set(ctx, BrowserPrefs.HTTPS_UPGRADE, v), mark);
		toggle(ctx, body, R.drawable.ic_v2_privacy, "Block third-party cookies", null,
			() -> BrowserPrefs.get(ctx, BrowserPrefs.THIRD_PARTY_COOKIES_BLOCK),
			v -> BrowserPrefs.set(ctx, BrowserPrefs.THIRD_PARTY_COOKIES_BLOCK, v), mark);
		toggle(ctx, body, R.drawable.ic_v2_eye_off, "Private mode", "no history, no cache",
			() -> AppConfigsRepo.getConfig().isPrivateBrowsingEnabled,
			v -> save(c -> c.isPrivateBrowsingEnabled = v), mark);

		sectionTitle(ctx, body, "Page");
		toggle(ctx, body, R.drawable.ic_v2_settings, "JavaScript", null,
			() -> AppConfigsRepo.getConfig().isJavaScriptEnabled,
			v -> save(c -> c.isJavaScriptEnabled = v), mark);
		toggle(ctx, body, R.drawable.ic_v2_image, "Load images", null,
			() -> AppConfigsRepo.getConfig().isImageLoadingEnabled,
			v -> save(c -> c.isImageLoadingEnabled = v), mark);
		toggle(ctx, body, R.drawable.ic_v2_desktop, "Desktop site", null,
			() -> AppConfigsRepo.getConfig().isDesktopBrowsingEnabled,
			v -> save(c -> c.isDesktopBrowsingEnabled = v), mark);
		toggle(ctx, body, R.drawable.ic_v2_video, "Download button (video / audio detector)", null,
			() -> AppConfigsRepo.getConfig().isVideoGrabberEnabled,
			v -> save(c -> c.isVideoGrabberEnabled = v), mark);
		dialog.show();
	}

	private static void save(Consumer<AppConfigs> edit) {
		AppConfigs config = AppConfigsRepo.getConfig();
		edit.accept(config);
		config.save();
	}

	static ImageView iconButton(Context ctx, int icon, boolean accent, View.OnClickListener click) {
		ImageView v = new ImageView(ctx);
		float d = ctx.getResources().getDisplayMetrics().density;
		v.setImageResource(icon);
		v.setColorFilter(androidx.core.content.ContextCompat.getColor(ctx, accent
			? R.color.style_color_primary : R.color.style_color_text_primary));
		v.setBackgroundResource(R.drawable.bg_v2_icon_btn);
		int pad = (int) (12 * d);
		v.setPadding(pad, pad, pad, pad);
		v.setOnClickListener(click);
		return v;
	}
}
