package coreUtils.library.views;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A GridLayoutManager that automatically calculates and adjusts the number of columns
 * based on a desired minimum column width and the available space in the RecyclerView.
 *
 * <p>This layout manager dynamically determines the optimal span count by dividing the
 * available space (excluding padding) by the specified column width. The actual column
 * width may be larger than the requested minimum width depending on the remaining space.</p>
 *
 * <p>Typical usage example:</p>
 * <pre>
 * AutoFitGridLayoutManager layoutManager = new AutoFitGridLayoutManager(context, 200);
 * recyclerView.setLayoutManager(layoutManager);
 *
 * // Later, if you need to change the column width:
 * layoutManager.setColumnWidth(250);
 * </pre>
 *
 * @see GridLayoutManager
 * @see RecyclerView
 */
public class AutoFitGridLayoutManager extends GridLayoutManager {
	
	/**
	 * The minimum desired width for each column in pixels.
	 *
	 * <p>This value serves as the target column width used to calculate the optimal
	 * number of columns. The actual column width in the final layout will be at least
	 * this value, but may be larger due to space distribution across the calculated
	 * number of columns.</p>
	 *
	 * <p>Default value is 0, which means no columns will be calculated until a valid
	 * positive width is set via {@link #setColumnWidth(int)}.</p>
	 */
	private int columnWidth;
	
	/**
	 * Flag indicating whether the column width has been changed and the span count
	 * needs to be recalculated during the next layout pass.
	 *
	 * <p>This flag is set to {@code true} when {@link #setColumnWidth(int)} is called
	 * with a valid positive value. It is cleared after the span count has been
	 * recalculated in {@link #onLayoutChildren(RecyclerView.Recycler, RecyclerView.State)}.
	 * Initially set to {@code true} to ensure the first layout pass performs the
	 * initial column calculation.</p>
	 */
	private boolean columnWidthChanged = true;
	
	/**
	 * Constructs an AutoFitGridLayoutManager with the specified minimum column width.
	 *
	 * <p>The orientation defaults to VERTICAL with a single initial span count.
	 * The layout manager will automatically adjust the number of columns during
	 * the first layout pass based on the provided column width and available space.</p>
	 *
	 * @param context     The Context used to access resources and system services
	 * @param columnWidth The desired minimum width for each column in pixels.
	 *                    Must be greater than 0 for the auto-fit behavior to work properly
	 */
	public AutoFitGridLayoutManager(Context context, int columnWidth) {
		super(context, 1);
		setColumnWidth(columnWidth);
	}
	
	/**
	 * Updates the desired minimum column width for this layout manager.
	 *
	 * <p>When the column width is changed, the layout manager will recalculate the
	 * optimal span count during the next layout pass. The new value will only take
	 * effect if it is positive and different from the current column width.</p>
	 *
	 * <p>Note that the actual column width in the final layout may exceed this value
	 * due to space distribution across the calculated number of columns.</p>
	 *
	 * @param newColumnWidth The desired minimum width for each column in pixels.
	 *                       Must be greater than 0 to have any effect. Values less than
	 *                       or equal to zero are silently ignored.
	 */
	public void setColumnWidth(int newColumnWidth) {
		if (newColumnWidth > 0 && newColumnWidth != columnWidth) {
			columnWidth = newColumnWidth;
			columnWidthChanged = true;
		}
	}
	
	/**
	 * Calculates and applies the optimal number of columns before laying out children.
	 *
	 * <p>This method intercepts the layout process to dynamically adjust the span count
	 * based on the current available space and the configured minimum column width.
	 * The calculation occurs only when {@code columnWidthChanged} flag is set to true
	 * and the column width is valid (greater than zero).</p>
	 *
	 * <p>The available space is determined differently based on the orientation:</p>
	 * <ul>
	 *   <li>For VERTICAL orientation, uses width minus horizontal padding</li>
	 *   <li>For HORIZONTAL orientation, uses height minus vertical padding</li>
	 * </ul>
	 *
	 * <p>The span count is calculated as {@code availableSpace / columnWidth}, with
	 * a minimum of 1 column. After updating the span count, the change flag is cleared
	 * and the normal layout process continues with the new configuration.</p>
	 *
	 * @param recycler The Recycler that can provide recycled views for layout
	 * @param state    The current State of the RecyclerView providing information
	 *                 about the current data set and scroll position
	 * @see GridLayoutManager#onLayoutChildren(RecyclerView.Recycler, RecyclerView.State)
	 */
	@Override
	public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
		if (columnWidthChanged && columnWidth > 0) {
			int totalSpace;
			if (getOrientation() == VERTICAL) {
				totalSpace = getWidth() - getPaddingRight() - getPaddingLeft();
			} else {
				totalSpace = getHeight() - getPaddingTop() - getPaddingBottom();
			}
			
			int spanCount = Math.max(1, totalSpace / columnWidth);
			setSpanCount(spanCount);
			columnWidthChanged = false;
		}
		super.onLayoutChildren(recycler, state);
	}
}