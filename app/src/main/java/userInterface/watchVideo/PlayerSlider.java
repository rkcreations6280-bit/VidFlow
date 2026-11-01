package userInterface.watchVideo;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.nextgen.R;

import java.util.Locale;

/**
 * Vertical pill slider for the player (brightness and volume). Drag to change, shows the value on
 * top and an icon at the bottom. Drawn in the app's orange on a dark glass track.
 */
public class PlayerSlider extends View {

	public interface Listener {
		void onChange(float value);

		void onTouch(boolean down);
	}

	private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private final Path clip = new Path();
	private Drawable icon;
	private float value = 0.5f;
	private Listener listener;
	private final float density;
	private boolean rotateIcon = false;
	private float pulse = 1f;
	private ValueAnimator pulseAnimator;

	public PlayerSlider(Context context) {
		this(context, null);
	}

	public PlayerSlider(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		density = context.getResources().getDisplayMetrics().density;
		track.setColor(0xD9121212);
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeWidth(density);
		stroke.setColor(0x24FFFFFF);
		fill.setColor(ContextCompat.getColor(context, R.color.style_color_primary));
		text.setTextAlign(Paint.Align.CENTER);
		text.setTextSize(11 * density);
		try {
			Typeface face = ResourcesCompat.getFont(context, R.font.font_family_semibold);
			if (face != null) text.setTypeface(face);
		} catch (Throwable ignored) {
		}
	}

	public void setIcon(@DrawableRes int res) {
		Drawable d = ContextCompat.getDrawable(getContext(), res);
		icon = d == null ? null : d.mutate();
		invalidate();
	}

	/** The icon turns with the value (the sun for brightness). */
	public void setRotateIcon(boolean rotate) {
		rotateIcon = rotate;
		invalidate();
	}

	/** A small pop of the icon, used whenever the value changes. */
	private void bump() {
		if (pulseAnimator != null && pulseAnimator.isRunning()) return;
		pulseAnimator = ValueAnimator.ofFloat(1f, 1.3f, 1f);
		pulseAnimator.setDuration(260);
		pulseAnimator.setInterpolator(new OvershootInterpolator(1.6f));
		pulseAnimator.addUpdateListener(a -> {
			pulse = (float) a.getAnimatedValue();
			invalidate();
		});
		pulseAnimator.start();
	}

	/** Swap the icon with a pop (mute / low / high volume). */
	public void swapIcon(@DrawableRes int res) {
		setIcon(res);
		pulseAnimator = null;
		bump();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	/** Moves the slider without notifying the listener. */
	public void setValue(float v) {
		float clamped = Math.max(0f, Math.min(1f, v));
		if (clamped != value) {
			value = clamped;
			bump();
			invalidate();
		}
	}

	public float getValue() {
		return value;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float w = getWidth();
		float h = getHeight();
		float r = w / 2f;
		rect.set(density, density, w - density, h - density);
		canvas.drawRoundRect(rect, r, r, track);

		canvas.save();
		clip.reset();
		clip.addRoundRect(rect, r, r, Path.Direction.CW);
		canvas.clipPath(clip);
		float top = rect.bottom - (rect.height() * value);
		canvas.drawRect(rect.left, top, rect.right, rect.bottom, fill);
		canvas.restore();
		canvas.drawRoundRect(rect, r, r, stroke);

		// value on top
		float labelY = 24 * density;
		text.setColor(top < labelY - 6 * density ? 0xFF2A1600 : 0xFFFFFFFF);
		canvas.drawText(String.format(Locale.US, "%d", Math.round(value * 100)), w / 2f,
			labelY, text);

		// icon at the bottom
		if (icon != null) {
			int size = (int) (20 * density);
			int left = (int) ((w - size) / 2f);
			int iconTop = (int) (h - size - 12 * density);
			icon.setBounds(left, iconTop, left + size, iconTop + size);
			boolean covered = top < iconTop;
			icon.setColorFilter(new PorterDuffColorFilter(covered ? 0xFF2A1600 : 0xFFFFFFFF,
				PorterDuff.Mode.SRC_IN));
			canvas.save();
			canvas.translate(left + size / 2f, iconTop + size / 2f);
			canvas.rotate(rotateIcon ? value * 180f : 0f);
			canvas.scale(pulse, pulse);
			canvas.translate(-(left + size / 2f), -(iconTop + size / 2f));
			icon.draw(canvas);
			canvas.restore();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
				if (listener != null) listener.onTouch(true);
				update(event);
				return true;
			case MotionEvent.ACTION_MOVE:
				update(event);
				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (listener != null) listener.onTouch(false);
				return true;
			default:
				return super.onTouchEvent(event);
		}
	}

	private void update(MotionEvent event) {
		float pad = 8 * density;
		float usable = Math.max(1f, getHeight() - 2 * pad);
		float v = 1f - (event.getY() - pad) / usable;
		float next = Math.max(0f, Math.min(1f, v));
		if (Math.abs(next - value) > 0.004f) bump();
		value = next;
		invalidate();
		if (listener != null) listener.onChange(value);
	}
}
