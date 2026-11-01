package sysModules.sysPlayer.helper;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.mediacodec.MediaCodecAdapter;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

/**
 * A {@link MediaCodecVideoRenderer} which always enables the output surface
 * workaround that ExoPlayer applies on several devices known to implement
 * {@link android.media.MediaCodec#setOutputSurface} incorrectly.
 *
 * <p>This prevents "Unrecoverable player error" crashes on devices not in
 * ExoPlayer's known-device list.</p>
 */
public final class CustomMediaCodecVideoRenderer extends MediaCodecVideoRenderer {

    @SuppressWarnings("checkstyle:ParameterNumber")
    public CustomMediaCodecVideoRenderer(final Context context,
                                         final MediaCodecAdapter.Factory codecAdapterFactory,
                                         final MediaCodecSelector mediaCodecSelector,
                                         final long allowedJoiningTimeMs,
                                         final boolean enableDecoderFallback,
                                         @Nullable final Handler eventHandler,
                                         @Nullable final VideoRendererEventListener eventListener,
                                         final int maxDroppedFramesToNotify) {
        super(context, codecAdapterFactory, mediaCodecSelector, allowedJoiningTimeMs,
                enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(final String name) {
        return true;
    }
}
