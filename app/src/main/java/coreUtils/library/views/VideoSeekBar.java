package coreUtils.library.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nextgen.R;

import org.jetbrains.annotations.NotNull;

/**
 * A custom video seek bar view that provides a draggable thumb, progress tracking,
 * buffer indication, and touch interaction for media playback control. This view
 * is designed specifically for video player interfaces, offering visual feedback
 * for both current playback position and buffered content.
 *
 * <p><strong>Core features:</strong>
 * <ul>
 * <li>Draggable thumb for manual progress seeking.</li>
 * <li>Separate colors for track background, buffer, and playback progress.</li>
 * <li>Customizable dimensions via XML attributes.</li>
 * <li>Shadow effect on the thumb for improved visual depth.</li>
 * <li>Progress change listener for external callbacks.</li>
 * </ul>
 *
 * <p><strong>XML attributes (R.styleable.VideoSeekBar):</strong>
 * vsb_trackHeight, vsb_thumbSize, vsb_thumbPadding, vsb_trackColor,
 * vsb_bufferColor, vsb_progressColor, vsb_thumbColor.
 *
 * @see View
 * @see #setProgress(float)
 * @see #setBufferProgress(float)
 * @see OnProgressChangedListener
 */
public class VideoSeekBar extends View {
	
	private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	
	private boolean isDragging = false;
	private float dragOffsetX = 0f;
	
	private final RectF trackRect = new RectF();
	private final RectF bufferRect = new RectF();
	private final RectF progressRect = new RectF();
	
	private float progress = 0f;
	private float bufferProgress = 0f;
	
	private float trackHeight;
	private float thumbRadius;
	private float thumbPadding;
	
	private int trackColor;
	private int bufferColor;
	private int progressColor;
	private int thumbColor;
	
	private OnProgressChangedListener listener;
	
	/**
	 * Constructs a VideoSeekBar with only a context. Default attribute values are
	 * used for all styling parameters (track height, thumb size, colors, etc.).
	 *
	 * @param context The current context, used to access resources and display metrics.
	 * @see #init(AttributeSet)
	 */
	public VideoSeekBar(Context context) {
		super(context);
		init(null);
	}
	
	/**
	 * Constructs a VideoSeekBar with context and XML attributes. This constructor
	 * is called when the view is inflated from a layout file. Custom attributes
	 * defined in {@code R.styleable.VideoSeekBar} are applied via {@link #init(AttributeSet)}.
	 *
	 * @param context The current context, used to access resources and display metrics.
	 * @param attrs   XML attributes that customize the seek bar appearance and behavior.
	 * @see #init(AttributeSet)
	 */
	public VideoSeekBar(@NotNull Context context,
	                    @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(attrs);
	}
	
	/**
	 * Constructs a VideoSeekBar with context, XML attributes, and a default style
	 * resource. This constructor allows additional styling via a theme attribute
	 * or explicit style resource. The default style is applied before XML attributes
	 * are processed.
	 *
	 * @param context      The current context, used to access resources and display metrics.
	 * @param attrs        XML attributes that customize the seek bar appearance.
	 * @param defStyleAttr An attribute in the current theme that contains a reference
	 *                     to a style resource for default styling.
	 * @see #init(AttributeSet)
	 */
	public VideoSeekBar(@NonNull Context context,
	                    @Nullable AttributeSet attrs,
	                    int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(attrs);
	}
	
	/**
	 * Initializes the custom seek bar with default dimensions, colors, and optional
	 * XML attribute overrides. This method sets the track height, thumb radius,
	 * padding, and color values, then configures a shadow layer on the thumb paint.
	 *
	 * <p><strong>Default values:</strong>
	 * <ul>
	 * <li>trackHeight = 8dp</li>
	 * <li>thumbRadius = 12dp (derived from default 24dp thumb size)</li>
	 * <li>thumbPadding = 8dp</li>
	 * <li>trackColor = "#222222" (dark gray)</li>
	 * <li>bufferColor = "#666666" (medium gray)</li>
	 * <li>progressColor = WHITE</li>
	 * <li>thumbColor = WHITE</li>
	 * </ul>
	 *
	 * <p>If an {@link AttributeSet} is provided, custom attributes from the
	 * {@code R.styleable.VideoSeekBar} are applied. The thumb size attribute defines
	 * the diameter, which is halved to obtain the radius. A soft shadow is applied
	 * to the thumb using {@link android.graphics.Paint#setShadowLayer(float, float, float, int)}.
	 * The view layer type is set to {@link android.view.View#LAYER_TYPE_SOFTWARE}
	 * to ensure shadow rendering compatibility.
	 *
	 * @param attrs Optional XML attributes for customizing the seek bar appearance.
	 * @see TypedArray
	 */
	private void init(@Nullable AttributeSet attrs) {
		trackHeight = dp(8);
		thumbRadius = dp(12);
		thumbPadding = dp(8);
		
		trackColor = Color.parseColor("#222222");
		bufferColor = Color.parseColor("#666666");
		progressColor = Color.WHITE;
		thumbColor = Color.WHITE;
		
		if (attrs != null) {
			try (TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.VideoSeekBar)) {
				trackHeight = ta.getDimension(R.styleable.VideoSeekBar_vsb_trackHeight, trackHeight);
				float thumbSize = ta.getDimension(R.styleable.VideoSeekBar_vsb_thumbSize, dp(24));
				
				thumbRadius = thumbSize / 2f;
				thumbPadding = ta.getDimension(R.styleable.VideoSeekBar_vsb_thumbPadding, thumbPadding);
				trackColor = ta.getColor(R.styleable.VideoSeekBar_vsb_trackColor, trackColor);
				bufferColor = ta.getColor(R.styleable.VideoSeekBar_vsb_bufferColor, bufferColor);
				progressColor = ta.getColor(R.styleable.VideoSeekBar_vsb_progressColor, progressColor);
				thumbColor = ta.getColor(R.styleable.VideoSeekBar_vsb_thumbColor, thumbColor);
			}
		}
		
		thumbPaint.setShadowLayer(dp(3), 0, dp(1), 0x33000000);
		setLayerType(LAYER_TYPE_SOFTWARE, null);
	}
	
	/**
	 * Measures the view to determine its final width and height. This method ensures
	 * the progress bar has a minimum height sufficient to comfortably accommodate
	 * the thumb circle and track with appropriate padding.
	 *
	 * <p><strong>Height calculation:</strong>
	 * The desired height is the maximum of:
	 * <ul>
	 * <li>40dp (minimum comfortable touch target size).</li>
	 * <li>Twice the thumb radius plus 12dp (thumb diameter + vertical clearance).</li>
	 * </ul>
	 *
	 * <p>The width is taken directly from the {@link MeasureSpec} without modification,
	 * allowing the parent layout to control horizontal sizing. The height is resolved
	 * using {@link #resolveSize(int, int)} which respects the parent's constraints
	 * while ensuring the desired minimum height is met.
	 *
	 * @param widthMeasureSpec  Horizontal space requirements as imposed by the parent.
	 * @param heightMeasureSpec Vertical space requirements as imposed by the parent.
	 * @see #resolveSize(int, int)
	 * @see #dp(float)
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int desiredHeight = (int) Math.max(dp(40), thumbRadius * 2 + dp(12));
		
		int width = MeasureSpec.getSize(widthMeasureSpec);
		int height = resolveSize(desiredHeight, heightMeasureSpec);
		
		setMeasuredDimension(width, height);
	}
	
	/**
	 * Draws the custom progress bar including the background track, buffer progress,
	 * playback progress, and the draggable thumb. This method is called whenever the
	 * view needs to be redrawn, such as after progress updates or invalidations.
	 *
	 * <p><strong>Drawing order (bottom to top):</strong>
	 * <ol>
	 * <li>Background track - Full width rounded rectangle with {@code trackColor}.</li>
	 * <li>Buffer progress - Partial overlay showing buffered content, if bufferProgress > 0.</li>
	 * <li>Playback progress - Partial overlay showing current position, if progress > 0.</li>
	 * <li>Thumb circle - Draggable knob positioned at the current progress point.</li>
	 * </ol>
	 *
	 * <p>The track and progress rectangles share the same vertical center (half of view
	 * height). The thumb position is calculated as a linear interpolation between the
	 * left and right bounds based on the current progress value (0.0 to 1.0).
	 *
	 * @param canvas The canvas on which the progress bar components will be drawn.
	 * @see #onDraw(Canvas)
	 * @see #setProgress(float)
	 * @see #setBufferProgress(float)
	 */
	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		
		float centerY = getHeight() / 2f;
		float startX = thumbRadius + thumbPadding;
		float endX = getWidth() - thumbRadius - thumbPadding;
		float radius = trackHeight / 2f;
		
		trackRect.set(startX, centerY - trackHeight / 2f,
			endX, centerY + trackHeight / 2f);
		
		trackPaint.setColor(trackColor);
		canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
		
		if (bufferProgress > 0f) {
			trackPaint.setColor(bufferColor);
			bufferRect.set(trackRect.left, trackRect.top,
				trackRect.left + (trackRect.width() * bufferProgress), trackRect.bottom);
			canvas.drawRoundRect(bufferRect, radius, radius, trackPaint);
		}
		
		if (progress > 0f) {
			trackPaint.setColor(progressColor);
			progressRect.set(trackRect.left, trackRect.top,
				trackRect.left + (trackRect.width() * progress), trackRect.bottom);
			canvas.drawRoundRect(progressRect, radius, radius, trackPaint);
		}
		
		float thumbX = startX + ((endX - startX) * progress);
		thumbPaint.setColor(thumbColor);
		canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint);
	}
	
	/**
	 * Handles touch events for the custom progress bar, enabling dragging of the
	 * thumb to change the progress value. This method calculates the thumb position
	 * based on the current progress, then processes DOWN, MOVE, and UP/CANCEL events.
	 *
	 * <p><strong>Touch behavior:</strong>
	 * <ul>
	 * <li>ACTION_DOWN: If touch is within thumb area, starts dragging with offset tracking.
	 *     Otherwise, jumps progress to the touched position directly.</li>
	 * <li>ACTION_MOVE: Updates progress based on thumb drag movement if dragging is active.</li>
	 * <li>ACTION_UP/ACTION_CANCEL: Ends dragging and releases touch interception.</li>
	 * </ul>
	 *
	 * <p>The method requests that the parent view does not intercept touch events
	 * while dragging to ensure smooth thumb movement. After progress changes, the
	 * listener is notified and the view is redrawn.
	 *
	 * @param event The motion event containing touch coordinates and action type.
	 * @return {@code true} if the event was handled, {@code false} otherwise.
	 * @see #updateProgressFromTouch(float)
	 * @see OnProgressChangedListener#onProgressChanged(float)
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float startX = thumbRadius + thumbPadding;
		float endX = getWidth() - thumbRadius - thumbPadding;
		float thumbX = startX + ((endX - startX) * progress);
		
		switch (event.getActionMasked()) {
			
			case MotionEvent.ACTION_DOWN: {
				float dx = Math.abs(event.getX() - thumbX);
				if (dx <= thumbRadius * 2f) {
					isDragging = true;
					dragOffsetX = event.getX() - thumbX;
					getParent().requestDisallowInterceptTouchEvent(true);
					return true;
				}
				
				updateProgressFromTouch(event.getX());
				if (listener != null) {
					listener.onProgressChanged(progress);
				}
				
				invalidate();
				return true;
			}
			
			case MotionEvent.ACTION_MOVE: {
				if (!isDragging) {
					return true;
				}
				
				float x = event.getX() - dragOffsetX;
				updateProgressFromTouch(x);
				
				if (listener != null) {
					listener.onProgressChanged(progress);
				}
				
				return true;
			}
			
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL: {
				isDragging = false;
				getParent().requestDisallowInterceptTouchEvent(false);
				performClick();
				return true;
			}
		}
		
		return super.onTouchEvent(event);
	}
	
	/**
	 * Performs a click action on this custom progress bar view. This method delegates
	 * to the superclass implementation to ensure standard click handling, including
	 * accessibility events and click listeners, are properly triggered.
	 *
	 * <p>Override is required because the custom view handles touch events for
	 * dragging, but explicit clicks (e.g., via accessibility or keyboard) still
	 * need to be processed correctly.
	 *
	 * @return {@code true} if the click was handled, {@code false} otherwise.
	 * @see android.view.View#performClick()
	 */
	@Override
	public boolean performClick() {
		return super.performClick();
	}
	
	/**
	 * Updates the progress value based on a touch event's X-coordinate within the view.
	 * This method calculates the valid touchable range from the left thumb edge to the
	 * right thumb edge, clamps the input X value to this range, then computes the
	 * normalized progress value (0.0 to 1.0) based on the relative position within
	 * that range. After updating the progress, the view is invalidated to redraw.
	 *
	 * <p><strong>Range calculation:</strong>
	 * <ul>
	 * <li>startX = thumb radius + thumb padding (left boundary)</li>
	 * <li>endX = view width - thumb radius - thumb padding (right boundary)</li>
	 * <li>progress = (clamped X - startX) / (endX - startX)</li>
	 * </ul>
	 *
	 * <p>This method does not clamp the progress value further, as the calculation
	 * naturally yields values between 0.0 and 1.0 due to the X clamping.
	 *
	 * @param x The raw X-coordinate of the touch event, in pixels relative to the view.
	 * @see #setProgress(float)
	 * @see #invalidate()
	 */
	private void updateProgressFromTouch(float x) {
		float startX = thumbRadius + thumbPadding;
		float endX = getWidth() - thumbRadius - thumbPadding;
		
		x = Math.max(startX, Math.min(endX, x));
		progress = (x - startX) / (endX - startX);
		
		invalidate();
	}
	
	/**
	 * Sets the current playback progress value and triggers a redraw of the view.
	 * The provided progress is clamped between 0.0 (start) and 1.0 (end) before
	 * being stored. This method also updates the stored value and calls
	 * {@link #invalidate()} to refresh the visual representation.
	 *
	 * @param progress The new progress value, typically from 0f to 1f.
	 * @see #getProgress()
	 * @see #invalidate()
	 */
	public void setProgress(float progress) {
		this.progress = Math.max(0f, Math.min(1f, progress));
		invalidate();
	}
	
	/**
	 * Returns the current playback progress value.
	 *
	 * @return The current progress as a float between 0f and 1f.
	 * @see #setProgress(float)
	 */
	public float getProgress() {
		return progress;
	}
	
	/**
	 * Sets the current buffer progress value (how much of the media has been
	 * buffered) and triggers a redraw of the view. The provided value is clamped
	 * between 0.0 and 1.0. This is typically used to show a secondary progress
	 * bar indicating buffered content ahead of the current playback position.
	 *
	 * @param bufferProgress The new buffer progress value, from 0f to 1f.
	 * @see #getBufferProgress()
	 * @see #invalidate()
	 */
	public void setBufferProgress(float bufferProgress) {
		this.bufferProgress = Math.max(0f, Math.min(1f, bufferProgress));
		invalidate();
	}
	
	/**
	 * Returns the current buffer progress value.
	 *
	 * @return The current buffer progress as a float between 0f and 1f.
	 * @see #setBufferProgress(float)
	 */
	public float getBufferProgress() {
		return bufferProgress;
	}
	
	/**
	 * Registers a listener to receive callbacks when the progress value changes.
	 * The listener is invoked internally after {@link #setProgress(float)} updates
	 * the value, allowing external components to react to progress changes.
	 *
	 * @param listener The listener to notify on progress changes, or null to clear.
	 * @see OnProgressChangedListener#onProgressChanged(float)
	 */
	public void setOnProgressChangedListener(OnProgressChangedListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Callback interface for receiving progress change events from the custom view.
	 * Implement this interface to respond to user-initiated progress changes or
	 * playback position updates.
	 */
	public interface OnProgressChangedListener {
		/**
		 * Called when the progress value has been updated.
		 *
		 * @param progress The new progress value, clamped between 0f and 1f.
		 */
		void onProgressChanged(float progress);
	}
	
	/**
	 * Converts a density-independent pixel (dp) value to raw pixels based on the
	 * current screen density. This utility method is used internally for rendering
	 * the progress bar and knob at consistent physical sizes across devices.
	 *
	 * @param value The dp value to convert (e.g., 48dp).
	 * @return The equivalent pixel value for the current display density.
	 */
	private float dp(float value) {
		return value * getResources().getDisplayMetrics().density;
	}
}