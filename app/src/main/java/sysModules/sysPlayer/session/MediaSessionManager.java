package sysModules.sysPlayer.session;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;

import coreUtils.library.process.LoggerUtils;
import sysModules.newPipeLib.parsers.YtSteamExtractor;
import sysModules.sysPlayer.engine.MediaEngine;
import sysModules.sysPlayer.queue.PlayQueueItem;

/**
 * Manages the integration between an ExoPlayer instance and Android's media session
 * framework. This class handles media session lifecycle, metadata publishing,
 * queue navigation, and custom action providers for system UI components such as
 * lock screen controls, Android Auto, Wear OS, and Bluetooth devices.
 *
 * <p><strong>Core responsibilities:</strong>
 * <ul>
 * <li>Creates and manages a {@link MediaSessionCompat} instance.</li>
 * <li>Binds the media session to an ExoPlayer via {@link MediaSessionConnector}.</li>
 * <li>Publishes track metadata (title, artist, duration, artwork URI) to system UI.</li>
 * <li>Manages a queue navigator for skip-to-next/previous and queue item selection.</li>
 * <li>Provides custom action support (e.g., "Close" button) on media controls.</li>
 * <li>Handles session lifecycle: connect, release, and metadata invalidation.</li>
 * </ul>
 *
 * <p>The {@code @SuppressWarnings("deprecation")} annotation silences warnings
 * for deprecated APIs required for backward compatibility.
 *
 * @see MediaSessionCompat
 * @see MediaSessionConnector
 * @see PlayQueueNavigator
 */
@SuppressWarnings("deprecation") public final class MediaSessionManager {
	private static final LoggerUtils logger = LoggerUtils.from(MediaSessionManager.class);
	
	private final Context context;
	private final MediaEngine engine;
	
	@Nullable private MediaSessionCompat mediaSession;
	@Nullable private MediaSessionConnector sessionConnector;
	@Nullable private PlayQueueNavigator queueNavigator;
	
	/**
	 * Constructs a MediaSessionManager that manages the integration between a media
	 * session, an ExoPlayer instance, and system media controls. The manager handles
	 * the lifecycle of the media session, session connector, and metadata publishing.
	 *
	 * @param context The application context used to create the media session.
	 *                The application context is stored to avoid memory leaks.
	 * @param engine  The media engine that provides playback state and metadata.
	 */
	public MediaSessionManager(@NonNull Context context, @NonNull MediaEngine engine) {
		this.context = context.getApplicationContext();
		this.engine = engine;
	}
	
	/**
	 * Initializes and activates the media session, establishing the connection to
	 * system media controls. This method performs the following steps:
	 *
	 * <ol>
	 * <li>Releases any existing media session resources via {@link #release()}.</li>
	 * <li>Creates a new {@link MediaSessionCompat} with the tag "VidFlowPlayback".</li>
	 * <li>Sets the session as active, making it visible to system UI components.</li>
	 * <li>Creates a {@link MediaSessionConnector} and attaches it to the session.</li>
	 * <li>Sets the metadata provider to use {@link #buildMetadata(Player)}.</li>
	 * <li>If a queue navigator is already configured, attaches it to the connector.</li>
	 * </ol>
	 *
	 * <p>After calling this method, the media session is ready to receive transport
	 * controls and publish playback information. The session remains active until
	 * {@link #release()} is called.
	 *
	 * @see #release()
	 * @see MediaSessionCompat
	 * @see MediaSessionConnector
	 * @see #setQueueNavigator(PlayQueueNavigator)
	 */
	public void connect() {
		release();
		mediaSession = new MediaSessionCompat(context, "VidFlowPlayback");
		mediaSession.setActive(true);
		sessionConnector = new MediaSessionConnector(mediaSession);
		sessionConnector.setEnabledPlaybackActions(
				PlaybackStateCompat.ACTION_PLAY |
				PlaybackStateCompat.ACTION_PAUSE |
				PlaybackStateCompat.ACTION_PLAY_PAUSE |
				PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
				PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
				PlaybackStateCompat.ACTION_SEEK_TO |
				PlaybackStateCompat.ACTION_STOP |
				PlaybackStateCompat.ACTION_FAST_FORWARD |
				PlaybackStateCompat.ACTION_REWIND);
		sessionConnector.setMediaMetadataProvider(this::buildMetadata);
		if (queueNavigator != null) {
			sessionConnector.setQueueNavigator(queueNavigator);
		}
		logger.debug("MediaSession connected");
	}
	
	/**
	 * Sets the ExoPlayer instance to be controlled via the media session. This method
	 * delegates to the underlying {@link MediaSessionConnector}, which handles the
	 * binding between the media session and the player's transport controls.
	 *
	 * <p>Passing {@code null} disconnects the current player from the media session,
	 * effectively disabling media control integration. The session remains active
	 * but no longer controls any player.
	 *
	 * <p>This method should be called after the session is initialized and before
	 * the session is made active. Player changes during playback are supported but
	 * may cause transient UI glitches in system controls.
	 *
	 * @param player The ExoPlayer instance to bind to the media session, or
	 *               {@code null} to disconnect the current player.
	 * @see MediaSessionConnector#setPlayer(Player)
	 */
	public void setPlayer(@Nullable Player player) {
		if (sessionConnector != null) {
			sessionConnector.setPlayer(player);
		}
	}
	
	/**
	 * Sets a custom queue navigator for the media session. The navigator enables
	 * system UI components to display the play queue and support skip-to-next,
	 * skip-to-previous, and skip-to-queue-item actions.
	 *
	 * <p>Passing {@code null} removes the queue navigator, disabling queue-related
	 * features in system media controls (only basic play/pause/stop will be shown).
	 *
	 * <p>The navigator is typically created with references to the play queue and
	 * media engine, and should be set after the session connector is initialized.
	 *
	 * @param navigator The {@link PlayQueueNavigator} to use for queue navigation,
	 *                  or {@code null} to remove the current navigator.
	 * @see PlayQueueNavigator
	 * @see MediaSessionConnector#setQueueNavigator(MediaSessionConnector.QueueNavigator)
	 */
	public void setQueueNavigator(@Nullable PlayQueueNavigator navigator) {
		this.queueNavigator = navigator;
		if (sessionConnector != null) {
			sessionConnector.setQueueNavigator(navigator);
		}
	}
	
	/**
	 * Sets a custom "Close" action on the media session's transport controls.
	 * When a callback is provided, a close button (using system icon
	 * {@link android.R.drawable#ic_menu_close_clear_cancel}) is added to system UI
	 * components (lock screen, Android Auto, etc.). The callback is executed when
	 * the user taps this close button.
	 *
	 * <p>Passing {@code null} removes the custom action provider, hiding the
	 * close button from system media controls.
	 *
	 * @param callback Runnable to execute when close action is triggered,
	 *                 or {@code null} to remove the close action.
	 */
	public void setCloseCallback(@Nullable Runnable callback) {
		if (sessionConnector == null) return;
		if (callback != null) {
			sessionConnector.setCustomActionProviders(
				new SessionConnectorActionProvider(
					"ACTION_CLOSE", "Close",
					android.R.drawable.ic_menu_close_clear_cancel, callback));
		} else {
			sessionConnector.setCustomActionProviders();
		}
	}
	
	/**
	 * Invalidates the current media session metadata, forcing the system UI to
	 * refresh and fetch the latest metadata. This method should be called whenever
	 * track metadata changes, such as when a new track starts playing, the title
	 * or artist information is updated, or a higher-quality thumbnail becomes
	 * available.
	 *
	 * <p>After invalidation, the system will request fresh metadata via the
	 * {@link MediaSessionConnector} callbacks, which in turn call
	 * {@link #buildMetadata(Player)} to construct the updated metadata.
	 *
	 * <p>Common scenarios requiring invalidation:
	 * <ul>
	 * <li>Track changes (play next/previous, queue selection).</li>
	 * <li>Dynamic metadata updates during live streams.</li>
	 * <li>Thumbnail loading completes after initial metadata publication.</li>
	 * </ul>
	 *
	 * @see MediaSessionConnector#invalidateMediaSessionMetadata()
	 * @see #buildMetadata(Player)
	 */
	public void invalidateMetadata() {
		if (sessionConnector != null) {
			sessionConnector.invalidateMediaSessionMetadata();
		}
	}
	
	/**
	 * Returns the underlying {@link MediaSessionCompat} instance. This provides
	 * direct access to the media session for advanced operations not covered by
	 * this wrapper, such as setting custom session callbacks or managing playback
	 * state flags beyond standard transport controls.
	 *
	 * @return The media session instance, or {@code null} if the session has not
	 * been initialized or has been released.
	 * @see #release()
	 */
	@Nullable
	public MediaSessionCompat getMediaSession() {
		return mediaSession;
	}
	
	/**
	 * Returns the media session token for integration with external components.
	 * The token can be used with {@link MediaControllerCompat}
	 * to create a media controller from another process, or to share playback state
	 * with system components such as Android Auto, Wear OS, and Bluetooth devices.
	 *
	 * <p><strong>Typical usage:</strong>
	 * <pre>
	 * MediaSessionCompat.Token token = mediaSessionManager.getSessionToken();
	 * MediaControllerCompat controller = new MediaControllerCompat(context, token);
	 * </pre>
	 *
	 * @return The session token, or {@code null} if no media session is currently
	 * active or the session has been released.
	 * @see MediaSessionCompat#getSessionToken()
	 */
	@Nullable
	public MediaSessionCompat.Token getSessionToken() {
		return mediaSession != null ? mediaSession.getSessionToken() : null;
	}
	
	/**
	 * Releases all resources held by the MediaSession manager. This method should
	 * be called when the player is being destroyed (e.g., in onDestroy() of an
	 * Activity or Fragment) to prevent memory leaks and unregister from system
	 * media controls.
	 *
	 * <p><strong>Cleanup performed:</strong>
	 * <ul>
	 * <li>Disconnects the session connector from the player by setting player to null.</li>
	 * <li>Releases the session connector reference.</li>
	 * <li>Deactivates and releases the MediaSessionCompat instance.</li>
	 * <li>Clears the queue navigator reference.</li>
	 * </ul>
	 *
	 * <p>After release, the MediaSession is no longer available for system
	 * integration and should not be used further.
	 */
	public void release() {
		if (sessionConnector != null) {
			sessionConnector.setPlayer(null);
			sessionConnector = null;
		}
		if (mediaSession != null) {
			mediaSession.setActive(false);
			mediaSession.release();
			mediaSession = null;
		}
		queueNavigator = null;
		logger.debug("MediaSession released");
	}
	
	/**
	 * Builds media metadata for the currently playing track to be displayed on
	 * system UI components (lock screen, Android Auto, Wear OS, etc.).
	 *
	 * <p><strong>Fields populated:</strong>
	 * <ul>
	 * <li>TITLE – Track title from {@link PlayQueueItem#getTitle()}.</li>
	 * <li>ARTIST – Uploader/channel name from {@link PlayQueueItem#getUploader()}.</li>
	 * <li>DURATION – Track duration in milliseconds (converted from seconds).</li>
	 * <li>ALBUM_ART_URI / ART_URI – URL of the first available thumbnail image.</li>
	 * </ul>
	 *
	 * <p>If there is no currently playing item, an empty metadata builder is returned
	 * to avoid null pointer exceptions. Duration is multiplied by 1000L to convert
	 * from seconds to milliseconds as expected by MediaMetadataCompat constants.
	 *
	 * @param player The current {@link Player} instance (used to query current item).
	 * @return A {@link MediaMetadataCompat} object containing track metadata.
	 */
	@Nullable private MediaMetadataCompat externalMetadata;

	/** Metadata for a player that is not driven by the engine's play queue. */
	public void setExternalMetadata(@Nullable String title, @Nullable String artist, long durationMs,
	                                @Nullable String artUrl) {
		MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
			.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title == null ? "" : title)
			.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist == null ? "" : artist);
		if (durationMs > 0) b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
		if (artUrl != null && !artUrl.isEmpty()) {
			b.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUrl);
			b.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUrl);
		}
		externalMetadata = b.build();
		invalidateMetadata();
	}

	public void clearExternalMetadata() {
		externalMetadata = null;
	}

	private MediaMetadataCompat buildMetadata(@NonNull Player player) {
		if (externalMetadata != null) return externalMetadata;
		PlayQueueItem currentItem = engine.getCurrentItem();
		if (currentItem == null) {
			return new MediaMetadataCompat.Builder().build();
		}
		
		MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
			.putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentItem.getTitle())
			.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentItem.getUploader())
			.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentItem.getDuration() * 1000L);
		
		List<Image> thumbnails = currentItem.getThumbnails();
		if (!thumbnails.isEmpty()) {
			String artUrl = YtSteamExtractor.getBestImageQuality(thumbnails);
			builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUrl);
			builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUrl);
		}
		return builder.build();
	}
}
