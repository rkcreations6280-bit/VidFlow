package dataRepo.appConfigs;

/**
 * Observer interface for receiving notifications when the application configuration
 * changes. Implement this interface and register with
 * {@link AppConfigsRepo#registerObserver(AppConfigsObserver)} to react to updates
 * to the persistent {@link AppConfigs} entity.
 *
 * <p>The callback is invoked after the configuration has been successfully saved
 * to the database. Use this to update UI components, refresh cached values, or
 * trigger dependent operations that rely on the new configuration state.
 *
 * @see AppConfigsRepo#registerObserver(AppConfigsObserver)
 * @see AppConfigsRepo#unregisterObserver(AppConfigsObserver)
 * @see AppConfigs
 */
public interface AppConfigsObserver {
	
	/**
	 * Called automatically whenever the application configuration is updated.
	 * Implementations should handle this callback to react to changes in persistent
	 * settings, such as theme preferences, feature toggles, or user-specific options.
	 *
	 * <p>This method is invoked on the thread that triggered the database change
	 * (typically the main thread if {@link AppConfigsRepo#save(AppConfigs)} is called
	 * from the UI thread). Avoid performing long-running operations synchronously
	 * within this callback; delegate heavy work to background threads.
	 *
	 * @param config The updated {@link AppConfigs} instance containing the new
	 *               configuration values. Never {@code null}.
	 * @see AppConfigsRepo#registerObserver(AppConfigsObserver)
	 * @see AppConfigsRepo#save(AppConfigs)
	 */
	void onConfigChanged(AppConfigs config);
}