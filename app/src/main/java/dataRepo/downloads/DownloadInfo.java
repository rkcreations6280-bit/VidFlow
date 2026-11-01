package dataRepo.downloads;

import static coreUtils.library.storage.FileStorageUtility.saveStringToInternalStorage;

import androidx.annotation.Nullable;

import com.nextgen.R;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

import coreUtils.library.process.DownloaderUtils;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.storage.FileExtensions;
import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.strings.StringHelper;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;


public class DownloadInfo implements Serializable {
    private final transient LoggerUtils logger = LoggerUtils.from(getClass());

    
    public Long downloadId = 0L;

    public int downloadStatus = DOWNLOAD_STATUS_PAUSED;
    public String downloadStatusInfo = "--";
    public String userAlertMessage = "";

    public boolean isRunning = false;
    public boolean isComplete = false;
    public boolean isDeleted = false;
    public boolean isRemoved = false;
    public boolean hasUserOpenedTheFile = false;

    public boolean isWaitingForNetwork = false;
    public boolean isResumeSupported = false;
    public boolean isMultiThreadSupported = false;
    public boolean isFileSizeUnknown = false;
    public boolean isDownloadFromBrowser = false;

    public boolean isFileUrlExpired = false;
    public boolean expiredUrlDialogShown = false;
    public boolean isDestinationFileMissing = false;
    public boolean isChecksumInvalid = false;
    public boolean isFileCorrupted = false;
    public boolean isSmartDirInitialized = false;

    public boolean isSmartDirEnabled = true;
    public boolean isThumbnailDisabled = false;
    public boolean isCompletionAudioEnabled = true;
    public boolean isNotificationDisabled = false;
    public boolean isAutoResumeEnabled = true;
    public int autoResumeRetryLimit = 35;
    public long downloadSpeedCap = 0L;
    public boolean isWifiRequired = false;
    public String customUserAgent = "";
    public String proxyServer = "";

    public String ytdlpExecutionCommand = "";
    public boolean isYtdlpInitialized = false;
    public boolean isYtdlpErrorFound = false;

    public String ytdlpErrorMessage = "";
    public String ytdlpTempOutputPath = "";
    public String ytdlpStatusInfo = "";

    public String fileName = "";
    public String fileMimeType = "";
    public String fileCategoryName = "";
    public String fileDirectory = "";
    public String fileDirectoryURI = "";
    public String fileContentDisposition = "";
    public String fileChecksum = "";
    public String fileSiteReferrer = "";
    public String fileExtension = "";
    public String fileUrl = "";
    public String finalFilePath = "";

    public long fileSize = 0L;
    public String fileSizeInFormat = "";
    public long downloadedByte = 0L;
    public String downloadedByteInFormat = "--";
    public long remainingBytes = 0L;

    public long progressPercentage = 0L;
    public String progressPercentageInFormat = "";

    public String thumbPath = "";
    public String thumbnailUrl = "";

    public String siteCookieString = "";
    public Map<String, String> extraWebHeaders = null;

    public long startTimeDate = 0L;
    public String startTimeDateInFormat = "";

    public long lastModifiedTimeDate = 0L;
    public String lastModifiedTimeDateInFormat = "";

    public long completedTimeDate = 0L;
    public String completedTimeDateInFormat = "";

    public long averageSpeed = 0L;
    public String averageSpeedInFormat = "--";

    public long maxSpeed = 0L;
    public String maxSpeedInFormat = "--";

    public long realtimeSpeed = 0L;
    public String realtimeSpeedInFormat = "--";

    public long timeSpentInMilliSec = 0L;
    public String timeSpentInFormat = "--:--";

    public long remainingTimeInSec = 0L;
    public String remainingTimeInFormat = "--:--";

    public int resumeSessionRetryCount = 0;
    public int totalTrackedConnectionRetries = 0;
    public int failedChunkCount = 0;
    public int activeThreadCount = 0;

    public long[] partStartingPoint = new long[18];
    public long[] partEndingPoint = new long[18];
    public long[] partChunkSizes = new long[18];
    public long[] partsDownloadedByte = new long[18];
    public int[] partProgressPercentage = new int[18];

    public transient VideoMetaInfo videoMetaInfo = null;
    public long videoInfoId = -1L;

    public transient VideoFormateInfo videoFormatInfo = null;
    public long videoFormatId = -1L;

    public transient RemoteFileInfo remoteFileInfo = null;
    public long remoteFileInfoId = -1L;

    public Long mediaPlaybackDuration = 0L;
    public String mediaPlaybackDurationInFormat = "--:--";

    public static final String ID_DOWNLOAD_KEY = "Download_ID_KEY";
    public static final String DOWNLOAD_THUMBNAIL_FILE_EXTENSION = ".download_thub.jpg";
    public static final String DOWNLOAD_COOKIE_FILE_EXTENSION = ".download_cookie.jpg";
    public static final String TEMP_DOWNLOAD_FILE_EXTENSION = ".tmp_download";

    public static final int DOWNLOAD_STATUS_PENDING = 0;
    public static final int DOWNLOAD_STATUS_DOWNLOADING = 1;
    public static final int DOWNLOAD_STATUS_PAUSED = 2;
    public static final int DOWNLOAD_STATUS_FAILED = 3;
    public static final int DOWNLOAD_STATUS_COMPLETED = 4;

    public DownloadInfo() {
        initStorageLocation();
    }

    private void initStorageLocation() {
        AppConfigs appConfig = AppConfigsRepo.getConfig();
        if (appConfig == null) return;

        if (appConfig.defaultDownloadLocationType == AppConfigs.PRIVATE_FOLDER) {
            File appsInternalDataFolder = FileStorageUtility.getInternalDataFolder();
            String internalDataFolderPath = appsInternalDataFolder.getAbsolutePath();
            String appDownloadDirPath = internalDataFolderPath + "/Downloaded_Files";
            fileDirectory = StringHelper.removeDuplicateSlashes(appDownloadDirPath);

        } else if (appConfig.defaultDownloadLocationType == AppConfigs.SYSTEM_GALLERY) {
            File publicDownloadFolder = FileStorageUtility.getPublicDownloadFolder();
            String publicDownloadFolderPath = publicDownloadFolder.getAbsolutePath();
            String fullDownloadPath = publicDownloadFolderPath + "/" + StringHelper.getText(R.string.title_default_app_folder);
            fileDirectory = StringHelper.removeDuplicateSlashes(fullDownloadPath);
        }
    }

    public String getFileCategoryName() {
        return getFileCategoryName(false);
    }

    public String getFileCategoryName(boolean omitBranding) {
        if (omitBranding) {
            if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.IMAGE_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_images);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.VIDEO_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_videos);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.MUSIC_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_sounds);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.DOCUMENT_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_documents);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.PROGRAM_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_programs);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.ARCHIVE_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_archives);
            } else {
                return StringHelper.getText(R.string.title_vidflow_others);
            }
        } else {
            if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.IMAGE_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_vidflow_images);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.VIDEO_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_vidflow_videos);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.MUSIC_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_vidflow_sounds);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.DOCUMENT_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_vidflow_documents);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.PROGRAM_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_vidflow_programs);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.ARCHIVE_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_vidflow_archives);
            } else {
                return StringHelper.getText(R.string.title_vidflow_others);
            }
        }
    }

    @Nullable
    public String getCookiesFilePathIfAvailable() {
        if (siteCookieString == null || siteCookieString.isEmpty()) return null;

        String cookieFileName = downloadId + DOWNLOAD_COOKIE_FILE_EXTENSION;
        File appsInternalDataFolder = FileStorageUtility.getInternalDataFolder();
        File cookieFile = new File(appsInternalDataFolder, cookieFileName);
        if (cookieFile.exists() && cookieFile.isFile()) {
            return cookieFile.getAbsolutePath();
        } else {
            File savedCookieFile = saveCookiesIfAvailable(false);
            if (savedCookieFile == null) {
                return null;
            } else {
                return savedCookieFile.getAbsolutePath();
            }
        }
    }

    @Nullable
    public File saveCookiesIfAvailable(boolean shouldOverride) {
        if (siteCookieString == null || siteCookieString.isEmpty()) {
            return null;
        }

        String cookieFileName = downloadId + DOWNLOAD_COOKIE_FILE_EXTENSION;
        File appsInternalDataFolder = FileStorageUtility.getInternalDataFolder();
        File cookieFile = new File(appsInternalDataFolder, cookieFileName);
        if (!shouldOverride && cookieFile.exists()) {
            return cookieFile;
        }

        String formattedCookies = DownloaderUtils.convertToNetscapeCookies(siteCookieString);
        boolean isSaved = saveStringToInternalStorage(cookieFile, formattedCookies);
        if (isSaved && cookieFile.exists()) {
            return cookieFile;
        } else {
            return null;
        }
    }

    public File getDestinationFile() {
        return new File(fileDirectory, fileName);
    }
}