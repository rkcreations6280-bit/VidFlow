package userInterface.openingSplash;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.nextgen.databinding.ActivityOpening1Binding;

import coreUtils.base.BaseActivity;
import coreUtils.base.BaseApplication;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import coreUtils.library.process.VersionInfo;
import coreUtils.library.views.ActivityAnimator;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import userInterface.languagePicker.LanguageActivity;
import userInterface.mainScreen.MainActivity;
import userInterface.termsConsPolicy.TermsPolicyActivity;
import userInterface.userPrivacy.PrivacyPolicyActivity;

/**
 * The initial splash activity shown when the application is launched.
 * It displays the app version, syncs the download engine configuration in the
 * background, and then routes the user to the appropriate next screen.
 *
 * <p><strong>Startup flow:</strong>
 * <ol>
 *   <li>Display the current version name and code on screen.</li>
 *  <li>Fire off a background sync of the download engine configuration
 *       (5 sec timeout, fire‑and‑forget, lifecycle‑aware).</li>
  *   <li>After a short delay, navigate to the next screen.</li>
 * </ol>
 *
 * @see BaseActivity
 * @see ActivityOpening1Binding
 * @see ThreadTask
 */
public final class OpeningActivity extends BaseActivity<ActivityOpening1Binding> {

    private final LoggerUtils logger = LoggerUtils.from(getClass());

    ThreadTask<Boolean, Boolean> configSyncTask = new ThreadTask<>();

    /**
     * Guards against duplicate navigation. Set to {@code true} by whichever
     * navigation path fires first. Other paths check this flag and bail.
     */
    private boolean hasNavigated = false;


    /**
     * Determines whether the activity's screen orientation should be locked.
     * This implementation returns {@code true}, forcing the opening screen
     * to remain in portrait mode regardless of device rotation.
     *
     * <p><strong>Design rationale:</strong>
     * Locking the orientation ensures the version information, loading
     * indicator, and any introductory content maintain a consistent layout
     * without unexpected reconfigurations during the brief display period
     * before navigation.
     *
     * @return {@code true} to lock the activity to portrait orientation.
     * @see BaseActivity#shouldLockOrientation()
     */
    @Override
    protected boolean shouldLockOrientation() {
        return true;
    }

    /**
     * Inflates the activity's layout using view binding and returns the generated
     * binding instance for {@code activity_opening_0.xml}. This method is called
     * during the base activity's {@code setContentView()} phase to create the
     * binding object that provides type-safe access to all views in the layout,
     * including the version TextView and any loading animations.
     *
     * @param inflater The layout inflater service used to create the view hierarchy.
     *                 Must not be {@code null}.
     * @return The {@link ActivityOpening1Binding} instance containing references
     * to all views defined in the opening screen layout.
     * @see BaseActivity#inflateBinding(LayoutInflater)
     */
    @Override
    protected ActivityOpening1Binding inflateBinding(LayoutInflater inflater) {
        return ActivityOpening1Binding.inflate(inflater);
    }

    /**
     * Performs post-layout initialization after the content view has been inflated.
     * This method is invoked by the base activity at the end of {@code onCreate()}
     * and kicks off all startup tasks in parallel.
     *
     * <p><strong>Initialization order:</strong>
     * <ol>
     * <li>Loads and displays the current app version via {@link #loadVersionInfo()}.</li>
     * <li>Schedules a 3 second fallback via {@link #scheduleFallbackNavigation()}
     *     that force‑navigates if nothing else has completed.</li>
     * </ol>
     *
     * @see BaseActivity#onLoadedLayout()
     * @see #loadVersionInfo()
     * @see #scheduleFallbackNavigation()
     */
    @Override
    protected void onLoadedLayout() {
        loadVersionInfo();
        scheduleFallbackNavigation();
    }

    /**
     * Loads and displays the current application version information on the screen.
     * This method retrieves both the version name (e.g., "1.2.3") and version code
     * (e.g., "4") from {@link VersionInfo}, formats them as a combined string,
     * logs the result for debugging, and sets the text on the version TextView.
     *
     * <p><strong>Format example:</strong> "1.2.3 (4)"
     *
     * @see VersionInfo#getVersionCode(android.content.Context)
     * @see VersionInfo#getVersionName(android.content.Context)
     */
    private void loadVersionInfo() {
        BaseApplication app = BaseApplication.AppContext;
        String versionCode = String.valueOf(VersionInfo.getVersionCode(app));
        String versionName = String.valueOf(VersionInfo.getVersionName(app));
        String versionInfo = versionName + " (" + versionCode + ")";
        logger.debug("Version result: " + versionInfo);
        binding.tvVersion.setText(versionInfo);
    }



    /**
     * Schedules a fallback navigation task that triggers after a 3-second timeout.
     * This method ensures the opening screen does not hang indefinitely if the
     * configuration sync task takes too long to complete.
     *
     * <p><strong>Behavior:</strong>
     * <ul>
     * <li>Posts a delayed runnable to the main thread with a 3000ms delay.</li>
     * <li>If navigation has already occurred or the activity is finishing, returns early.</li>
     * <li>Sets the {@code hasNavigated} flag to {@code true} to prevent duplicate navigation.</li>
     * <li>Logs a warning indicating that timeout has been reached.</li>
     * <li>Cancels the configuration sync task if it is still running.</li>
     * <li>Forces navigation to the next screen via {@link #proceedToNextScreen()}.</li>
     * </ul>
     *
     * <p>This is a defensive mechanism to prevent the user from being stuck on the
     * opening screen due to network failures or slow API responses.
     *
     * @see #proceedToNextScreen()
     * @see #hasNavigated
     * @see Handler#postDelayed(Runnable, long)
     */
    private void scheduleFallbackNavigation() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (hasNavigated || isFinishing()) return;
            hasNavigated = true;

            logger.warning("Opening screen delay finished, navigating");
            if (!configSyncTask.isCancelled()) configSyncTask.cancel();
    
            proceedToNextScreen();
        }, 1200);
    }




    /**
     * Navigates to the appropriate next screen based on the application's
     * configuration state. This method constructs an intent using
     * {@link #getDestinationIntent()}, adds flags to clear the activity stack,
     * starts the target activity with a fade transition animation, and finishes
     * the current opening screen.
     *
     * <p><strong>Intent flags:</strong>
     * <ul>
     * <li>{@link Intent#FLAG_ACTIVITY_NEW_TASK} - Starts the activity as a new task.</li>
     * <li>{@link Intent#FLAG_ACTIVITY_CLEAR_TASK} - Clears any existing activities
     *     from the task stack before launching.</li>
     * </ul>
     *
     * <p>The method logs whether the locale is already configured for debugging
     * purposes, then applies a fade animation and closes the opening screen.
     *
     * @see #getDestinationIntent()
     * @see ActivityAnimator#animActivityFade(BaseActivity)
     */
    private void proceedToNextScreen() {
        Intent destinationIntent = getDestinationIntent();
        destinationIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (AppConfigsRepo.getConfig().isLocaleConfigured)
            logger.debug("Locale is already configured, opening main activity");
        else logger.debug("Locale is not configured, opening language activity.");

        startActivity(destinationIntent);
        ActivityAnimator.animActivityFade(OpeningActivity.this);
        finish();
    }


    /**
     * Determines the appropriate destination activity based on the current
     * application configuration state. The decision logic evaluates:
     * <ol>
     * <li>If terms and conditions have not been accepted, opens
     *     {@link TermsPolicyActivity}.</li>
     * <li>If terms are accepted but locale is not configured, opens
     *     {@link LanguageActivity}.</li>
     * <li>If both terms and locale are configured, opens {@link MainActivity}.</li>
     * </ol>
     *
     * <p>The method also adds an extra with key
     * {@link TermsPolicyActivity#KEY_ACTIVITY_LAUNCHED_LOCATION} and value
     * {@link TermsPolicyActivity#LAUNCHED_FROM_OPENING_SCREEN} to indicate the
     * origin of the launch for tracking or behavior customization.
     *
     * @return A non-null {@link Intent} targeting the determined destination
     *         {@link BaseActivity} subclass.
     * @see AppConfigsRepo#getConfig()
     * @see TermsPolicyActivity
     * @see LanguageActivity
     * @see MainActivity
     */
    @NonNull
    private Intent getDestinationIntent() {
        Intent destinationIntent;
        AppConfigs appConfig = AppConfigsRepo.getConfig();
        boolean isTermsAccepted = appConfig.isTermsConditionsAgreed;
        boolean isLocaleConfigured = appConfig.isLocaleConfigured;

        Class<? extends BaseActivity<? extends ViewBinding>> nextActivityToOpen =
                isTermsAccepted ? LanguageActivity.class : TermsPolicyActivity.class;

        if (isLocaleConfigured) nextActivityToOpen = MainActivity.class;

        destinationIntent = new Intent(this, nextActivityToOpen);
        destinationIntent.getShortExtra(
                TermsPolicyActivity.KEY_ACTIVITY_LAUNCHED_LOCATION,
                TermsPolicyActivity.LAUNCHED_FROM_OPENING_SCREEN);

        return destinationIntent;
    }

}
