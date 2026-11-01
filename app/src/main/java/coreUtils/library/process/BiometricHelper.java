package coreUtils.library.process;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static coreUtils.library.strings.StringHelper.getText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.nextgen.R;

import java.util.concurrent.Executor;

import coreUtils.base.BaseActivity;

/**
 * A utility class for managing biometric authentication processes within the application.
 *
 * <p>This class simplifies the usage of {@link BiometricPrompt} by
 * handling the availability checks and providing a simple callback mechanism for
 * authentication results. It supports both strong biometrics and device credentials
 * (PIN, pattern, or password).</p>
 */
public final class BiometricHelper {

    /**
     * Logger instance for recording events and errors related to biometric authentication.
     */
    private static final LoggerUtils logger = LoggerUtils.from(BiometricHelper.class);

    private BiometricHelper() {}

    /**
     * Callback interface used to handle the result of a biometric authentication process.
     */
    public interface AuthResultCallback {
        /**
         * Invoked when the biometric authentication process completes.
         *
         * @param success {@code true} if the user was successfully authenticated;
         *                {@code false} if the authentication failed, was canceled, or is unavailable.
         */
        void onResult(boolean success);
    }

    /**
     * Initiates the biometric authentication process using strong biometrics or device credentials.
     * <p>
     * This method checks for the availability of biometric hardware and security settings.
     * If available, it displays a system prompt to the user. The result of the
     * authentication attempt is returned via the provided callback.
     *
     * @param activity The activity context used to host the biometric prompt.
     * @param callback The callback to receive the authentication result (true if successful, false otherwise).
     */
    public static void authenticate(@Nullable BaseActivity<?> activity,
                                    @NonNull AuthResultCallback callback) {
        if (activity == null) {
            logger.error("Activity is null, cannot authenticate");
            callback.onResult(false);
            return;
        }

        BiometricManager biometricManager = BiometricManager.from(activity);
        int authenticators;

        authenticators = BIOMETRIC_STRONG | DEVICE_CREDENTIAL;
        int authResult = biometricManager.canAuthenticate(authenticators);
        if (authResult == BiometricManager.BIOMETRIC_SUCCESS) {
            logger.info("Biometric authentication available");
            executePrompt(activity, authenticators, callback);
        } else {
            logger.error("Biometric authentication not available");
            callback.onResult(false);
        }
    }

    /**
     * Initializes and displays the biometric prompt to the user.
     * <p>
     * This method configures the {@link BiometricPrompt} with the specified authenticators,
     * handles the authentication lifecycle, and notifies the caller of the result via
     * the provided callback.
     * </p>
     *
     * @param activity       The activity context used to host the prompt and provide the main executor.
     * @param authenticators A bitmask of {@link BiometricManager.Authenticators}
     *                       defining the allowed authentication methods.
     * @param callback       The callback to be invoked with the authentication result (true if
     *                       successful, false otherwise).
     */
    private static void executePrompt(@NonNull BaseActivity<?> activity, int authenticators,
                                      @NonNull AuthResultCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);
        BiometricPrompt.PromptInfo.Builder builder =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getText(R.string.label_unlock_requires))
                        .setAllowedAuthenticators(authenticators);

        if ((authenticators & DEVICE_CREDENTIAL) == 0) {
            builder.setNegativeButtonText(getText(R.string.label_cancel));
        }

        BiometricPrompt.PromptInfo promptInfo = builder.build();
        BiometricPrompt biometricPrompt =
                new BiometricPrompt(activity, executor,
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                    @NonNull BiometricPrompt.AuthenticationResult result) {
                                super.onAuthenticationSucceeded(result);
                                callback.onResult(true);
                            }

                            @Override
                            public void onAuthenticationError(
                                    int errorCode, @NonNull CharSequence errString) {
                                super.onAuthenticationError(errorCode, errString);
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                    logger.error("Biometric authentication failed");
                                }

                                callback.onResult(false);
                            }
                        });

        biometricPrompt.authenticate(promptInfo);
    }
}