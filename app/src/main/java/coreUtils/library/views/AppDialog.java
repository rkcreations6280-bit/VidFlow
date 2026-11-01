package coreUtils.library.views;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.nextgen.R;

/**
 * The app's own dialog: dark rounded card, orange primary button, same fonts as the rest of the
 * app. {@link Builder} has the same method names as AlertDialog.Builder, so it replaces it directly.
 */
public final class AppDialog extends Dialog {

	private final Builder b;

	private AppDialog(@NonNull Context context, Builder builder) {
		super(context);
		this.b = builder;
	}

	private static int dp(Context c, float v) {
		return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
	}

	private static int color(Context c, int res) {
		return ContextCompat.getColor(c, res);
	}

	private static Typeface font(Context c, int res) {
		try {
			return ResourcesCompat.getFont(c, res);
		} catch (Throwable e) {
			return null;
		}
	}

	private TextView text(CharSequence value, float sp, int colorRes, int fontRes) {
		TextView tv = new TextView(getContext());
		tv.setText(value);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		tv.setTextColor(color(getContext(), colorRes));
		Typeface face = font(getContext(), fontRes);
		if (face != null) tv.setTypeface(face);
		return tv;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Context c = getContext();

		LinearLayout card = new LinearLayout(c);
		card.setOrientation(LinearLayout.VERTICAL);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(color(c, R.color.style_color_card));
		bg.setCornerRadius(dp(c, 22));
		bg.setStroke(dp(c, 1), color(c, R.color.style_color_card_stroke));
		card.setBackground(bg);
		card.setPadding(dp(c, 22), dp(c, 22), dp(c, 22), dp(c, 14));

		if (b.title != null && b.title.length() > 0) {
			TextView title = text(b.title, 19, R.color.style_color_text_primary, R.font.font_family_semibold);
			card.addView(title, new LinearLayout.LayoutParams(-1, -2));
		}

		LinearLayout body = new LinearLayout(c);
		body.setOrientation(LinearLayout.VERTICAL);
		if (b.message != null && b.message.length() > 0) {
			TextView message = text(b.message, 14.5f, R.color.style_color_text_secondary, R.font.font_family_regular);
			message.setLineSpacing(0, 1.15f);
			message.setTextIsSelectable(false);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.topMargin = dp(c, 10);
			body.addView(message, lp);
		}
		if (b.items != null) {
			for (int i = 0; i < b.items.length; i++) {
				final int index = i;
				boolean checked = b.checked == i;
				TextView row = text(b.items[i], 16, checked ? R.color.style_color_primary
					: R.color.style_color_text_primary, checked ? R.font.font_family_semibold : R.font.font_family_regular);
				row.setGravity(Gravity.CENTER_VERTICAL);
				row.setMinHeight(dp(c, 48));
				row.setPadding(dp(c, 6), 0, dp(c, 6), 0);
				if (b.checked >= 0) row.setText((checked ? "●   " : "○   ") + b.items[i]);
				row.setBackground(pressed(c, 12));
				row.setOnClickListener(v -> {
					if (b.itemListener != null) b.itemListener.onClick(AppDialog.this, index);
					dismiss();
				});
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
				if (i == 0) lp.topMargin = dp(c, 8);
				body.addView(row, lp);
			}
		}
		if (b.view != null) {
			if (b.view.getParent() instanceof ViewGroup) ((ViewGroup) b.view.getParent()).removeView(b.view);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.topMargin = dp(c, 12);
			body.addView(b.view, lp);
		}
		ScrollView scroll = new ScrollView(c);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(body);
		card.addView(scroll, new LinearLayout.LayoutParams(-1, -2, 1f));

		LinearLayout buttons = new LinearLayout(c);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
		if (b.neutralText != null) {
			buttons.addView(button(b.neutralText, b.neutralListener, DialogInterface.BUTTON_NEUTRAL, false));
			View spacer = new View(c);
			buttons.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
		}
		if (b.negativeText != null) {
			buttons.addView(button(b.negativeText, b.negativeListener, DialogInterface.BUTTON_NEGATIVE, false));
		}
		if (b.positiveText != null) {
			buttons.addView(button(b.positiveText, b.positiveListener, DialogInterface.BUTTON_POSITIVE, true));
		}
		if (buttons.getChildCount() > 0) {
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
			lp.topMargin = dp(c, 14);
			card.addView(buttons, lp);
		} else {
			card.setPadding(dp(c, 22), dp(c, 22), dp(c, 22), dp(c, 18));
		}

		setContentView(card);
		setCancelable(b.cancelable);
		setCanceledOnTouchOutside(b.cancelable);
		if (b.cancelListener != null) setOnCancelListener(b.cancelListener);
		Window window = getWindow();
		if (window != null) {
			window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
			int width = Math.min(c.getResources().getDisplayMetrics().widthPixels - dp(c, 40), dp(c, 420));
			window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
			window.setDimAmount(0.6f);
		}
	}

	private static GradientDrawable pressed(Context c, int radius) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(Color.TRANSPARENT);
		g.setCornerRadius(dp(c, radius));
		return g;
	}

	private View button(CharSequence label, @Nullable DialogInterface.OnClickListener listener,
	                    int which, boolean primary) {
		Context c = getContext();
		TextView tv = text(label, 14.5f, primary ? R.color.style_color_button_primary_text
			: R.color.style_color_primary, R.font.font_family_semibold);
		tv.setGravity(Gravity.CENTER);
		tv.setMinHeight(dp(c, 42));
		tv.setPadding(dp(c, 18), 0, dp(c, 18), 0);
		GradientDrawable g = new GradientDrawable();
		g.setCornerRadius(dp(c, 21));
		if (primary) g.setColor(color(c, R.color.style_color_primary));
		else g.setColor(Color.TRANSPARENT);
		tv.setBackground(g);
		tv.setOnClickListener(v -> {
			if (listener != null) listener.onClick(AppDialog.this, which);
			dismiss();
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
		lp.leftMargin = dp(c, 6);
		tv.setLayoutParams(lp);
		return tv;
	}

	public static final class Builder {
		private final Context context;
		CharSequence title;
		CharSequence message;
		View view;
		CharSequence[] items;
		int checked = -1;
		DialogInterface.OnClickListener itemListener;
		CharSequence positiveText, negativeText, neutralText;
		DialogInterface.OnClickListener positiveListener, negativeListener, neutralListener;
		boolean cancelable = true;
		DialogInterface.OnCancelListener cancelListener;

		public Builder(@NonNull Context context) {
			this.context = context;
		}

		public Builder setTitle(CharSequence title) {
			this.title = title;
			return this;
		}

		public Builder setMessage(CharSequence message) {
			this.message = message;
			return this;
		}

		public Builder setView(View view) {
			this.view = view;
			return this;
		}

		public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener listener) {
			this.items = items;
			this.itemListener = listener;
			this.checked = -1;
			return this;
		}

		public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem,
		                                    DialogInterface.OnClickListener listener) {
			this.items = items;
			this.itemListener = listener;
			this.checked = checkedItem < 0 ? Integer.MAX_VALUE - 1 : checkedItem;
			return this;
		}

		public Builder setPositiveButton(CharSequence text, @Nullable DialogInterface.OnClickListener l) {
			positiveText = text;
			positiveListener = l;
			return this;
		}

		public Builder setNegativeButton(CharSequence text, @Nullable DialogInterface.OnClickListener l) {
			negativeText = text;
			negativeListener = l;
			return this;
		}

		public Builder setNeutralButton(CharSequence text, @Nullable DialogInterface.OnClickListener l) {
			neutralText = text;
			neutralListener = l;
			return this;
		}

		public Builder setCancelable(boolean cancelable) {
			this.cancelable = cancelable;
			return this;
		}

		public Builder setOnCancelListener(DialogInterface.OnCancelListener l) {
			this.cancelListener = l;
			return this;
		}

		public AppDialog create() {
			return new AppDialog(context, this);
		}

		public AppDialog show() {
			AppDialog dialog = create();
			try {
				dialog.show();
			} catch (Throwable ignored) {
			}
			return dialog;
		}
	}
}
