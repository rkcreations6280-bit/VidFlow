package userInterface.mainScreen;

import coreUtils.library.process.FragNavigator;

/**
 * Enumeration defining the available navigation tabs in the main screen of the application.
 * Each constant represents a distinct section or feature area that can be displayed
 * within the main activity's bottom navigation bar or tab layout.
 *
 * <p><strong>Available tabs:</strong>
 * <ul>
 * <li>{@link #HOME_TAB} - Home section with personalized content and recommendations.</li>
 * <li>{@link #MUSIC_TAB} - Music player and library management interface.</li>
 * <li>{@link #MOVIES_TABS} - Movies section with video browsing and playback.</li>
 * <li>{@link #GAMES_TAB} - Games section for interactive entertainment.</li>
 * <li>{@link #BROWSER_TABS} - Web browser interface for internet navigation.</li>
 * <li>{@link #DOWNLOADS_TAB} - Downloads manager for tracking and managing files.</li>
 * </ul>
 *
 * <p>This enum is typically used with a {@link FragNavigator} to switch between
 * the corresponding fragments for each tab when the user selects a different
 * navigation option in the UI.
 *
 * @see FragNavigator
 * @see userInterface.mainScreen.MainActivity
 */

public enum NavigationTabs {
	HOME_TAB, MUSIC_TAB, MOVIES_TABS, GAMES_TAB, BROWSER_TABS, DOWNLOADS_TAB, SETTINGS_TAB
}