package coreUtils.library.process;

import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.lang.ref.WeakReference;

/**
 * A lifecycle-aware utility class for scheduling delayed tasks.
 *
 * <p>This class provides a safe way to execute code after a specific duration by tying the
 * execution to an Android {@link LifecycleOwner}. It automatically cancels pending timers
 * when the associated lifecycle is destroyed to prevent memory leaks and crashes (e.g.,
 * attempting to update a UI element after an Activity is finished).</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Automatic cleanup on {@code Lifecycle.Event.ON_DESTROY}.</li>
 *   <li>Use of {@link WeakReference} to prevent the listener from holding
 *       strong references to the calling context.</li>
 *   <li>Logging of scheduler events for debugging purposes.</li>
 * </ul>
 */
public final class TimeScheduler {

    /**
     * Logger instance for tracking scheduling events, lifecycle changes, and error reporting.
     */
    private static final LoggerUtils logger = LoggerUtils.from(TimeScheduler.class);

    /**
     * Executes a task after a specified delay while respecting the provided LifecycleOwner's state.
     * <p>
     * This method automatically handles memory management by using a {@link WeakReference} for the listener
     * and attaching a {@link DefaultLifecycleObserver} to the {@code lifecycleOwner}. If the lifecycle
     * is destroyed before the delay completes, the timer is canceled and references are cleared to
     * prevent memory leaks or background execution after the UI is gone.
     *
     * @param timeInMillis   The delay duration in milliseconds.
     * @param lifecycleOwner The lifecycle component (e.g., Activity or Fragment) that governs the task's lifespan.
     * @param listener       The callback to be executed once the delay has elapsed.
     */
    public static void runDelayed(int timeInMillis,
                                  @NonNull LifecycleOwner lifecycleOwner,
                                  @NonNull OnTaskFinishListener listener) {
        logger.debug("Delay started: " + timeInMillis + "ms");
        final WeakReference<OnTaskFinishListener> weakListener = new WeakReference<>(listener);
        final CountDownTimer timer = getCountDownTimer(timeInMillis, weakListener);
        lifecycleOwner.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                cancelTimer(timer);
                weakListener.clear();
                logger.debug("Timer observer: Lifecycle destroyed, timer cleaned up.");
            }
        });
    }

    /**
     * Creates and starts a {@link CountDownTimer} that executes a listener after a specified delay.
     *
     * <p>The listener is accessed through a {@link WeakReference} to ensure that the timer
     * does not prevent the listener from being garbage collected. When the timer finishes,
     * it attempts to retrieve the listener and invoke its callback.</p>
     *
     */
    @NonNull
    private static CountDownTimer getCountDownTimer(
            int timeInMillis, @NonNull WeakReference<OnTaskFinishListener> weakListener) {
        final CountDownTimer timer = new CountDownTimer(timeInMillis, timeInMillis) {
            @Override
            public void onTick(long millisUntilFinished) {}

            @Override
            public void onFinish() {
                OnTaskFinishListener actualListener = weakListener.get();
                if (actualListener != null) {
                    logger.debug("Delay finished, executing listener.");
                    actualListener.afterDelay();
                } else {
                    logger.warning("Delay finished, but listener was garbage collected.");
                }
            }
        };

        timer.start();
        return timer;
    }

    /**
     * Cancels the provided {@link CountDownTimer} instance.
     * <p>
     * This method ensures the timer is stopped safely by checking for nullity
     * and catching any potential exceptions that may occur during the cancellation process.
     * </p>
     *
     * @param timer The timer to be cancelled. If null, the method does nothing.
     */
    public static void cancelTimer(CountDownTimer timer) {
        try {
            if (timer != null) {
                timer.cancel();
                logger.debug("Timer cancelled");
            }
        } catch (Exception error) {
            logger.error("Error while cancelling timer", error);
        }
    }

    /**
     * Callback interface used to handle the completion of a scheduled task.
     */
    public interface OnTaskFinishListener {
        void afterDelay();
    }
}