package coreUtils.library.views;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A {@link RecyclerView.ItemDecoration} that applies adaptive spacing to items
 * within a {@link GridLayoutManager}, with special handling for full-span items.
 * <p>
 * This decoration differentiates between standard grid items and items that occupy
 * the entire span width (e.g., headers, footers, or promotional banners). Full-span
 * items receive uniform spacing on all four sides, with top spacing applied only
 * to the first position. Standard grid items receive asymmetric horizontal spacing
 * where left and right gaps are distributed proportionally (full spacing on outer
 * edges, half spacing between columns). Bottom spacing is applied uniformly to
 * all non-full-span items.
 * </p>
 * <ul>
 * <li>Uses {@link GridLayoutManager.SpanSizeLookup} to detect full-span items</li>
 * <li>Full-span items are identified when {@code spanSize == spanCount}</li>
 * <li>Column calculation skips position 0 (assumed full-span item)</li>
 * <li>Silently returns on invalid positions or missing layout manager</li>
 * </ul>
 *
 * @see RecyclerView.ItemDecoration
 * @see GridLayoutManager
 * @see GridLayoutManager.SpanSizeLookup
 */
public class GridSpacingDecoration extends RecyclerView.ItemDecoration {
	private final int spacingPx;
	
	/**
	 * Constructs a grid spacing decoration with the specified gap size.
	 * <p>
	 * The provided spacing value is applied uniformly as the base unit for all
	 * offsets. Full-span items receive this value directly on all edges, while
	 * standard grid items receive either full or half values depending on their
	 * column position (leftmost, middle, or rightmost column).
	 * </p>
	 *
	 * @param spacingPx the base spacing size in pixels, applied as the maximum
	 *                  gap between adjacent items
	 */
	public GridSpacingDecoration(int spacingPx) {
		this.spacingPx = spacingPx;
	}
	
	/**
	 * Calculates per-item offsets based on span occupancy and column position.
	 * <p>
	 * This method distinguishes between two item types:
	 * <ul>
	 * <li><b>Full-span items</b> (spanSize == spanCount): receive {@code spacingPx}
	 *     on left, right, and bottom edges. Top edge receives spacing only when the
	 *     item is at position 0.</li>
	 * <li><b>Standard grid items</b>: receive bottom spacing of {@code spacingPx}.
	 *     Horizontal offsets are asymmetric: leftmost column receives full
	 *     {@code spacingPx} on left and half on right; other columns receive half
	 *     on left and full on right.</li>
	 * </ul>
	 * The method silently returns if the child has no valid adapter position or
	 * if the parent's layout manager is not a {@link GridLayoutManager}.
	 * </p>
	 *
	 * @param outRect the rectangle to receive the offset values for this child
	 * @param view    the child view to decorate
	 * @param parent  the {@link RecyclerView} containing the child
	 * @param state   the current state of the RecyclerView (unused in this
	 *                implementation)
	 */
	@Override
	public void getItemOffsets(@NonNull Rect outRect,
	                           @NonNull View view,
	                           @NonNull RecyclerView parent,
	                           @NonNull RecyclerView.State state) {
		int position = parent.getChildAdapterPosition(view);
		if (position == RecyclerView.NO_POSITION) return;
		
		GridLayoutManager layoutManager = (GridLayoutManager) parent.getLayoutManager();
		if (layoutManager == null) return;
		
		int spanCount = layoutManager.getSpanCount();
		int spanSize = layoutManager.getSpanSizeLookup().getSpanSize(position);
		
		if (spanSize == spanCount) {
			outRect.left = spacingPx;
			outRect.right = spacingPx;
			outRect.bottom = spacingPx;
			if (position == 0) outRect.top = spacingPx;
		} else {
			int gridPos = position - 1;
			int column = gridPos % spanCount;
			
			outRect.bottom = spacingPx;
			
			if (column == 0) {
				outRect.left = spacingPx;
				outRect.right = spacingPx / 2;
			} else {
				outRect.left = spacingPx / 2;
				outRect.right = spacingPx;
			}
		}
	}
}