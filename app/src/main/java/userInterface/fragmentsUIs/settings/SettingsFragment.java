package userInterface.fragmentsUIs.settings;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.nextgen.databinding.FragSettings1Binding;
import com.nextgen.databinding.ItemSettingsFooter1Binding;
import com.nextgen.databinding.ItemSettingsRow1Binding;
import com.nextgen.databinding.ItemSettingsSection1Binding;
import com.nextgen.R;

import java.util.function.Consumer;
import java.util.function.Supplier;

import coreUtils.base.BaseFragment;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import userInterface.aboutUs.AboutActivity;
import userInterface.languagePicker.LanguageActivity;
import userInterface.mainScreen.MainActivity;
import userInterface.termsConsPolicy.TermsPolicyActivity;

/** Settings tab. Sections and rows are built here; styles come from item_settings_*_1.xml. */
public class SettingsFragment extends BaseFragment<FragSettings1Binding> {

	@Override
	protected FragSettings1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
		return FragSettings1Binding.inflate(inflater, container, false);
	}

	@Override
	protected void onLoadedLayout() {
		if (getActivity() instanceof MainActivity) {
			((MainActivity) getActivity()).setupNestedScrollListener(binding.NestedScrollView);
		}
		build();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (getActivity() instanceof MainActivity && binding != null) {
			((MainActivity) getActivity()).cleanupScrollListener(binding.NestedScrollView);
		}
	}

	private void build() {
		Context ctx = requireContext();
		LayoutInflater inflater = LayoutInflater.from(ctx);
		LinearLayout root = binding.settingsContainer;
		root.removeAllViews();

		LinearLayout box = section(inflater, root, "Downloads");
		toggle(inflater, box, R.drawable.ic_v2_wifi, "Download on Wi-Fi only",
			() -> cfg().wifiOnlyDownloadEnabled, v -> cfg().wifiOnlyDownloadEnabled = v);
		toggle(inflater, box, R.drawable.ic_v2_notification_off, "Hide download notification",
			() -> cfg().isDownloadNotificationHidden, v -> cfg().isDownloadNotificationHidden = v);
		toggle(inflater, box, R.drawable.ic_v2_touch, "Open file on single click",
			() -> cfg().isDownloadOpenedOnSingleClick, v -> cfg().isDownloadOpenedOnSingleClick = v);
		toggle(inflater, box, R.drawable.ic_v2_sound, "Completion sound",
			() -> cfg().isDownloadCompletionSoundEnabled, v -> cfg().isDownloadCompletionSoundEnabled = v);

		box = section(inflater, root, "Browser");
		toggle(inflater, box, R.drawable.ic_v2_block, "Ad blocker",
			() -> cfg().isAdBlockerEnabled, v -> cfg().isAdBlockerEnabled = v);
		toggle(inflater, box, R.drawable.ic_v2_block, "Popup blocker",
			() -> cfg().isPopupBlockerEnabled, v -> cfg().isPopupBlockerEnabled = v);
		toggle(inflater, box, R.drawable.ic_v2_image, "Load images",
			() -> cfg().isImageLoadingEnabled, v -> cfg().isImageLoadingEnabled = v);
		toggle(inflater, box, R.drawable.ic_v2_video, "Video grabber",
			() -> cfg().isVideoGrabberEnabled, v -> cfg().isVideoGrabberEnabled = v);

		box = section(inflater, root, "Browser privacy");
		bToggle(inflater, box, R.drawable.ic_v2_video, "Play-in-app button on videos", sysModules.browserEngine.BrowserPrefs.PLAY_BUTTON);
		bToggle(inflater, box, R.drawable.ic_v2_shield, "Block trackers", sysModules.browserEngine.BrowserPrefs.TRACKER_BLOCK);
		bToggle(inflater, box, R.drawable.ic_v2_touch, "Block hidden touch overlays", sysModules.browserEngine.BrowserPrefs.TOUCH_BLOCK);
		bToggle(inflater, box, R.drawable.ic_v2_block, "Block unwanted redirects", sysModules.browserEngine.BrowserPrefs.REDIRECT_BLOCK);
		bToggle(inflater, box, R.drawable.ic_v2_block, "Prevent automatic downloads", sysModules.browserEngine.BrowserPrefs.DOWNLOAD_PREVENT);
		bToggle(inflater, box, R.drawable.ic_v2_lock, "Upgrade to HTTPS", sysModules.browserEngine.BrowserPrefs.HTTPS_UPGRADE);
		bToggle(inflater, box, R.drawable.ic_v2_block, "Block third-party cookies", sysModules.browserEngine.BrowserPrefs.THIRD_PARTY_COOKIES_BLOCK);
		link(inflater, box, R.drawable.ic_v2_search, "Search engine",
			sysModules.browserEngine.BrowserPrefs.ENGINE_NAMES[sysModules.browserEngine.BrowserPrefs.engine(ctx)],
			() -> {
				int next = (sysModules.browserEngine.BrowserPrefs.engine(ctx) + 1)
					% sysModules.browserEngine.BrowserPrefs.ENGINE_NAMES.length;
				sysModules.browserEngine.BrowserPrefs.setEngine(ctx, next);
				build();
			});
		link(inflater, box, R.drawable.ic_v2_trash, "Clear browsing data", null, () -> {
			sysModules.browserEngine.BrowserSession.get().clearAllData(ctx);
			coreUtils.library.views.AppToast.show(ctx, "Browsing data cleared");
		});

		box = section(inflater, root, "General");
		toggle(inflater, box, R.drawable.ic_v2_suggest, "Daily suggestions",
			() -> cfg().isDailySuggestionsEnabled, v -> cfg().isDailySuggestionsEnabled = v);
		link(inflater, box, R.drawable.ic_v2_language, "Language",
			cfg().selectedLanguageCode + " / " + cfg().selectedRegionCode,
			() -> go(new Intent(ctx, LanguageActivity.class)));

		box = section(inflater, root, "About");
		link(inflater, box, R.drawable.ic_v2_terms, "Terms & conditions", null, () -> {
			Intent i = new Intent(ctx, TermsPolicyActivity.class);
			i.putExtra(TermsPolicyActivity.KEY_ACTIVITY_LAUNCHED_LOCATION,
				TermsPolicyActivity.LAUNCHED_FROM_SETTINGS_SCREEN);
			go(i);
		});
		link(inflater, box, R.drawable.ic_v2_info, "About", null,
			() -> go(new Intent(ctx, AboutActivity.class)));

		ItemSettingsFooter1Binding footer = ItemSettingsFooter1Binding.inflate(inflater, root, false);
		footer.tvVersion.setText(versionText(ctx));
		footer.btnRestart.setOnClickListener(v -> restartApp(ctx));
		root.addView(footer.getRoot());
	}

	/** Opens a screen with the project's slide-left animation. */
	private void go(Intent intent) {
		startActivity(intent);
		if (getActivity() instanceof coreUtils.base.BaseActivity) {
			coreUtils.library.views.ActivityAnimator.animActivitySlideLeft(
				(coreUtils.base.BaseActivity<?>) getActivity());
		}
	}

	private static AppConfigs cfg() {
		return AppConfigsRepo.getConfig();
	}

	private String versionText(Context ctx) {
		try {
			String v = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
			return getString(R.string.v2_app_version) + " " + (v == null ? "" : "v" + v);
		} catch (Throwable e) {
			return getString(R.string.v2_app_version);
		}
	}

	private void restartApp(Context ctx) {
		Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
		if (launch == null) return;
		launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(launch);
		Runtime.getRuntime().exit(0);
	}

	private LinearLayout section(LayoutInflater inflater, LinearLayout root, String title) {
		ItemSettingsSection1Binding s = ItemSettingsSection1Binding.inflate(inflater, root, false);
		s.tvSectionTitle.setText(title);
		root.addView(s.getRoot());
		return s.rowsContainer;
	}

	private void bToggle(LayoutInflater inflater, LinearLayout box, int icon, String title, String key) {
		Context c = requireContext();
		toggle(inflater, box, icon, title,
			() -> sysModules.browserEngine.BrowserPrefs.get(c, key),
			v -> sysModules.browserEngine.BrowserPrefs.set(c, key, v));
	}

	private void toggle(LayoutInflater inflater, LinearLayout box, int icon, String title,
	                    Supplier<Boolean> getter, Consumer<Boolean> setter) {
		ItemSettingsRow1Binding row = ItemSettingsRow1Binding.inflate(inflater, box, false);
		row.imgIcon.setImageResource(icon);
		row.tvTitle.setText(title);
		Runnable render = () -> {
			boolean on = getter.get();
			row.tvValue.setVisibility(View.VISIBLE);
			row.tvValue.setText(on ? "On" : "Off");
			row.imgTrailing.setImageResource(R.drawable.ic_v2_check);
			row.imgTrailing.setAlpha(on ? 1f : 0.2f);
		};
		render.run();
		row.rowRoot.setOnClickListener(v -> {
			setter.accept(!getter.get());
			cfg().save();
			render.run();
		});
		box.addView(row.getRoot());
	}

	private void link(LayoutInflater inflater, LinearLayout box, int icon, String title,
	                  String value, Runnable action) {
		ItemSettingsRow1Binding row = ItemSettingsRow1Binding.inflate(inflater, box, false);
		row.imgIcon.setImageResource(icon);
		row.tvTitle.setText(title);
		if (value != null) {
			row.tvValue.setVisibility(View.VISIBLE);
			row.tvValue.setText(value);
		}
		row.imgTrailing.setImageResource(R.drawable.ic_keyboard_arrow_right);
		row.rowRoot.setOnClickListener(v -> action.run());
		box.addView(row.getRoot());
	}
}
