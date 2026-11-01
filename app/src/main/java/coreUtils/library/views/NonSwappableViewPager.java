package coreUtils.library.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

/**
 * A ViewPager implementation that allows runtime toggling of swipe/paging functionality.
 * <p>
 * This custom ViewPager extends the standard Android ViewPager and adds the ability
 * to enable or disable user-initiated page swiping at runtime. When paging is disabled,
 * users cannot swipe horizontally to navigate between pages, making the ViewPager act
 * like a static container that can only be changed programmatically.
 * </p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 * <li>Toggle swipe navigation dynamically via {@link #setPagingEnabled(boolean)}.</li>
 * <li>Retains full standard ViewPager functionality when enabled.</li>
 * <li>Correctly dispatches click events for accessibility.</li>
 * <li>Ideal for controlled flows like onboarding, tutorials, or forms.</li>
 * </ul>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * NonSwappableViewPager viewPager = findViewById(R.id.viewPager);
 *
 * // Disable user swiping (programmatic navigation only)
 * viewPager.setPagingEnabled(false);
 *
 * // Navigate programmatically
 * viewPager.setCurrentItem(2, true);
 *
 * // Re-enable swiping when appropriate
 * viewPager.setPagingEnabled(true);
 * </pre>
 * </p>
 *
 * <p><b>Behavior Details:</b>
 * <ul>
 * <li>Disabled: {@link #onTouchEvent} returns false, blocking touch input.</li>
 * <li>Enabled: Delegated to superclass for native paging behavior.</li>
 * <li>Accessibility: Click events are always dispatched regardless of state.</li>
 * </ul>
 * </p>
 *
 * @see ViewPager
 * @see #setPagingEnabled(boolean)
 * @see #isPagingEnabled()
 */
public class NonSwappableViewPager extends ViewPager {
	private boolean isPagingEnabled = true;
	
	/**
	 * Constructs a new NonSwappableViewPager with default attributes.
	 * <p>
	 * This constructor creates a ViewPager that can have its swipe/paging functionality
	 * disabled via {@link #setPagingEnabled(boolean)}. The paging feature is enabled
	 * by default.
	 * </p>
	 *
	 * @param context the application context used to access resources and themes
	 */
	public NonSwappableViewPager(@NonNull Context context) {
		super(context);
	}
	
	/**
	 * Constructs a new NonSwappableViewPager with custom XML attributes.
	 * <p>
	 * This constructor creates a ViewPager that can have its swipe/paging functionality
	 * disabled via {@link #setPagingEnabled(boolean)}. The paging feature is enabled
	 * by default. XML attributes are passed to the superclass for standard ViewPager
	 * configuration.
	 * </p>
	 *
	 * @param context the application context used to access resources and themes
	 * @param attrs   an XML attribute set containing custom styling attributes
	 */
	public NonSwappableViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}
	
	/**
	 * Handles touch events on the ViewPager with optional paging enabled.
	 * <p>
	 * This method processes touch events and only allows scrolling/paging when
	 * paging is enabled via {@link #setPagingEnabled(boolean)}. When paging is
	 * disabled, touch events are ignored, preventing users from swiping between
	 * pages. Additionally, when the touch event is an ACTION_UP (finger lift),
	 * {@link #performClick()} is called to ensure accessibility click events
	 * are properly dispatched.
	 * </p>
	 *
	 * @param event the motion event to process
	 * @return true if the touch event was handled, false otherwise
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_UP) {
			performClick();
		}
		return isPagingEnabled && super.onTouchEvent(event);
	}
	
	/**
	 * Performs a click on this view and returns the result.
	 * <p>
	 * This method delegates to the superclass implementation to handle accessibility
	 * click events and standard click interactions. It is typically called automatically
	 * when accessibility services trigger a click on this view.
	 * </p>
	 *
	 * @return true if the click was handled, false otherwise
	 */
	@Override
	public boolean performClick() {
		return super.performClick();
	}
	
	/**
	 * Intercepts touch events before they are dispatched to child views.
	 * <p>
	 * This method controls whether paging (swipe to change pages) is enabled on this
	 * view. If paging is disabled, the method returns false, preventing the view
	 * from intercepting touch events for paging purposes. If paging is enabled,
	 * touch events are delegated to the superclass for normal paging behavior.
	 * </p>
	 *
	 * @param event the motion event being intercepted
	 * @return true if the touch event should be intercepted, false otherwise
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		return isPagingEnabled && super.onInterceptTouchEvent(event);
	}
	
	/**
	 * Enables or disables paging (swipe to change pages) functionality.
	 * <p>
	 * When paging is enabled, users can swipe horizontally to navigate between
	 * pages. When disabled, swipe gestures are ignored and touch events are
	 * passed through to child views or handled by other touch logic.
	 * </p>
	 *
	 * @param enabled true to enable paging, false to disable it
	 */
	public void setPagingEnabled(boolean enabled) {
		this.isPagingEnabled = enabled;
	}
	
	/**
	 * Returns whether paging (swipe to change pages) is currently enabled.
	 * <p>
	 * This method indicates if horizontal swipe gestures will trigger page
	 * navigation or if they will be ignored.
	 * </p>
	 *
	 * @return true if paging is enabled, false otherwise
	 */
	public boolean isPagingEnabled() {
		return isPagingEnabled;
	}
}
