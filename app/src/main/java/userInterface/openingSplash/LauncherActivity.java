package userInterface.openingSplash;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nextgen.R;

import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import sysModules.crashedHandler.AppCrashedInfo;
import sysModules.crashedHandler.AppCrashedRepo;
import sysModules.crashedHandler.CrashLogger;
import sysModules.crashedHandler.GlobalCrashedHandler;
import userInterface.appCrashed.AppCrashedActivity;
import userInterface.languagePicker.LanguageActivity;

/**
 * Entry point activity that determines the app's startup destination based on
 * crash recovery state. This activity acts as a decision router, checking
 * whether the app experienced a crash during the previous session. If a crash
 * is detected, the user is redirected to {@link AppCrashedActivity} with
 * diagnostic information; otherwise, normal app flow proceeds to
 * {@link OpeningActivity}.
 *
 * <p><strong>Activity behavior:</strong>
 * The activity does not display any UI of its own. It immediately evaluates
 * the crash flag from {@link AppConfigsRepo}, clears the activity back stack
 * using intent flags, and finishes itself after starting the target activity.
 * This ensures the launcher activity is not retained in the navigation history.
 *
 * <p><strong>Crash detection mechanism:</strong>
 * The {@code hasAppCrashedRecently} flag is typically set by a global crash
 * handler (e.g., {@link GlobalCrashedHandler}) before the app terminates.
 * This activity resets the flag to {@code false} when a crash is reported,
 * preventing infinite crash loops. The associated {@link AppCrashedInfo} entity
 * is stored in SQLite and retrieved using a fixed row ID constant.
 *
 * <p><strong>Animation:</strong>
 * A fade transition is applied to all activity launches using
 * {@code R.anim.anim_fade_enter} and {@code R.anim.anim_fade_exit}, providing
 * a smooth visual experience when switching between activities.
 *
 * @see AppCompatActivity
 * @see AppCrashedActivity
 * @see OpeningActivity
 * @see AppConfigsRepo
 * @see GlobalCrashedHandler
 */
public class LauncherActivity extends AppCompatActivity {
	
	/**
	 * Initializes the activity and determines the navigation destination based on
	 * recent crash status. This method checks whether the app crashed during the
	 * previous session by reading the {@link AppConfigs#hasAppCrashedRecently} flag.
	 * If a crash occurred, the user is redirected to {@link AppCrashedActivity}
	 * with crash details; otherwise, normal flow proceeds to {@link OpeningActivity}.
	 *
	 * <p><strong>Crash recovery flow:</strong>
	 * When {@code hasAppCrashedRecently} is {@code true}, the flag is immediately
	 * reset to {@code false} to prevent infinite crash loops. The {@link AppCrashedInfo}
	 * entity (with fixed box ID {@link AppCrashedInfo#APP_CRASHED_OBJECT_BOX_ID})
	 * is retrieved from SQLite and passed as a serialized extra to the crash
	 * reporting screen. Both paths clear the activity back stack using
	 * {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_CLEAR_TASK},
	 * ensuring the user cannot return to this launcher activity via the back button.
	 *
	 * <p><strong>Visual transition:</strong>
	 * A fade animation is applied to both navigation paths via
	 * {@link #overridePendingTransition(int, int)} using
	 * {@code R.anim.anim_fade_enter} and {@code R.anim.anim_fade_exit}.
	 *
	 * @param savedInstanceState Previously saved state bundle. Not used in this
	 *                           implementation as the activity always finishes
	 *                           immediately after redirecting.
	 * @see AppConfigsRepo#getConfig()
	 * @see AppConfigs#hasAppCrashedRecently
	 * @see AppCrashedActivity
	 * @see OpeningActivity
	 * @see #finish()
	 */
	@Override public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// If a crash report could not be shown right after the crash, show it now.
		if (CrashLogger.launchPendingReportIfAny(this)) {
			finish();
			return;
		}
		AppConfigs appConfig = AppConfigsRepo.getConfig();
		if (appConfig.hasAppCrashedRecently) {
			appConfig.hasAppCrashedRecently = false;
			appConfig.save();
			
			AppCrashedInfo crashedInfo = AppCrashedRepo.get();
			
			Intent intent = new Intent(this, AppCrashedActivity.class);
			intent.putExtra(AppCrashedActivity.CRASHED_INFO_INTENT_KEY, crashedInfo);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_fade_exit);
			startActivity(intent);
			finish();
		} else {
			Intent intent = new Intent(this, OpeningActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_fade_exit);
			startActivity(intent);
			finish();
		}
	}
	
	/**
	 * Forces an application crash by intentionally accessing an array index out of bounds.
	 * This method is strictly for testing purposes only, specifically to verify crash
	 * reporting mechanisms, error handling logic, or analytics tracking of unexpected exceptions.
	 * The method suppresses expected warnings about the array access violation and the
	 * data flow issue since the crash is deliberate.
	 */
	@SuppressWarnings({"MismatchedReadAndWriteOfArray", "DataFlowIssue"})
	private static void makeAppCrash() {
		String[] all = new String[10];
		String string = all[11];  // Deliberate ArrayIndexOutOfBoundsException
	}
	
	/**
	 * Launches the FeedbackActivity in a new task stack specifically for debug testing.
	 * This test method clears the activity stack and applies custom fade animations
	 * before transitioning to the feedback screen. After starting the intent, the
	 * current activity is finished to simulate a clean navigation flow. This approach
	 * is intended for testing user feedback flows, deep linking scenarios, or
	 * activity lifecycle behaviors without preserving previous activities in the back stack.
	 */
	private void debugTestLaunch() {
		Intent intent = new Intent(this, LanguageActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		overridePendingTransition(R.anim.anim_fade_enter, R.anim.anim_fade_exit);
		startActivity(intent);
		finish();
	}
}
