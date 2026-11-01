package userInterface.mainScreen;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.nextgen.R;
import com.nextgen.databinding.ActivityMain1Binding;
import com.nextgen.databinding.ActivityMain1Tab1Binding;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Objects;

import coreUtils.base.BaseActivity;
import coreUtils.base.BaseFragment;
import coreUtils.library.process.FragNavigator;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import sysModules.sysPlayer.model.PlayerType;
import sysModules.sysPlayer.notification.NotificationConstants;
import sysModules.sysPlayer.queue.PendingPlaybackQueue;
import sysModules.sysPlayer.queue.PlayQueueItem;
import sysModules.sysPlayer.queue.SinglePlayQueue;
import sysModules.sysPlayer.service.PlaybackService;
import sysModules.sysPlayer.service.ServiceBridge;
import userInterface.fragmentsUIs.browser.BrowserFragment;
import userInterface.fragmentsUIs.downloads.DownloadsFragment;
import userInterface.fragmentsUIs.settings.SettingsFragment;
import userInterface.fragmentsUIs.homepage.HomepageFragment;

/**
 * Main activity serving as the primary hub for the application. This activity
 * provides bottom navigation tabs for switching between different sections
 * (Home, Music, Movies, Games, Browser, Downloads) and manages fragment
 * transactions using a {@link FragNavigator}.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Displays bottom navigation bar with tab buttons, icons, and labels.</li>
 * <li>Manages fragment navigation via {@link FragNavigator}.</li>
 * <li>Maintains the currently selected tab state via {@link MainViewModel}.</li>
 * <li>Updates tab UI highlighting (semi-bold font, selected state) on tab switches.</li>
 * <li>Locks screen orientation to portrait for consistent layout.</li>
 * </ul>
 *
 * <p><strong>Navigation tabs:</strong>
 * Home, Music, Movies, Games, Browser, Downloads. Currently, only the Home tab
 * has a dedicated {@link HomepageFragment}; other tabs redirect to HomepageFragment
 * as placeholders pending implementation.
 *
 * <p><strong>Layout:</strong>
 * Uses {@code activity_main1.xml} with bottom navigation bar
 * ({@code bottomTabs}) and a fragment container ({@code R.id.fragmentContainer}).
 *
 * @see BaseActivity
 * @see ActivityMain1Binding
 * @see FragNavigator
 * @see MainViewModel
 * @see NavigationTabs
 * @see HomepageFragment
 */
public final class MainActivity extends BaseActivity<ActivityMain1Binding> {
	
	private final LoggerUtils logger = LoggerUtils.from(getClass());
	private FragNavigator fragNavigator;
	private MainViewModel mainViewModel;
	private boolean isBottomNavVisible = true;
	private int scrollDistance = 0;
	private static final int SCROLL_THRESHOLD = 20;
	private int navigationBarHeight = 0;
	private int statusBarHeight = 0;
	
	/**
	 * Determines whether the activity's screen orientation should be locked.
	 * This implementation returns {@code true}, forcing the main activity to
	 * remain in portrait mode regardless of device rotation.
	 *
	 * <p><strong>Design rationale:</strong>
	 * Locking the orientation ensures consistent layout and navigation behavior
	 * across all tabs (Home, Music, Movies, Games, Browser, Downloads). This
	 * prevents UI reconfigurations that could disrupt user interactions such as
	 * scrolling through lists, watching videos, or browsing web content.
	 *
	 * @return {@code true} to lock the activity to portrait orientation.
	 * @see BaseActivity#shouldLockOrientation()
	 */
	@Override protected boolean shouldLockOrientation() {
		return false; // the main screen rotates on every device
	}
	
	/**
	 * Inflates the activity's layout using view binding and returns the generated
	 * binding instance for {@code activity_main1.xml}. This method is called
	 * during the base activity's {@code setContentView()} phase to create the
	 * binding object that provides type-safe access to all views in the layout.
	 *
	 * <p>The layout includes the bottom navigation bar (tab buttons, icons, labels)
	 * and a fragment container ({@code R.id.fragmentContainer}) where different
	 * fragments are displayed based on the selected tab.</p>
	 *
	 * @param inflater The layout inflater service used to create the view hierarchy.
	 *                 Must not be {@code null}.
	 * @return The {@link ActivityMain1Binding} instance containing references to
	 * all views defined in the main activity layout.
	 * @see BaseActivity#inflateBinding(LayoutInflater)
	 * @see ActivityMain1Binding
	 */
	@Override protected ActivityMain1Binding inflateBinding(LayoutInflater inflater) {
		return ActivityMain1Binding.inflate(inflater);
	}
	
	/**
	 * Performs post-layout initialization after the content view has been inflated.
	 * This method is invoked by the base activity at the end of {@code onCreate()}
	 * and is responsible for setting up the ViewModel, fragment navigator, bottom
	 * navigation tabs, and loading the initial homepage.
	 *
	 * <p><strong>Initialization order:</strong>
	 * <ol>
	 * <li>Initializes the MainViewModel via {@link #initMainViewModel()}.</li>
	 * <li>Initializes the FragmentNavigator for fragment transactions via
	 *     {@link #initFragmentNavigator()}.</li>
	 * <li>Sets up bottom tab button click listeners via {@link #initBottomTabButtons()}.</li>
	 * <li>Programmatically loads the homepage fragment via {@link #loadHomepage()}.</li>
	 * </ol>
	 *
	 * @see BaseActivity#onLoadedLayout()
	 * @see #initMainViewModel()
	 * @see #initFragmentNavigator()
	 * @see #initBottomTabButtons()
	 * @see #loadHomepage()
	 */
	@Override protected void onLoadedLayout() {
		adjustWindowPadding();
		applyWideNav();
		initMainViewModel();
		initFragmentNavigator();
		initBottomTabButtons();
		loadHomepage();

		// A video is still playing in the background: reopening the app lands back on the player.
		try {
			if (sysModules.sysPlayer.service.ExternalPlayback.takeLaunchResume()) {
				startActivity(userInterface.watchVideo.PlayRequest.resumeIntent(this));
			}
		} catch (Throwable ignored) {
		}
		
		// Remove partial download files left behind by an earlier crash / killed app.
		ThreadTask.executeInBackground(() ->
			sysModules.downloadSys.DownloadService.cleanupOrphans(getApplicationContext()));
	}
	
	/**
	 * Called when the activity is no longer in the foreground. This method saves
	 * the current scroll position state before the activity loses focus. The
	 * {@code scrollDistance} field is reset to 0 to prevent stale scroll position
	 * data from being used when the activity resumes or is recreated.
	 *
	 * <p>Resetting the scroll distance here ensures that the bottom navigation
	 * bar visibility logic starts from a clean state when the user returns to
	 * this activity, avoiding unexpected animation or visibility states.
	 *
	 * @see android.app.Activity#onPause()
	 * @see #scrollDistance
	 */
	@Override
	protected void onPause() {
		super.onPause();
		scrollDistance = 0;
	}
	
	/**
	 * Adjusts the bottom margin of the bottom tab navigation view to accommodate
	 * the system navigation bar height. This method attaches a window insets listener
	 * to the bottom navigation view, extracts the navigation bar height from the
	 * insets, and applies it as a bottom margin to prevent UI elements from being
	 * obscured by gesture navigation handles or the traditional navigation bar.
	 *
	 * <p><strong>Insets handling:</strong>
	 * The listener extracts the navigation bar insets using
	 * {@link WindowInsetsCompat.Type#navigationBars()}. The bottom margin is
	 * dynamically updated whenever the insets change (e.g., orientation change,
	 * keyboard visibility toggle, or navigation mode switch).
	 *
	 * <p>The layout parameters are cast to {@link FrameLayout.LayoutParams} based on
	 * the assumption that the bottom navigation view is hosted within a FrameLayout
	 * (common for CoordinatorLayout or ConstraintLayout with appropriate constraints).
	 *
	 * @see ViewCompat#setOnApplyWindowInsetsListener(View, OnApplyWindowInsetsListener)
	 * @see WindowInsetsCompat#getInsets(int)
	 */
	/** Wide screens: the bottom bar is a centred pill instead of a full-width strip. */
	private void applyWideNav() {
		if (!coreUtils.library.views.WideLayout.isWide(this)) return;
		FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) binding.bottomNavContainer.getLayoutParams();
		lp.width = coreUtils.library.views.WideLayout.dp(this, 560);
		lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
		binding.bottomNavContainer.setLayoutParams(lp);
	}

	private void adjustWindowPadding() {
		ViewCompat.setOnApplyWindowInsetsListener(
			binding.bottomNavContainer, (view, insets) -> {
				int navigationBars = WindowInsetsCompat.Type.navigationBars();
				int statusBars = WindowInsetsCompat.Type.statusBars();
				
				navigationBarHeight = insets.getInsets(navigationBars).bottom;
				statusBarHeight = insets.getInsets(statusBars).top;
				
				FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
				params.bottomMargin = navigationBarHeight;
				view.setLayoutParams(params);
				return insets;
			});
	}
	
	/**
	 * Initializes the MainViewModel for the main activity. This method creates a
	 * ViewModel instance using {@link ViewModelProvider} scoped to this activity.
	 * The ViewModel manages UI-related data such as the currently selected
	 * navigation tab and survives configuration changes.
	 *
	 * <p>The ViewModel is stored in the {@code mainViewModel} field and can be
	 * accessed by other methods within this activity for observing LiveData
	 * or updating state.
	 *
	 * @see ViewModelProvider
	 * @see MainViewModel
	 * @see #onLoadedLayout()
	 */
	private void initMainViewModel() {
		ViewModelProvider viewModelProvider = new ViewModelProvider(this);
		mainViewModel = viewModelProvider.get(MainViewModel.class);
	}
	
	/**
	 * Initializes the FragmentNavigator for managing fragment transactions.
	 * This method retrieves the {@link FragmentManager} from the activity and
	 * creates a new {@link FragNavigator} instance targeting the container
	 * view with ID {@code R.id.fragmentContainer}.
	 *
	 * <p>The navigator is responsible for replacing fragments within the main
	 * activity's container, applying fade animations, and managing back stack
	 * entries for fragment navigation.
	 *
	 * @see FragmentManager
	 * @see FragNavigator
	 * @see #onLoadedLayout()
	 */
	private void initFragmentNavigator() {
		FragmentManager frgManager = getSupportFragmentManager();
		fragNavigator = new FragNavigator(frgManager, R.id.fragmentContainer);
	}
	
	/**
	 * Programmatically triggers the home tab click event to load the homepage fragment.
	 * This method simulates a user click on the home tab button, initiating the
	 * navigation to {@link HomepageFragment} and updating the UI to reflect the
	 * home tab as selected. It is typically called during activity initialization
	 * to set the initial screen.
	 *
	 * @see #initBottomTabButtons()
	 * @see View#callOnClick()
	 */
	private void loadHomepage() {
		ActivityMain1Tab1Binding bottomTabs = binding.bottomTabs;
		bottomTabs.btnHomeTab.callOnClick();
	}
	
	/**
	 * Initializes click listeners for all bottom navigation tab buttons.
	 * Each tab, when clicked, navigates to its corresponding fragment using
	 * {@link #switchTab(BaseFragment, View, ImageView, TextView, NavigationTabs)}.
	 *
	 * <p><strong>Tab mappings:</strong>
	 * <ul>
	 * <li>Home Tab → {@link HomepageFragment} with {@link NavigationTabs#HOME_TAB}</li>
	 * <li>Music Tab → {@link HomepageFragment} with {@link NavigationTabs#MUSIC_TAB}</li>
	 * <li>Movies Tab → {@link HomepageFragment} with {@link NavigationTabs#MOVIES_TABS}</li>
	 * <li>Games Tab → {@link HomepageFragment} with {@link NavigationTabs#GAMES_TAB}</li>
	 * <li>Browser Tab → {@link HomepageFragment} with {@link NavigationTabs#HOME_TAB}</li>
	 * <li>Files Tab → {@link HomepageFragment} with {@link NavigationTabs#HOME_TAB}</li>
	 * </ul>
	 *
	 * <p><strong>Note:</strong> Currently, all tabs except Home navigate to
	 * {@link HomepageFragment} with placeholder enum values. This indicates that
	 * dedicated fragments for Music, Movies, Games, Browser, and Files tabs
	 * are pending implementation (todo items).
	 *
	 * @see #switchTab(BaseFragment, View, ImageView, TextView, NavigationTabs)
	 * @see HomepageFragment
	 */
	private void initBottomTabButtons() {
		ActivityMain1Tab1Binding t = binding.bottomTabs;
		t.btnHomeTab.setOnClickListener(view ->
			switchTab(new HomepageFragment(), t.btnHomeTab, t.imgHomeTab,
				t.tvHomeTab, NavigationTabs.HOME_TAB));
		t.btnBrowserTab.setOnClickListener(view ->
			switchTab(new BrowserFragment(), t.btnBrowserTab, t.imgBrowserTab,
				t.tvBrowserTab, NavigationTabs.BROWSER_TABS));
		t.btnDownloadsTab.setOnClickListener(view ->
			switchTab(DownloadsFragment.create("done"), t.btnDownloadsTab, t.imgDownloadsTab,
				t.tvDownloadsTab, NavigationTabs.DOWNLOADS_TAB));
		t.btnSettingsTab.setOnClickListener(view ->
			switchTab(new SettingsFragment(), t.btnSettingsTab, t.imgSettingsTab,
				t.tvSettingsTab, NavigationTabs.SETTINGS_TAB));
	}

	/** Opens one of the bottom tabs from code (e.g. a back button in a fragment). */
	public void openTab(@NonNull NavigationTabs tab) {
		ActivityMain1Tab1Binding t = binding.bottomTabs;
		switch (tab) {
			case BROWSER_TABS: t.btnBrowserTab.callOnClick(); break;
			case DOWNLOADS_TAB: t.btnDownloadsTab.callOnClick(); break;
			case SETTINGS_TAB: t.btnSettingsTab.callOnClick(); break;
			default: t.btnHomeTab.callOnClick(); break;
		}
	}

	/**
	 * Switches the currently displayed fragment to the specified tab fragment.
	 * This method navigates to the target fragment, updates the ViewModel with
	 * the active tab enum, and refreshes the UI to highlight the selected tab.
	 *
	 * <p>The navigation does not add the transaction to the back stack, as tab
	 * switching should not accumulate back stack entries. Each tab replacement
	 * directly replaces the previous fragment.</p>
	 *
	 * @param fragment      The fragment to navigate to (corresponding to the tab).
	 * @param btnTab        The button/layout view representing the tab.
	 * @param imgTab        The ImageView within the tab (icon).
	 * @param tvTab         The TextView within the tab (label).
	 * @param activeTabEnum The enum value representing the active tab.
	 * @see #updateTabUI(View, ImageView, TextView)
	 * @see FragNavigator#navigateTo(BaseFragment, boolean)
	 */
	private void switchTab(@NonNull BaseFragment<?> fragment,
	                       @NonNull View btnTab,
	                       @NotNull ImageView imgTab,
	                       @NotNull TextView tvTab,
	                       @NonNull NavigationTabs activeTabEnum) {
		binding.bottomNavContainer.animate().cancel();
		scrollDistance = 0;
		isBottomNavVisible = true;
		binding.bottomNavContainer.setTranslationY(0);
		
		fragNavigator.navigateTo(fragment, false);
		mainViewModel.setCurrentTabs(activeTabEnum);
		updateTabUI(btnTab, imgTab, tvTab);
	}
	
	/**
	 * Updates the UI state of all bottom navigation tabs to reflect the newly
	 * selected tab. This method deselects all tabs, resets all text labels to
	 * regular font, then applies semi-bold font and selected state to the active tab.
	 *
	 * <p><strong>Visual changes applied:</strong>
	 * <ul>
	 * <li>All tab buttons have {@code selected} state set to {@code false}.</li>
	 * <li>All tab text views have regular font typeface.</li>
	 * <li>The active tab gets semi-bold font typeface and selected state set to
	 *     {@code true}.</li>
	 * </ul>
	 *
	 * @param activeBtnTab The button/layout view of the active tab.
	 * @param activeImgTab The ImageView of the active tab (icon).
	 * @param activeTvTab  The TextView of the active tab (label).
	 * @see #switchTab(BaseFragment, View, ImageView, TextView, NavigationTabs)
	 */
	private void updateTabUI(@NotNull View activeBtnTab,
	                         @NotNull ImageView activeImgTab,
	                         @NotNull TextView activeTvTab) {
		ActivityMain1Tab1Binding t = binding.bottomTabs;
		t.btnHomeTab.setSelected(false);
		t.btnBrowserTab.setSelected(false);
		t.btnDownloadsTab.setSelected(false);
		t.btnSettingsTab.setSelected(false);
		t.imgHomeTab.setSelected(false);
		t.imgBrowserTab.setSelected(false);
		t.imgDownloadsTab.setSelected(false);
		t.imgSettingsTab.setSelected(false);
		t.tvHomeTab.setSelected(false);
		t.tvBrowserTab.setSelected(false);
		t.tvDownloadsTab.setSelected(false);
		t.tvSettingsTab.setSelected(false);

		Typeface regularFont = ResourcesCompat.getFont(this, R.font.font_family_regular);
		Typeface semiBoldFont = ResourcesCompat.getFont(this, R.font.font_family_semibold);
		t.tvHomeTab.setTypeface(regularFont);
		t.tvBrowserTab.setTypeface(regularFont);
		t.tvDownloadsTab.setTypeface(regularFont);
		t.tvSettingsTab.setTypeface(regularFont);

		activeImgTab.setSelected(true);
		activeTvTab.setSelected(true);
		activeTvTab.setTypeface(semiBoldFont);
		activeBtnTab.setSelected(true);
	}
	
	/**
	 * Configures a scroll listener on the provided {@link NestedScrollView} to
	 * automatically show or hide the bottom navigation bar based on scroll direction.
	 * When the user scrolls down (increasing Y), the bottom nav hides. When the user
	 * scrolls up (decreasing Y), the bottom nav shows. The current scroll position
	 * is stored in {@code scrollDistance} for reference.
	 *
	 * <p><strong>Scroll behavior:</strong>
	 * <ul>
	 * <li>Scrolling down (scrollY > oldScrollY) → hides bottom nav if visible.</li>
	 * <li>Scrolling up (scrollY < oldScrollY) → shows bottom nav if hidden.</li>
	 * <li>No action when scroll position does not change.</li>
	 * </ul>
	 *
	 * @param nestedScrollView The {@link NestedScrollView} to attach the scroll listener to.
	 *                         Must not be {@code null}.
	 * @see #hideBottomNav()
	 * @see #showBottomNav()
	 * @see NestedScrollView#setOnScrollChangeListener(View.OnScrollChangeListener)
	 */
	public void setupNestedScrollListener(@NonNull NestedScrollView nestedScrollView) {
		nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
			(view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
				int deltaY = scrollY - oldScrollY;
				
				if ((deltaY > 0 && scrollDistance < 0) ||
					(deltaY < 0 && scrollDistance > 0)) {
					scrollDistance = 0;
				}
				
				scrollDistance += deltaY;
				if (Math.abs(scrollDistance) > SCROLL_THRESHOLD) {
					if (deltaY > 0 && isBottomNavVisible) {
						hideBottomNav();
						scrollDistance = 0;
					} else if (deltaY < 0 && !isBottomNavVisible) {
						showBottomNav();
						scrollDistance = 0;
					}
				}
			});
	}
	
	/**
	 * Applies edge-to-edge insets to a NestedScrollView by adding padding for the
	 * status bar and navigation bar, plus extra spacing. This method attaches a
	 * window insets listener that dynamically adjusts the scroll view's top and
	 * bottom padding when system insets change (e.g., orientation rotation or
	 * navigation mode changes).
	 *
	 * <p><strong>Padding calculation:</strong>
	 * <ul>
	 * <li>Top padding = status bar height + 5dp extra spacing.</li>
	 * <li>Bottom padding = navigation bar height + 5dp extra spacing.</li>
	 * <li>Left and right padding remain unchanged.</li>
	 * </ul>
	 *
	 * <p>Clip-to-padding is disabled via {@link NestedScrollView#setClipToPadding(boolean)}
	 * to ensure the scrollable content renders correctly with the applied padding.
	 * The 5dp extra spacing prevents content from visually touching the status bar
	 * or navigation bar areas.
	 *
	 * @param scrollView The NestedScrollView to apply edge-to-edge insets to.
	 *                   Must not be null.
	 * @see ViewCompat#setOnApplyWindowInsetsListener(View, OnApplyWindowInsetsListener)
	 * @see WindowInsetsCompat.Type#statusBars()
	 * @see WindowInsetsCompat.Type#navigationBars()
	 */
	public void applyEdgeToEdgeScrolling(NestedScrollView scrollView) {
		ViewCompat.setOnApplyWindowInsetsListener(scrollView, (view, insets) -> {
			int statusBars = WindowInsetsCompat.Type.statusBars();
			int navigationBars = WindowInsetsCompat.Type.navigationBars();
			
			int statusBarHeight = insets.getInsets(statusBars).top;
			int navigationBarHeight = insets.getInsets(navigationBars).bottom;
			
			int extraTopPadding = (int) (5 * getResources().getDisplayMetrics().density);
			int extraBottomPadding = (int) (5 * getResources().getDisplayMetrics().density);
			
			view.setPadding(
				view.getPaddingLeft(),
				statusBarHeight + extraTopPadding,
				view.getPaddingRight(),
				navigationBarHeight + extraBottomPadding
			);
			
			((NestedScrollView) view).setClipToPadding(false);
			return insets;
		});
	}
	
	/**
	 * Removes the scroll change listener from the provided {@link NestedScrollView}.
	 * This method sets the scroll change listener to {@code null}, effectively
	 * detaching the listener that was previously attached via
	 * {@link #setupNestedScrollListener(NestedScrollView)}. Call this method to
	 * prevent memory leaks or unwanted scroll-triggered UI changes when the
	 * associated fragment or activity is being destroyed.
	 *
	 * @param nestedScrollView The {@link NestedScrollView} from which to remove
	 *                         the scroll change listener. Must not be {@code null}.
	 * @see #setupNestedScrollListener(NestedScrollView)
	 * @see NestedScrollView#setOnScrollChangeListener(View.OnScrollChangeListener)
	 */
	public void cleanupScrollListener(NestedScrollView nestedScrollView) {
		if (nestedScrollView != null) {
			nestedScrollView.setOnScrollChangeListener(
				(NestedScrollView.OnScrollChangeListener) null
			);
		}
	}
	
	/**
	 * Animates the bottom navigation bar out of view if it is currently visible.
	 * This method checks the {@code isBottomNavVisible} flag and returns early if
	 * the navigation is already hidden. The animation translates the view downward
	 * by its own height over 100ms using an accelerating interpolator for a
	 * smooth fade-out effect.
	 *
	 * <p><strong>Animation details:</strong>
	 * <ul>
	 * <li>Translation Y: moves from 0 to view height</li>
	 * <li>Duration: 100 milliseconds</li>
	 * <li>Interpolator: {@link AccelerateInterpolator} (starts slow, ends fast)</li>
	 * <li>Layer: enabled for hardware acceleration during animation</li>
	 * </ul>
	 *
	 * @see #showBottomNav()
	 * @see android.view.ViewPropertyAnimator
	 */
	private void hideBottomNav() {
		if (!isBottomNavVisible) return;
		
		binding.bottomNavContainer.animate().cancel();
		int navigationTabHeight = binding.bottomNavContainer.getHeight();
		binding.bottomNavContainer.animate()
			.translationY(navigationTabHeight + navigationBarHeight)
			.setDuration(100)
			.withLayer()
			.setInterpolator(new AccelerateInterpolator())
			.start();
		isBottomNavVisible = false;
	}
	
	/**
	 * Animates the bottom navigation bar into view if it is currently hidden.
	 * This method checks the {@code isBottomNavVisible} flag and returns early if
	 * the navigation is already visible. The animation translates the view upward
	 * from its hidden position (bottom) back to 0 over 100ms using a decelerating
	 * interpolator for a smooth fade-in effect.
	 *
	 * <p><strong>Animation details:</strong>
	 * <ul>
	 * <li>Translation Y: moves from view height to 0</li>
	 * <li>Duration: 100 milliseconds</li>
	 * <li>Interpolator: {@link DecelerateInterpolator} (starts fast, ends slow)</li>
	 * <li>Layer: enabled for hardware acceleration during animation</li>
	 * </ul>
	 *
	 * @see #hideBottomNav()
	 * @see android.view.ViewPropertyAnimator
	 */
	private void showBottomNav() {
		if (isBottomNavVisible) return;
		
		binding.bottomNavContainer.animate().cancel();
		binding.bottomNavContainer.animate()
			.translationY(0)
			.setDuration(100)
			.withLayer()
			.setInterpolator(new DecelerateInterpolator())
			.start();
		isBottomNavVisible = true;
	}
}
