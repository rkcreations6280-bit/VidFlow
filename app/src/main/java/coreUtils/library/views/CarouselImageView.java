package coreUtils.library.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom View that displays an infinitely scrolling horizontal carousel of images.
 * <p>
 * This component provides a touch-interactive carousel that displays images loaded
 * via Glide, with support for drag-to-scroll, fling gestures, and optional automatic
 * scrolling. Images are rendered using center-crop scaling, and pagination dots
 * indicate the current position within the carousel.
 * </p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>Infinite Scrolling:</b> Circular navigation that wraps around seamlessly</li>
 *   <li><b>Touch Gestures:</b> Drag to scroll manually with velocity-based fling support</li>
 *   <li><b>Auto-Scroll:</b> Optional automatic advancement to next image</li>
 *   <li><b>Click Detection:</b> Single-tap detection with position and URL callbacks</li>
 *   <li><b>Visual Feedback:</b> Pagination dots with smooth color transitions</li>
 *   <li><b>Center-Crop Rendering:</b> Images fill available space while preserving aspect ratio</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * CarouselImageView carousel = findViewById(R.id.carousel);
 * carousel.setOnImageClickListener((index, url) -> {
 *     Toast.makeText(context, "Clicked: " + url, Toast.LENGTH_SHORT).show();
 * });
 * carousel.loadImages(Arrays.asList(
 *     "https://example.com/image1.jpg",
 *     "https://example.com/image2.jpg",
 *     "https://example.com/image3.jpg"
 * ));
 * carousel.setAutoScrollEnabled(true);
 * </pre>
 * </p>
 *
 * <p><b>Custom Attributes (attrs.xml):</b>
 * <pre>
 * &lt;declare-styleable name="CarouselImageView"&gt;
 *     &lt;attr name="autoScrollEnabled" format="boolean" /&gt;
 *     &lt;attr name="autoScrollDelay" format="integer" /&gt;
 *     &lt;attr name="inactiveDotColor" format="color" /&gt;
 *     &lt;attr name="activeDotColor" format="color" /&gt;
 * &lt;/declare-styleable&gt;
 * </pre>
 * </p>
 *
 * @see View
 * @see Scroller
 * @see GestureDetector
 * @see OnImageClickListener
 */
public class CarouselImageView extends View {
	
	private final List<Bitmap> bitmaps = new ArrayList<>();
	private final List<String> imageUrls = new ArrayList<>();
	
	private final Paint imagePaint = new Paint(
		Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	
	private final int activeDotColor = Color.parseColor("#555555");
	private final int inactiveDotColor = Color.parseColor("#CCCCCC");
	
	private static final long AUTO_SCROLL_DELAY = 4500;
	
	private int currentIndex = 0;
	private float scrollOffset = 0f;
	
	private final Scroller scroller;
	private final GestureDetector gestureDetector;
	private VelocityTracker velocityTracker;
	
	private float downX;
	private float lastX;
	private boolean isDragging = false;
	private final int touchSlop;
	private final int minFlingVelocity;
	
	private boolean autoScrollEnabled = true;
	private OnImageClickListener listener;
	
	/**
	 * Callback interface for handling image click events within the CarouselImageView.
	 * <p>
	 * Implement this interface to respond when a user taps on an image in the carousel.
	 * The callback provides both the position index of the clicked image and its URL,
	 * allowing the host component to perform actions such as opening a full-screen
	 * gallery viewer, sharing the image, or navigating to a detail screen.
	 * </p>
	 *
	 * <p><b>Usage Example:</b>
	 * <pre>
	 * carouselImageView.setOnImageClickListener(new OnImageClickListener() {
	 *     public void onClick(int index, String url) {
	 *         Intent intent = new Intent(context, ImageDetailActivity.class);
	 *         intent.putExtra("image_url", url);
	 *         intent.putExtra("position", index);
	 *         startActivity(intent);
	 *     }
	 * });
	 * </pre>
	 * </p>
	 */
	public interface OnImageClickListener {
		
		/**
		 * Called when an image in the carousel is clicked or tapped.
		 *
		 * @param index the position index of the clicked image within the carousel (0-based)
		 * @param url   the URL of the clicked image for further processing or display
		 */
		void onClick(int index, String url);
	}
	
	/**
	 * Enables or disables automatic scrolling of the carousel.
	 * <p>
	 * When enabled, the carousel will automatically advance to the next item
	 * after a fixed delay. Disabling auto-scroll stops any pending scroll events
	 * but does not cancel an in-progress scroll animation.
	 * </p>
	 *
	 * @param enabled true to enable auto-scroll, false to disable
	 */
	public void setAutoScrollEnabled(boolean enabled) {
		autoScrollEnabled = enabled;
	}
	
	/**
	 * Runnable that handles automatic scrolling to the next item.
	 * <p>
	 * This runnable advances the carousel to the next image, then schedules itself
	 * to run again after the configured delay. It only executes when auto-scroll
	 * is enabled and there is more than one image to display.
	 * </p>
	 */
	private final Runnable autoScrollRunnable = new Runnable() {
		@Override
		public void run() {
			if (!autoScrollEnabled || bitmaps.size() <= 1) return;
			smoothScrollTo(currentIndex + 1);
			postDelayed(this, AUTO_SCROLL_DELAY);
		}
	};
	
	/**
	 * Constructs a new CarouselImageView with custom attributes.
	 * <p>
	 * Initializes the scroller with a decelerate interpolator for smooth fling animations,
	 * configures touch detection thresholds (touch slop and minimum fling velocity),
	 * and sets up a gesture detector for click/tap events on images.
	 * </p>
	 *
	 * <p><b>Initialized Components:</b>
	 * <ul>
	 *   <li>Scroller with DecelerateInterpolator for smooth fling ending</li>
	 *   <li>Touch slop from ViewConfiguration for drag detection</li>
	 *   <li>Minimum fling velocity for gesture-based scrolling</li>
	 *   <li>GestureDetector for single-tap image click detection</li>
	 * </ul>
	 * </p>
	 *
	 * @param context the context for the view
	 * @param attrs   optional XML attributes (can be null)
	 */
	public CarouselImageView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		
		scroller = new Scroller(context, new DecelerateInterpolator());
		ViewConfiguration vc = ViewConfiguration.get(context);
		touchSlop = vc.getScaledTouchSlop();
		minFlingVelocity = vc.getScaledMinimumFlingVelocity();
		
		gestureDetector = new GestureDetector(
			context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
				if (!isDragging && listener != null && !imageUrls.isEmpty()) {
					listener.onClick(currentIndex, imageUrls.get(currentIndex));
				}
				return true;
			}
		});
		
		setClickable(true);
	}
	
	/**
	 * Registers a callback to be invoked when an image in the carousel is clicked.
	 * <p>
	 * The listener receives the index of the clicked image and its URL,
	 * allowing the host component to perform actions such as opening a full-screen
	 * viewer or sharing the image.
	 * </p>
	 *
	 * @param listener the callback to invoke on image click events
	 */
	public void setOnImageClickListener(OnImageClickListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Loads multiple images from URLs into the carousel view asynchronously.
	 * <p>
	 * This method clears any existing images and loads new ones using Glide. Each image
	 * is loaded as a Bitmap and added to the internal list. During loading, auto-scroll
	 * is temporarily stopped and resumed after all images are queued. The view is
	 * invalidated to redraw as each image completes loading.
	 * </p>
	 *
	 * <p><b>Behavior:</b>
	 * <ul>
	 *   <li>Does nothing if the URL list is null or empty</li>
	 *   <li>Clears existing bitmaps and URLs before loading new ones</li>
	 *   <li>Resets scroll position to the first item</li>
	 *   <li>Loads each image asynchronously via Glide</li>
	 *   <li>Resumes auto-scroll after loading if enabled</li>
	 * </ul>
	 * </p>
	 *
	 * @param urls list of image URLs to load into the carousel
	 */
	public void loadImages(List<String> urls) {
		if (urls == null || urls.isEmpty()) return;
		removeCallbacks(autoScrollRunnable);
		bitmaps.clear();
		imageUrls.clear();
		imageUrls.addAll(urls);
		currentIndex = 0;
		scrollOffset = 0f;
		invalidate();
		
		for (String url : urls) {
			Glide.with(getContext())
				.asBitmap()
				.load(url)
				.into(new CustomTarget<Bitmap>() {
					@Override
					public void onResourceReady(
						@NonNull Bitmap resource,
						@Nullable Transition<? super Bitmap> transition) {
						bitmaps.add(resource);
						invalidate();
					}
					
					@Override
					public void onLoadCleared(@Nullable Drawable placeholder) {}
				});
		}
		
		if (autoScrollEnabled) {
			postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
		}
	}
	
	/**
	 * Smoothly scrolls the carousel to a specific item index.
	 * <p>
	 * This method uses a Scroller to animate the transition from the current scroll
	 * position to the target index. It handles circular wrapping by adjusting the
	 * start position when scrolling past the first or last item, ensuring the
	 * animation travels the shortest path. The scroll animation lasts 350 milliseconds.
	 * </p>
	 *
	 * <p><b>Wrapping Logic:</b>
	 * <ul>
	 *   <li>Wraps target index to valid range (0 to size-1)</li>
	 *   <li>When scrolling forward past last item, adjusts start position to simulate wrap</li>
	 *   <li>When scrolling backward past first item, adjusts start position to simulate wrap</li>
	 * </ul>
	 * </p>
	 *
	 * @param index the target item index to scroll to (will be wrapped to valid range)
	 */
	private void smoothScrollTo(int index) {
		if (bitmaps.isEmpty()) return;
		int size = bitmaps.size();
		
		int targetIndex = index;
		if (targetIndex < 0) targetIndex = size - 1;
		else if (targetIndex >= size) targetIndex = 0;
		
		int startX = (int) (scrollOffset * 1000f);
		int endX = targetIndex * 1000;
		
		if (index >= size && scrollOffset > size - 1) {
			startX = (int) ((scrollOffset - size) * 1000f);
		} else if (index < 0 && scrollOffset < 1) {
			startX = (int) ((scrollOffset + size) * 1000f);
		}
		
		scroller.forceFinished(true);
		scroller.startScroll(startX, 0, endX - startX, 0, 350);
		currentIndex = targetIndex;
		invalidate();
	}
	
	/**
	 * Handles fling scrolling animation by updating the scroll offset during overscroll.
	 * <p>
	 * This method is called by the system during fling animations. It retrieves the current
	 * scroll position from the Scroller, normalizes the scrollOffset to wrap around within
	 * the item range, and triggers a redraw. The scrolling continues until the animation
	 * completes.
	 * </p>
	 */
	@Override
	public void computeScroll() {
		if (scroller.computeScrollOffset()) {
			scrollOffset = scroller.getCurrX() / 1000f;
			int size = Math.max(1, bitmaps.size());
			while (scrollOffset < 0f) scrollOffset += size;
			while (scrollOffset >= size) scrollOffset -= size;
			invalidate();
		}
	}
	
	/**
	 * Renders the carousel view including visible images and pagination dots.
	 * <p>
	 * This method draws the current, previous, and next items to create an infinite
	 * scrolling carousel effect. It calculates the relative position of each adjacent
	 * item, translates the canvas appropriately, and draws each bitmap using
	 * center-crop scaling. Pagination dots are drawn at the bottom of the view.
	 * </p>
	 *
	 * <p><b>Drawing Logic:</b>
	 * <ul>
	 *   <li>Only draws the item at centerIndex and its immediate neighbors (-1, +1)</li>
	 *   <li>Handles circular wrapping using modulo arithmetic</li>
	 *   <li>Normalizes relative positions for proper wrapping around boundaries</li>
	 *   <li>Translates canvas horizontally based on relative position * view width</li>
	 *   <li>Applies center-crop scaling to each bitmap</li>
	 * </ul>
	 * </p>
	 *
	 * @param canvas the canvas to draw the carousel content on
	 */
	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		super.onDraw(canvas);
		if (bitmaps.isEmpty()) return;
		
		float width = getWidth();
		float height = getHeight();
		int count = bitmaps.size();
		int centerIndex = Math.round(scrollOffset);
		
		for (int i = -1; i <= 1; i++) {
			int index = (centerIndex + i) % count;
			if (index < 0) index += count;
			
			float relative = index - scrollOffset;
			if (relative > count / 2f) relative -= count;
			if (relative < -count / 2f) relative += count;
			
			canvas.save();
			canvas.translate(relative * width, 0);
			drawCenterCropBitmap(canvas, bitmaps.get(index), width, height);
			canvas.restore();
		}
		drawDots(canvas, width, height);
	}
	
	/**
	 * Draws a bitmap centered within the canvas using center-crop scaling.
	 * <p>
	 * This method scales the bitmap to fill the entire view area while maintaining its
	 * aspect ratio. The image is cropped equally on both sides or top/bottom as needed
	 * to fit exactly. This is similar to ImageView's ScaleType.CENTER_CROP behavior.
	 * </p>
	 *
	 * @param canvas     the canvas to draw the bitmap on
	 * @param bitmap     the bitmap to be drawn
	 * @param viewWidth  the width of the target drawing area
	 * @param viewHeight the height of the target drawing area
	 */
	private void drawCenterCropBitmap(Canvas canvas, Bitmap bitmap,
	                                  float viewWidth, float viewHeight) {
		float bW = bitmap.getWidth();
		float bH = bitmap.getHeight();
		float scale = Math.max(viewWidth / bW, viewHeight / bH);
		float dx = (viewWidth - bW * scale) * 0.5f;
		float dy = (viewHeight - bH * scale) * 0.5f;
		
		Matrix matrix = new Matrix();
		matrix.setScale(scale, scale);
		matrix.postTranslate(dx, dy);
		canvas.drawBitmap(bitmap, matrix, imagePaint);
	}
	
	/**
	 * Draws the pagination indicator dots at the bottom of the carousel view.
	 * <p>
	 * This method renders a series of dots representing each item in the carousel,
	 * with the dot corresponding to the currently visible item highlighted in a
	 * different color. The active dot color smoothly interpolates between inactive
	 * and active colors based on scroll offset for a transition effect. Dots are
	 * centered horizontally and positioned 35dp from the bottom edge.
	 * </p>
	 *
	 * <p><b>Visual Behavior:</b>
	 * <ul>
	 *   <li>Dots are hidden if there is only one item</li>
	 *   <li>Current/active dot uses a color interpolated between inactive and active colors</li>
	 *   <li>Dots are evenly spaced and centered</li>
	 * </ul>
	 * </p>
	 *
	 * @param canvas the canvas to draw the dots on
	 * @param width  the width of the view for centering calculations
	 * @param height the height of the view for vertical positioning
	 */
	private void drawDots(Canvas canvas, float width, float height) {
		int count = bitmaps.size();
		if (count <= 1) return;
		float dotRadius = 5f;
		float spacing = 18f;
		float totalWidth = (count * (dotRadius * 2)) + ((count - 1) * spacing);
		float startX = (width - totalWidth) / 2f;
		
		for (int i = 0; i < count; i++) {
			float distance = Math.abs(i - scrollOffset);
			if (distance > count / 2f) distance = Math.abs(distance - count);
			
			dotPaint.setColor(distance < 1f ?
				interpolateColor(inactiveDotColor,
					activeDotColor, 1f - distance) : inactiveDotColor);
			canvas.drawCircle(startX + dotRadius, height - 35f, dotRadius, dotPaint);
			startX += (dotRadius * 2) + spacing;
		}
	}
	
	/**
	 * Interpolates between two colors using HSV color space for smoother transitions.
	 * <p>
	 * This method converts the start and end colors to HSV (Hue, Saturation, Value)
	 * format, interpolates each component based on the given fraction, and converts
	 * back to an integer color value. HSV interpolation typically produces more
	 * natural color transitions than RGB interpolation.
	 * </p>
	 *
	 * @param startColor the starting color (when fraction = 0)
	 * @param endColor   the ending color (when fraction = 1)
	 * @param fraction   the interpolation factor between 0.0 and 1.0
	 * @return the interpolated color value
	 */
	private int interpolateColor(int startColor, int endColor, float fraction) {
		float[] startHSV = new float[3], endHSV = new float[3];
		Color.colorToHSV(startColor, startHSV);
		Color.colorToHSV(endColor, endHSV);
		for (int i = 0; i < 3; i++)
			endHSV[i] =
				startHSV[i] + (endHSV[i] - startHSV[i]) * fraction;
		return Color.HSVToColor(endHSV);
	}
	
	/**
	 * Handles touch events for the carousel view, enabling drag-to-scroll and fling gestures.
	 * <p>
	 * This method processes touch interactions including drag scrolling, velocity-based flinging,
	 * and auto-scroll cancellation/resumption. It uses a GestureDetector for tap detection and
	 * a VelocityTracker for fling calculations. When dragging, it prevents parent view from
	 * intercepting touch events for smoother scrolling.
	 * </p>
	 *
	 * <p><b>Touch Handling:</b>
	 * <ul>
	 *   <li>ACTION_DOWN: Cancels auto-scroll and any ongoing animations</li>
	 *   <li>ACTION_MOVE: Scrolls the carousel horizontally when touch slop threshold is exceeded</li>
	 *   <li>ACTION_UP/CANCEL: Performs fling or smooth scroll to nearest item, resumes auto-scroll</li>
	 * </ul>
	 * </p>
	 *
	 * @param event the motion event containing touch data
	 * @return true if the event was handled, false otherwise
	 */
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		gestureDetector.onTouchEvent(event);
		if (bitmaps.size() <= 1) return true;
		
		if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
		velocityTracker.addMovement(event);
		
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				removeCallbacks(autoScrollRunnable);
				if (!scroller.isFinished()) scroller.abortAnimation();
				downX = event.getX();
				lastX = downX;
				isDragging = false;
				return true;
			
			case MotionEvent.ACTION_MOVE:
				float currentX = event.getX();
				float dx = currentX - lastX;
				if (!isDragging && Math.abs(currentX - downX) > touchSlop) {
					isDragging = true;
					getParent().requestDisallowInterceptTouchEvent(true);
				}
				if (isDragging) {
					scrollOffset -= dx / getWidth();
					int size = bitmaps.size();
					while (scrollOffset < 0f) scrollOffset += size;
					while (scrollOffset >= size) scrollOffset -= size;
					invalidate();
				}
				lastX = currentX;
				return true;
			
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (isDragging) {
					velocityTracker.computeCurrentVelocity(1000);
					float xVelocity = velocityTracker.getXVelocity();
					int size = bitmaps.size();
					int targetIndex;
					
					if (Math.abs(xVelocity) > minFlingVelocity) {
						targetIndex = (xVelocity > 0) ?
							(int) Math.floor(scrollOffset) :
							(int) Math.ceil(scrollOffset);
					} else {
						targetIndex = Math.round(scrollOffset);
					}
					smoothScrollTo(targetIndex);
				}
				
				isDragging = false;
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
				
				if (autoScrollEnabled) {
					postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
				}
				
				return true;
		}
		return super.onTouchEvent(event);
	}
	
	/**
	 * Called when the view is attached to a window.
	 * <p>
	 * This lifecycle method starts the auto-scroll functionality when the view becomes
	 * visible on screen, provided auto-scroll is enabled. The scroll runnable will
	 * execute after the configured delay period.
	 * </p>
	 */
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (autoScrollEnabled) {
			postDelayed(autoScrollRunnable, AUTO_SCROLL_DELAY);
		}
	}
	
	/**
	 * Called when the view is detached from a window.
	 * <p>
	 * This lifecycle method cleans up any pending auto-scroll callbacks to prevent
	 * memory leaks and ensure the runnable is not executed after the view is no longer
	 * attached to the window hierarchy.
	 * </p>
	 */
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		removeCallbacks(autoScrollRunnable);
	}
}