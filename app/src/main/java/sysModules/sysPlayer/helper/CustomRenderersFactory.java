package sysModules.sysPlayer.helper;

import android.content.Context;
import android.os.Handler;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;

/**
 * A {@link DefaultRenderersFactory} which uses {@link CustomMediaCodecVideoRenderer}
 * instead of the default {@code MediaCodecVideoRenderer}. This forces the output
 * surface workaround on all devices, preventing crashes during surface changes.
 *
 * <p>Also removes ExoPlayer's reflection-based extension loading since no
 * extensions are bundled with the app.</p>
 */
public final class CustomRenderersFactory extends DefaultRenderersFactory {

    public CustomRenderersFactory(final Context context) {
        super(context);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    protected void buildVideoRenderers(final Context context,
                                       @ExtensionRendererMode final int extensionRendererMode,
                                       final MediaCodecSelector mediaCodecSelector,
                                       final boolean enableDecoderFallback,
                                       final Handler eventHandler,
                                       final VideoRendererEventListener eventListener,
                                       final long allowedVideoJoiningTimeMs,
                                       final ArrayList<Renderer> out) {
        out.add(new CustomMediaCodecVideoRenderer(context, getCodecAdapterFactory(),
                mediaCodecSelector, allowedVideoJoiningTimeMs, enableDecoderFallback,
                eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY));
    }
}
