package sysModules.sysPlayer.playback;

import android.content.Context;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.video.PlaceholderSurface;

/**
 * Prevents "Unrecoverable player error" by managing the video surface lifecycle.
 * When the surface is destroyed (e.g. during rotation or backgrounding), a
 * {@link PlaceholderSurface} is set on the player to keep it in a valid state.
 * When the surface is recreated, the real surface is restored.
 *
 * <p>Based on ExoPlayer issue #2703 workaround.</p>
 */
public final class SurfaceHolderCallback implements SurfaceHolder.Callback {

    private final Context context;
    private final Player player;
    private PlaceholderSurface placeholderSurface;

    public SurfaceHolderCallback(final Context context, final Player player) {
        this.context = context.getApplicationContext();
        this.player = player;
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        player.setVideoSurface(holder.getSurface());
    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder,
                               final int format,
                               final int width,
                               final int height) {
        /* No-op */
    }

    @Override
    public void surfaceDestroyed(final SurfaceHolder holder) {
        if (placeholderSurface == null) {
            placeholderSurface = PlaceholderSurface.newInstanceV17(context, false);
        }
        player.setVideoSurface(placeholderSurface);
    }

    public void release() {
        if (placeholderSurface != null) {
            placeholderSurface.release();
            placeholderSurface = null;
        }
    }
}
