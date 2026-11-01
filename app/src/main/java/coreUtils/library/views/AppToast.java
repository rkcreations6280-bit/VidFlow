package coreUtils.library.views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.nextgen.R;

import java.lang.ref.WeakReference;

/**
 * In-app toast in the app's own style: a dark rounded card with an orange edge, shown at the
 * bottom of the current screen. It is drawn inside the activity, so it also works on Android 11+
 * where custom system toasts are ignored. Without an activity it falls back to a plain toast.
 */
public final class AppToast {
	private static WeakReference<View> current = new WeakReference<>(null);

	private AppToast() {}

	public static void show(Context context, CharSequence text) {
		show(context, text, false);
	}

	public static void show(Context context, CharSequence text, boolean longer) {
		if (context == null || text == null) return;
		Activity activity = find(context);
		if (activity == null || activity.isFinishing()) {
			try {
				Toast.makeText(context.getApplicationContext(), text,
					longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
			} catch (Throwable ignored) {
			}
			return;
		}
		final ViewGroup root = activity.findViewById(android.R.id.content);
		if (root == null) return;
		View old = current.get();
		if (old != null && old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);

		float d = activity.getResources().getDisplayMetrics().density;
		TextView view = new TextView(activity);
		view.setText(text);
		view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		view.setTextColor(ContextCompat.getColor(activity, R.color.style_color_text_primary));
		view.setMaxLines(4);
		view.setPadding((int) (18 * d), (int) (12 * d), (int) (18 * d), (int) (12 * d));
		try {
			Typeface face = ResourcesCompat.getFont(activity, R.font.font_family_medium);
			if (face != null) view.setTypeface(face);
		} catch (Throwable ignored) {
		}
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(ContextCompat.getColor(activity, R.color.style_color_card));
		bg.setCornerRadius(24 * d);
		bg.setStroke((int) (1 * d), ContextCompat.getColor(activity, R.color.style_color_primary));
		view.setBackground(bg);
		view.setElevation(8 * d);
		view.setClickable(true);

		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
			Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		lp.bottomMargin = (int) (96 * d);
		lp.leftMargin = (int) (24 * d);
		lp.rightMargin = (int) (24 * d);
		view.setAlpha(0f);
		root.addView(view, lp);
		current = new WeakReference<>(view);
		view.animate().alpha(1f).setDuration(160).start();
		view.postDelayed(() -> {
			if (view.getParent() == null) return;
			view.animate().alpha(0f).setDuration(200).withEndAction(() -> {
				if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
			}).start();
		}, longer ? 3500 : 2000);
	}

	private static Activity find(@NonNull Context context) {
		Context c = context;
		for (int i = 0; i < 8 && c != null; i++) {
			if (c instanceof Activity) return (Activity) c;
			if (!(c instanceof ContextWrapper)) return null;
			c = ((ContextWrapper) c).getBaseContext();
		}
		return null;
	}
}
