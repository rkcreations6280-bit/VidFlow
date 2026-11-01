package userInterface.watchVideo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.nextgen.R;

import java.util.List;
import java.util.function.IntConsumer;

/** Themed list picker used by the player (speed, audio, subtitles, quality, more). */
final class PlayerSheets {

	private PlayerSheets() {}

	static void choose(Context ctx, String title, List<String> labels, int selected,
	                   IntConsumer onPick) {
		final BottomSheetDialog dialog = coreUtils.library.views.WideLayout.sheet(new BottomSheetDialog(ctx));
		Typeface semibold = font(ctx, R.font.font_family_semibold);
		Typeface regular = font(ctx, R.font.font_family_regular);

		LinearLayout sheet = new LinearLayout(ctx);
		sheet.setOrientation(LinearLayout.VERTICAL);
		sheet.setBackgroundResource(R.drawable.pl_bg_sheet);
		sheet.setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 18));

		View handle = new View(ctx);
		handle.setBackgroundResource(R.drawable.pl_bg_handle);
		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 5));
		hp.gravity = Gravity.CENTER_HORIZONTAL;
		hp.bottomMargin = dp(ctx, 6);
		sheet.addView(handle, hp);

		TextView heading = new TextView(ctx);
		heading.setText(title);
		heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		if (semibold != null) heading.setTypeface(semibold);
		heading.setTextColor(ContextCompat.getColor(ctx, R.color.style_color_text_primary));
		heading.setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8));
		sheet.addView(heading);

		LinearLayout list = new LinearLayout(ctx);
		list.setOrientation(LinearLayout.VERTICAL);
		for (int i = 0; i < labels.size(); i++) {
			final int index = i;
			boolean on = i == selected;
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.setMinimumHeight(dp(ctx, 52));
			row.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
			if (on) {
				row.setBackgroundResource(R.drawable.pl_bg_row_selected);
			} else {
				TypedValue out = new TypedValue();
				ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
				row.setBackgroundResource(out.resourceId);
			}

			TextView label = new TextView(ctx);
			label.setText(labels.get(i));
			label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
			label.setTextColor(on ? ContextCompat.getColor(ctx, R.color.style_color_primary)
				: ContextCompat.getColor(ctx, R.color.style_color_text_primary));
			Typeface face = on ? semibold : regular;
			if (face != null) label.setTypeface(face);
			row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

			if (on) {
				ImageView check = new ImageView(ctx);
				check.setImageResource(R.drawable.pl_ic_check);
				check.setColorFilter(ContextCompat.getColor(ctx, R.color.style_color_primary));
				row.addView(check, new LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22)));
			}
			row.setOnClickListener((View v) -> {
				dialog.dismiss();
				onPick.accept(index);
			});
			list.addView(row, new LinearLayout.LayoutParams(-1, -2));
		}
		ScrollView scroll = new ScrollView(ctx);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(list);
		sheet.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

		dialog.setContentView(sheet);
		dialog.setOnShowListener(d -> {
			FrameLayout container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
			if (container != null) {
				container.setBackgroundColor(Color.TRANSPARENT);
				BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(container);
				behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
				behavior.setSkipCollapsed(true);
				behavior.setMaxWidth(dp(ctx, 560));
			}
		});
		dialog.show();
	}

	private static Typeface font(Context ctx, int res) {
		try {
			return ResourcesCompat.getFont(ctx, res);
		} catch (Throwable e) {
			return null;
		}
	}

	private static int dp(Context ctx, int value) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
			ctx.getResources().getDisplayMetrics());
	}
}
