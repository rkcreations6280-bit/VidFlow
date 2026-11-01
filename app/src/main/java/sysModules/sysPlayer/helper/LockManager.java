package sysModules.sysPlayer.helper;

import android.content.Context;
import android.net.wifi.WifiManager;

import androidx.core.content.ContextCompat;

import coreUtils.library.process.LoggerUtils;

/**
 * Manages both {@link android.os.PowerManager.WakeLock} and
 * {@link WifiManager.WifiLock} together. The WiFi lock prevents Android
 * from disconnecting WiFi during long background audio streams (e.g. music),
 * while the CPU wake lock keeps the processor active for decoding.
 */
public final class LockManager {
	private static final LoggerUtils logger = LoggerUtils.from(LockManager.class);
	private static final String WAKE_LOCK_TAG = "VidFlow:LockManager";
	
	private final android.os.PowerManager powerManager;
	private final WifiManager wifiManager;
	
	private android.os.PowerManager.WakeLock wakeLock;
	private WifiManager.WifiLock wifiLock;
	
	public LockManager(final Context context) {
		powerManager = ContextCompat.getSystemService(context.getApplicationContext(),
			android.os.PowerManager.class);
		wifiManager = ContextCompat.getSystemService(context, WifiManager.class);
	}
	
	public void acquireWifiAndCpu() {
		logger.debug("acquireWifiAndCpu() called");
		if (wakeLock != null && wakeLock.isHeld() && wifiLock != null && wifiLock.isHeld()) {
			return;
		}
		
		if (powerManager != null) {
			wakeLock = powerManager.newWakeLock(
				android.os.PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
			wakeLock.acquire(30 * 60 * 1000L);
		}
		
		if (wifiManager != null) {
			wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, WAKE_LOCK_TAG);
			wifiLock.acquire();
		}
	}
	
	public void releaseWifiAndCpu() {
		logger.debug("releaseWifiAndCpu() called");
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}
		if (wifiLock != null && wifiLock.isHeld()) {
			wifiLock.release();
		}
		
		wakeLock = null;
		wifiLock = null;
	}
	
	public boolean isHeld() {
		return (wakeLock != null && wakeLock.isHeld())
			|| (wifiLock != null && wifiLock.isHeld());
	}
}
