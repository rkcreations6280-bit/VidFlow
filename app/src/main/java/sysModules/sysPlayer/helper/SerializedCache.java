package sysModules.sysPlayer.helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import sysModules.sysPlayer.queue.PlayQueue;

public final class SerializedCache {
    private static final String TAG = "SerializedCache";
    private static final String FILE_NAME = "play_queue_cache.dat";

    private SerializedCache() {}

    public static void writePlayQueue(@Nullable Context context, @Nullable PlayQueue queue) {
        if (context == null || queue == null) return;
        try {
            File file = new File(context.getCacheDir(), FILE_NAME);
            try (FileOutputStream fos = new FileOutputStream(file);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(queue);
                oos.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write play queue cache", e);
        }
    }

    @Nullable
    public static PlayQueue readPlayQueue(@Nullable Context context) {
        if (context == null) return null;
        File file = new File(context.getCacheDir(), FILE_NAME);
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (PlayQueue) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "Failed to read play queue cache", e);
            return null;
        }
    }

    public static void clear(@Nullable Context context) {
        if (context == null) return;
        File file = new File(context.getCacheDir(), FILE_NAME);
        if (file.exists()) file.delete();
    }
}
