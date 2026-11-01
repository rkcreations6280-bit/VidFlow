package coreUtils.library.media;

import static java.util.Arrays.copyOf;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import coreUtils.library.process.LoggerUtils;

/**
 * Utility class for MP4 file validation, seek-ability checking, and moov atom optimization.
 * <p>
 * This final utility class provides static methods for working with MP4 video files,
 * including validation, fast-start conversion (moving the moov atom to the beginning),
 * and seek ability testing. It uses MP4Parser library for deep file structure analysis
 * and provides both basic signature validation and advanced structural validation.
 * </p>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li><b>Validation:</b> Basic "ftyp" signature check and advanced track/sample validation</li>
 *   <li><b>Seek ability Testing:</b> Detects if MP4 files have moov atom positioned for streaming</li>
 *   <li><b>Fast-Start Conversion:</b> Rewrites MP4 files to move moov atom to the beginning</li>
 *   <li><b>Temporary File Management:</b> Uses prefixed temp files with .mp4 extension for safe processing</li>
 * </ul>
 * </p>
 *
 * <p><b>Typical Usage:</b>
 * <pre>
 * // Validate MP4 file
 * if (MP4FileUtility.isValidMp4File(file)) {
 *     // Check if seekable (moov atom at start)
 *     if (MP4FileUtility.isMp4Seekable(file)) {
 *         playStreaming(file);
 *     } else {
 *         // Optimize for streaming
 *         File optimized = new File(cacheDir, "optimized.mp4");
 *         MP4FileUtility.moveMoovAtomToStart(file, optimized);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @see MovieCreator
 * @see DefaultMp4Builder
 * @see FileDataSourceImpl
 */
public final class MP4FileUtility {
	
	private static final LoggerUtils logger = LoggerUtils.from(MP4FileUtility.class);
	
	/**
	 * Prefix for temporary files created during moov atom optimization.
	 * <p>
	 * This prefix is prepended to the original filename when creating temporary files
	 * during the fast-start conversion process. Combined with the suffix, it ensures
	 * temporary files are easily identifiable and don't conflict with existing files.
	 * Example: "moov_optimize_video_12345.mp4"
	 * </p>
	 */
	public static final String TMP_MOOV_OPTIMIZED_PREFIX = "moov_optimize_";
	
	/**
	 * File extension suffix for temporary MP4 files.
	 * <p>
	 * This suffix ensures that temporary files created during the optimization process
	 * have the correct .mp4 file extension. This is important for proper media type
	 * detection by Android's media framework and ensures compatibility with media players
	 * that rely on file extensions for format identification.
	 * </p>
	 */
	public static final String TMP_MOOV_OPTIMIZED_SUFFIX = ".mp4";
	
	/**
	 * Private constructor to prevent instantiation of this utility class.
	 * <p>
	 * This class only provides static utility methods for MP4 file operations
	 * and should not be instantiated.
	 * </p>
	 */
	private MP4FileUtility() {}
	
	/**
	 * Performs advanced validation of an MP4 file using MP4Parser library.
	 * <p>
	 * This method goes beyond basic signature checking by fully parsing the MP4
	 * structure and verifying that it contains valid tracks with sample data.
	 * A valid MP4 file must have at least one track (video, audio, or subtitle)
	 * and each track must contain at least one sample (frame or audio chunk).
	 * </p>
	 *
	 * <p><b>Validation Checks:</b>
	 * <ul>
	 *   <li>File exists and has minimum size of 100 bytes</li>
	 *   <li>MP4 structure can be successfully parsed</li>
	 *   <li>At least one track is present in the movie</li>
	 *   <li>Every track contains non-empty sample data</li>
	 * </ul>
	 * </p>
	 *
	 * @param file the MP4 file to validate
	 * @return true if the file is a valid MP4 with tracks and samples,
	 * false otherwise
	 */
	public static boolean isValidMp4FileAdvanced(File file) {
		if (!file.exists() || file.length() < 100) return false;
		
		try (FileDataSourceImpl dataSource =
			     new FileDataSourceImpl(file.getAbsolutePath())) {
			Movie movie = MovieCreator.build(dataSource);
			if (movie.getTracks().isEmpty()) return false;
			for (Track track : movie.getTracks()) {
				if (track.getSamples().isEmpty()) return false;
			}
			return true;
		} catch (Exception error) {
			logger.error("Advanced MP4 validation failed", error);
			return false;
		}
	}
	
	/**
	 * Validates whether a file is a basic MP4 file by checking its brand signature.
	 * <p>
	 * This method performs a quick validation by reading the first 12 bytes of the file
	 * and checking for the presence of the "ftyp" (file type) atom at offset 4. This
	 * signature identifies the file as an MP4 container. The file must exist and have
	 * a minimum size of 12 bytes to pass this validation.
	 * </p>
	 *
	 * @param file the file to validate as an MP4
	 * @return true if the file contains the "ftyp" MP4 signature at the correct offset,
	 * false otherwise
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isValidMp4File(File file) {
		if (!file.exists() || file.length() < 12) return false;
		try (FileInputStream fis = new FileInputStream(file)) {
			
			byte[] buffer = new byte[12];
			int bytesRead = fis.read(buffer);
			
			if (bytesRead < 12) return false;
			String signature = new String(buffer, 4, 4, StandardCharsets.US_ASCII);
			return "ftyp".equals(signature);
		} catch (Exception error) {
			logger.error("Error validating MP4 file", error);
			return false;
		}
	}
	
	/**
	 * Rewrites an MP4 file by moving the 'moov' atom to the beginning for better seek ability.
	 * <p>
	 * This method converts a standard MP4 file (with moov atom at the end) into a
	 * "fast-start" or "web-optimized" MP4 where the moov atom is placed at the beginning.
	 * This enables streaming and random access without downloading the entire file.
	 * The process uses MP4Parser to rebuild the file, writes to a temporary file, and
	 * performs multiple validation checks before replacing the original.
	 * </p>
	 *
	 * <p><b>Validation Steps:</b>
	 * <ul>
	 *   <li>Checks input file exists, readable, and non-empty</li>
	 *   <li>Ensures output directory is writable with sufficient free space (2x file size)</li>
	 *   <li>Validates input file is a proper MP4 file</li>
	 *   <li>Creates temporary file for processing</li>
	 *   <li>Rebuilds MP4 with DefaultMp4Builder (moov atom first)</li>
	 *   <li>Validates output MP4 signature and advanced structure</li>
	 *   <li>Verifies size ratio between input and output (0.5x - 1.5x)</li>
	 *   <li>Renames temporary file to final output</li>
	 * </ul>
	 * </p>
	 *
	 * @param inputFile  the source MP4 file to convert (must be valid)
	 * @param outputFile the destination file for the optimized MP4
	 * @return true if the file was successfully converted and validated,
	 * false otherwise
	 */
	public static boolean moveMoovAtomToStart(File inputFile, File outputFile) {
		if (!inputFile.exists()) return false;
		if (inputFile.length() <= 0L) return false;
		if (!inputFile.canRead()) return false;
		
		File outputDir = outputFile.getParentFile();
		if (outputDir != null) {
			if (!outputDir.exists() && !outputDir.mkdirs()) return false;
			if (!outputDir.canWrite()) return false;
		}
		
		long requiredSpace = inputFile.length() * 2;
		File spaceDir = outputDir;
		if (spaceDir == null) spaceDir = inputFile.getParentFile();
		
		long availableSpace = 0L;
		if (spaceDir != null) availableSpace = spaceDir.getFreeSpace();
		if (availableSpace < requiredSpace) return false;
		if (!isValidMp4File(inputFile)) return false;
		
		File tempFile;
		try {
			String prefix = TMP_MOOV_OPTIMIZED_PREFIX + inputFile.getName();
			tempFile = File.createTempFile(prefix, TMP_MOOV_OPTIMIZED_SUFFIX, outputDir);
		} catch (Exception error) {
			logger.error("Temp file creation failed", error);
			return false;
		}
		
		try (FileDataSourceImpl dataSource =
			     new FileDataSourceImpl(inputFile.getAbsolutePath())) {
			Movie movie = MovieCreator.build(dataSource);
			
			if (movie.getTracks().isEmpty()) {
				tempFile.delete();
				return false;
			}
			
			DefaultMp4Builder builder = new DefaultMp4Builder();
			Container container = builder.build(movie);
			
			try (FileOutputStream fos = new FileOutputStream(tempFile)) {
				container.writeContainer(fos.getChannel());
				fos.getChannel().force(true);
			}
			
			if (!isValidMp4File(tempFile)) {
				tempFile.delete();
				return false;
			}
			
			long inputSize = inputFile.length();
			long tempSize = tempFile.length();
			
			double sizeRatio = (double) tempSize / (double) inputSize;
			if (sizeRatio < 0.5 || sizeRatio > 1.5) {
				tempFile.delete();
				return false;
			}
			
			if (outputFile.exists() && !outputFile.delete()) {
				tempFile.delete();
				return false;
			}
			
			boolean renamed = tempFile.renameTo(outputFile);
			if (!renamed) {
				tempFile.delete();
				return false;
			}
			
			if (!isValidMp4FileAdvanced(outputFile)) {
				outputFile.delete();
				return false;
			}
			
			return true;
		} catch (Exception error) {
			logger.error("Error moving moov atom to start", error);
			
			try {
				if (tempFile.exists()) tempFile.delete();
			} catch (Exception ignored) {}
			
			try {
				if (outputFile.exists()) outputFile.delete();
			} catch (Exception ignored) {}
			
			return false;
		} finally {
			try {
				if (tempFile.exists()) tempFile.delete();
			} catch (Exception ignored) {}
		}
	}
	
	/**
	 * Validates whether an MP4 file is seekable by checking for the 'moov' atom near the start.
	 * <p>
	 * This method performs two-stage validation: first, it checks the file header for the
	 * presence of the 'moov' atom within the first 64KB (indicating a fast-start/fastseek
	 * MP4). Then it uses MP4Parser to verify that the file contains valid tracks with samples.
	 * A file is considered seekable if the 'moov' atom (which contains sample tables and
	 * timing data) is located early in the file, enabling efficient random access.
	 * </p>
	 *
	 * <p><b>Validation Steps:</b>
	 * <ol>
	 *   <li>Checks file existence and minimum size (≥ 100 bytes)</li>
	 *   <li>Reads first 64KB and scans for 'moov' atom within 1024 bytes of start</li>
	 *   <li>Uses MP4Parser to verify tracks and samples are present and valid</li>
	 * </ol>
	 * </p>
	 *
	 * @param file the MP4 file to validate for seek ability
	 * @return true if the file is seekable (moov atom present early and valid structure),
	 * false otherwise
	 */
	public static boolean isMp4Seekable(File file) {
		if (!file.exists() || file.length() < 100) return false;
		
		try (FileInputStream fis = new FileInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int bytesRead = fis.read(buffer);
			
			if (bytesRead <= 0) {
				logger.debug("Failed to read file: " + file.getAbsolutePath());
				return false;
			}
			
			byte[] content = bytesRead == buffer.length ? buffer : copyOf(buffer, bytesRead);
			if (!containsMoovAtomAtStart(content)) {
				logger.debug("'moov' atom not found near start: " + file.getName());
				return false;
			}
		} catch (Exception error) {
			logger.error("Error validating MP4 file", error);
			return false;
		}
		
		try (FileDataSourceImpl dataSource =
			     new FileDataSourceImpl(file.getAbsolutePath())) {
			Movie movie = MovieCreator.build(dataSource);
			if (movie.getTracks().isEmpty()) {
				return false;
			}
			
			for (Track track : movie.getTracks()) {
				if (track.getSamples().isEmpty()) {
					return false;
				}
			}
			
			return true;
		} catch (Exception error) {
			logger.error("MP4Parser validation failed", error);
			return false;
		}
	}
	
	/**
	 * Scans the beginning of an MP4 file for the 'moov' atom within the first 1024 bytes.
	 * <p>
	 * This method parses MP4 atom/box structure from the provided byte array, reading
	 * box sizes and types. It checks if a 'moov' box is found at index ≤ 1024, which
	 * indicates the file is optimized for streaming or fast seeking (often called
	 * "fast-start" or "web-optimized" MP4). The box size is read as a 32-bit integer,
	 * and the box type is read as a 4-character ASCII string.
	 * </p>
	 *
	 * @param data the MP4 file header bytes (first N bytes of the file)
	 * @return true if the 'moov' atom is present within the first 1024 bytes,
	 * false otherwise
	 */
	public static boolean containsMoovAtomAtStart(byte[] data) {
		int index = 0;
		while (index + 8 <= data.length) {
			int size = ((data[index] & 0xFF) << 24)
				| ((data[index + 1] & 0xFF) << 16)
				| ((data[index + 2] & 0xFF) << 8)
				| (data[index + 3] & 0xFF);
			
			if (size <= 0) break;
			if (size < 8) break;
			if (index + size > data.length) break;
			String type = new String(data, index + 4, 4, StandardCharsets.US_ASCII);
			if ("moov".equals(type)) return index <= 1024;
			index += size;
		}
		
		return false;
	}
}