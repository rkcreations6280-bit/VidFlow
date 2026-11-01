package coreUtils.base;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

/**
 * Abstract base class for Fragments that utilizes View Binding to reduce boilerplate code.
 *
 * <p>This class provides a structured way to initialize layouts, manage lifecycle-safe
 * references to {@link ViewBinding}, and access application-level resources. Subclasses
 * must implement {@link #inflateBinding} and {@link #onLoadedLayout}.</p>
 *
 * <p>The class handles the nulling of the binding reference in {@link #onDetach()}
 * to prevent memory leaks and provides utility methods for safe context and fragment
 * state management.</p>
 *
 * @param <VB> The specific {@link ViewBinding} type associated with the fragment's layout.
 */
public abstract class BaseFragment<VB extends ViewBinding> extends Fragment {
	
	protected BaseApplication application;
	protected Context context;
	protected VB binding;
	
	/**
	 * Inflates the {@link ViewBinding} instance for this fragment's layout.
	 * <p>
	 * Subclasses must implement this method to provide the specific binding class
	 * (e.g., {@code FragmentExampleBinding.inflate(inflater, container, false)}).
	 *
	 * @param inflater  The LayoutInflater object that can be used to inflate any views in the fragment.
	 * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
	 * @return The created {@link ViewBinding} instance.
	 */
	protected abstract VB inflateBinding(LayoutInflater inflater, ViewGroup container);
	
	/**
	 * Called after the layout has been inflated and the view hierarchy has been created.
	 * <p>
	 * Subclasses should implement this method to perform view initialization, such as
	 * setting up click listeners, configuring adapters, or observing ViewModel data.
	 * At this point, the {@link #binding} is guaranteed to be initialized and ready for use.
	 * </p>
	 */
	protected abstract void onLoadedLayout();
	
	/**
	 * Called when the fragment is first attached to its context.
	 * This implementation initializes the local {@code context} reference and attempts
	 * to cast the application context to {@link BaseApplication} for easier access
	 * to global application state.
	 *
	 * @param context The context to which the fragment is being attached.
	 */
	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		this.context = context;
		Context applicationContext = context.getApplicationContext();
		if (applicationContext instanceof BaseApplication) {
			application = (BaseApplication) applicationContext;
		}
	}
	
	/**
	 * Creates and inflates the fragment's view hierarchy.
	 * <p>
	 * This lifecycle method inflates the layout using ViewBinding, creating the
	 * fragment's UI. The binding instance is stored for later access to UI components.
	 * Returns the root view of the inflated layout to be displayed by the fragment.
	 * </p>
	 *
	 * @param inflater           LayoutInflater used to inflate the layout
	 * @param container          parent view that the fragment's UI will be attached to
	 * @param savedInstanceState previously saved state data, or null if none exists
	 * @return the root View of the inflated layout, or null if the fragment doesn't have a UI
	 */
	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
	                         @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		binding = inflateBinding(inflater, container);
		return binding.getRoot();
	}
	
	/**
	 * Called immediately after onCreateView() when the view hierarchy has been created.
	 * <p>
	 * This lifecycle method is invoked after the view has been inflated and all
	 * UI components are ready. It delegates to onLoadedLayout() where subclasses
	 * can perform view initialization, set up click listeners, load data, or
	 * start animations.
	 * </p>
	 *
	 * @param view               the root view of the fragment
	 * @param savedInstanceState previously saved state data, or null if none exists
	 */
	@Override
	public void onViewCreated(@NonNull View view,
	                          @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		int max = maxContentWidthDp();
		if (max > 0) coreUtils.library.views.WideLayout.centerColumn(view, max);
		onLoadedLayout();
	}

	/** Widest the content may get on tablets / landscape; 0 means use the whole width. */
	protected int maxContentWidthDp() {
		return 760;
	}
	
	/**
	 * Called when the fragment is detached from its activity.
	 * <p>
	 * This lifecycle method clears references to the activity context and view binding
	 * to prevent memory leaks after the fragment is no longer attached to the UI.
	 * </p>
	 */
	@Override
	public void onDetach() {
		super.onDetach();
		context = null;
		binding = null;
	}
	
	/**
	 * Returns the view binding instance for this fragment.
	 * <p>
	 * This method provides access to the inflated ViewBinding object, allowing
	 * UI components to be accessed safely after the view has been created.
	 * </p>
	 *
	 * @return the ViewBinding instance associated with this fragment
	 */
	public VB getBinding() {
		return binding;
	}
	
	/**
	 * Safely retrieves the fragment's arguments bundle.
	 * <p>
	 * This method returns the arguments bundle that was set via {@link #setArguments(Bundle)},
	 * or null if no arguments were provided. It provides a nullable-safe wrapper for
	 * the standard getArguments() method.
	 * </p>
	 *
	 * @return the arguments Bundle, or null if no arguments were set
	 */
	@Nullable
	protected Bundle getArgumentsSafe() {
		return getArguments();
	}
	
	/**
	 * Checks whether the fragment is currently alive and attached to an activity.
	 * <p>
	 * This method verifies that the fragment has been added to an activity, the activity
	 * is not null, and the fragment has not been detached. Useful before performing
	 * UI operations or context-dependent tasks.
	 * </p>
	 *
	 * @return true if the fragment is alive and attached, false otherwise
	 */
	protected boolean isFragmentAlive() {
		return isAdded() && getActivity() != null && !isDetached();
	}
	
	/**
	 * Returns the context associated with this fragment, throwing an exception if not available.
	 * <p>
	 * This method provides a guaranteed non-null context reference, throwing an
	 * IllegalStateException if the fragment is not currently attached to an activity.
	 * Use when a context is absolutely required for an operation.
	 * </p>
	 *
	 * @return the non-null Context of the attached activity
	 * @throws IllegalStateException if the fragment is not attached to an activity
	 */
	protected Context requireSafeContext() {
		return requireContext();
	}
	
	/**
	 * Returns the global BaseApplication instance.
	 * <p>
	 * This method provides convenient access to the application context from within
	 * the fragment, useful for accessing app-wide resources, repositories, or
	 * application-level services.
	 * </p>
	 *
	 * @return the BaseApplication singleton instance
	 */
	protected BaseApplication getApp() {
		return application;
	}
}