package sysModules.downloadSys;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Locale;

import sysModules.sysPlayer.datasource.YoutubeHttpDataSource;

/**
 * Backup download path for YouTube, used when yt-dlp fails. Streams are fetched through the same
 * data source the player uses, in chunks, and video + audio are joined with Android's own
 * MediaMuxer. Partial files are kept, so pause / resume continues where it stopped.
 */
public final class NewPipeDownloader {

	public interface Sink {
		/** Called often while bytes arrive. {@code total} may be 0 when unknown. */
		void onBytes(long done, long total, long bytesPerSecond);

		/** True when the user paused or cancelled. */
		boolean stopped();
	}

	public static final class Stopped extends IOException {
		Stopped() {
			super("stopped");
		}
	}

	private static final int CHUNK = 6 * 1024 * 1024;

	private NewPipeDownloader() {}

	/** Downloads into {@code workDir} and returns the finished file. */
	public static File run(String url, File workDir, String baseName, boolean audioOnly, int height,
	                       long estimate, Sink sink) throws Exception {
		StreamInfo info = NewPipeEngine.load(url);
		//noinspection ResultOfMethodCallIgnored
		workDir.mkdirs();

		if (audioOnly) {
			AudioStream audio = NewPipeEngine.pickAudio(info, true);
			if (audio == null) throw new Exception("No audio stream is available.");
			String ext = suffixOf(audio.getFormat() == null ? null : audio.getFormat().getSuffix(), "m4a");
			File out = new File(workDir, baseName + "." + ext);
			File part = new File(workDir, "audio.tmpa.part");
			Progress progress = new Progress(sink, Math.max(estimate, NewPipeEngine.sizeOf(audio)));
			fetch(audio, part, progress);
			rename(part, out);
			return out;
		}

		VideoStream videoOnly = NewPipeEngine.pickVideoOnly(info, height);
		AudioStream audio = NewPipeEngine.pickAudio(info, true);
		VideoStream muxed = NewPipeEngine.pickMuxed(info, height);

		// Prefer the best option that does not exceed the chosen quality.
		boolean useSplit = videoOnly != null && audio != null
			&& audio.getFormat() == org.schabi.newpipe.extractor.MediaFormat.M4A
			&& (muxed == null || videoOnly.getHeight() > muxed.getHeight());

		File out = new File(workDir, baseName + ".mp4");
		if (useSplit) {
			File videoPart = new File(workDir, "video.tmpv.part");
			File audioPart = new File(workDir, "audio.tmpa.part");
			long total = NewPipeEngine.sizeOf(videoOnly) + NewPipeEngine.sizeOf(audio);
			Progress progress = new Progress(sink, Math.max(estimate, total));
			fetch(videoOnly, videoPart, progress);
			progress.nextFile();
			fetch(audio, audioPart, progress);
			if (sink.stopped()) throw new Stopped();
			mux(videoPart, audioPart, out);
			//noinspection ResultOfMethodCallIgnored
			videoPart.delete();
			//noinspection ResultOfMethodCallIgnored
			audioPart.delete();
			return out;
		}
		if (muxed == null) throw new Exception("No downloadable video stream was found.");
		File part = new File(workDir, "video.tmpv.part");
		Progress progress = new Progress(sink, Math.max(estimate, NewPipeEngine.sizeOf(muxed)));
		fetch(muxed, part, progress);
		rename(part, out);
		return out;
	}

	private static String suffixOf(String suffix, String fallback) {
		return suffix == null || suffix.isEmpty() ? fallback : suffix.toLowerCase(Locale.US);
	}

	private static void rename(File from, File to) throws IOException {
		if (to.exists() && !to.delete()) throw new IOException("Could not replace the old file.");
		if (!from.renameTo(to)) throw new IOException("Could not finish the file.");
	}

	// ── Progress across one or two files ─────────────────────────────

	private static final class Progress {
		final Sink sink;
		final long total;
		long finishedFiles = 0;
		long current = 0;
		final long startedAt = System.currentTimeMillis();
		long startedBytes = -1;

		Progress(Sink sink, long total) {
			this.sink = sink;
			this.total = total;
		}

		void report(long inCurrent) {
			current = inCurrent;
			long done = finishedFiles + current;
			if (startedBytes < 0) startedBytes = done;
			long elapsed = Math.max(1, System.currentTimeMillis() - startedAt);
			long speed = (done - startedBytes) * 1000L / elapsed;
			sink.onBytes(done, Math.max(total, done), Math.max(0, speed));
		}

		void nextFile() {
			finishedFiles += current;
			current = 0;
		}
	}

	// ── Fetching one stream (resumable, chunked) ─────────────────────

	private static void fetch(Stream stream, File part, Progress progress) throws Exception {
		String link = stream.getContent();
		long known = NewPipeEngine.sizeOf(stream);
		long have = part.exists() ? part.length() : 0;
		if (known > 0 && have > known) {
			//noinspection ResultOfMethodCallIgnored
			part.delete();
			have = 0;
		}

		DataSource source = new YoutubeHttpDataSource.Factory()
			.setRangeParameterEnabled(true)
			.setRnParameterEnabled(true)
			.setConnectTimeoutMs(15000)
			.setReadTimeoutMs(20000)
			.setAllowCrossProtocolRedirects(true)
			.createDataSource();

		byte[] buffer = new byte[64 * 1024];
		try (RandomAccessFile file = new RandomAccessFile(part, "rw")) {
			file.seek(have);
			progress.report(have);
			while (known <= 0 || have < known) {
				if (progress.sink.stopped()) throw new Stopped();
				long want = known > 0 ? Math.min(CHUNK, known - have) : CHUNK;
				long chunkRead = 0;
				try {
					source.open(new DataSpec(Uri.parse(link), have, want));
					while (chunkRead < want) {
						if (progress.sink.stopped()) throw new Stopped();
						int read = source.read(buffer, 0, (int) Math.min(buffer.length, want - chunkRead));
						if (read == C.RESULT_END_OF_INPUT) break;
						file.write(buffer, 0, read);
						chunkRead += read;
						have += read;
						progress.report(have);
					}
				} finally {
					try {
						source.close();
					} catch (Throwable ignored) {
					}
				}
				if (chunkRead == 0) break;                 // nothing more to read
				if (known <= 0 && chunkRead < want) break; // unknown size: short chunk means the end
			}
		}
		if (known > 0 && have < known) throw new IOException("The connection ended early.");
	}

	// ── Joining video + audio ────────────────────────────────────────

	private static void mux(File videoFile, File audioFile, File out) throws Exception {
		if (out.exists() && !out.delete()) throw new IOException("Could not replace the old file.");
		MediaExtractor video = new MediaExtractor();
		MediaExtractor audio = new MediaExtractor();
		MediaMuxer muxer = null;
		boolean started = false;
		try {
			video.setDataSource(videoFile.getAbsolutePath());
			audio.setDataSource(audioFile.getAbsolutePath());
			int videoIndex = trackOf(video, "video/");
			int audioIndex = trackOf(audio, "audio/");
			if (videoIndex < 0 || audioIndex < 0) throw new IOException("Could not read the streams.");
			video.selectTrack(videoIndex);
			audio.selectTrack(audioIndex);

			muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
			int videoTrack = muxer.addTrack(video.getTrackFormat(videoIndex));
			int audioTrack = muxer.addTrack(audio.getTrackFormat(audioIndex));
			muxer.start();
			started = true;

			copy(video, muxer, videoTrack, video.getTrackFormat(videoIndex));
			copy(audio, muxer, audioTrack, audio.getTrackFormat(audioIndex));
		} finally {
			try {
				if (muxer != null) {
					if (started) muxer.stop();
					muxer.release();
				}
			} catch (Throwable ignored) {
			}
			video.release();
			audio.release();
		}
	}

	private static int trackOf(MediaExtractor extractor, String prefix) {
		for (int i = 0; i < extractor.getTrackCount(); i++) {
			String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
			if (mime != null && mime.startsWith(prefix)) return i;
		}
		return -1;
	}

	private static void copy(MediaExtractor extractor, MediaMuxer muxer, int track,
	                         MediaFormat format) {
		int size = 1024 * 1024;
		if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
			size = Math.max(size, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
		}
		ByteBuffer buffer = ByteBuffer.allocate(size);
		MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
		while (true) {
			buffer.clear();
			int read = extractor.readSampleData(buffer, 0);
			if (read < 0) break;
			info.offset = 0;
			info.size = read;
			info.presentationTimeUs = extractor.getSampleTime();
			info.flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
				? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
			muxer.writeSampleData(track, buffer, info);
			extractor.advance();
		}
	}
}
