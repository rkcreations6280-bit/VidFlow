package coreUtils.library.views;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.BreakIterator;
import java.util.Locale;

import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;

/**
 * Utility class providing helper methods for advanced TextView manipulation
 * and text styling. This class offers functionality for applying gradient
 * spans to specific text ranges and normalizing non-Latin characters in
 * mixed-script text.
 *
 * <p><strong>Core features:</strong>
 * <ul>
 * <li>Apply gradient color spans to text ranges via
 *     {@link #applyGradientSpan(TextView, int, int, int, int)}.</li>
 * <li>Normalize tall non-Latin characters (e.g., Devanagari, Chinese, Arabic)
 *     relative to Latin text using
 *     {@link #normalizeTallSymbols(TextView, String, float, OnDoneListener)}.</li>
 * </ul>
 *
 * <p>All methods are static and operate on the provided TextView instances.
 * Background processing is used for normalization to avoid blocking the UI thread.
 *
 * @see GradientSpan
 * @see OnDoneListener
 * @see #applyGradientSpan(TextView, int, int, int, int)
 * @see #normalizeTallSymbols(TextView, String, float, OnDoneListener)
 */
public class TextViewsUtils {
	
	private static final LoggerUtils logger = LoggerUtils.from(TextViewsUtils.class);
	
	/**
	 * Callback interface for receiving the processed result of text normalization
	 * operations. Implement this interface to be notified when asynchronous text
	 * processing (e.g., {@link TextViewsUtils#normalizeTallSymbols}) has completed
	 * and the resulting {@link Spannable} is ready to be applied to a TextView.
	 *
	 * <p>The callback is invoked on the main thread after background processing
	 * finishes, making it safe to update UI components directly within the
	 * implementation.
	 * </p>
	 *
	 * @see TextViewsUtils#normalizeTallSymbols(TextView, String, float, OnDoneListener)
	 * @see Spannable
	 */
	public interface OnDoneListener {
		void onDone(Spannable spannable);
	}
	
	/**
	 * Normalizes non-Latin characters in a TextView by reducing their font size
	 * relative to Latin text. This method processes the original text asynchronously,
	 * iterating through Unicode grapheme clusters and applying a
	 * {@link RelativeSizeSpan} to characters that do not belong to the Latin script.
	 *
	 * <p><strong>Processing logic:</strong>
	 * <ul>
	 * <li>Uses {@link BreakIterator} to iterate through character clusters correctly.</li>
	 * <li>For each cluster, checks if it contains any non-space, non-Latin characters.</li>
	 * <li>If true, applies {@link RelativeSizeSpan} with the specified reduction factor.</li>
	 * <li>Executes on a background thread via {@link ThreadTask} to avoid blocking the UI.</li>
	 * <li>Only applies the result if the TextView text hasn't changed during processing.</li>
	 * </ul>
	 *
	 * <p>This is particularly useful for mixed-script text where non-Latin scripts
	 * (e.g., Devanagari, Chinese, Arabic) should appear smaller to maintain visual
	 * balance alongside Latin characters.
	 *
	 * @param textView        The TextView to modify. Must not be null.
	 * @param originalText    The original text to process (may differ from current TextView text).
	 * @param reductionFactor The factor to reduce non-Latin character size (e.g., 0.8f for 80%).
	 * @param onDone          Optional callback invoked when normalization is complete.
	 * @see RelativeSizeSpan
	 * @see BreakIterator
	 * @see Character.UnicodeScript
	 */
	public static void normalizeTallSymbols(@NonNull final TextView textView,
	                                        @NonNull final String originalText,
	                                        final float reductionFactor,
	                                        @Nullable final OnDoneListener onDone) {
		if (originalText.isEmpty()) return;
		new ThreadTask.Builder<Spannable, Void>()
			.withBackgroundTask(callback -> {
				final SpannableStringBuilder spannable =
					new SpannableStringBuilder(originalText);
				
				BreakIterator boundaryIterator =
					BreakIterator.getCharacterInstance(Locale.getDefault());
				boundaryIterator.setText(originalText);
				
				int currentStart = boundaryIterator.first();
				int currentEnd = boundaryIterator.next();
				
				while (currentEnd != BreakIterator.DONE) {
					String cluster = originalText.substring(currentStart, currentEnd);
					boolean shouldReduce = false;
					
					for (int index = 0; index < cluster.length(); index++) {
						char c = cluster.charAt(index);
						int codePoint = Character.codePointAt(cluster, index);
						
						if (!Character.isSpaceChar(c) &&
							Character.UnicodeScript.of(codePoint)
								!= Character.UnicodeScript.LATIN) {
							shouldReduce = true;
							break;
						}
					}
					
					if (shouldReduce) {
						try {
							spannable.setSpan(
								new RelativeSizeSpan(reductionFactor),
								currentStart,
								currentEnd,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
							);
						} catch (Exception error) {
							logger.error("Error normalizing textview: ", error);
						}
					}
					
					currentStart = currentEnd;
					currentEnd = boundaryIterator.next();
				}
				return spannable;
			})
			.withResultTask(spannable -> {
				if (textView.getText().toString().equals(originalText)) {
					if (onDone != null) {
						onDone.onDone(spannable);
					}
				}
			})
			.build()
			.start();
	}
	
	/**
	 * Applies a gradient color span to a specific range of text within a TextView.
	 * This method creates a {@link GradientSpan} that transitions from a start color
	 * to an end color across the specified character range. If the existing text is
	 * not a {@link Spannable}, it is wrapped in a {@link SpannableStringBuilder}.
	 *
	 * <p><strong>Usage example:</strong>
	 * <pre>
	 * TextViewsUtils.applyGradientSpan(titleView, Color.RED, Color.BLUE, 0, 5);
	 * </pre>
	 *
	 * <p>The span is applied with {@link Spannable#SPAN_EXCLUSIVE_EXCLUSIVE} flags,
	 * meaning the gradient does not extend when text is inserted at the boundaries.
	 *
	 * @param textView   The TextView containing the target text. Must not be null.
	 * @param startColor The starting color of the gradient (e.g., {@link Color#RED}).
	 * @param endColor   The ending color of the gradient (e.g., {@link Color#BLUE}).
	 * @param startIndex The starting index (inclusive) where the gradient begins.
	 * @param endIndex   The ending index (exclusive) where the gradient ends.
	 * @throws IndexOutOfBoundsException If startIndex or endIndex are out of bounds.
	 * @see GradientSpan
	 * @see Spannable
	 */
	public static void applyGradientSpan(@NonNull TextView textView, int startColor,
	                                     int endColor, int startIndex, int endIndex) {
		CharSequence text = textView.getText();
		if (text == null || text.length() == 0) return;
		if (!(text instanceof Spannable)) text = new SpannableStringBuilder(text);
		
		Spannable spannable = (Spannable) text;
		int exclusive = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
		GradientSpan gradientSpan = new GradientSpan(startColor, endColor);
		spannable.setSpan(gradientSpan, startIndex, endIndex, exclusive);
		textView.setText(spannable);
	}
	
	/**
	 * Custom {@link ReplacementSpan} that draws text with a linear gradient spanning
	 * from a start color to an end color across the width of the text. This span
	 * measures the exact width of the target text using the provided paint and draws
	 * the text with a {@link LinearGradient} shader applied.
	 *
	 * <p>The gradient spans horizontally across the text bounds, from the start
	 * X coordinate to {@code startX + textWidth}, creating a smooth color transition.
	 * After drawing, the shader is cleared from the paint to avoid affecting other
	 * text spans or subsequent drawing operations.
	 *
	 * @see ReplacementSpan
	 * @see LinearGradient
	 * @see TextViewsUtils#applyGradientSpan(TextView, int, int, int, int)
	 */
	private static class GradientSpan extends ReplacementSpan {
		private final int startColor;
		private final int endColor;
		
		GradientSpan(int startColor, int endColor) {
			this.startColor = startColor;
			this.endColor = endColor;
		}
		
		@Override
		public int getSize(@NonNull Paint paint, CharSequence text,
		                   int start, int end, @Nullable Paint.FontMetricsInt fm) {
			return (int) paint.measureText(text, start, end);
		}
		
		@Override
		public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
		                 float x, int top, int y, int bottom, @NonNull Paint paint) {
			float textWidth = paint.measureText(text, start, end);
			Shader shader = new LinearGradient(x, top, x + textWidth, top,
				startColor, endColor, Shader.TileMode.CLAMP);
			paint.setShader(shader);
			canvas.drawText(text, start, end, x, bottom - paint.descent(), paint);
			paint.setShader(null);
		}
	}
}
