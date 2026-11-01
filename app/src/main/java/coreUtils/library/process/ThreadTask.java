package coreUtils.library.process;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A lifecycle-aware utility class for managing background tasks with UI thread callbacks.
 *
 * <p>ThreadTask simplifies the process of executing code in the background and delivering
 * results, progress updates, or errors back to the main thread. It utilizes {@link WeakReference}
 * for callbacks to prevent memory leaks and supports automatic cancellation through
 * {@link LifecycleOwner}.</p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>Lifecycle Awareness:</b> Automatically cancels tasks when the associated
 *       {@code LifecycleOwner} (like an Activity or Fragment) is destroyed.</li>
 *   <li><b>Progress Reporting:</b> Allows background tasks to publish updates to the UI.</li>
 *   <li><b>Timeout Support:</b> Configurable maximum execution time to prevent hanging tasks.</li>
 *   <li><b>Memory Safety:</b> Uses weak references for UI-bound callbacks to avoid leaking
 *       Context or View objects.</li>
 *   <li><b>Custom Executors:</b> Can run on a shared thread pool or a user-provided {@link ExecutorService}.</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * ThreadTask.Builder<String, Integer> builder = new ThreadTask.Builder<>();
 * builder.withBackgroundTask(callback -> {
 *             for (int i = 0; i <= 100; i += 25) {
 *                 callback.onProgress(i);
 */
public final class ThreadTask<TaskResult, ProgressType> {

    /**
     * Logger instance used for tracking internal execution states, errors, and task lifecycle events.
     */
    private final LoggerUtils logger = LoggerUtils.from(getClass());

    /**
     * A global {@link Handler} instance tied to the main (UI) thread's looper.
     * Used for delivering task results, progress updates, and errors back to the UI thread,
     * as well as managing execution timeouts.
     */
    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * A shared, fixed-size thread pool used for background task execution when a specific
     * {@link ExecutorService} is not provided.
     * <p>
     * The pool size is determined dynamically based on the number of available processors,
     * with a minimum of 2 threads, to optimize performance across different hardware.
     */
    private static final ExecutorService SHARED_EXECUTOR;

    static {
        int threadPoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        SHARED_EXECUTOR = Executors.newFixedThreadPool(threadPoolSize);
    }

    /**
     * The core task implementation that defines the logic to be executed on a background thread.
     * This field stores the reference to the {@link BackgroundTask} which handles the primary
     * work and provides progress updates.
     */
    private BackgroundTask<TaskResult, ProgressType> backgroundTask;

    /**
     * A weak reference to the task that handles the result on the UI thread.
     * Using a {@link WeakReference} prevents memory leaks, especially when
     * the result task is tied to a lifecycle-bound component like an Activity or Fragment.
     */
    private WeakReference<ResultTask<TaskResult>> resultTaskRef;

    /**
     * A weak reference to the task that handles UI updates for progress increments.
     * Using a {@link WeakReference} prevents memory leaks, particularly when the
     * task is attached to an Activity or Fragment that may be destroyed before
     * the background execution completes.
     */
    private WeakReference<ProgressUpdateTask<ProgressType>> progressTaskRef;

    /**
     * A weak reference to the {@link ErrorTask} callback. This callback is invoked on the
     * main thread if an exception occurs during background execution or if the task times out.
     * Using a {@link WeakReference} helps prevent memory leaks of the UI components that
     * implement the error handling logic.
     */
    private WeakReference<ErrorTask> errorTaskRef;

    /**
     * The custom {@link ExecutorService} used to execute the background task.
     * If this is null, the {@link #SHARED_EXECUTOR} will be used by default.
     */
    private ExecutorService executorService;

    /**
     * The future task representing the active background execution.
     * Used to monitor the execution state and to support cancellation of the
     * background thread.
     */
    private FutureTask<?> backgroundFutureTask;

    /**
     * Flag indicating whether the task has been canceled.
     * <p>
     * This atomic boolean is used to safely signal and check the cancellation status
     * across different threads (UI and background). When true, further progress updates
     * and final results will be suppressed, and ongoing background execution is
     * encouraged to terminate.
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * The result produced by the background task execution. This value is stored
     * after the background work completes and is subsequently delivered to the
     * registered {@link ResultTask} on the main thread.
     */
    private TaskResult result;

    /**
     * The maximum duration in milliseconds that the background task is allowed to run.
     * If the task exceeds this time, it will be automatically canceled and an error
     * will be delivered. A value of 0 indicates no timeout limit.
     */
    private long maxExecutionTimeMs = 0;

    /**
     * A runnable scheduled on the main thread to handle task timeouts.
     * When triggered, it cancels the task execution and delivers a timeout error
     * if the task has exceeded its specified maximum execution time.
     */
    private Runnable timeoutRunnable;

    /**
     * Observer used to monitor the {@link LifecycleOwner} state.
     * It ensures the background task is automatically canceled and resources are
     * cleaned up when the lifecycle reaches the {@code ON_DESTROY} state.
     */
    private DefaultLifecycleObserver lifecycleObserver;

    /**
     * A weak reference to the {@link LifecycleOwner} used to monitor the lifecycle status.
     * This allows the task to be automatically canceled when the owner (e.g., Activity or Fragment)
     * is destroyed, while preventing memory leaks by allowing the owner to be garbage collected.
     */
    private WeakReference<LifecycleOwner> lifecycleOwnerRef;

    /**
     * Binds the task to a {@link LifecycleOwner} to ensure automatic cancellation.
     * <p>
     * When the provided {@code owner} reaches the {@code ON_DESTROY} state, the task
     * is automatically canceled and resources are cleaned up. If this method is called
     * multiple times, it will stop observing the previous owner before attaching to the new one.
     * </p>
     *
     * @param owner The lifecycle owner (e.g., Activity or Fragment) to observe.
     */
    public void observeLifecycle(@NonNull LifecycleOwner owner) {
        LifecycleOwner previousOwner =
                lifecycleOwnerRef != null ? lifecycleOwnerRef.get() : null;

        if (previousOwner != null && lifecycleObserver != null) {
            previousOwner.getLifecycle().removeObserver(lifecycleObserver);
        }

        lifecycleOwnerRef = new WeakReference<>(owner);
        lifecycleObserver = new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                cancel();
                owner.getLifecycle().removeObserver(this);
                lifecycleObserver = null;
                lifecycleOwnerRef = null;
                logger.debug("ThreadTask auto-cancelled by lifecycle.");
            }
        };

        owner.getLifecycle().addObserver(lifecycleObserver);
    }

    /**
     * Starts the execution of the background task.
     * <p>
     * This method performs the following steps:
     * <ul>
     *     <li>Checks if a task is already running; if so, it ignores the start request.</li>
     *     <li>Validates that a {@link BackgroundTask} has been provided.</li>
     *     <li>Resets the cancellation state and previous results.</li>
     *     <li>Sets up a timeout timer if {@code maxExecutionTimeMs} is greater than zero.</li>
     *     <li>Submits the task to either a custom {@link ExecutorService} or the shared thread pool.</li>
     * </ul>
     * <p>
     * During execution, progress updates, results, and errors are dispatched to their
     * respective callbacks on the main UI thread.
     */
    public void start() {
        if (backgroundFutureTask != null && !backgroundFutureTask.isDone()) {
            logger.warning("Task already running");
            return;
        }

        final BackgroundTask<TaskResult, ProgressType> taskInstance = backgroundTask;
        if (taskInstance == null) {
            logger.error("Cannot start: BackgroundTask is null.");
            return;
        }

        cancelInternal(false);
        cancelled.set(false);
        result = null;

        if (maxExecutionTimeMs > 0) {
            timeoutRunnable = () -> {
                if (!cancelled.get()) {
                    logger.warning("Task timed out. Cancelling...");
                    String message = "Task timeout after " + maxExecutionTimeMs + " ms";
                    RuntimeException timeoutException = new RuntimeException(message);
                    deliverError(timeoutException);
                    UI_HANDLER.post(this::cancel);
                }
            };

            UI_HANDLER.postDelayed(timeoutRunnable, maxExecutionTimeMs);
        }

        Runnable taskRunnable = () -> {
            try {
                if (cancelled.get()) return;
                result = taskInstance.runInBackground(progress -> {
                    if (!cancelled.get()) {
                        deliverProgress(progress);
                    }
                });

                if (!cancelled.get()) {
                    deliverResult();
                }

            } catch (Exception error) {
                logger.error("ThreadTask execution failed.", error);
                if (!cancelled.get()) {
                    deliverError(error);
                }
            } finally {
                cleanupTimeout();
            }
        };

        ExecutorService executor = executorService != null
                ? executorService : SHARED_EXECUTOR;

        backgroundFutureTask = new FutureTask<>(taskRunnable, null);
        executor.submit(backgroundFutureTask);
    }

    /**
     * Delivers the background task result to the main UI thread.
     * <p>
     * This method posts a runnable to the {@link #UI_HANDLER}. It verifies that the task
     * has not been canceled and that the {@link ResultTask} listener (stored via
     * {@link WeakReference}) is still available before invoking the callback.
     */
    private void deliverResult() {
        UI_HANDLER.post(() -> {
            if (cancelled.get()) return;
            ResultTask<TaskResult> task =
                    resultTaskRef != null ? resultTaskRef.get() : null;

            if (task != null) {
                task.onResult(result);
            }
        });
    }

    /**
     * Posts the progress update to the main UI thread.
     * <p>
     * This method ensures that the progress update is delivered only if the task
     * has not been canceled. It retrieves the {@link ProgressUpdateTask} via a
     * {@link WeakReference} to prevent memory leaks.
     * </p>
     *
     * @param progress The progress data to be delivered to the listener.
     */
    private void deliverProgress(ProgressType progress) {
        UI_HANDLER.post(() -> {
            if (cancelled.get()) return;
            ProgressUpdateTask<ProgressType> task =
                    progressTaskRef != null ? progressTaskRef.get() : null;

            if (task != null) {
                task.onProgressUpdate(progress);
            }
        });
    }

    /**
     * Delivers an error to the registered {@link ErrorTask} on the UI thread.
     * <p>
     * This method retrieves the error callback via a {@link WeakReference} and ensures
     * that the {@code onError} notification is dispatched on the main thread, regardless
     * of which thread the error originated from.
     * </p>
     *
     * @param error The throwable exception encountered during task execution.
     */
    private void deliverError(@NonNull Throwable error) {
        UI_HANDLER.post(() -> {
            ErrorTask task =
                    errorTaskRef != null ? errorTaskRef.get() : null;

            if (task != null) {
                task.onError(error);
            }
        });
    }

    /**
     * Cancels the task execution and clears all associated callbacks.
     * <p>
     * This method interrupts the background thread if the task is currently running,
     * marks the task as canceled to prevent further result or progress deliveries,
     * and removes references to the background task and all result/progress/error listeners
     * to prevent memory leaks.
     */
    public void cancel() {
        cancelInternal(true);
    }

    /**
     * Internal method to handle the cancellation of the background task.
     * <p>
     * This method flags the task as canceled, interrupts the background thread if it is currently
     * running, and cleans up any pending timeout scheduled on the UI thread.
     * </p>
     *
     * @param clearCallbacks If {@code true}, releases references to the background task and all
     *                       result/progress/error listeners to prevent memory leaks.
     */
    private void cancelInternal(boolean clearCallbacks) {
        cancelled.set(true);

        if (backgroundFutureTask != null) {
            backgroundFutureTask.cancel(true);
            backgroundFutureTask = null;
        }

        cleanupTimeout();
        result = null;

        if (clearCallbacks) {
            backgroundTask = null;
            if (resultTaskRef != null) {
                resultTaskRef.clear();
                resultTaskRef = null;
            }

            if (progressTaskRef != null) {
                progressTaskRef.clear();
                progressTaskRef = null;
            }

            if (errorTaskRef != null) {
                errorTaskRef.clear();
                errorTaskRef = null;
            }
        }
    }

    /**
     * Cancels the scheduled timeout callback and clears the timeout runnable.
     * This ensures that no timeout-related errors are triggered after the task
     * has already completed or been manually canceled.
     */
    private void cleanupTimeout() {
        if (timeoutRunnable != null) {
            UI_HANDLER.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    /**
     * Checks whether this task has been canceled.
     *
     * @return {@code true} if the task was canceled before it completed normally,
     * {@code false} otherwise.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Sets the background operation to be executed by this task.
     * This task defines the logic that will run on a background thread.
     *
     * @param task The {@link BackgroundTask} implementation containing the background logic,
     *             or {@code null} to clear the current task.
     */
    public void setBackgroundTask(@Nullable BackgroundTask<TaskResult, ProgressType> task) {
        this.backgroundTask = task;
    }

    /**
     * Sets the callback to be invoked when the background task completes successfully.
     * The callback is stored as a {@link WeakReference} to prevent memory leaks
     * and is executed on the main (UI) thread.
     *
     * @param task The callback to handle the task result, or {@code null} to remove the existing callback.
     */
    public void setResultTask(@Nullable ResultTask<TaskResult> task) {
        this.resultTaskRef = task != null ? new WeakReference<>(task) : null;
    }

    /**
     * Sets the task to be executed whenever progress updates are delivered from the background process.
     * The callback is held as a {@link WeakReference} to prevent memory leaks and will be invoked
     * on the UI thread.
     *
     * @param task The progress update listener, or {@code null} to remove the current listener.
     */
    public void setProgressUpdateTask(@Nullable ProgressUpdateTask<ProgressType> task) {
        this.progressTaskRef = task != null ? new WeakReference<>(task) : null;
    }

    /**
     * Sets the task to be executed when an error occurs during background execution.
     * The callback is stored as a {@link WeakReference} to prevent memory leaks.
     *
     * @param task The error callback to be invoked on the UI thread, or null to remove the current listener.
     */
    public void setErrorTask(@Nullable ErrorTask task) {
        this.errorTaskRef = task != null ? new WeakReference<>(task) : null;
    }

    /**
     * Sets the maximum allowed time for the task to execute.
     * If the task exceeds this duration, it will be automatically canceled,
     * and an error will be delivered via the ErrorTask callback.
     *
     * @param ms The maximum execution time in milliseconds. Set to 0 (or less) to disable the timeout.
     */
    public void setMaxExecutionTimeMs(long ms) {
        this.maxExecutionTimeMs = ms;
    }

    /**
     * Sets a custom {@link ExecutorService} to be used for executing the background task.
     * If no executor is explicitly set, the default {@code SHARED_EXECUTOR} will be used.
     *
     * @param executorService The executor service to manage the background thread execution.
     */
    public void setExecutorService(@NonNull ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * Executes the specified task on the application's main (UI) thread.
     * Use this method to safely perform UI updates or interactions from a background thread.
     *
     * @param task The task containing the logic to be executed on the UI thread.
     */
    public static void executeOnMainThread(@NonNull UITask task) {
        UI_HANDLER.post(task::runOnUIThread);
    }

    /**
     * Executes a simple task on a background thread pool without returning a result
     * or tracking progress. This uses the shared fixed thread pool defined in the class.
     *
     * @param task The task to be executed in the background.
     */
    public static void executeInBackground(@NonNull BackgroundTaskNoResult task) {
        SHARED_EXECUTOR.execute(task::runInBackground);
    }

    /**
     * Interface representing a unit of work to be executed on a background thread.
     *
     * @param <Result>   The type of the result produced by the task.
     * @param <Progress> The type of the progress updates published during execution.
     */
    public interface BackgroundTask<Result, Progress> {
        Result runInBackground(@NonNull ProgressCallback<Progress> callback);
    }

    /**
     * Callback interface for receiving the result of a background task.
     * Methods in this interface are invoked on the main (UI) thread.
     *
     * @param <Result> The type of the result produced by the task.
     */
    public interface ResultTask<Result> {
        void onResult(Result result);
    }

    /**
     * Interface definition for a callback to be invoked when the background task
     * publishes a progress update. The methods in this interface are executed on the UI thread.
     *
     * @param <Progress> The type of the progress units published by the task.
     */
    public interface ProgressUpdateTask<Progress> {
        void onProgressUpdate(Progress progress);
    }

    /**
     * Interface used within the background thread to report progress updates.
     *
     * @param <Progress> The type of the progress data being reported.
     */
    public interface ProgressCallback<Progress> {
        void onProgress(Progress progress);
    }

    /**
     * Interface definition for a callback to be invoked when an error occurs during
     * the execution of a {@link ThreadTask}.
     * <p>
     * The {@link #onError(Throwable)} method is invoked on the main (UI) thread.
     */
    public interface ErrorTask {
        void onError(@NonNull Throwable error);
    }

    /**
     * Functional interface representing a task that must be executed on the main (UI) thread.
     * Usually used in conjunction with {@link #executeOnMainThread(UITask)}.
     */
    public interface UITask {
        void runOnUIThread();
    }

    /**
     * Functional interface representing a simple task to be executed on a background thread
     * that does not return a result and does not report progress.
     */
    public interface BackgroundTaskNoResult {
        void runInBackground();
    }

    /**
     * A builder class for creating and configuring {@link ThreadTask} instances.
     *
     * <p>This builder follows the standard builder pattern to allow for sequential
     * configuration of background operations, UI thread callbacks, lifecycle observation,
     * and execution constraints like timeouts and custom executors.</p>
     *
     * @param <JobResult>    The type of the result produced by the background task.
     * @param <ProgressType> The type of the progress updates emitted during task execution.
     */
    public static class Builder<JobResult, ProgressType> {

        /**
         * The core logic to be executed on a background thread.
         * This task is responsible for performing the primary operation and optionally
         * reporting progress updates back to the UI thread via a callback.
         */
        private BackgroundTask<JobResult, ProgressType> backgroundTask;

        /**
         * The callback to be invoked on the main thread when the background task
         * completes successfully and returns a result.
         */
        private ResultTask<JobResult> resultTask;

        /**
         * The task callback used to handle progress updates dispatched from the background process.
         * This callback is typically executed on the main UI thread.
         */
        private ProgressUpdateTask<ProgressType> progressTask;

        /**
         * The task to be executed on the main thread if an error or exception occurs
         * during the background execution or if the task times out.
         */
        private ErrorTask errorTask;

        /**
         * The custom executor service used to run the background task.
         * If null, the task will fall back to using the {@link #SHARED_EXECUTOR}.
         */
        private ExecutorService executorService;

        /**
         * The lifecycle owner used to automatically manage the task's execution.
         * When the owner is destroyed, the task will be automatically canceled
         * to prevent memory leaks or unnecessary processing.
         */
        private LifecycleOwner lifecycleOwner;

        /**
         * The maximum time in milliseconds allowed for the background task to execute.
         * If the task exceeds this duration, it will be automatically canceled.
         */
        private long timeoutMs;

        /**
         * Sets the background operation to be performed by this task.
         *
         * @param task An implementation of {@link BackgroundTask} defining the logic
         *             to be executed on a background thread.
         * @return This builder instance for method chaining.
         */
        public Builder<JobResult, ProgressType> withBackgroundTask(
                BackgroundTask<JobResult, ProgressType> task) {
            this.backgroundTask = task;
            return this;
        }

        /**
         * Sets the task to be executed on the UI thread once the background process
         * completes successfully.
         *
         * @param task The callback that receives the result of the background operation.
         * @return This builder instance for method chaining.
         */
        public Builder<JobResult, ProgressType> withResultTask(ResultTask<JobResult> task) {
            this.resultTask = task;
            return this;
        }

        /**
         * Sets the task that will be executed on the UI thread whenever progress updates
         * are dispatched from the background operation.
         *
         * @param task The implementation of {@link ProgressUpdateTask} to handle progress updates.
         * @return This builder instance for method chaining.
         */
        public Builder<JobResult, ProgressType> withProgressTask(ProgressUpdateTask<ProgressType> task) {
            this.progressTask = task;
            return this;
        }

        /**
         * Sets the callback to be executed on the UI thread if an error occurs during the
         * background task execution or if the task times out.
         *
         * @param task The {@link ErrorTask} to handle the exception.
         * @return This builder instance for chaining.
         */
        public Builder<JobResult, ProgressType> withErrorTask(ErrorTask task) {
            this.errorTask = task;
            return this;
        }

        /**
         * Sets a custom {@link ExecutorService} to run the background task.
         * If no executor is provided, a shared internal thread pool will be used by default.
         *
         * @param executor The executor service to use for task execution.
         * @return The builder instance for method chaining.
         */
        public Builder<JobResult, ProgressType> withExecutor(ExecutorService executor) {
            this.executorService = executor;
            return this;
        }

        /**
         * Binds the task to a {@link LifecycleOwner}. The task will be automatically
         * canceled when the provided lifecycle owner is destroyed (e.g., when an
         * Activity or Fragment is finished).
         *
         * @param owner The lifecycle owner to observe.
         * @return This builder instance for chaining.
         */
        public Builder<JobResult, ProgressType> withLifecycle(LifecycleOwner owner) {
            this.lifecycleOwner = owner;
            return this;
        }

        /**
         * Sets a maximum execution time for the task. If the task does not complete within
         * the specified duration, it will be automatically canceled and an error will
         * be delivered via the {@link ErrorTask}.
         *
         * @param timeoutMs The maximum allowed execution time in milliseconds.
         * @return This builder instance for method chaining.
         */
        public Builder<JobResult, ProgressType> withTimeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Constructs a new {@link ThreadTask} instance configured with the parameters
         * provided to this builder.
         * <p>
         * This method initializes the task with the background logic, result/error handlers,
         * progress listeners, and optional configurations like execution timeouts or
         * custom executor services. If a {@link LifecycleOwner} was provided, the task
         * will automatically observe it for lifecycle-based cancellation.
         * </p>
         *
         * @return A fully configured {@link ThreadTask} ready to be started.
         */
        public ThreadTask<JobResult, ProgressType> build() {
            ThreadTask<JobResult, ProgressType> task = new ThreadTask<>();
            task.setBackgroundTask(backgroundTask);
            task.setResultTask(resultTask);
            task.setProgressUpdateTask(progressTask);
            task.setErrorTask(errorTask);
            task.setMaxExecutionTimeMs(timeoutMs);

            if (executorService != null) {
                task.setExecutorService(executorService);
            }

            if (lifecycleOwner != null) {
                task.observeLifecycle(lifecycleOwner);
            }

            return task;
        }
    }
}