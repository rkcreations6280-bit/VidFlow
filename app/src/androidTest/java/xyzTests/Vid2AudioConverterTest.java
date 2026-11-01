package xyzTests;

import static android.os.Environment.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import coreUtils.library.media.Vid2AudioConverter;

@RunWith(AndroidJUnit4.class)
public class Vid2AudioConverterTest {
	
	private Context context;
	private Vid2AudioConverter converter;
	
	@Before
	public void setUp() {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		converter = new Vid2AudioConverter();
	}

	@After
	public void tearDown() {
		File downloadsDir = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
		File outputFile = new File(downloadsDir, "extracted_audio.mp3");
		if (outputFile.exists()) {
			outputFile.delete();
			System.out.println("Cleaned up: " + outputFile.getAbsolutePath());
		}
	}
	
	@Test
	public void testAudioExtraction() throws Exception {
		File inputFile = new File(context.getCacheDir(), "test_video.mp4");
		
		Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
		copyAssetToFile(testContext, "test_video.mp4", inputFile);
		
		File downloadsDir = getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS);
		File outputFile = new File(downloadsDir, "extracted_audio.mp3");

		if (outputFile.exists()) {
			outputFile.delete();
		}
		
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> resultPath = new AtomicReference<>();
		AtomicReference<String> errorMessage = new AtomicReference<>();
		
		new Handler(Looper.getMainLooper()).post(() ->
			converter.extractAudio(
				null,
				inputFile.getAbsolutePath(),
				outputFile.getAbsolutePath(),
				new Vid2AudioConverter.ConversionListener() {
					@Override
					public void onProgress(int progress) {
						System.out.println("Extraction progress: " + progress + "%");
					}
					
					@Override
					public void onSuccess(String outputFile) {
						resultPath.set(outputFile);
						latch.countDown();
					}
					
					@Override
					public void onFailure(String error) {
						errorMessage.set(error);
						latch.countDown();
					}
				}));
		
		boolean completed = latch.await(60, TimeUnit.SECONDS);
		assertTrue("Extraction timed out", completed);
		
		if (errorMessage.get() != null) {
			fail("Extraction failed: " + errorMessage.get());
		}
		
		assertNotNull("Result path should not be null", resultPath.get());
		File resultFile = new File(resultPath.get());
		assertTrue("Output file should exist", resultFile.exists());
		assertTrue("Output file should not be empty", resultFile.length() > 0);
		
		System.out.println("Audio extraction successful. Saved at: " + resultPath.get() +
			" (Size: " + resultFile.length() + " bytes)");
	}
	
	private void copyAssetToFile(Context sourceContext, String assetName, File outFile) throws IOException {
		try (InputStream in = sourceContext.getAssets().open(assetName);
		     OutputStream out = new FileOutputStream(outFile)) {
			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
	}
}
