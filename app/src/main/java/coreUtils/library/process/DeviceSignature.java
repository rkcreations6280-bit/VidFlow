package coreUtils.library.process;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.nextgen.R;

import java.security.MessageDigest;

import coreUtils.library.strings.StringHelper;

public final class DeviceSignature {

    private static volatile DeviceSignature instance;
    private static volatile String cachedId;
    private final Context appContext;
    private static final String APP_SALT =
            "com." + StringHelper.getText(R.string.title_app_name) + ".pro";

    private DeviceSignature(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    public static DeviceSignature getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (DeviceSignature.class) {
                if (instance == null) {
                    instance = new DeviceSignature(context);
                }
            }
        }
        return instance;
    }

    @NonNull
    public String generate() {
        if (cachedId != null) return cachedId;

        synchronized (DeviceSignature.class) {
            if (cachedId != null) return cachedId;

            String fingerprint =
                    androidId() + "|" +
                            Build.MANUFACTURER + "|" +
                            Build.MODEL + "|" +
                            Build.BOARD + "|" +
                            appContext.getPackageName() + "|" +
                            APP_SALT;

            cachedId = sha256(fingerprint);
            return cachedId;
        }
    }

    @SuppressLint("HardwareIds")
    @NonNull
    private String androidId() {
        String id = Settings.Secure.getString(
                appContext.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        if (id == null || id.isEmpty() ||
                "9774d56d682e549c".equals(id)) {
            return "fallback";
        }

        return id;
    }

    @NonNull
    private String sha256(@NonNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}