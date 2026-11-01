package userInterface.fragmentsUIs.downloads;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nextgen.R;
import com.nextgen.databinding.DialogDownloadOptions1Binding;
import com.nextgen.databinding.FragDownloads1Binding;
import com.nextgen.databinding.ItemDownloadActive1Binding;
import com.nextgen.databinding.ItemDownloadDone1Binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coreUtils.base.BaseFragment;
import userInterface.fragmentsUIs.homepage.HomeActions;
import sysModules.interCaches.AppRawFiles;
import sysModules.downloadSys.DownloadHistory;
import sysModules.downloadSys.DownloadRegistry;
import sysModules.downloadSys.DownloadService;
import userInterface.intentHandler.IntentHandlerActivity;
import userInterface.mainScreen.MainActivity;
import userInterface.mainScreen.NavigationTabs;

/**
 * Downloads tab. One screen with an Active | Downloaded toggle.
 * Active = downloads running now (from {@link DownloadRegistry}).
 * Downloaded = finished files (from {@link DownloadHistory}).
 */
public class DownloadsFragment extends BaseFragment<FragDownloads1Binding>
	implements DownloadRegistry.Listener {

	public static final String ARG_MODE = "mode"; // "active" or "done"

	public static DownloadsFragment create(String mode) {
		DownloadsFragment fragment = new DownloadsFragment();
		Bundle args = new Bundle();
		args.putString(ARG_MODE, mode);
		fragment.setArguments(args);
		return fragment;
	}

	private static final String[] SORT_NAMES = {"Newest first", "Oldest first", "Name (A–Z)", "Largest first"};
	private int sortMode = 0;
	private boolean showActive = false;
	private final ExecutorService io = Executors.newFixedThreadPool(2);
	private final Handler main = new Handler(Looper.getMainLooper());
	private final Map<String, String> durationCache = new HashMap<>();
	private final List<DownloadRegistry.Job> activeItems = new ArrayList<>();
	private final List<DownloadHistory.Item> doneItems = new ArrayList<>();
	private final RecyclerView.Adapter<RecyclerView.ViewHolder> activeAdapter = new ActiveAdapter();
	private final RecyclerView.Adapter<RecyclerView.ViewHolder> doneAdapter = new DoneAdapter();

	@Override
	protected FragDownloads1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
		return FragDownloads1Binding.inflate(inflater, container, false);
	}

	@Override
	protected void onLoadedLayout() {
		Bundle args = getArguments();
		showActive = args != null && "active".equals(args.getString(ARG_MODE));
		sortMode = requireContext().getSharedPreferences("downloads_ui", Context.MODE_PRIVATE)
			.getInt("sort", 0);
		binding.tvSort.setOnClickListener(v -> new coreUtils.library.views.AppDialog.Builder(requireContext())
			.setTitle("Sort by")
			.setSingleChoiceItems(SORT_NAMES, sortMode, (d, which) -> {
				sortMode = which;
				requireContext().getSharedPreferences("downloads_ui", Context.MODE_PRIVATE)
					.edit().putInt("sort", which).apply();
				d.dismiss();
				reload();
			})
			.show());
		binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
		binding.segActive.setOnClickListener(v -> setSegment(true));
		binding.segDone.setOnClickListener(v -> setSegment(false));
		binding.hdrLeft.setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openTab(NavigationTabs.HOME_TAB);
			}
		});
		binding.hdrRight.setOnClickListener(v -> showAddDialog());
		setSegment(showActive);
	}

	@Override
	public void onResume() {
		super.onResume();
		DownloadRegistry.addListener(this);
		if (binding != null) reload();
	}

	@Override
	public void onPause() {
		super.onPause();
		DownloadRegistry.removeListener(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		io.shutdownNow();
	}

	@Override
	public void onChanged() {
		if (binding == null || getContext() == null) return;
		reload();
	}

	private void setSegment(boolean active) {
		showActive = active;
		if (binding == null) return;
		binding.segActive.setSelected(active);
		binding.segDone.setSelected(!active);
		binding.hdrTitle.setText(active ? binding.segActive.getText() : binding.segDone.getText());
		binding.list.setAdapter(active ? activeAdapter : doneAdapter);
		reload();
		binding.list.scheduleLayoutAnimation();
	}

	private void reload() {
		if (binding == null || getContext() == null) return;
		activeItems.clear();
		activeItems.addAll(DownloadRegistry.snapshot());
		doneItems.clear();
		doneItems.addAll(DownloadHistory.list(requireContext(), "all"));
		binding.tvSort.setVisibility(showActive ? View.GONE : View.VISIBLE);
		binding.tvSort.setText(SORT_NAMES[sortMode]);
		sortDone();
		if (showActive) {
			activeAdapter.notifyDataSetChanged();
			binding.tvEmpty.setText("No active downloads.\nShare a video link to this app to start one.");
			showEmpty(activeItems.isEmpty(), true);
		} else {
			doneAdapter.notifyDataSetChanged();
			binding.tvEmpty.setText("No downloaded files yet.");
			showEmpty(doneItems.isEmpty(), false);
		}
	}

	private void showEmpty(boolean empty, boolean forActive) {
		binding.emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
		if (!empty) {
			binding.lottieEmpty.cancelAnimation();
			return;
		}
		AppRawFiles raw = AppRawFiles.getInstance();
		com.airbnb.lottie.LottieComposition comp = forActive
			? raw.getOpenActiveTasksComposition() : raw.getEmptyGhostComposition();
		if (comp != null) {
			binding.lottieEmpty.setComposition(comp);
		} else {
			binding.lottieEmpty.setAnimation(forActive
				? R.raw.animation_active_tasks : R.raw.animation_empty_ghost);
		}
		binding.lottieEmpty.playAnimation();
	}

	private void sortDone() {
		switch (sortMode) {
			case 1:
				Collections.sort(doneItems, (a, b) -> Long.compare(a.time, b.time));
				break;
			case 2:
				Collections.sort(doneItems, (a, b) -> a.title.compareToIgnoreCase(b.title));
				break;
			case 3:
				Collections.sort(doneItems, (a, b) -> Long.compare(b.size, a.size));
				break;
			default:
				Collections.sort(doneItems, (a, b) -> Long.compare(b.time, a.time));
		}
	}

	// ── Add by link ──────────────────────────────────────────────────

	private void showAddDialog() {
		Context ctx = requireContext();
		EditText input = new EditText(ctx);
		input.setHint("Paste video link");
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
		try {
			ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
			if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
				CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(ctx);
				if (text != null && text.toString().startsWith("http")) input.setText(text);
			}
		} catch (Throwable ignored) {
		}
		new coreUtils.library.views.AppDialog.Builder(ctx)
			.setTitle("Add download")
			.setView(input)
			.setPositiveButton("Continue", (d, w) -> {
				String link = input.getText().toString().trim();
				if (link.isEmpty()) return;
				Intent intent = new Intent(ctx, IntentHandlerActivity.class);
				intent.setAction(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, link);
				startActivity(intent);
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	// ── File actions ─────────────────────────────────────────────────

	private String mimeOf(DownloadHistory.Item item) {
		try {
			String type = requireContext().getContentResolver().getType(Uri.parse(item.uri));
			if (type != null) return type;
		} catch (Throwable ignored) {
		}
		return item.audio ? "audio/*" : "video/*";
	}

	private void openFile(DownloadHistory.Item item) {
		try {
			HomeActions.playFile(requireContext(), Uri.parse(item.uri), item.title, item.audio);
		} catch (Throwable e) {
			toast("Could not open this file");
		}
	}

	private void shareFile(DownloadHistory.Item item) {
		try {
			Intent send = new Intent(Intent.ACTION_SEND);
			send.setType(mimeOf(item));
			send.putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri));
			send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivity(Intent.createChooser(send, item.title));
		} catch (Throwable e) {
			toast("Could not share this file");
		}
	}

	private void deleteFile(DownloadHistory.Item item) {
		new coreUtils.library.views.AppDialog.Builder(requireContext())
			.setTitle("Delete file?")
			.setMessage(item.title)
			.setPositiveButton("Delete", (d, w) -> {
				try {
					requireContext().getContentResolver().delete(Uri.parse(item.uri), null, null);
				} catch (Throwable ignored) {
				}
				DownloadHistory.remove(requireContext(), item.uri);
				reload();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void renameFile(DownloadHistory.Item item) {
		Context ctx = requireContext();
		EditText input = new EditText(ctx);
		input.setSingleLine(true);
		input.setText(item.title);
		input.setSelection(input.getText().length());
		new coreUtils.library.views.AppDialog.Builder(ctx)
			.setTitle("Rename")
			.setView(input)
			.setPositiveButton("Save", (d, w) -> {
				String name = input.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
				if (name.isEmpty()) return;
				try {
					String ext = "";
					Cursor0 c = new Cursor0();
					String current = c.displayName(ctx.getContentResolver(), Uri.parse(item.uri));
					if (current != null && current.lastIndexOf('.') > 0) {
						ext = current.substring(current.lastIndexOf('.'));
					}
					ContentValues values = new ContentValues();
					values.put("_display_name", name + ext);
					ctx.getContentResolver().update(Uri.parse(item.uri), values, null, null);
					DownloadHistory.rename(ctx, item.uri, name);
					reload();
				} catch (Throwable e) {
					toast("Could not rename this file");
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	/** Tiny helper so the rename code above stays readable. */
	private static final class Cursor0 {
		String displayName(ContentResolver resolver, Uri uri) {
			try (android.database.Cursor cursor = resolver.query(uri,
				new String[]{"_display_name"}, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
			} catch (Throwable ignored) {
			}
			return null;
		}
	}

	private void showInfo(DownloadHistory.Item item) {
		String when = DateFormat.format("dd MMM yyyy, hh:mm a", new Date(item.time)).toString();
		String message = "Title: " + item.title
			+ "\nType: " + (item.audio ? "Audio" : "Video")
			+ "\nSize: " + Formatter.formatShortFileSize(requireContext(), item.size)
			+ "\nDownloaded: " + when
			+ "\nLocation: Downloads/NextGen";
		new coreUtils.library.views.AppDialog.Builder(requireContext())
			.setTitle("File info")
			.setMessage(message)
			.setPositiveButton("OK", null)
			.show();
	}

	private void showOptions(DownloadHistory.Item item) {
		Context ctx = requireContext();
		BottomSheetDialog dialog = coreUtils.library.views.WideLayout.sheet(new BottomSheetDialog(ctx));
		DialogDownloadOptions1Binding b = DialogDownloadOptions1Binding.inflate(getLayoutInflater());
		dialog.setContentView(b.getRoot());
		b.tvTitle.setText(item.title);
		b.tvUrl.setText(item.audio ? "Audio" : "Video");
		loadThumb(b.imgThumb, item.uri);
		b.btnPlay.setOnClickListener(v -> { dialog.dismiss(); openFile(item); });
		b.optOpen.setOnClickListener(v -> { dialog.dismiss(); openFile(item); });
		b.optShare.setOnClickListener(v -> { dialog.dismiss(); shareFile(item); });
		b.optClear.setOnClickListener(v -> {
			dialog.dismiss();
			DownloadHistory.remove(ctx, item.uri);
			reload();
		});
		b.optDelete.setOnClickListener(v -> { dialog.dismiss(); deleteFile(item); });
		b.optRename.setOnClickListener(v -> { dialog.dismiss(); renameFile(item); });
		b.optLink.setOnClickListener(v -> {
			dialog.dismiss();
			ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
			if (cm != null) {
				cm.setPrimaryClip(ClipData.newPlainText("link", item.uri));
				toast("Copied");
			}
		});
		b.optInfo.setOnClickListener(v -> { dialog.dismiss(); showInfo(item); });
		dialog.show();
	}

	private void toast(String text) {
		if (getContext() != null) coreUtils.library.views.AppToast.show(getContext(), text);
	}

	// ── Thumbnails / duration ────────────────────────────────────────

	private void loadThumb(android.widget.ImageView view, String uriString) {
		view.setTag(uriString);
		view.setImageDrawable(null);
		final Context app = requireContext().getApplicationContext();
		io.execute(() -> {
			Bitmap bitmap = null;
			try {
				bitmap = app.getContentResolver().loadThumbnail(Uri.parse(uriString),
					new Size(320, 180), null);
			} catch (Throwable ignored) {
			}
			final Bitmap result = bitmap;
			main.post(() -> {
				if (result != null && uriString.equals(view.getTag())) view.setImageBitmap(result);
			});
		});
	}

	private void loadDuration(android.widget.TextView view, String uriString) {
		String cached = durationCache.get(uriString);
		view.setTag(uriString);
		if (cached != null) {
			view.setText(cached);
			view.setVisibility(cached.isEmpty() ? View.GONE : View.VISIBLE);
			return;
		}
		view.setVisibility(View.GONE);
		final Context app = requireContext().getApplicationContext();
		io.execute(() -> {
			String text = "";
			try {
				MediaMetadataRetriever r = new MediaMetadataRetriever();
				r.setDataSource(app, Uri.parse(uriString));
				String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
				r.release();
				if (d != null) {
					long s = Long.parseLong(d) / 1000;
					text = s >= 3600
						? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
						: String.format(Locale.US, "%d:%02d", s / 60, s % 60);
				}
			} catch (Throwable ignored) {
			}
			final String shown = text;
			main.post(() -> {
				durationCache.put(uriString, shown);
				if (uriString.equals(view.getTag())) {
					view.setText(shown);
					view.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
				}
			});
		});
	}

	// ── Adapters ─────────────────────────────────────────────────────

	private final class DoneAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			ItemDownloadDone1Binding b = ItemDownloadDone1Binding
				.inflate(LayoutInflater.from(parent.getContext()), parent, false);
			return new RecyclerView.ViewHolder(b.getRoot()) {};
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			ItemDownloadDone1Binding b = ItemDownloadDone1Binding.bind(holder.itemView);
			DownloadHistory.Item item = doneItems.get(position);
			b.tvTitle.setText(item.title);
			b.tvSource.setText(item.audio ? "AUDIO" : "VIDEO");
			b.imgPlay.setVisibility(item.audio ? View.GONE : View.VISIBLE);
			String when = DateFormat.format("dd MMM yyyy", new Date(item.time)).toString();
			b.tvInfo.setText(Formatter.formatShortFileSize(b.getRoot().getContext(), item.size)
				+ "  •  " + when);
			loadThumb(b.imgThumb, item.uri);
			loadDuration(b.tvDuration, item.uri);
			b.itemRoot.setOnClickListener(v -> openFile(item));
			b.btnMore.setOnClickListener(v -> showOptions(item));
		}

		@Override
		public int getItemCount() {
			return doneItems.size();
		}
	}

	private final class ActiveAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			ItemDownloadActive1Binding b = ItemDownloadActive1Binding
				.inflate(LayoutInflater.from(parent.getContext()), parent, false);
			return new RecyclerView.ViewHolder(b.getRoot()) {};
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			ItemDownloadActive1Binding b = ItemDownloadActive1Binding.bind(holder.itemView);
			DownloadRegistry.Job job = activeItems.get(position);
			b.tvTitle.setText(job.title);
			b.tvSource.setText(job.audio ? "AUDIO" : "VIDEO");
			b.tvDuration.setText(job.percent + "%");
			b.progress.setIndeterminate(false);
			b.progress.setProgress(job.percent);
			b.tvStatus.setText(job.status);
			if (job.state == DownloadRegistry.PAUSED) {
				b.btnPause.setImageResource(R.drawable.ic_v2_play);
				b.btnPause.setContentDescription(getString(R.string.v2_resume));
				b.btnPause.setOnClickListener(v -> DownloadService.resume(v.getContext(), job.id));
			} else if (job.state == DownloadRegistry.FAILED) {
				b.btnPause.setImageResource(R.drawable.ic_v2_restart);
				b.btnPause.setContentDescription(getString(R.string.v2_retry));
				b.btnPause.setOnClickListener(v -> DownloadService.resume(v.getContext(), job.id));
			} else {
				b.btnPause.setImageResource(R.drawable.ic_v2_pause);
				b.btnPause.setContentDescription(getString(R.string.v2_pause));
				b.btnPause.setOnClickListener(v -> DownloadService.pause(v.getContext(), job.id));
			}
			b.btnCancel.setOnClickListener(v -> new coreUtils.library.views.AppDialog.Builder(v.getContext())
				.setTitle("Cancel download?")
				.setMessage(job.title)
				.setPositiveButton("Cancel download", (d, w) -> {
					DownloadService.cancel(v.getContext(), job.id);
					if (job.state == DownloadRegistry.PAUSED || job.state == DownloadRegistry.FAILED) {
						DownloadRegistry.remove(job.id);
					}
				})
				.setNegativeButton("Keep", null)
				.show());
		}

		@Override
		public int getItemCount() {
			return activeItems.size();
		}
	}
}
