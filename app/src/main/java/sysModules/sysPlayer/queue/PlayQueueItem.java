package sysModules.sysPlayer.queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class PlayQueueItem implements Serializable {
	private static final long serialVersionUID = 1L;
	public static final long RECOVERY_UNSET = Long.MIN_VALUE;
	private static final String EMPTY_STRING = "";
	
	@NonNull private final String title;
	@NonNull private final String url;
	private final int serviceId;
	private final long duration;
	@NonNull private final List<Image> thumbnails;
	@NonNull private final String uploader;
	private final String uploaderUrl;
	@NonNull private final StreamType streamType;
	private boolean autoQueued;
	private long recoveryPosition;
	private transient Throwable error;
	
	public PlayQueueItem(@NonNull StreamInfo info) {
		this(info.getName(), info.getUrl(), info.getServiceId(), info.getDuration(),
			info.getThumbnails(), info.getUploaderName(),
			info.getUploaderUrl(), info.getStreamType());
		this.setStreamInfo(info);
		if (info.getStartPosition() > 0) {
			setRecoveryPosition(info.getStartPosition() * 1000);
		}
	}
	
	public PlayQueueItem(@NonNull StreamInfoItem item) {
		this(item.getName(), item.getUrl(), item.getServiceId(), item.getDuration(),
			item.getThumbnails(), item.getUploaderName(),
			item.getUploaderUrl(), item.getStreamType());
	}
	
	public PlayQueueItem(@NonNull android.net.Uri uri,
	                     @NonNull String title,
	                     @Nullable String uploader,
	                     long duration) {
		this.title = title;
		this.url = uri.toString();
		this.serviceId = -1;
		this.duration = duration;
		this.thumbnails = Collections.emptyList();
		this.uploader = uploader != null ? uploader : EMPTY_STRING;
		this.uploaderUrl = null;
		this.streamType = StreamType.VIDEO_STREAM;
		this.recoveryPosition = RECOVERY_UNSET;
	}
	
	public boolean isLocalItem() {
		return serviceId < 0;
	}
	
	public PlayQueueItem(@Nullable String name,
	                     @Nullable String url,
	                     int serviceId,
	                     long duration,
	                     @NonNull List<Image> thumbnails,
	                     @Nullable String uploader,
	                     @Nullable String uploaderUrl,
	                     @NonNull StreamType streamType) {
		this.title = name != null ? name : EMPTY_STRING;
		this.url = url != null ? url : EMPTY_STRING;
		this.serviceId = serviceId;
		this.duration = duration;
		this.thumbnails = thumbnails;
		this.uploader = uploader != null ? uploader : EMPTY_STRING;
		this.uploaderUrl = uploaderUrl;
		this.streamType = streamType;
		this.recoveryPosition = RECOVERY_UNSET;
	}
	
	public boolean isSameItem(@Nullable PlayQueueItem other) {
		if (other == null) return false;
		return serviceId == other.serviceId && url.equals(other.url);
	}
	
	public @NonNull String getTitle() {return title;}
	
	public @NonNull String getUrl() {return url;}
	
	public int getServiceId() {return serviceId;}
	
	public long getDuration() {return duration;}
	
	public @NonNull List<Image> getThumbnails() {return thumbnails;}
	
	public @NonNull String getUploader() {return uploader;}
	
	public @Nullable String getUploaderUrl() {return uploaderUrl;}
	
	public @NonNull StreamType getStreamType() {return streamType;}
	
	public long getRecoveryPosition() {return recoveryPosition;}
	
	public void setRecoveryPosition(long recoveryPosition) {this.recoveryPosition = recoveryPosition;}
	
	public @Nullable Throwable getError() {return error;}
	
	public void setError(@Nullable Throwable error) {this.error = error;}
	
	public boolean isAutoQueued() {return autoQueued;}
	
	public void setAutoQueued(boolean autoQueued) {this.autoQueued = autoQueued;}
	
	private transient volatile Thread fetchThread;
	private transient StreamInfo streamInfo;

	@Nullable
	public StreamInfo getStreamInfo() {return streamInfo;}

	public void setStreamInfo(@Nullable StreamInfo info) {this.streamInfo = info;}
	
	public void cancelFetch() {
		synchronized (this) {
			Thread t = fetchThread;
			if (t != null) {
				t.interrupt();
				fetchThread = null;
			}
		}
	}
	
	public interface StreamCallback {
		void onSuccess(StreamInfo info);
		void onError(Throwable error);
	}
	
	public void fetchStreamInfo(@NonNull StreamCallback callback) {
		// Return cached info immediately if already fetched
		final StreamInfo cached = streamInfo;
		if (cached != null) {
			callback.onSuccess(cached);
			return;
		}
		cancelFetch();
		Thread t = new Thread(() -> {
			try {
				StreamingService service = null;
				for (StreamingService s : ServiceList.all()) {
					if (s.getServiceId() == serviceId) {
						service = s;
						break;
					}
				}
				if (service == null) {
					callback.onError(new IllegalArgumentException("Unknown service: " + serviceId));
					return;
				}
				StreamInfo info = StreamInfo.getInfo(service, url);
				streamInfo = info;
				fetchThread = null;
				callback.onSuccess(info);
			} catch (Throwable t2) {
				error = t2;
				fetchThread = null;
				callback.onError(t2);
			}
		}, "PlayQueueItem-fetch-" + title);
		fetchThread = t;
		t.start();
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PlayQueueItem that = (PlayQueueItem) o;
		return serviceId == that.serviceId && url.equals(that.url);
	}
	
	@Override
	public int hashCode() {
		return 31 * serviceId + url.hashCode();
	}
	
	@NonNull @Override
	public String toString() {
		return title + " (" + url + ")";
	}
}
