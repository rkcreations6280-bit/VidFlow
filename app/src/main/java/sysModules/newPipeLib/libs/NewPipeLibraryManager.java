package sysModules.newPipeLib.libs;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import coreUtils.library.networks.HttpClientProvider;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import okhttp3.OkHttpClient;

public final class NewPipeLibraryManager {
	private static final LoggerUtils logger = LoggerUtils.from(NewPipeLibraryManager.class);
	
	private NewPipeLibraryManager() {}
	
	public static void initializeNewPipeLibrary() {
		ThreadTask<Boolean, Boolean> newPipeInitTask = new ThreadTask<>();
		newPipeInitTask.setBackgroundTask(callback -> {
			try {
				logger.debug("NewPipeExtractor library is initializing");
				sysModules.downloadSys.NewPipeEngine.ensureInit();
				logger.debug("NewPipeExtractor library has been initialized");
				return true;
			} catch (Exception error) {
				logger.error("NewPipeExtractor library initialization failed.");
				return false;
			}
		});
		newPipeInitTask.start();
	}
}
