package coreUtils.library.media;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
/**
 * Converts video files to audio by extracting the audio track using Android's MediaExtractor API.
 * <p>
 * This class provides asynchronous audio extraction from video or audio files, supporting
 * various output formats including MP3 (raw extraction), AAC/MP4A (MP4 container), and
 * Opus/Vorbis (WebM container). It manages background tasks with lifecycle awareness,
 * progress reporting, and proper resource cleanup on cancellation or error.
 * </p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>Async Extraction:</b> Runs extraction on background threads without blocking UI</li>
 *   <li><b>Lifecycle Awareness:</b> Automatically cancels tasks when LifecycleOwner is destroyed</li>
 *   <li><b>Progress Reporting:</b> Real-time extraction progress via ConversionListener</li>
 *   <li><b>Format Detection:</b> Automatically detects audio MIME type and chooses right output container</li>
 *   <li><b>Error Handling:</b> Cleans up partial files and reports detailed error messages</li>
 * </ul>
 * </p>
 *
 * <p><b>Supported Input Formats:</b>
 * Any media file containing an audio track (MP4, MKV, AVI, MOV, etc.) as long as
 * Android's MediaExtractor can read it.
 * </p>
 *
 * <p><b>Output Format Mapping:</b>
 * <ul>
 *   <li>audio/mpeg → Raw MP3 file</li>
 *   <li>audio/aac or audio/mp4a-latm → MP4 container</li>
 *   <li>audio/opus or audio/vorbis → WebM container</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * Vid2AudioConverter converter = new Vid2AudioConverter();
 * converter.extractAudio(this, "/sdcard/video.mp4", "/sdcard/audio.mp3",
 *     new ConversionListener() {
 *         public void onProgress(int progress) {
 *             progressBar.setProgress(progress);
 *         }
 *         public void onSuccess(String outputFile) {
 *             playAudio(outputFile);
 *         }
 *         public void onFailure(String errorMessage) {
 *             showError(errorMessage);
 *         }
 *     });
 * </pre>
 * </p>
 *
 * @see MediaExtractor
 * @see MediaMuxer
 * @see ThreadTask
 * @see ConversionListener
 */
public class Vid2AudioConverter {
	
	private final LoggerUtils logger = LoggerUtils.from(getClass());
	
	/**
	 * Background task reference for managing the audio extraction operation.
	 * <p>
	 * This task handles cancellation, lifecycle binding, and thread management
	 * for the extraction process. It is null when no extraction is in progress.
	 * </p>
	 */
	private ThreadTask<String, Integer> conversionTask;
	
	/**
	 * Extracts the audio track from a video or audio file asynchronously.
	 * <p>
	 * This method starts a background task to extract the first audio track from the
	 * input file and save it as a standalone audio file. The operation is performed
	 * on a background thread, with progress, success, and error callbacks delivered
	 * through the provided ConversionListener. The task can be tied to a LifecycleOwner
	 * for automatic cancellation when the associated UI component is destroyed.
	 * </p>
	 *
	 * <p><b>Features:</b>
	 * <ul>
	 *   <li>Automatic cancellation when lifecycle owner is destroyed</li>
	 *   <li>Real-time progress reporting (0-100%)</li>
	 *   <li>Support for various audio formats (MP3, AAC, Opus, Vorbis)</li>
	 *   <li>Proper cleanup of partial files on error</li>
	 * </ul>
	 * </p>
	 *
	 * @param owner      LifecycleOwner for automatic task cancellation (can be null)
	 * @param inputFile  path to the source file containing the audio track
	 * @param outputFile path where the extracted audio will be saved
	 * @param listener   callback interface for progress, success, and failure events
	 */
	public void extractAudio(@Nullable LifecycleOwner owner,
	                         @NonNull String inputFile,
	                         @NonNull String outputFile,
	                         @NonNull ConversionListener listener) {
		
		logger.debug("Starting audio extraction. Input: " + inputFile + ", Output: " + outputFile);
		conversionTask = new ThreadTask.Builder<String, Integer>()
			.withLifecycle(owner)
			.withBackgroundTask(callback -> performExtraction(inputFile, outputFile, callback))
			.withProgressTask(listener::onProgress)
			.withResultTask(listener::onSuccess)
			.withErrorTask(error -> {
				logger.error("Audio extraction failed: " + error.getMessage(), error);
				listener.onFailure(error.getMessage());
			})
			.build();
		
		conversionTask.start();
	}
	
	/**
	 * Extracts the audio track from a media file and saves it as a standalone audio file.
	 * <p>
	 * This method uses Android's MediaExtractor and MediaMuxer APIs to extract the first
	 * audio track found in the input file. It supports various audio formats including
	 * MP3 (raw extraction), AAC, MP4A, Opus, and Vorbis. Progress is reported through
	 * the callback, and the operation can be canceled via the conversionTask flag.
	 * </p>
	 *
	 * <p><b>Supported Output Formats:</b>
	 * <ul>
	 *   <li>MP3 → Raw MP3 file (no container)</li>
	 *   <li>AAC/MP4A → MP4 container</li>
	 *   <li>Opus/Vorbis → WebM container</li>
	 * </ul>
	 * </p>
	 *
	 * <p><b>Extraction Process:</b>
	 * <ol>
	 *   <li>Verifies source file existence</li>
	 *   <li>Scans all tracks to find the first audio track</li>
	 *   <li>Determines output format based on audio MIME type</li>
	 *   <li>Reads samples in 4MB chunks and writes to output</li>
	 *   <li>Reports progress as percentage of bytes processed</li>
	 *   <li>Cleans up partial file on error or cancellation</li>
	 * </ol>
	 * </p>
	 *
	 * @param inputFile  the path to the source media file containing audio track
	 * @param outputFile the path where the extracted audio file will be saved
	 * @param callback   callback for reporting extraction progress (0-100%)
	 * @return the output file path on successful extraction
	 * @throws RuntimeException if source file missing, no audio track found,
	 *                          unsupported audio format, or extraction is canceled
	 */
	private String performExtraction(String inputFile, String outputFile,
	                                 ThreadTask.ProgressCallback<Integer> callback) {
		MediaExtractor extractor = new MediaExtractor();
		MediaMuxer muxer = null;
		FileOutputStream fos = null;
		
		try {
			File sourceFile = new File(inputFile);
			if (!sourceFile.exists()) {
				throw new RuntimeException("Source file does not exist: " + inputFile);
			}
			
			extractor.setDataSource(inputFile);
			
			int audioTrackIndex = -1;
			MediaFormat format = null;
			
			int trackCount = extractor.getTrackCount();
			for (int i = 0; i < trackCount; i++) {
				MediaFormat trackFormat = extractor.getTrackFormat(i);
				String mime = trackFormat.getString(MediaFormat.KEY_MIME);
				if (mime != null && mime.startsWith("audio/")) {
					audioTrackIndex = i;
					format = trackFormat;
					extractor.selectTrack(i);
					logger.debug("Selected audio track #" + i + " with MIME: " + mime);
					break;
				}
			}
			
			if (audioTrackIndex == -1) {
				throw new RuntimeException("No audio track found in: " + inputFile);
			}
			
			String mime = format.getString(MediaFormat.KEY_MIME);
			boolean isMp3 = "audio/mpeg".equals(mime);
			int newTrackIndex = -1;
			
			if (isMp3) {
				logger.debug("Detected MP3. Using raw extraction.");
				fos = new FileOutputStream(outputFile);
			} else {
				int muxerFormat;
				if ("audio/aac".equals(mime) || "audio/mp4a-latm".equals(mime)) {
					muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
				} else if ("audio/opus".equals(mime) || "audio/vorbis".equals(mime)) {
					muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
				} else {
					throw new RuntimeException("Unsupported audio MIME type for extraction: " + mime);
				}
				
				muxer = new MediaMuxer(outputFile, muxerFormat);
				newTrackIndex = muxer.addTrack(format);
				muxer.start();
				logger.debug("Muxing started. Output format: " + muxerFormat);
			}
			
			ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
			MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
			
			long totalSize = sourceFile.length();
			long processedSize = 0;
			int lastReportedProgress = -1;
			
			while (true) {
				if (conversionTask != null && conversionTask.isCancelled()) {
					logger.warning("Extraction cancelled by user or lifecycle.");
					throw new RuntimeException("Audio extraction cancelled");
				}
				
				buffer.clear();
				int sampleSize = extractor.readSampleData(buffer, 0);
				if (sampleSize < 0) {
					logger.debug("Reached end of stream.");
					break;
				}
				
				if (isMp3) {
					byte[] chunk = new byte[sampleSize];
					buffer.get(chunk);
					fos.write(chunk);
				} else {
					bufferInfo.offset = 0;
					bufferInfo.size = sampleSize;
					bufferInfo.presentationTimeUs = extractor.getSampleTime();
					
					int sampleFlags = extractor.getSampleFlags();
					bufferInfo.flags = 0;
					if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
						bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
					}
					if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
						bufferInfo.flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
					}
					
					muxer.writeSampleData(newTrackIndex, buffer, bufferInfo);
				}
				
				extractor.advance();
				processedSize += sampleSize;
				int progress = (int) ((processedSize * 100) / totalSize);
				
				if (progress != lastReportedProgress) {
					callback.onProgress(progress);
					lastReportedProgress = progress;
				}
			}
			
			if (muxer != null) {
				muxer.stop();
			}
			
			logger.debug("Extraction successful. File saved: " + outputFile);
			return outputFile;
			
		} catch (Exception error) {
			File partialFile = new File(outputFile);
			if (partialFile.exists()) {
				partialFile.delete();
			}
			throw new RuntimeException(error);
		} finally {
			extractor.release();
			if (muxer != null) {
				muxer.release();
			}
			if (fos != null) {
				try {
					fos.close();
				} catch (Exception ignore) {}
			}
		}
	}
	
	/**
	 * Cancels the ongoing MP4 conversion task if one is currently running.
	 * <p>
	 * This method safely stops the background conversion process by cancelling
	 * the ThreadTask. It is typically called when the user navigates away from
	 * the conversion screen or when a new conversion is requested before the
	 * previous one completes.
	 * </p>
	 */
	public void cancel() {
		if (conversionTask != null) {
			conversionTask.cancel();
		}
	}
	
	/**
	 * Callback interface for monitoring MP4 file conversion progress and completion.
	 * <p>
	 * Implement this interface to receive real-time updates during the MP4
	 * fast-start conversion process. Methods are called at various stages including
	 * progress updates, successful completion with the output file path, and error
	 * conditions with descriptive messages.
	 * </p>
	 */
	public interface ConversionListener {
		
		/**
		 * Called periodically during conversion to report current progress.
		 * <p>
		 * This method is invoked as the conversion process advances, allowing
		 * UI components to update progress indicators such as progress bars
		 * or percentage text views.
		 * </p>
		 *
		 * @param progress the conversion completion percentage (0-100)
		 */
		void onProgress(int progress);
		
		/**
		 * Called when the MP4 conversion has completed successfully.
		 * <p>
		 * This method provides the path to the optimized output file, which
		 * has been fully validated and is ready for playback or further processing.
		 * </p>
		 *
		 * @param outputFile the absolute path to the successfully converted MP4 file
		 */
		void onSuccess(String outputFile);
		
		/**
		 * Called when an error occurs during the conversion process.
		 * <p>
		 * This method provides a human-readable error description explaining
		 * why the conversion failed, such as insufficient disk space, invalid
		 * input file, or I/O errors during processing.
		 * </p>
		 *
		 * @param errorMessage a descriptive error message explaining the failure cause
		 */
		void onFailure(String errorMessage);
	}
}
