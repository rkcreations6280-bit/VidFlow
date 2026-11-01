package coreUtils.library.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;

import com.nextgen.R;

import coreUtils.base.BaseActivity;

/**
 * Provides a centralized, null-safe API for applying standard transition animations
 * to {@link BaseActivity} instances.
 * <p>
 * This utility class encapsulates common activity transition animations including
 * directional slides (left, right, up, down), swipe gestures, fade effects, and
 * scaling transitions. All public methods perform a null check on the provided
 * activity reference before invoking
 * {@link Activity#overridePendingTransition(int, int)}, silently ignoring any
 * {@code null} input to prevent {@link NullPointerException} crashes.
 * </p>
 * <ul>
 * <li>All methods are static and can be invoked without an instance</li>
 * <li>The class is declared {@code final} to prevent subclassing</li>
 * <li>Animation resources must be defined in {@code res/anim/}</li>
 * <li>Each method corresponds to a semantically named animation pair</li>
 * </ul>
 *
 * @see BaseActivity
 * @see Activity#overridePendingTransition(int, int)
 */
public final class ActivityAnimator {
	
	private ActivityAnimator() {}
	
	/**
	 * Applies a fade transition animation to the given activity.
	 * <p>
	 * This method uses {@link Activity#overridePendingTransition(int, int)} to
	 * create a smooth cross-fade effect between activities. The new activity
	 * gradually fades in while the current activity simultaneously fades out.
	 * This subtle transition is well-suited for overlay dialogs or context
	 * switches where directional movement would be distracting. The operation
	 * is safely ignored when the activity reference is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_fade_enter} (alpha 0 → 1)</li>
	 * <li>Exit animation: {@code R.anim.anim_fade_exit} (alpha 1 → 0)</li>
	 * <li>No effect when {@code activity} is {@code null}</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 perform no operation
	 */
	public static void animActivityFade(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_fade_enter,
				R.anim.anim_fade_exit
			);
		}
	}
	
	/**
	 * Applies an "in-and-out" scaling transition animation to the given activity.
	 * <p>
	 * This method triggers a compound animation where the new activity scales
	 * inward (typically from a smaller size to full size) while the current
	 * activity scales outward and diminishes. This effect is visually prominent
	 * and often used for modal presentations or zoom-style transitions between
	 * related content screens. The call is silently ignored if the provided
	 * activity reference is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_in_out_enter} (scale up)</li>
	 * <li>Exit animation: {@code R.anim.anim_in_out_exit} (scale down)</li>
	 * <li>Idempotent {@code null} handling prevents crashes</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 safely no-op
	 */
	public static void animActivityInAndOut(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_in_out_enter,
				R.anim.anim_in_out_exit
			);
		}
	}
	
	/**
	 * Applies a downward slide transition animation to the given activity.
	 * <p>
	 * This method configures a vertical slide animation where the new activity
	 * descends from above the screen while the current activity simultaneously
	 * slides downward and exits. This direction is commonly used for returning
	 * from a child activity to a parent, or for revealing content that sits
	 * logically above the current screen in the navigation hierarchy. The
	 * operation has no effect when the provided activity is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_slide_down_enter}</li>
	 * <li>Exit animation: {@code R.anim.anim_slide_down_exit}</li>
	 * <li>Requires custom animations defined in {@code res/anim/}</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 perform no operation
	 */
	public static void animActivitySlideDown(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_slide_down_enter,
				R.anim.anim_slide_down_exit
			);
		}
	}
	
	/**
	 * Applies a leftward slide transition animation to the given activity.
	 * <p>
	 * This method invokes {@link Activity#overridePendingTransition(int, int)} to
	 * animate the activity transition with content sliding horizontally toward the
	 * left. The new activity enters from the right while the current activity exits
	 * toward the left. The operation is safely ignored when the provided activity
	 * reference is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_slide_left_enter}</li>
	 * <li>Exit animation: {@code R.anim.anim_slide_left_exit}</li>
	 * <li>No effect when {@code activity} is {@code null}</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 perform no operation
	 */
	public static void animActivitySlideLeft(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_slide_left_enter,
				R.anim.anim_slide_left_exit
			);
		}
	}
	
	/**
	 * Applies a rightward swipe transition animation to the given activity.
	 * <p>
	 * This method triggers a swipe-based transition where the new content enters
	 * from the right side and the old content exits toward the right. This
	 * animation is commonly used for backward navigation or dismiss gestures.
	 * The call is silently ignored if the activity reference is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_swipe_right_enter}</li>
	 * <li>Exit animation: {@code R.anim.anim_swipe_right_exit}</li>
	 * <li>Idempotent {@code null} handling prevents {@link NullPointerException}</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 safely no-op
	 */
	public static void animActivitySwipeRight(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_swipe_right_enter,
				R.anim.anim_swipe_right_exit
			);
		}
	}
	
	/**
	 * Applies an upward slide transition animation to the given activity.
	 * <p>
	 * This method configures a vertical slide animation where the new activity
	 * rises upward from the bottom edge to cover the screen, while the current
	 * activity simultaneously slides upward and out of view. This is frequently
	 * used for modal or bottom-sheet style transitions. The operation has no
	 * effect when the provided activity is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_slide_up_enter}</li>
	 * <li>Exit animation: {@code R.anim.anim_slide_up_exit}</li>
	 * <li>Requires custom animations defined in {@code res/anim/}</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 perform no operation
	 */
	public static void animActivitySlideUp(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_slide_up_enter,
				R.anim.anim_slide_up_exit
			);
		}
	}
	
	/**
	 * Applies a left-to-right swipe transition animation to the given activity.
	 * <p>
	 * This method invokes {@link Activity#overridePendingTransition(int, int)} on the
	 * provided activity instance, using custom animation resources that simulate a
	 * horizontal swipe gesture entering from the left and exiting to the right.
	 * The operation is silently ignored if the activity reference is {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_swipe_left_enter}</li>
	 * <li>Exit animation: {@code R.anim.anim_swipe_left_exit}</li>
	 * <li>No effect when {@code activity} is {@code null}</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 safely no-op
	 */
	public static void animActivitySwipeLeft(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_swipe_left_enter,
				R.anim.anim_swipe_left_exit
			);
		}
	}
	
	/**
	 * Applies a right-to-left slide transition animation to the given activity.
	 * <p>
	 * This method calls {@link Activity#overridePendingTransition(int, int)} with
	 * sliding animations where the new content enters from the left and the current
	 * content exits to the right. This is commonly used for forward navigation
	 * transitions. The call is safely ignored if the activity reference is
	 * {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code R.anim.anim_slide_in_left}</li>
	 * <li>Exit animation: {@code R.anim.anim_slide_out_right}</li>
	 * <li>Idempotent {@code null} handling prevents crashes</li>
	 * </ul>
	 *
	 * @param activity the target {@link BaseActivity} instance, or {@code null} to
	 *                 perform no operation
	 */
	public static void animActivitySlideRight(@Nullable BaseActivity<?> activity) {
		if (activity != null) {
			activity.overridePendingTransition(
				R.anim.anim_slide_in_left,
				R.anim.anim_slide_out_right
			);
		}
	}
	
	/**
	 * Creates a material-style slide transition options bundle for the given activity.
	 * <p>
	 * This factory method constructs an {@link ActivityOptionsCompat} instance using
	 * {@link ActivityOptionsCompat#makeCustomAnimation(Context, int, int)} with
	 * Android's built-in slide animations. The resulting options can be passed to
	 * {@link android.app.Activity#startActivity(Intent, Bundle)} or similar
	 * transition-aware methods. Returns {@code null} if the provided activity is
	 * {@code null}.
	 * </p>
	 * <ul>
	 * <li>Enter animation: {@code android.R.anim.slide_in_left}</li>
	 * <li>Exit animation: {@code android.R.anim.slide_out_right}</li>
	 * <li>Uses platform animations, no custom resource dependency</li>
	 * </ul>
	 *
	 * @param activity the source {@link BaseActivity} used as the animation context,
	 *                 may be {@code null}
	 * @return an {@link ActivityOptionsCompat} configured with slide animations, or
	 * {@code null} if {@code activity} is {@code null}
	 */
	@Nullable
	public static ActivityOptionsCompat getMaterialSlideOptions(@Nullable BaseActivity<?> activity) {
		if (activity == null) return null;
		return ActivityOptionsCompat.makeCustomAnimation(
			activity,
			android.R.anim.slide_in_left,
			android.R.anim.slide_out_right
		);
	}
}