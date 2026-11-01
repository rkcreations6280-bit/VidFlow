package sysModules.sysPlayer.session;

import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

/**
 * Provides a custom media session action that can be exposed to system UI
 * components such as lock screen controls, Android Auto, Wear OS, and Bluetooth
 * devices. This class implements {@link MediaSessionConnector.CustomActionProvider}
 * to define a single custom action with an identifier, display name, icon,
 * and an optional callback to execute when the action is triggered.
 *
 * <p>These providers are registered with a {@link MediaSessionConnector} and
 * their custom actions become available in system media controls. The action's
 * callback runs on the UI thread when the user interacts with the control.
 *
 * <p>The {@code @SuppressWarnings("ALL")} annotation is used to silence
 * lint warnings that may not apply to this internal media session utility class.
 *
 * @see MediaSessionConnector.CustomActionProvider
 * @see PlaybackStateCompat.CustomAction
 * @see #getCustomAction(Player)
 * @see #onCustomAction(Player, String, Bundle)
 */
@SuppressWarnings("ALL") public final class SessionConnectorActionProvider
	implements MediaSessionConnector.CustomActionProvider {
	
	private final String action;
	private final String displayName;
	private final @DrawableRes int iconResId;
	@Nullable private final Runnable callback;
	
	/**
	 * Constructs a new SessionConnectorActionProvider with the specified action ID,
	 * display name, icon resource, and optional callback. This provider is used to
	 * define custom media session actions that can be exposed to system UI components
	 * such as lock screen controls, wearables, and auto companions.
	 *
	 * @param action      The unique string identifier for this custom action
	 *                    (e.g., "ACTION_LIKE").
	 * @param displayName The human-readable label shown in system UI for this action.
	 * @param iconResId   Resource ID of the icon representing this action.
	 * @param callback    Optional {@link Runnable} executed when the action is
	 *                    triggered. May be {@code null}.
	 */
	public SessionConnectorActionProvider(@NonNull String action,
	                                      @NonNull String displayName,
	                                      int iconResId,
	                                      @Nullable Runnable callback) {
		this.action = action;
		this.displayName = displayName;
		this.iconResId = iconResId;
		this.callback = callback;
	}
	
	/**
	 * Creates and returns a {@link PlaybackStateCompat.CustomAction} instance for this
	 * provider. This method is called by the session connector to build the action
	 * that will be added to the media session's playback state.
	 *
	 * @param player The current {@link Player} instance (unused in this implementation,
	 *               but required by the interface contract).
	 * @return A configured {@link PlaybackStateCompat.CustomAction} containing the
	 * action ID, display name, and icon resource.
	 */
	@NonNull @Override
	public PlaybackStateCompat.CustomAction getCustomAction(@NonNull Player player) {
		return new PlaybackStateCompat
			.CustomAction.Builder(action, displayName, iconResId).build();
	}
	
	/**
	 * Handles execution of this custom action when triggered by the system. If the
	 * incoming action ID matches this provider's action and a callback is present,
	 * the callback is executed. This allows the session connector to route custom
	 * actions from system UI back to the application logic.
	 *
	 * @param player The current {@link Player} instance (unused in this implementation).
	 * @param action The action ID being triggered.
	 * @param extras Optional extras bundle containing additional data (unused here).
	 */
	@Override
	public void onCustomAction(@NonNull Player player,
	                           @NonNull String action,
	                           @Nullable Bundle extras) {
		if (this.action.equals(action) && callback != null) {
			callback.run();
		}
	}
}
