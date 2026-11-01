package coreUtils.library.views;

import android.content.Context;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Helpers that make the phone layouts behave on tablets and in landscape: content sits in a
 * centred column of limited width, bottom sheets do not stretch across the whole screen.
 */
public final class WideLayout {

	private WideLayout() {}

	public static int dp(Context c, int v) {
		return Math.round(v * c.getResources().getDisplayMetrics().density);
	}

	/** True for tablets and for any screen wider than 600dp (landscape phones included). */
	public static boolean isWide(Context c) {
		return c.getResources().getConfiguration().screenWidthDp >= 600;
	}

	/** Keeps the content of {@code root} in a centred column of at most {@code maxDp}. */
	public static void centerColumn(final View root, final int maxDp) {
		final int baseLeft = root.getPaddingLeft();
		final int baseRight = root.getPaddingRight();
		root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
			int width = r - l;
			int max = dp(v.getContext(), maxDp);
			int side = width > max ? (width - max) / 2 : 0;
			int left = baseLeft + side;
			int right = baseRight + side;
			if (v.getPaddingLeft() != left || v.getPaddingRight() != right) {
				v.setPadding(left, v.getPaddingTop(), right, v.getPaddingBottom());
			}
		});
	}

	/** A bottom sheet that stays 560dp wide at most and opens fully, also in landscape. */
	public static BottomSheetDialog sheet(BottomSheetDialog dialog) {
		try {
			BottomSheetBehavior<android.widget.FrameLayout> behavior = dialog.getBehavior();
			behavior.setMaxWidth(dp(dialog.getContext(), 560));
			behavior.setSkipCollapsed(true);
			behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
		} catch (Throwable ignored) {
		}
		return dialog;
	}
}
