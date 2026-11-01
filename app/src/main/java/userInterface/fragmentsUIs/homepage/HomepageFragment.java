package userInterface.fragmentsUIs.homepage;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.nextgen.databinding.FragHomepage1Binding;
import com.nextgen.databinding.ItemHomeRecent1Binding;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import coreUtils.base.BaseFragment;
import sysModules.downloadSys.DownloadHistory;
import userInterface.mainScreen.MainActivity;
import userInterface.mainScreen.NavigationTabs;

/**
 * Home screen. Layout: res/layout/frag_homepage_1.xml.
 * Button behaviour: {@link HomeActions}.
 */
public class HomepageFragment extends BaseFragment<FragHomepage1Binding> {

	private final ExecutorService io = Executors.newFixedThreadPool(2);
	private final Handler main = new Handler(Looper.getMainLooper());

	private final ActivityResultLauncher<String[]> pickVideo =
		registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
			if (uri == null || getContext() == null) return;
			try {
				requireContext().getContentResolver().takePersistableUriPermission(uri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} catch (Throwable ignored) {
			}
			HomeActions.play(requireContext(), uri);
		});

	@Override
	protected FragHomepage1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
		return FragHomepage1Binding.inflate(inflater, container, false);
	}

	@Override
	protected void onLoadedLayout() {
		if (getActivity() instanceof MainActivity) {
			((MainActivity) getActivity()).setupNestedScrollListener(binding.NestedScrollView);
		}
		binding.btnLocal.setOnClickListener(v -> pickVideo.launch(new String[]{"video/*", "audio/*"}));
		binding.btnPaste.setOnClickListener(v -> pasteLink());
		binding.btnClear.setOnClickListener(v -> binding.etVideoUrl.setText(""));
		binding.btnPlay.setOnClickListener(v -> {
			String link = link();
			if (link != null) HomeActions.play(requireContext(), Uri.parse(link));
		});
		binding.btnFetch.setOnClickListener(v -> {
			String link = link();
			if (link != null) HomeActions.fetch(requireContext(), link);
		});
		binding.etVideoUrl.setOnEditorActionListener((v, actionId, event) -> {
			String link = link();
			if (link != null) HomeActions.fetch(requireContext(), link);
			return true;
		});
		binding.btnHistory.setOnClickListener(v -> openBrowserSheet("history"));
		binding.btnBookmarks.setOnClickListener(v -> openBrowserSheet("bookmarks"));
		binding.tvViewAll.setOnClickListener(v -> {
			if (getActivity() instanceof MainActivity) {
				((MainActivity) getActivity()).openTab(NavigationTabs.DOWNLOADS_TAB);
			}
		});
		refreshRecent();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (binding != null) refreshRecent();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (getActivity() instanceof MainActivity && binding != null) {
			((MainActivity) getActivity()).cleanupScrollListener(binding.NestedScrollView);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		io.shutdownNow();
	}

	/** Returns the typed link, or null (with a message) when it is empty. */
	private String link() {
		String text = binding.etVideoUrl.getText().toString().trim();
		if (text.isEmpty()) {
			coreUtils.library.views.AppToast.show(requireContext(), "Paste a video link first");
			return null;
		}
		return text;
	}

	private void pasteLink() {
		try {
			ClipboardManager cm = (ClipboardManager) requireContext()
				.getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip = cm == null ? null : cm.getPrimaryClip();
			if (clip != null && clip.getItemCount() > 0) {
				CharSequence text = clip.getItemAt(0).coerceToText(requireContext());
				if (text != null) binding.etVideoUrl.setText(text.toString().trim());
			}
		} catch (Throwable ignored) {
		}
	}

	private void refreshRecent() {
		Context ctx = requireContext();
		binding.historyList.removeAllViews();
		List<DownloadHistory.Item> items = DownloadHistory.list(ctx, "all");
		binding.tvRecentEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
		LayoutInflater inflater = LayoutInflater.from(ctx);
		int count = Math.min(5, items.size());
		for (int i = 0; i < count; i++) {
			DownloadHistory.Item item = items.get(i);
			ItemHomeRecent1Binding row = ItemHomeRecent1Binding
				.inflate(inflater, binding.historyList, false);
			row.tvTitle.setText(item.title);
			row.tvInfo.setText(Formatter.formatShortFileSize(ctx, item.size) + "  •  "
				+ DateFormat.format("dd MMM yyyy", new Date(item.time)));
			loadThumb(row.imgThumb, item.uri);
			row.itemRoot.setOnClickListener(v -> HomeActions.playFile(ctx, Uri.parse(item.uri), item.title, item.audio));
			binding.historyList.addView(row.getRoot());
		}
	}

	private void loadThumb(ImageView view, String uriString) {
		view.setTag(uriString);
		final Context app = requireContext().getApplicationContext();
		try {
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
		} catch (Throwable ignored) {
		}
	}

	private void openBrowserSheet(String which) {
		sysModules.browserEngine.BrowserSession.get().pendingSheet = which;
		if (getActivity() instanceof MainActivity) {
			((MainActivity) getActivity()).openTab(NavigationTabs.BROWSER_TABS);
		}
	}
}
