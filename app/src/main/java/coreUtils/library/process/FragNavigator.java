package coreUtils.library.process;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import coreUtils.base.BaseFragment;

/**
 * A utility class for managing fragment navigation within a single container.
 * This class simplifies fragment transactions by providing a consistent API
 * for replacing fragments with fade animations and optional back stack support.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Handles fragment replacement transactions with fade animations.</li>
 * <li>Prevents redundant navigation to the currently displayed fragment.</li>
 * <li>Supports adding transactions to the back stack for back button navigation.</li>
 * <li>Provides a clean abstraction over {@link FragmentTransaction} operations.</li>
 * </ul>
 *
 * <p><strong>Usage example:</strong>
 * <pre>
 * FragNavigator navigator = new FragNavigator(getSupportFragmentManager(), R.id.container);
 * navigator.navigateTo(new HomeFragment(), true);
 * navigator.navigateTo(new SettingsFragment(), true);
 * </pre>
 *
 * <p>The navigator is typically initialized in an Activity's {@code onCreate()}
 * and reused for all fragment navigation within that Activity.
 *
 * @see FragmentManager
 * @see FragmentTransaction
 * @see BaseFragment
 */
public class FragNavigator {
	
	private final FragmentManager fragmentManager;
	private final @IdRes int containerId;
	
	/**
	 * Constructs a new FragNavigator instance with the specified FragmentManager and
	 * container ID. This navigator is responsible for managing fragment transactions
	 * within the given container.
	 *
	 * @param fragmentManager The FragmentManager used to execute fragment transactions.
	 *                        Must not be {@code null}.
	 * @param containerId     The resource ID of the container view (e.g., FrameLayout)
	 *                        where fragments will be placed.
	 */
	public FragNavigator(FragmentManager fragmentManager, @IdRes int containerId) {
		this.fragmentManager = fragmentManager;
		this.containerId = containerId;
	}
	
	/**
	 * Navigates to the specified fragment by replacing the current fragment in the
	 * container. If the requested fragment is already displayed, the method returns
	 * without performing any transaction. Fade animations are applied to the transition.
	 *
	 * <p><strong>Behavior details:</strong>
	 * <ul>
	 * <li>Checks the currently displayed fragment; if it matches the target fragment,
	 *     the navigation is skipped to avoid redundant replacements.</li>
	 * <li>Uses fade-in and fade-out animations from {@link android.R.anim}.</li>
	 * <li>If {@code addToBackStack} is {@code true}, the transaction is added to the
	 *     back stack using the fragment's class simple name as the tag.</li>
	 * </ul>
	 *
	 * @param fragment       The fragment to navigate to. Must not be {@code null}.
	 * @param addToBackStack If {@code true}, the transaction is added to the back
	 *                       stack, allowing the user to press back to return to the
	 *                       previous fragment.
	 * @see FragmentTransaction#replace(int, Fragment, String)
	 * @see FragmentTransaction#addToBackStack(String)
	 */
	public void navigateTo(BaseFragment<?> fragment, boolean addToBackStack) {
		// Same class with a different "mode" argument (e.g. Music/Movies/Files tabs) is a different screen.
		String mode = fragment.getArguments() != null
			? fragment.getArguments().getString("mode", "") : "";
		String tag = fragment.getClass().getSimpleName() + (mode.isEmpty() ? "" : ":" + mode);
		Fragment currentFragment = fragmentManager.findFragmentById(containerId);
		if (currentFragment != null && tag.equals(currentFragment.getTag())) return;
		
		FragmentTransaction transaction = fragmentManager.beginTransaction();
		transaction.setCustomAnimations(com.nextgen.R.anim.anim_fade_enter, com.nextgen.R.anim.anim_fade_exit);
		transaction.replace(containerId, fragment, tag);
		if (addToBackStack) {
			transaction.addToBackStack(tag);
		}
		
		transaction.commit();
	}
}