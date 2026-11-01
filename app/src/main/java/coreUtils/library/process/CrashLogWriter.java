package coreUtils.library.process;

import static java.util.Locale.getDefault;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;

import com.nextgen.R;

import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.strings.StringHelper;

public final class CrashLogWriter {

    private static final LoggerUtils logger = LoggerUtils.from(CrashLogWriter.class);
    private static final ExecutorService EXECUTOR = newSingleThreadExecutor();
    private static PrintWriter writer;

    private static synchronized PrintWriter getWriter() {
        if (writer == null) {
            try {
                File publicDownloadFolder = FileStorageUtility.getPublicDownloadFolder();
                String storageRoot = publicDownloadFolder.getAbsolutePath();
                logger.debug("Getting device public download path:" + storageRoot);
                if (storageRoot.isEmpty()) {
                    logger.error("Failed to get device public download path");
                    return null;
                }

                String subfolderName = StringHelper.getText(R.string.title_default_app_folder);
                String configPath = storageRoot + "/" + subfolderName + "/.configs";
                configPath = StringHelper.removeDuplicateSlashes(configPath);

                File configDirectory = new File(configPath);
                if (!configDirectory.exists()) {
                    boolean hasCreated = configDirectory.mkdirs();
                    if (hasCreated) {
                        logger.debug("Created log directory");
                    } else {
                        logger.error("Failed to create log directory");
                        return null;
                    }
                }

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", getDefault());
                String timeStamp = dateFormat.format(System.currentTimeMillis());
                String fileName = ".crash_log_stream" + timeStamp + ".txt";
                File crashLogFile = new File(configDirectory, fileName);

                writer = new PrintWriter(new FileWriter(crashLogFile, true), true);
            } catch (IOException error) {
                logger.error("Failed to open log file", error);
            }
        }
        return writer;
    }

    public static void record(String tag, String message) {
        EXECUTOR.execute(() -> {
            try {
                PrintWriter printWriter = getWriter();
                if (printWriter != null) {
                    printWriter.println(System.currentTimeMillis()
                            + " [" + tag + "] "
                            + message);
                }
            } catch (Exception error) {
                logger.error("Failed to record crash log", error);
            }
        });
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
        if (writer != null) writer.close();
    }
}