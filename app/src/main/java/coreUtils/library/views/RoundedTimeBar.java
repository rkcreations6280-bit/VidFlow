package coreUtils.library.views;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.applyDimension;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;

import com.google.android.exoplayer2.ui.DefaultTimeBar;

/**
 * A custom time bar with rounded corners that extends ExoPlayer's DefaultTimeBar.
 * <p>
 * This view provides a seek bar / progress bar for media playback with visually
 * appealing rounded corners on all four edges. It is designed for use with
 * ExoPlayer's time bar functionality while adding aesthetic rounded corner
 * clipping to match modern UI design patterns.
 * </p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Rounded corners with 10dp radius (converted to pixels based on screen density)</li>
 *   <li>Uses path clipping to round all four corners of the progress bar</li>
 *   <li>Maintains all original DefaultTimeBar functionality (buffering, progress, scrubbing)</li>
 *   <li>Corner radius automatically adapts to different screen densities</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * // In layout XML
 * &lt;com.nextgen.coreUtils.library.views.RoundedTimeBar
 *     android:id="@+id/timeBar"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" /&gt;
 *
 * // In code
 * RoundedTimeBar timeBar = findViewById(R.id.timeBar);
 * timeBar.setDuration(90000); // 90 seconds
 * timeBar.setPosition(45000); // 45 seconds position
 * timeBar.setBufferedPosition(60000); // 60 seconds buffered
 * </pre>
 * </p>
 *
 * <p><b>Clipping Mechanism:</b>
 * The view overrides {@link #onSizeChanged} to create a rounded rectangle clip path
 * that matches the view's bounds. In {@link #onDraw}, the canvas is clipped with
 * this path before drawing the superclass content, resulting in perfectly rounded corners.
 * </p>
 *
 * <p><b>Performance Note:</b>
 * Path clipping may have a minor performance impact compared to standard rectangular
 * drawing. However, this is negligible for a single time bar element in a typical UI.
 * </p>
 *
 * @see DefaultTimeBar
 * @see Path
 * @see Canvas#clipPath(Path)
 */
public class RoundedTimeBar extends DefaultTimeBar {
	
	private final Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path clipPath = new Path();
	private final RectF rectF = new RectF();
	private final float cornerRadius;
	
	/**
	 * Constructs a new RoundedTimeBar with the specified context and attributes.
	 * <p>
	 * This constructor initializes the corner radius to 10dp, which is converted to pixels
	 * based on the current screen density. The rounded corners will be applied to all
	 * four corners of the time bar.
	 * </p>
	 *
	 * @param context the application context used to access resources and display metrics
	 * @param attrs   an XML attribute set containing custom styling attributes
	 */
	public RoundedTimeBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
		cornerRadius = applyDimension(COMPLEX_UNIT_DIP, 10, displayMetrics);
	}
	
	/**
	 * Called when the size of the view changes.
	 * <p>
	 * This method updates the rectangle bounds and recreates the clip path whenever
	 * the view's dimensions change (e.g., during layout or orientation changes).
	 * The clip path defines the rounded rectangle shape that will be used to clip
	 * the canvas during drawing.
	 * </p>
	 *
	 * @param w    the current width of the view
	 * @param h    the current height of the view
	 * @param oldw the previous width of the view
	 * @param oldh the previous height of the view
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		rectF.set(0, 0, w, h);
		clipPath.reset();
		clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
	}
	
	/**
	 * Draws the rounded time bar with clipped corners.
	 * <p>
	 * This method applies a clip path to the canvas before calling the superclass
	 * draw method, ensuring that the time bar's content (including the progress
	 * indicator and thumb) is confined to the rounded rectangle shape. The clip
	 * path is restored after drawing to prevent affecting other drawing operations.
	 * </p>
	 *
	 * @param canvas the canvas on which the view will draw itself
	 */
	@Override
	public void onDraw(Canvas canvas) {
		canvas.save();
		canvas.clipPath(clipPath);
		super.onDraw(canvas);
		canvas.restore();
	}
	
	/**
	 * Returns the Paint object used for drawing the rounded corners.
	 * <p>
	 * This method provides access to the round paint, which can be used to customize
	 * the appearance of the rounded corners such as changing the color, style, or
	 * stroke width if additional customization is needed.
	 * </p>
	 *
	 * @return the Paint instance used for rounded corner rendering
	 */
	public Paint getRoundPaint() {
		return roundPaint;
	}
}