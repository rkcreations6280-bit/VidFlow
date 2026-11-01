package coreUtils.library.storage;

import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.nextgen.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coreUtils.base.BaseApplication;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.strings.StringHelper;
import dataRepo.downloads.DownloadInfo;
import java.util.List;
import sysModules.downloadSys.DownloadSysInf;

/**
 * Utility class providing a comprehensive set of methods for file system operations,
 * storage management, and URI handling on Android.
 *
 * <p>This class includes functionality for:</p>
 * <ul>
 *     <li>Accessing internal and public storage directories.</li>
 *     <li>Managing system permissions for "All Files Access".</li>
 *     <li>Interacting with the Android MediaStore (scanning files).</li>
 *     <li>Parsing file names from URIs, Content-Dispositions, and URLs.</li>
 *     <li>Reading/writing strings to internal storage.</li>
 *     <li>Sanitizing and validating file names.</li>
 *     <li>Detecting file types (Audio, Video, Document, etc.) based on extensions.</li>
 *     <li>Performing file integrity checks via SHA-256 hashing.</li>
 * </ul>
 *
 * <p>This class is final and cannot be instantiated.</p>
 *
 */
public final class FileStorageUtility {
	
	/**
	 * Logger instance for tracking events, errors, and debugging information within this class.
	 */
	private static final LoggerUtils logger = LoggerUtils.from(FileStorageUtility.class);
	private static final long ONE_KB = 1024;
	private static final long ONE_MB = ONE_KB * 1024;
	private static final long ONE_GB = ONE_MB * 1024;
	
	private FileStorageUtility() {}
	
	/**
	 * Retrieves the absolute path to the directory on the primary shared/external storage
	 * device where the application can place persistent files it owns.
	 *
	 * @return A {@link File} object representing the application's internal data directory.
	 */
	public static File getInternalDataFolder() {
		return BaseApplication.getInstance().getFilesDir();
	}
	
	/**
	 * Retrieves the standard external storage directory for downloads.
	 * <p>
	 * This method uses {@link Environment#getExternalStoragePublicDirectory(String)} with
	 * {@link Environment#DIRECTORY_DOWNLOADS} to locate the public folder where downloaded
	 * files are typically stored.
	 *
	 * @return A {@link File} object representing the public downloads' directory.
	 */
	public static File getPublicDownloadFolder() {
		String directoryDownloads = Environment.DIRECTORY_DOWNLOADS;
		return Environment.getExternalStoragePublicDirectory(directoryDownloads);
	}
	
	/**
	 * Opens the system settings activity where the user can grant "All Files Access" permission
	 * (MANAGE_EXTERNAL_STORAGE) for this application.
	 * <p>
	 * This is primarily used on Android 11 (API level 30) and higher. If the specific
	 * all-files access settings cannot be opened, it falls back to opening the general
	 * Application Details settings page.
	 *
	 * @param context The context used to start the settings activity.
	 */
	public static void openAllFilesAccessSettings(Context context) {
		try {
			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
				throw new UnsupportedOperationException("All files access needs Android 11+");
			}
			Intent intent;
			intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
			intent.setData(Uri.parse("package:" + context.getPackageName()));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(intent);
			
		} catch (Exception error) {
			logger.error("Error opening all files access settings", error);
			try {
				Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
				intent.setData(Uri.parse("package:" + context.getPackageName()));
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(intent);
			} catch (Exception exception) {
				logger.error("Error opening application details settings", exception);
			}
		}
	}
	
	/**
	 * Opens an APK file to initiate the package installation process.
	 * <p>
	 * This method generates a content URI using {@link FileProvider} to securely share
	 * the file with the system installer, sets the appropriate MIME type for Android
	 * packages, and grants the necessary read permissions.
	 * </p>
	 *
	 * @param context The application or activity context used to access the FileProvider and start the activity.
	 * @param apkFile The {@link File} object pointing to the APK to be installed.
	 */
	public static void openApkFile(Context context, File apkFile) {
		try {
			String authority = context.getPackageName() + ".provider";
			Uri apkUri = FileProvider.getUriForFile(context, authority, apkFile);
			Intent intent = new Intent(ACTION_VIEW);
			intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
			intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(intent);
		} catch (Exception error) {
			logger.error("Error opening APK file", error);
		}
	}
	
	/**
	 * Checks whether the application has been granted the "All Files Access" permission (MANAGE_EXTERNAL_STORAGE).
	 * <p>
	 * This permission is required on Android 11 (API level 30) and higher to perform operations
	 * on files outside the app-specific directory and the MediaStore.
	 * </p>
	 *
	 * @param context The application context.
	 * @return {@code true} if the app has full file system access, {@code false} otherwise.
	 * @see Environment#isExternalStorageManager()
	 */
	public static boolean hasFullFileSystemAccess(Context context) {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
			// Android 10 and below: no "All files access"; use classic storage permissions.
			return androidx.core.content.ContextCompat.checkSelfPermission(context,
				android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				== android.content.pm.PackageManager.PERMISSION_GRANTED;
		}
		return Environment.isExternalStorageManager();
	}
	
	/**
	 * Updates the Android MediaStore by scanning all finished download records.
	 * <p>
	 * This method iterates through the repository of finished downloads and triggers
	 * a media scan for each file. This ensures that downloaded files (like images,
	 * videos, or audio) appear immediately in system gallery and media player apps.
	 * </p>
	 *
	 * @param downloadSysInf The download system interface used to access download records.
	 */
	public static void updateMediaStore(DownloadSysInf downloadSysInf) {
		try {
			if (downloadSysInf.isDownloadSystemInitialize()) return;
			List<DownloadInfo> allRecords = downloadSysInf.getAllDownloadRecordsFromRepo();
			List<DownloadInfo> finishedRecords = downloadSysInf.getAllFinishedDownloadRecordsFromRepo();
			
			int index = 0;
			while (index < finishedRecords.size()) {
				DownloadInfo downloadInfo = finishedRecords.get(index);
				if (downloadInfo != null) {
					File destinationFile = downloadInfo.getDestinationFile();
					addToMediaStore(destinationFile,
						(path, uri) -> logger.debug("Scanned: " + path));
				}
				index++;
			}
			
		} catch (Exception error) {
			logger.error("Error updating media store", error);
		}
	}
	
	public interface MediaScanCallback {
		void onCompleted(String path, Uri uri);
	}
	
	/**
	 * Triggers the Android Media Scanner to scan a specific file, ensuring it appears in
	 * the system's MediaStore (Gallery, Music apps, etc.).
	 *
	 * @param file              The file to be scanned and added to the media database.
	 * @param mediaScanCallback An optional callback to be notified when the scan is complete,
	 *                          providing the file path and its MediaStore {@link Uri}.
	 */
	public static void addToMediaStore(File file, MediaScanCallback mediaScanCallback) {
		try {
			if (file == null || !file.exists()) return;
			MediaScannerConnection.scanFile(
				BaseApplication.getInstance(),
				new String[]{file.getAbsolutePath()},
				null,
				(path, uri) -> {
					if (mediaScanCallback != null) {
						mediaScanCallback.onCompleted(path, uri);
					}
				}
			);
		} catch (Exception error) {
			logger.error("Error adding file to media store", error);
		}
	}
	
	/**
	 * Extracts the filename from the HTTP "Content-Disposition" header.
	 * <p>
	 * This method uses a regular expression to search for the {@code filename=} parameter
	 * within the provided header string. it supports both quoted and unquoted filenames
	 * and is case-insensitive.
	 * </p>
	 *
	 * @param contentDisposition The Content-Disposition header value from an HTTP response.
	 * @return The extracted filename if found; {@code null} if the header is null, empty,
	 * or if no filename parameter is present.
	 */
	@Nullable
	public static String extractFileNameFromContentDisposition(@Nullable String contentDisposition) {
		try {
			if (contentDisposition == null || contentDisposition.isEmpty()) return null;
			Pattern pattern = Pattern.compile("(?i)filename=[\"']?([^\";]+)");
			Matcher matcher = pattern.matcher(contentDisposition);
			if (matcher.find()) {
				return matcher.group(1);
			} else {
				return null;
			}
		} catch (Exception error) {
			logger.error("Error extracting file name from content disposition", error);
			return null;
		}
	}
	
	/**
	 * Decodes a URL-encoded string into its original form using the UTF-8 charset.
	 * This is typically used to convert percent-encoded file names (e.g., "my%20file.txt")
	 * back into human-readable strings ("my file.txt").
	 *
	 * @param encodedString The URL-encoded string to decode.
	 * @return The decoded string if successful; otherwise, returns the original
	 * encoded string if an error occurs during decoding.
	 */
	public static String decodeURLFileName(String encodedString) {
		try {
			return URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
		} catch (Exception error) {
			logger.error("Error decoding URL file name", error);
			return encodedString;
		}
	}
	
	/**
	 * Retrieves the file name from a given URI.
	 * <p>
	 * This method supports two types of URI schemes:
	 * <ul>
	 *     <li><b>content://</b> - Queries the ContentResolver for the {@link OpenableColumns#DISPLAY_NAME}.</li>
	 *     <li><b>file://</b> - Extracts the name directly from the file path.</li>
	 * </ul>
	 *
	 * @param context The context used to access the ContentResolver.
	 * @param uri     The URI to extract the file name from.
	 * @return The file name as a String, or {@code null} if the name could not be resolved
	 * or an error occurred.
	 */
	@Nullable
	public static String getFileNameFromUri(Context context, Uri uri) {
		try {
			String fileName = null;
			if ("content".equals(uri.getScheme())) {
				Cursor cursor = context.getContentResolver()
					.query(uri, null, null, null, null);
				
				if (cursor != null) {
					try {
						if (cursor.moveToFirst()) {
							int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
							if (nameIndex != -1) fileName = cursor.getString(nameIndex);
						}
					} finally {
						cursor.close();
					}
				}
			} else if ("file".equals(uri.getScheme())) {
				String path = uri.getPath();
				if (path != null) {
					fileName = new File(path).getName();
				}
			}
			
			return fileName;
			
		} catch (Exception error) {
			logger.error("Error getting file name from URI", error);
			return null;
		}
	}
	
	/**
	 * Converts a {@link Uri} into a {@link File} object by extracting its path.
	 * <p>
	 * Note: This method primarily works for "file://" URIs. For "content://" URIs
	 * provided by other applications or the MediaStore, this may not return a
	 * valid accessible file path on modern Android versions (API 29+).
	 * </p>
	 *
	 * @param uri The URI to convert.
	 * @return A {@link File} object if the path is valid, or {@code null} if the path
	 * is null or an exception occurs.
	 */
	@Nullable
	public static File getFileFromUri(Uri uri) {
		try {
			String filePath = uri.getPath();
			if (filePath != null) {
				return new File(filePath);
			} else {
				return null;
			}
		} catch (Exception error) {
			logger.error("Error getting file from URI", error);
			return null;
		}
	}
	
	/**
	 * Saves a string of text to the application's internal storage.
	 * The file is saved in {@link Context#MODE_PRIVATE} mode, making it accessible only to the
	 * calling application.
	 *
	 * @param fileName    The name of the file to be created or overwritten.
	 * @param fileContent The string content to write into the file.
	 * @return {@code true} if the string was successfully saved, {@code false} otherwise.
	 */
	public static boolean saveStringToInternalStorage(String fileName, String fileContent) {
		Context context = BaseApplication.getInstance();
		try (FileOutputStream outputStream =
			     context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
			outputStream.write(fileContent.getBytes(StandardCharsets.UTF_8));
			outputStream.close();
			return true;
		} catch (Exception error) {
			logger.error("Error saving string to internal storage", error);
			return false;
		}
	}
	
	/**
	 * Saves a string of text to the application's internal storage.
	 * <p>
	 * The file is created in the application's private data directory using {@link Context#MODE_PRIVATE},
	 * ensuring it is only accessible by this application. If a file with the same name already exists,
	 * its content will be overwritten.
	 * </p>
	 *
	 * @param targetFile  The name of the file to be created or overwritten.
	 * @param fileContent The string content to be encoded in UTF-8 and written to the file.
	 * @return {@code true} if the operation completed successfully; {@code false} if an I/O error
	 * occurred during the writing process.
	 */
	public static boolean saveStringToInternalStorage(File targetFile, String fileContent) {
		Context context = BaseApplication.getInstance();
		try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
			outputStream.write(fileContent.getBytes(StandardCharsets.UTF_8));
			outputStream.close();
			return true;
		} catch (Exception error) {
			logger.error("Error saving string to internal storage", error);
			return false;
		}
	}
	
	/**
	 * Reads the content of a file from the application's internal storage and returns it as a String.
	 * <p>
	 * This method retrieves the file using the application context, reads its bytes into a
	 * {@link ByteArrayOutputStream}, and converts the result to a UTF-8 encoded string.
	 * It handles compatibility for different Android versions when specifying the charset.
	 * </p>
	 *
	 * @param fileName The name of the file to read from internal storage.
	 * @return The string content of the file, or {@code null} if an error occurs or the file is not found.
	 */
	@Nullable
	public static String readStringFromInternalStorage(String fileName) {
		try {
			Context context = BaseApplication.getInstance();
			FileInputStream inputStream = context.openFileInput(fileName);
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			
			byte[] buffer = new byte[1024];
			int length;
			
			while ((length = inputStream.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			
			inputStream.close();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				return result.toString(StandardCharsets.UTF_8);
			} else {
				return result.toString(StandardCharsets.UTF_8);
			}
		} catch (Exception error) {
			logger.error("Error reading string from internal storage", error);
			return null;
		}
	}
	
	/**
	 * Sanitizes a file name using a strict (extreme) approach to ensure maximum compatibility
	 * across different file systems and operating systems.
	 * <p>
	 * This method:
	 * <ul>
	 *     <li>Replaces any character that is NOT an alphanumeric character, a parenthesis,
	 *         an at-sign (@), a bracket, an underscore, a dot, or a hyphen with an underscore.</li>
	 *     <li>Replaces spaces with underscores.</li>
	 *     <li>Collapses consecutive underscores (up to three) into a single underscore to maintain
	 *     cleanliness.</li>
	 * </ul>
	 *
	 * @param fileName The original file name to be sanitized.
	 * @return A sanitized version of the file name containing only safe characters.
	 */
	public static String sanitizeFileNameExtreme(String fileName) {
		return fileName
			.replaceAll("[^a-zA-Z0-9()@\\[\\]_.-]", "_")
			.replace(" ", "_")
			.replace("___", "_")
			.replace("__", "_");
	}
	
	/**
	 * Sanitizes a file name by removing or replacing characters that are generally
	 * illegal or problematic across various file systems (Windows, Linux, Android).
	 * <p>
	 * This method performs the following operations:
	 * <ul>
	 *     <li>Replaces illegal characters ({@code \ / : * ? " < > |}) and control characters
	 *     with an underscore.</li>
	 *     <li>Removes trailing dots.</li>
	 *     <li>Trims leading and trailing whitespace.</li>
	 *     <li>Replaces spaces with underscores.</li>
	 *     <li>Collapses multiple consecutive underscores into a single underscore.</li>
	 * </ul>
	 *
	 * @param fileName The original file name to be sanitized.
	 * @return A sanitized version of the file name safe for storage.
	 */
	public static String sanitizeFileNameNormal(String fileName) {
		return fileName
			.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}\\x00-\\x1F\\x7F]", "_")
			.replaceAll("\\.+$", "")
			.trim()
			.replaceAll("_+", "_")
			.replace(" ", "_")
			.replace("___", "_")
			.replace("__", "_");
	}
	
	/**
	 * Checks if a string is a valid file name by attempting to create a temporary file
	 * in the application's internal data folder.
	 *
	 * @param fileName The file name string to validate.
	 * @return {@code true} if the file name is valid and the file was successfully created
	 * and then deleted; {@code false} if the file name is invalid, already exists,
	 * or an I/O error occurred.
	 */
	public static boolean isFileNameValid(String fileName) {
		try {
			File directory = getInternalDataFolder();
			File tempFile = new File(directory, fileName);
			boolean isNewlyCreated = tempFile.createNewFile();
			
			if (isNewlyCreated) {
				return tempFile.delete();
			} else {
				return false;
			}
		} catch (Exception error) {
			logger.error("Error checking file name validity", error);
			return false;
		}
	}
	
	/**
	 * Checks whether the application has write access to the specified {@link DocumentFile}.
	 *
	 * @param file The DocumentFile to check for write permissions.
	 * @return {@code true} if the file is writable, {@code false} otherwise.
	 */
	public static boolean isWritableFile(@NonNull DocumentFile file) {
		return file.canWrite();
	}
	
	/**
	 * Checks if the application has write access to a specific directory by attempting to
	 * create, write to, and delete a temporary file.
	 * <p>
	 * This is more reliable than {@link DocumentFile#canWrite()} in some Scoped Storage
	 * scenarios where a directory might be reported as writable but actual file creation
	 * or stream opening fails.
	 *
	 * @param folder The {@link DocumentFile} representing the directory to test.
	 * @return {@code true} if a temporary file was successfully created, written to,
	 * and deleted; {@code false} otherwise.
	 */
	public static boolean hasWriteAccess(@Nullable DocumentFile folder) {
		if (folder == null) return false;
		try {
			DocumentFile tempFile = folder.createFile("text/plain", "temp_check_file.txt");
			if (tempFile != null) {
				OutputStream stream = BaseApplication.getInstance()
					.getContentResolver()
					.openOutputStream(tempFile.getUri());
				if (stream != null) {
					try (stream) {
						byte[] bytes = "test".getBytes(StandardCharsets.UTF_8);
						stream.write(bytes);
						stream.flush();
					}
				}
				tempFile.delete();
				return true;
			} else {
				return false;
			}
		} catch (Exception error) {
			logger.error("Error checking write access", error);
			return false;
		}
	}
	
	/**
	 * Writes a placeholder file of a specific size filled with empty bytes (zeros).
	 * This is typically used to pre-allocate space on the storage medium for a file.
	 *
	 * @param context  The application context used to get the ContentResolver.
	 * @param file     The {@link DocumentFile} representing the destination file.
	 * @param fileSize The desired size of the file in bytes. Note: large sizes may
	 *                 trigger an {@link OutOfMemoryError} as it allocates a byte array
	 *                 of this size in memory.
	 * @return {@code true} if the file was written successfully, {@code false} otherwise.
	 */
	public static boolean writeEmptyFile(Context context, DocumentFile file, long fileSize) {
		try {
			ContentResolver resolver = context.getContentResolver();
			OutputStream outputStream = resolver.openOutputStream(file.getUri());
			
			if (outputStream != null) {
				try {
					byte[] placeholder = new byte[(int) fileSize];
					outputStream.write(placeholder);
					outputStream.flush();
				} finally {
					outputStream.close();
				}
			}
			
			return true;
		} catch (Exception error) {
			logger.error("Error writing empty file", error);
			return false;
		}
	}
	
	/**
	 * Generates a unique file name within the specified directory to prevent naming conflicts.
	 * <p>
	 * The method first sanitizes the original file name using an extreme filter. If a file
	 * with that name already exists in the directory, it prepends a numeric index
	 * (e.g., "1_filename", "2_filename") and increments it until a unique name is found.
	 * </p>
	 *
	 * @param fileDirectory    The directory where the file will be located, represented as a {@link DocumentFile}.
	 * @param originalFileName The initial name of the file before sanitization and de-duplication.
	 * @return A sanitized, unique file name string that does not exist in the target directory.
	 */
	public static String generateUniqueFileName(DocumentFile fileDirectory,
	                                            String originalFileName) {
		String sanitizedFileName = sanitizeFileNameExtreme(originalFileName);
		int index = 1;
		
		Pattern pattern = Pattern.compile("^(\\d+)_");
		while (fileDirectory.findFile(sanitizedFileName) != null) {
			Matcher matcher = pattern.matcher(sanitizedFileName);
			if (matcher.find()) {
				int currentIndex = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
				sanitizedFileName = sanitizedFileName.replaceFirst("^(\\d+)_", "");
				index = currentIndex + 1;
			}
			
			sanitizedFileName = index + "_" + sanitizedFileName;
			index++;
		}
		
		return sanitizedFileName;
	}
	
	/**
	 * Searches for the first file within a specified directory that starts with the given prefix.
	 *
	 * @param internalDir The directory to search within.
	 * @param namePrefix  The prefix string to match against the start of file names.
	 * @return The first {@link File} object that matches the prefix and is a file (not a directory),
	 * or {@code null} if no match is found or an error occurs.
	 */
	@Nullable
	public static File findFileStartingWith(File internalDir, String namePrefix) {
		try {
			File[] files = internalDir.listFiles();
			if (files != null) {
				for (File file : files) {
					boolean matchesPrefix = file.getName().startsWith(namePrefix);
					if (file.isFile() && matchesPrefix) return file;
				}
			}
		} catch (Exception error) {
			logger.error("Error finding file starting with", error);
			return null;
		}
		return null;
	}
	
	/**
	 * Creates a new directory within the specified parent folder.
	 *
	 * @param parentFolder The {@link DocumentFile} representing the parent directory where
	 *                     the new folder should be created.
	 * @param folderName   The name of the new directory to be created.
	 * @return A {@link DocumentFile} representing the newly created directory, or {@code null} if
	 * the creation fails or if the parent folder is null.
	 */
	@Nullable
	public static DocumentFile makeDirectory(@Nullable DocumentFile parentFolder,
	                                         String folderName) {
		if (parentFolder == null) return null;
		try {
			return parentFolder.createDirectory(folderName);
		} catch (Exception error) {
			logger.error("Error making directory", error);
			return null;
		}
	}
	
	/**
	 * Attempts to determine the MIME type of file based on its name or extension.
	 * <p>
	 * The method first extracts the file extension and queries the {@link MimeTypeMap}.
	 * If no match is found, it attempts to resolve the type via the system {@link ContentResolver}
	 * using a generated content URI.
	 * </p>
	 *
	 * @param fileName The name or path of the file including the extension.
	 * @return The MIME type string (e.g., "image/jpeg"), or {@code null} if the type
	 * could not be determined or if the extension is missing.
	 */
	@Nullable
	public static String getMimeType(String fileName) {
		try {
			String extension = getFileExtension(fileName);
			if (extension != null) {
				extension = extension.toLowerCase(Locale.getDefault());
				
				String mimeType = MimeTypeMap.getSingleton()
					.getMimeTypeFromExtension(extension);
				
				if (mimeType != null) return mimeType;
				Uri uri = Uri.parse("content://" + extension);
				return BaseApplication.getInstance()
					.getContentResolver()
					.getType(uri);
			} else {
				return null;
			}
			
		} catch (Exception error) {
			logger.error("Error getting mime type", error);
			return null;
		}
	}
	
	/**
	 * Extracts the file extension from a given file name.
	 * <p>
	 * The extension is defined as the substring following the last dot ('.') in the name.
	 * If the file name is null, empty, contains no dot, or ends with a dot, this method returns null.
	 * </p>
	 *
	 * @param fileName The name of the file (e.g., "document.pdf").
	 * @return The file extension (e.g., "pdf"), or {@code null} if no valid extension is found.
	 */
	@Nullable
	public static String getFileExtension(String fileName) {
		if (fileName == null || fileName.isEmpty()) return null;
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex == -1 ||
			lastDotIndex == fileName.length() - 1) return null;
		
		String extension = fileName.substring(lastDotIndex + 1);
		return extension.isEmpty() ? null : extension;
	}
	
	/**
	 * Checks if the given {@link DocumentFile} matches any of the specified file extensions.
	 *
	 * @param file       The file to check. Can be null.
	 * @param extensions An array of valid file extensions (e.g., {"jpg", "png"}).
	 * @return {@code true} if the file name ends with any of the provided extensions,
	 * {@code false} otherwise or if the file/file name is null.
	 */
	public static boolean isFileType(@Nullable DocumentFile file,
	                                 String[] extensions) {
		if (file == null || file.getName() == null) return false;
		return endsWithExtension(file.getName(), extensions);
	}
	
	/**
	 * Checks if the given {@link DocumentFile} is an audio file based on its extension.
	 *
	 * @param file The file to check.
	 * @return {@code true} if the file name ends with a known music extension, {@code false} otherwise.
	 */
	public static boolean isAudio(DocumentFile file) {
		return isFileType(file, FileExtensions.MUSIC_EXTENSIONS);
	}
	
	/**
	 * Checks whether the given {@link DocumentFile} is an archive file based on its extension.
	 *
	 * @param file The file to check.
	 * @return {@code true} if the file's extension matches a known archive format,
	 * {@code false} otherwise.
	 */
	public static boolean isArchive(DocumentFile file) {
		return isFileType(file, FileExtensions.ARCHIVE_EXTENSIONS);
	}
	
	/**
	 * Checks whether the specified file is a program (executable or installer)
	 * based on its file extension.
	 *
	 * @param file The {@link DocumentFile} to check.
	 * @return {@code true} if the file's extension matches a known program extension,
	 * {@code false} otherwise or if the file is null.
	 */
	public static boolean isProgram(DocumentFile file) {
		return isFileType(file, FileExtensions.PROGRAM_EXTENSIONS);
	}
	
	/**
	 * Checks if the given {@link DocumentFile} is a video based on its file extension.
	 *
	 * @param file The file to check.
	 * @return {@code true} if the file has a video extension, {@code false} otherwise.
	 */
	public static boolean isVideo(DocumentFile file) {
		return isFileType(file, FileExtensions.VIDEO_EXTENSIONS);
	}
	
	/**
	 * Checks if the given {@link DocumentFile} is a document based on its file extension.
	 *
	 * @param file The document file to check.
	 * @return {@code true} if the file has a document extension, {@code false} otherwise.
	 */
	public static boolean isDocument(DocumentFile file) {
		return isFileType(file, FileExtensions.DOCUMENT_EXTENSIONS);
	}
	
	/**
	 * Checks if the given {@link DocumentFile} is an image based on its file extension.
	 *
	 * @param file The file to check.
	 * @return {@code true} if the file's extension matches one of the predefined image extensions,
	 * {@code false} otherwise.
	 */
	public static boolean isImage(DocumentFile file) {
		return isFileType(file, FileExtensions.IMAGE_EXTENSIONS);
	}
	
	/**
	 * Determines the category or folder type of given {@link DocumentFile} based on its extension.
	 *
	 * @param file The DocumentFile to analyze.
	 * @return A string representing the folder category (e.g., Music, Video, Documents)
	 * defined in {@link StaticAppInfo}. Returns the default "others" category
	 * if the file is null or the extension is unrecognized.
	 */
	public static String getFileType(DocumentFile file) {
		return getFileType(file != null ? file.getName() : null);
	}
	
	/**
	 * Categorizes a file based on its name and extension into a specific download folder category.
	 * <p>
	 * This method checks the file extension against known categories such as Audio, Archive,
	 * Program, Video, Document, and Image. If no match is found, it defaults to a general folder.
	 * </p>
	 *
	 * @param fileName The name of the file to categorize, including its extension.
	 * @return A string representing the folder category path defined in {@link StaticAppInfo}.
	 */
	public static String getFileType(@Nullable String fileName) {
		if (isAudioByName(fileName)) return StringHelper.getText(R.string.title_vidflow_sounds);
		if (isArchiveByName(fileName)) return StringHelper.getText(R.string.title_vidflow_archives);
		if (isProgramByName(fileName)) return StringHelper.getText(R.string.title_vidflow_programs);
		if (isVideoByName(fileName)) return StringHelper.getText(R.string.title_vidflow_videos);
		if (isDocumentByName(fileName)) return StringHelper.getText(R.string.title_vidflow_documents);
		if (isImageByName(fileName)) return StringHelper.getText(R.string.title_vidflow_images);
		return StringHelper.getText(R.string.title_vidflow_others);
	}
	
	/**
	 * Checks if a given file name ends with any of the specified extensions.
	 * The comparison is case-insensitive and automatically handles the dot separator.
	 *
	 * @param fileName   The name of the file to check. Can be null.
	 * @param extensions An array of extensions (e.g., "jpg", "mp3") to check against.
	 * @return {@code true} if the file name ends with one of the extensions, {@code false} otherwise.
	 */
	public static boolean endsWithExtension(@Nullable String fileName,
	                                        String[] extensions) {
		if (fileName == null) return false;
		String lowerName = fileName.toLowerCase(Locale.getDefault());
		for (String extension : extensions) {
			String lowerCase = extension.toLowerCase(Locale.getDefault());
			if (lowerName.endsWith("." + lowerCase)) return true;
		}
		return false;
	}
	
	/**
	 * Determines if a file is an audio file based on its name and extension.
	 *
	 * @param name The name of the file to check, including its extension.
	 * @return {@code true} if the file extension matches a known audio format
	 * defined in {@link FileExtensions#MUSIC_EXTENSIONS}, {@code false} otherwise.
	 */
	public static boolean isAudioByName(@Nullable String name) {
		return endsWithExtension(name, FileExtensions.MUSIC_EXTENSIONS);
	}
	
	/**
	 * Checks if the given file name represents an archive based on its extension.
	 *
	 * @param name The name of the file to check, can be null.
	 * @return {@code true} if the file name ends with a supported archive extension,
	 * {@code false} otherwise or if the name is null.
	 */
	public static boolean isArchiveByName(@Nullable String name) {
		return endsWithExtension(name, FileExtensions.ARCHIVE_EXTENSIONS);
	}
	
	/**
	 * Checks if a given file name corresponds to a program or executable file based on its extension.
	 *
	 * @param name The name of the file to check, including its extension.
	 * @return {@code true} if the file name ends with a recognized program extension;
	 * {@code false} otherwise or if the name is null.
	 */
	public static boolean isProgramByName(@Nullable String name) {
		return endsWithExtension(name, FileExtensions.PROGRAM_EXTENSIONS);
	}
	
	/**
	 * Checks if the given file name corresponds to a video file based on its extension.
	 *
	 * @param name The name of the file to check, including the extension. Can be null.
	 * @return {@code true} if the file name ends with a known video extension; {@code false} otherwise.
	 */
	public static boolean isVideoByName(@Nullable String name) {
		return endsWithExtension(name, FileExtensions.VIDEO_EXTENSIONS);
	}
	
	/**
	 * Checks if the given file name corresponds to a document file based on its extension.
	 *
	 * @param name The name of the file to check, including the extension.
	 * @return {@code true} if the file name ends with a recognized document extension;
	 * {@code false} otherwise or if the name is null.
	 */
	public static boolean isDocumentByName(@Nullable String name) {
		return endsWithExtension(name, FileExtensions.DOCUMENT_EXTENSIONS);
	}
	
	/**
	 * Checks if the given file name has an image extension.
	 *
	 * @param name The name of the file to check, including its extension.
	 * @return {@code true} if the file name ends with a supported image extension;
	 * {@code false} otherwise or if the name is null.
	 */
	public static boolean isImageByName(@Nullable String name) {
		return endsWithExtension(name, FileExtensions.IMAGE_EXTENSIONS);
	}
	
	/**
	 * Extracts the base name of a file by removing its extension.
	 * <p>
	 * This method searches for the last occurrence of a dot (.) and returns the substring
	 * preceding it. If no dot is found, or if the dot is the first character, the original
	 * file name is returned.
	 * </p>
	 *
	 * @param fileName The name of the file to process.
	 * @return The file name without the extension, or the original name if no extension exists.
	 */
	public static String getFileNameWithoutExtension(String fileName) {
		try {
			int dotIndex = fileName.lastIndexOf('.');
			if (dotIndex > 0) return fileName.substring(0, dotIndex);
			return fileName;
		} catch (Exception error) {
			logger.error("Error getting file name without extension", error);
			return fileName;
		}
	}
	
	/**
	 * Calculates the SHA-256 hash (checksum) of a given file and returns it as a hexadecimal string.
	 * <p>
	 * This method reads the file in chunks to efficiently process large files without consuming
	 * excessive memory.
	 *
	 * @param file The file to be hashed.
	 * @return A lowercase hexadecimal string representing the SHA-256 hash of the file,
	 * or {@code null} if an error occurs (e.g., file not found, no read permissions,
	 * or algorithm not available).
	 */
	@Nullable
	public static String getFileSha256(File file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (FileInputStream input = new FileInputStream(file)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				
				while ((bytesRead = input.read(buffer)) != -1) {
					digest.update(buffer, 0, bytesRead);
				}
			}
			
			byte[] hashBytes = digest.digest();
			StringBuilder hexString = new StringBuilder();
			
			for (byte b : hashBytes) {
				hexString.append(String.format("%02x", b));
			}
			
			return hexString.toString();
		} catch (Exception error) {
			logger.error("Error getting file sha256", error);
			return null;
		}
	}
	
	/**
	 * Calculates the total storage capacity of the device's internal data directory.
	 * <p>
	 * This method uses {@link StatFs} to retrieve the block size and total block count
	 * of the internal storage partition, returning the total space in bytes.
	 * </p>
	 *
	 * @return The total internal storage space in bytes.
	 */
	public static long getTotalStorageSpace() {
		File path = Environment.getDataDirectory();
		StatFs stat = new StatFs(path.getPath());
		long blockSize = stat.getBlockSizeLong();
		long totalBlocks = stat.getBlockCountLong();
		return totalBlocks * blockSize;
	}
	
	/**
	 * Calculates the amount of free (available) storage space on the device's internal data partition.
	 * <p>
	 * This method uses {@link StatFs} to query the internal data directory
	 * and calculates the size based on the number of available blocks and the block size.
	 * </p>
	 *
	 * @return The amount of free storage space in bytes.
	 */
	public static long getFreeStorageSpace() {
		File path = Environment.getDataDirectory();
		StatFs stat = new StatFs(path.getPath());
		long blockSize = stat.getBlockSizeLong();
		long availableBlocks = stat.getAvailableBlocksLong();
		return availableBlocks * blockSize;
	}
	
	/**
	 * Calculates the percentage of free space available on the internal storage.
	 * <p>
	 * This method compares the available space against the total space of the data directory.
	 * The result is a floating-point number between 0 and 100.
	 * </p>
	 *
	 * @return The percentage of free internal storage space (0.0 to 100.0).
	 * Returns 0.0 if the total space cannot be determined.
	 */
	public static float getFreeStoragePercentage() {
		long totalSpace = getTotalStorageSpace();
		if (totalSpace <= 0) return 0f;
		long freeSpace = getFreeStorageSpace();
		return ((float) freeSpace / totalSpace) * 100;
	}
	
	/**
	 * Calculates the total storage capacity of the primary external storage directory in bytes.
	 * <p>
	 * This method checks if the external storage is currently mounted and accessible.
	 * If mounted, it uses {@link StatFs} to determine the total number of
	 * blocks and the block size to calculate the total capacity.
	 * </p>
	 *
	 * @return The total external storage space in bytes, or {@code 0L} if the
	 * external storage is not mounted.
	 */
	public static long getTotalExternalStorageSpace() {
		String externalStorageState = Environment.getExternalStorageState();
		if (externalStorageState.equals(Environment.MEDIA_MOUNTED)) {
			File path = Environment.getExternalStorageDirectory();
			StatFs stat = new StatFs(path.getPath());
			long blockSize = stat.getBlockSizeLong();
			long totalBlocks = stat.getBlockCountLong();
			return totalBlocks * blockSize;
		} else {
			return 0L;
		}
	}
	
	/**
	 * Calculates the available (free) space on the primary external storage device in bytes.
	 * <p>
	 * This method checks if the external storage is currently mounted and accessible.
	 * If mounted, it retrieves the block size and the number of available blocks to
	 * calculate the total free bytes.
	 * </p>
	 *
	 * @return The amount of free space in bytes, or {@code 0L} if the external storage
	 * is not mounted.
	 */
	public static long getFreeExternalStorageSpace() {
		String externalStorageState = Environment.getExternalStorageState();
		if (externalStorageState.equals(Environment.MEDIA_MOUNTED)) {
			File path = Environment.getExternalStorageDirectory();
			StatFs stat = new StatFs(path.getPath());
			long blockSize = stat.getBlockSizeLong();
			long availableBlocks = stat.getAvailableBlocksLong();
			return availableBlocks * blockSize;
		} else {
			return 0L;
		}
	}
	
	/**
	 * Calculates the percentage of free space available on the primary external storage device.
	 * <p>
	 * This method compares the available space against the total space of the external storage.
	 * If the storage is not mounted or the total space is zero, it returns 0.
	 * </p>
	 *
	 * @return The percentage of free external storage space as a float (0.0 to 100.0).
	 */
	public static float getFreeExternalStoragePercentage() {
		long totalSpace = getTotalExternalStorageSpace();
		if (totalSpace > 0) {
			long freeSpace = getFreeExternalStorageSpace();
			return ((float) freeSpace / totalSpace) * 100;
		} else {
			return 0f;
		}
	}
	
	/**
	 * Converts a size in bytes into a human-readable string format (Bytes, KB, MB, or GB).
	 * <p>
	 * This method formats the size with two decimal places and uses the default locale.
	 * It uses the binary prefix system where 1 KB = 1024 bytes.
	 * </p>
	 *
	 * @param fileSize The size in bytes to be formatted.
	 * @return A formatted string representing the size with its appropriate unit (e.g., "1.50 MB").
	 */
	public static String humanReadableSizeOf(long fileSize) {
		if (fileSize < 0) {
			String errorMessage = "File size cannot be negative: ";
			throw new IllegalArgumentException(errorMessage + fileSize);
		}
		return humanReadableSizeOf((double) fileSize);
	}
	
	/**
	 * Converts a file size in bytes into a human-readable string representation (e.g., "1.50 GB").
	 * <p>
	 * This method categorizes the size into GB, MB, KB, or Bytes based on the magnitude of the
	 * provided long value and formats the result to two decimal places.
	 * </p>
	 *
	 * @param size The size in bytes to be converted.
	 * @return A formatted string representing the human-readable size with its appropriate unit.
	 */
	public static String humanReadableSizeOf(double size) {
		if (size < 0) {
			String errorMessage = "File size cannot be negative: ";
			throw new IllegalArgumentException(errorMessage + size);
		}
		
		DecimalFormat df = new DecimalFormat("##.##");
		if (size >= ONE_GB) {
			return formatUnit(size / ONE_GB, "GB", df);
		} else if (size >= ONE_MB) {
			return formatUnit(size / ONE_MB, "MB", df);
		} else if (size >= ONE_KB) {
			return formatUnit(size / ONE_KB, "KB", df);
		} else {
			return formatUnit(size, "B", df);
		}
	}
	
	public static String toMegabytesString(double size) {
		if (size < 0) {
			String errorMessage = "File size cannot be negative: ";
			throw new IllegalArgumentException(errorMessage + size);
		}
		
		double sizeInMb = size / (1024.0 * 1024.0);
		DecimalFormat df = new DecimalFormat("##.##");
		return df.format(sizeInMb) + " MB";
	}
	
	/**
	 * Formats a byte size into a human-readable string with the appropriate unit (B, KB, MB, or GB).
	 * <p>
	 * This method converts a raw byte count into a more readable format by scaling it to the
	 * largest applicable unit and rounding the result to two decimal places.
	 * </p>
	 *
	 * @param value The size in bytes to be formatted.
	 * @param unit  The unit of the size (e.g., "GB", "MB", "KB", or "B").
	 * @param df    The DecimalFormat used to format the result.
	 * @return A formatted string representing the size (e.g., "1.50 MB", "234.00 B").
	 */
	private static String formatUnit(double value, String unit, DecimalFormat df) {
		return df.format(value) + " " + unit;
	}
}
