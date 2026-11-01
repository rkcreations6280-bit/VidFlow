package coreUtils.library.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nextgen.R;

/**
 * A custom view that displays a vertical progress bar with rounded corners.
 * <p>
 * This view renders a vertical progress bar that fills from the bottom upward,
 * making it suitable for displaying vertical metrics such as volume levels,
 * signal strength, battery levels, or any data that benefits from vertical
 * orientation. The progress bar features rounded corners on both the background
 * and progress fill, providing a modern, polished appearance.
 * </p>
 *
 * <p><b>Visual Characteristics:</b>
 * <ul>
 *   <li>Fixed size: 10dp width × 200dp height (configurable via onMeasure)</li>
 *   <li>Rounded corners: 10dp radius on all corners</li>
 *   <li>Background color: color_primary (from resources)</li>
 *   <li>Progress color: color_secondary (from resources)</li>
 *   <li>Antialiasing: Enabled for smooth edges</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * // In layout XML
 * &lt;com.nextgen.coreUtils.library.views.VerticalProgressBar
 *     android:id="@+id/verticalProgressBar"
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content" /&gt;
 *
 * // In code
 * VerticalProgressBar progressBar = findViewById(R.id.verticalProgressBar);
 * progressBar.setMax(100);
 * progressBar.setProgress(65);
 * </pre>
 * </p>
 *
 * <p><b>Thread Safety:</b>
 * This view must be accessed from the UI thread only, as all drawing and
 * invalidation operations are thread-sensitive.
 * </p>
 *
 * @see View
 * @see Paint
 * @see Canvas
 */
public class VerticalProgressBar extends View {
	
	private int progress = 0;
	private int max = 100;
	private final Paint backgroundPaint = new Paint();
	private final Paint progressPaint = new Paint();
	
	/**
	 * Constructs a new VerticalProgressBar with default attributes.
	 * <p>
	 * This constructor delegates to the two-parameter constructor with a null AttributeSet,
	 * which in turn delegates to the full constructor. The progress bar will use default
	 * colors (color_primary for background, color_secondary for progress) and styling.
	 * </p>
	 *
	 * @param context the application context used to access resources and themes
	 */
	public VerticalProgressBar(@NonNull Context context) {
		this(context, null);
	}
	
	/**
	 * Constructs a new VerticalProgressBar with custom XML attributes.
	 * <p>
	 * This constructor delegates to the three-parameter constructor with a default style
	 * attribute of 0, allowing XML attributes to be processed by the parent view class.
	 * Custom attributes defined in the XML layout will be applied appropriately.
	 * </p>
	 *
	 * @param context the application context used to access resources and themes
	 * @param attrs   an XML attribute set containing custom styling attributes,
	 *                or null if no custom styling is provided
	 */
	public VerticalProgressBar(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	/**
	 * Constructs a new VerticalProgressBar with custom XML attributes and a default style.
	 * <p>
	 * This constructor initializes the paint objects with default colors:
	 * background = color_primary, progress = color_secondary. Both paints are
	 * configured with antialiasing enabled for smooth rounded corners.
	 * </p>
	 *
	 * @param context      the application context used to access resources and themes
	 * @param attrs        an XML attribute set containing custom styling attributes
	 * @param defStyleAttr the default style attribute to apply to this view
	 */
	public VerticalProgressBar(@NonNull Context context,
	                           @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		
		backgroundPaint.setColor(ContextCompat.getColor(context, R.color.color_primary));
		backgroundPaint.setStyle(Paint.Style.FILL);
		backgroundPaint.setAntiAlias(true);
		
		progressPaint.setColor(ContextCompat.getColor(context, R.color.color_secondary));
		progressPaint.setStyle(Paint.Style.FILL);
		progressPaint.setAntiAlias(true);
	}
	
	/**
	 * Sets the current progress value of the vertical progress bar.
	 * <p>
	 * The progress value is clamped between 0 and the maximum value (inclusive) to ensure
	 * it never exceeds the valid range. After updating the progress, the view is invalidated
	 * to trigger a redraw, which will update the progress bar's visual representation.
	 * </p>
	 *
	 * @param value the new progress value (will be clamped between 0 and max)
	 */
	public void setProgress(final int value) {
		progress = Math.max(0, Math.min(value, max));
		invalidate();
	}
	
	/**
	 * Sets the maximum value of the vertical progress bar.
	 * <p>
	 * The maximum value represents 100% progress. When the current progress equals the max,
	 * the progress bar will be completely filled from bottom to top. If the new max is less
	 * than the current progress, the progress will be automatically capped when next updated.
	 * After updating the max, the view is invalidated to reflect any progress percentage changes.
	 * </p>
	 *
	 * @param value the new maximum value (should be non-negative for proper rendering)
	 */
	public void setMax(final int value) {
		max = value;
		invalidate();
	}
	
	/**
	 * Measures the view to determine its width and height.
	 * <p>
	 * This method sets a fixed width of 10dp and a fixed height of 200dp for the
	 * vertical progress bar, regardless of the parent layout constraints. The
	 * resolveSize method ensures the dimensions are respected within the given
	 * measure specifications.
	 * </p>
	 *
	 * @param widthMeasureSpec  horizontal space requirements as passed by the parent
	 * @param heightMeasureSpec vertical space requirements as passed by the parent
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int desiredWidth = dpToPx(10);
		int desiredHeight = dpToPx(200);
		
		int width = resolveSize(desiredWidth, widthMeasureSpec);
		int height = resolveSize(desiredHeight, heightMeasureSpec);
		
		setMeasuredDimension(width, height);
	}
	
	/**
	 * Draws the vertical progress bar with rounded corners.
	 * <p>
	 * This method renders a vertical progress bar that fills from the bottom upward.
	 * It draws two rounded rectangles: a background rectangle representing the full
	 * track, and a progress rectangle whose height is proportional to the current
	 * progress value relative to the maximum value. Both rectangles have rounded
	 * corners with a 10dp radius.
	 * </p>
	 *
	 * <p><b>Drawing Details:</b>
	 * <ul>
	 *   <li>Background: Full height, drawn first</li>
	 *   <li>Progress: Height = (view height) × (progress / max), aligned to bottom</li>
	 *   <li>Both use pre-configured paints (backgroundPaint and progressPaint)</li>
	 * </ul>
	 * </p>
	 *
	 * @param canvas the canvas on which the progress bar will be drawn
	 */
	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		float radius = dpToPx(10);
		canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(),
			radius, radius, backgroundPaint);
		
		float progressHeight = 0f;
		if (max > 0) {
			progressHeight = (getHeight() * progress) / (float) max;
		}
		
		canvas.drawRoundRect(0f, getHeight() - progressHeight, getWidth(),
			getHeight(), radius, radius, progressPaint);
	}
	
	/**
	 * Converts density-independent pixels (dp) to absolute pixels.
	 * <p>
	 * This utility method uses the system's display metrics to convert a dp value
	 * to its equivalent pixel value for the current screen density. This ensures
	 * consistent sizing across devices with different screen densities.
	 * </p>
	 *
	 * @param dp the value in density-independent pixels to convert
	 * @return the equivalent pixel value for the current screen density
	 */
	private int dpToPx(int dp) {
		Resources displayResources = Resources.getSystem();
		return (int) (dp * displayResources.getDisplayMetrics().density);
	}
}