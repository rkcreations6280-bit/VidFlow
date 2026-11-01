package coreUtils.library.views;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A {@link RecyclerView.ItemDecoration} that distributes uniform spacing between
 * items in a {@link GridLayoutManager}.
 * <p>
 * This decoration calculates per-item offsets to create evenly distributed gaps
 * around grid cells while maintaining consistent total spacing across rows and
 * columns. The spacing algorithm supports two modes: edge-inclusive (padding
 * applied to screen edges) and edge-exclusive (no padding on outer boundaries).
 * The implementation dynamically adapts to the {@link GridLayoutManager}'s span
 * count and computes fractional spacing per column to avoid visual clustering.
 * </p>
 * <ul>
 * <li>Spacing is defined in pixels and applied uniformly in all directions</li>
 * <li>When {@code includeEdge} is {@code true}, top and bottom edges also receive
 *     spacing</li>
 * <li>Uses integer division carefully to preserve exact pixel distribution</li>
 * <li>Automatically ignores items with invalid adapter positions</li>
 * </ul>
 *
 * @see RecyclerView.ItemDecoration
 * @see GridLayoutManager
 * @see #getItemOffsets(Rect, View, RecyclerView, RecyclerView.State)
 */
public class GridLayoutSpacing extends RecyclerView.ItemDecoration {
	
	private final int spacingPx;
	private final boolean includeEdge;
	
	/**
	 * Constructs a spacing decoration with edge spacing enabled.
	 * <p>
	 * This is a convenience constructor that delegates to
	 * {@link #GridLayoutSpacing(int, boolean)} with {@code includeEdge} set to
	 * {@code true}, applying spacing to all four sides of the grid including
	 * screen edges.
	 * </p>
	 *
	 * @param spacingPx the uniform spacing between grid items in pixels
	 */
	public GridLayoutSpacing(int spacingPx) {
		this(spacingPx, true);
	}
	
	/**
	 * Constructs a spacing decoration with explicit edge inclusion control.
	 * <p>
	 * When {@code includeEdge} is {@code true}, the decoration adds spacing
	 * to the top of the first row and to the left/right edges of columns.
	 * When {@code false}, spacing is applied only between adjacent items,
	 * leaving the outer edges flush with the parent container boundaries.
	 * </p>
	 *
	 * @param spacingPx   the uniform spacing between grid items in pixels
	 * @param includeEdge {@code true} to apply spacing to screen edges,
	 *                    {@code false} to apply spacing only between items
	 */
	public GridLayoutSpacing(int spacingPx, boolean includeEdge) {
		this.spacingPx = spacingPx;
		this.includeEdge = includeEdge;
	}
	
	/**
	 * Calculates and applies per-item offset rectangles for grid spacing.
	 * <p>
	 * This method determines the column index of each child view based on its
	 * adapter position and the {@link GridLayoutManager}'s span count. It then
	 * computes proportional left and right offsets using integer arithmetic to
	 * distribute the total spacing evenly across columns. Top and bottom offsets
	 * are applied conditionally based on row position and the
	 * {@code includeEdge} flag. The method silently returns if the child has
	 * no valid adapter position or if the layout manager is not a
	 * {@link GridLayoutManager}.
	 * </p>
	 * <ul>
	 * <li>Left Edge: {@code spacingPx - column * spacingPx / spanCount}</li>
	 * <li>Right Edge: {@code (column + 1) * spacingPx / spanCount}</li>
	 * <li>Top: Applied to the first row only if {@code includeEdge} is true.</li>
	 * <li>Bottom: Applied to every row if {@code includeEdge} is true.</li>
	 * </ul>
	 *
	 * @param outRect the rectangle to receive the offset values for this child
	 * @param view    the child view to decorate
	 * @param parent  the {@link RecyclerView} containing the child
	 * @param state   the current state of the RecyclerView
	 */
	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
	                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		int position = parent.getChildAdapterPosition(view);
		if (position == RecyclerView.NO_POSITION) return;
		
		GridLayoutManager layoutManager = (GridLayoutManager) parent.getLayoutManager();
		if (layoutManager == null) return;
		
		int spanCount = layoutManager.getSpanCount();
		int column = position % spanCount;
		
		if (includeEdge) {
			outRect.left = spacingPx - column * spacingPx / spanCount;
			outRect.right = (column + 1) * spacingPx / spanCount;
			
			if (position < spanCount) {
				outRect.top = spacingPx;
			}
			outRect.bottom = spacingPx;
		} else {
			outRect.left = column * spacingPx / spanCount;
			outRect.right = spacingPx - (column + 1) * spacingPx / spanCount;
			if (position >= spanCount) {
				outRect.top = spacingPx;
			}
		}
	}
}