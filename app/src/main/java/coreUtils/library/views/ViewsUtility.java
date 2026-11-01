package coreUtils.library.views;

import android.view.View;

import coreUtils.library.process.LoggerUtils;

public class ViewsUtility {
	private static final LoggerUtils logger = LoggerUtils.from(TextViewsUtils.class);
	
	private ViewsUtility() {}
	
	/**
	 * Hides a view by setting its visibility to GONE without animation.
	 * <p>
	 * This convenience method immediately hides the target view without any fade effect.
	 * The view will no longer occupy any space in the layout. This is the simplest
	 * way to hide a view when animation is not required.
	 * </p>
	 *
	 * @param targetView the view to hide (does nothing if null)
	 */
	public static void hideView(View targetView) {
		hideView(targetView, View.GONE, false, 500);
	}
	
	/**
	 * Hides a view with optional fade-out animation and custom duration.
	 * <p>
	 * This method hides the view by setting its visibility to GONE. If animation is enabled,
	 * the view fades out over the specified duration before being hidden. The animation
	 * timeout determines how long the fade effect takes. Any existing animation on the
	 * target view is canceled before starting a new one.
	 * </p>
	 *
	 * @param targetView    the view to hide (does nothing if null)
	 * @param shouldAnimate true to fade out the view, false to hide instantly
	 * @param animTimeout   the duration of the fade animation in milliseconds
	 */
	public static void hideView(View targetView, boolean shouldAnimate,
	                            long animTimeout) {
		hideView(targetView, View.GONE, shouldAnimate, animTimeout);
	}
	
	/**
	 * Shows a view instantly without animation.
	 * <p>
	 * This convenience method immediately makes the target view visible without any
	 * fade effect. The view will reappear in its original position in the layout
	 * and occupy space as defined by its layout parameters.
	 * </p>
	 *
	 * @param targetView the view to show (does nothing if null)
	 */
	public static void showView(View targetView) {
		showView(targetView, false, 500);
	}
	
	/**
	 * Hides a view with configurable target visibility and optional fade-out animation.
	 * <p>
	 * This method allows specifying the final visibility state (e.g., GONE or INVISIBLE).
	 * If animation is enabled, the view fades out over the specified duration before
	 * having its visibility changed. The original alpha value is restored after the
	 * animation completes to ensure the view is fully opaque if shown again later.
	 * </p>
	 *
	 * @param targetView    the view to hide (does nothing if null or already hidden)
	 * @param visibility    the target visibility (View.GONE or View.INVISIBLE)
	 * @param shouldAnimate true to fade out the view, false to hide instantly
	 * @param animTimeout   the duration of the fade animation in milliseconds
	 */
	public static void hideView(View targetView, int visibility,
	                            boolean shouldAnimate, long animTimeout) {
		if (targetView == null) return;
		if (targetView.getVisibility() != View.VISIBLE) return;
		
		targetView.animate().cancel();
		if (shouldAnimate) {
			targetView.animate()
				.alpha(0f)
				.setDuration(animTimeout)
				.withEndAction(() -> {
					targetView.setVisibility(visibility);
					targetView.setAlpha(1f);
				})
				.start();
		} else {
			targetView.setVisibility(visibility);
		}
	}
	
	/**
	 * Shows a view with optional fade-in animation.
	 * <p>
	 * This method displays a hidden view. If animation is enabled, the view fades in
	 * over the specified duration. The view is first made visible with alpha 0,
	 * then animated to full opacity. Any existing animation on the target view is
	 * canceled before starting the new animation.
	 * </p>
	 *
	 * @param targetView    the view to show (does nothing if null or already visible)
	 * @param shouldAnimate true to fade in the view, false to show instantly
	 * @param animTimeout   the duration of the fade animation in milliseconds
	 */
	public static void showView(View targetView, boolean shouldAnimate,
	                            long animTimeout) {
		if (targetView == null) return;
		if (targetView.getVisibility() == View.VISIBLE) return;
		targetView.animate().cancel();
		
		if (shouldAnimate) {
			targetView.setAlpha(0f);
			targetView.setVisibility(View.VISIBLE);
			
			targetView.animate()
				.alpha(1f)
				.setDuration(animTimeout)
				.start();
		} else {
			targetView.setVisibility(View.VISIBLE);
		}
	}
}
