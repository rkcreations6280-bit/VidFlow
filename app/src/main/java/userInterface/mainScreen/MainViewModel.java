package userInterface.mainScreen;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import coreUtils.library.process.LoggerUtils;

/**
 * ViewModel for the main activity, responsible for managing UI-related data
 * that survives configuration changes. This ViewModel maintains the currently
 * selected navigation tab state across screen rotations and other lifecycle events.
 *
 * <p><strong>Managed state:</strong>
 * <ul>
 * <li>{@code currentTabs} - The currently selected navigation tab (e.g.,
 *     HOME_TAB, MUSIC_TAB, MOVIES_TABS, GAMES_TAB, BROWSER_TABS, DOWNLOADS_TAB).</li>
 * </ul>
 *
 * <p>The selected tab state is exposed via getter/setter methods and can be
 * observed by the UI layer to update the bottom navigation bar highlighting.
 * This ViewModel is scoped to the MainActivity lifecycle and is typically
 * obtained via {@link ViewModelProvider}.
 *
 * <p><strong>Usage example:</strong>
 * <pre>
 * MainViewModel viewModel = new ViewModelProvider(this).get(MainViewModel.class);
 * NavigationTabs currentTab = viewModel.getCurrentTabs();
 * viewModel.setCurrentTabs(NavigationTabs.MUSIC_TAB);
 * </pre>
 *
 * @see ViewModel
 * @see NavigationTabs
 * @see MainActivity
 */
public class MainViewModel extends ViewModel {
	
	private final LoggerUtils logger = LoggerUtils.from(MainViewModel.class);
	private NavigationTabs currentTabs;
	
	/**
	 * Returns the currently selected navigation tab in the main screen.
	 * This method provides access to the active tab value, which represents
	 * the section currently being displayed to the user (e.g., HOME_TAB, MUSIC_TAB).
	 *
	 * <p>The returned value may be {@code null} if no tab has been selected yet,
	 * which typically occurs before the initial navigation setup is completed.
	 *
	 * @return The currently selected {@link NavigationTabs} value, or {@code null}
	 * if no tab has been selected or the navigation state is uninitialized.
	 * @see #setCurrentTabs(NavigationTabs)
	 * @see NavigationTabs
	 */
	public @Nullable NavigationTabs getCurrentTabs() {
		return currentTabs;
	}
	
	/**
	 * Updates the currently selected navigation tab in the main screen.
	 * This method sets the active tab value, which should correspond to the
	 * section currently visible to the user. The new tab value is typically
	 * set when the user selects a different navigation option from the UI.
	 *
	 * <p>The {@code currentTabs} parameter must not be {@code null}, as the
	 * navigation state should always have a valid tab selected after the
	 * initial setup is complete.
	 *
	 * @param currentTabs The {@link NavigationTabs} value to set as the currently
	 *                    selected tab. Must not be {@code null}.
	 * @see #getCurrentTabs()
	 * @see NavigationTabs
	 */
	public void setCurrentTabs(@NotNull NavigationTabs currentTabs) {
		this.currentTabs = currentTabs;
	}
}
