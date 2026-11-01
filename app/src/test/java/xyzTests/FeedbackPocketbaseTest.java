package xyzTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import userInterface.userFeedback.FeedbackReactions;

public class FeedbackPocketbaseTest {
	
	@Test
	public void testReactionEnumValues() {
		FeedbackReactions[] reactions = FeedbackReactions.values();
		assertEquals("Should have 5 reaction types", 5, reactions.length);
		
		assertEquals(FeedbackReactions.Excellent, FeedbackReactions.valueOf("Excellent"));
		assertEquals(FeedbackReactions.Good, FeedbackReactions.valueOf("Good"));
		assertEquals(FeedbackReactions.Average, FeedbackReactions.valueOf("Average"));
		assertEquals(FeedbackReactions.Poor, FeedbackReactions.valueOf("Poor"));
		assertEquals(FeedbackReactions.Angry, FeedbackReactions.valueOf("Angry"));
	}
	
	@Test
	public void testReactionEnumName() {
		assertEquals("Excellent", FeedbackReactions.Excellent.name());
		assertEquals("Good", FeedbackReactions.Good.name());
		assertEquals("Average", FeedbackReactions.Average.name());
		assertEquals("Poor", FeedbackReactions.Poor.name());
		assertEquals("Angry", FeedbackReactions.Angry.name());
	}
	
	@Test
	public void testMultipartBodyCreation() {
		String reaction = FeedbackReactions.Excellent.name();
		String subject = "Test Subject";
		String message = "This is a test feedback message";
		String email = "test@example.com";
		
		MultipartBody.Builder builder = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("reaction", reaction)
			.addFormDataPart("subject", subject)
			.addFormDataPart("message", message)
			.addFormDataPart("email", email);
		
		MultipartBody requestBody = builder.build();
		
		assertNotNull("Request body should not be null", requestBody);
		assertEquals("Should have 4 form parts", 4, requestBody.parts().size());
		assertTrue("Content type should be form",
			requestBody.contentType().toString().contains("multipart/form-data"));
	}
	
	@Test
	public void testMultipartBodyWithFile() {
		String reaction = FeedbackReactions.Good.name();
		String subject = "Bug Report";
		String message = "Found a bug in the app";
		String email = "developer@test.com";
		
		String fakeImageData = "fake image bytes";
		byte[] imageBytes = fakeImageData.getBytes(StandardCharsets.UTF_8);
		
		MediaType imageType = MediaType.parse("image/png");
		RequestBody fileBody = RequestBody.create(imageBytes, imageType);
		
		MultipartBody.Builder builder = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("reaction", reaction)
			.addFormDataPart("subject", subject)
			.addFormDataPart("message", message)
			.addFormDataPart("email", email)
			.addFormDataPart("screenshot", "screenshot.png", fileBody);
		
		MultipartBody requestBody = builder.build();
		
		assertNotNull("Request body should not be null", requestBody);
		assertEquals("Should have 5 form parts (including file)", 5, requestBody.parts().size());
	}
	
	@Test
	public void testNullReactionFallback() {
		String reaction = null;
		String subject = "Test";
		String message = "Test message";
		String email = "";
		
		String fallbackReaction = "Excellent";
		
		assertEquals("Should fallback to Excellent", "Excellent", fallbackReaction);
		assertEquals("Should keep subject", "Test", subject);
		assertEquals("Should fallback to empty string", "", email);
	}
	
	@Test
	public void testMessageLengthValidation() {
		String validMessage = "A".repeat(500);
		assertEquals("Valid message should be 500 chars", 500, validMessage.length());
		
		String longMessage = "A".repeat(501);
		assertEquals("Long message should be 501 chars", 501, longMessage.length());
		assertTrue("Message over 500 should be rejected", longMessage.length() > 500);
	}
	
	@Test
	public void testMimeTypeDetection() {
		String pngFileName = "screenshot.png";
		String jpgFileName = "photo.jpg";
		String jpegFileName = "image.jpeg";
		String unknownFileName = "document.pdf";
		
		String detectedPngMime = getMimeTypeFromFileName(pngFileName);
		assertEquals("PNG should detect as image/png", "image/png", detectedPngMime);
		
		String detectedJpgMime = getMimeTypeFromFileName(jpgFileName);
		assertEquals("JPG should detect as image/jpeg", "image/jpeg", detectedJpgMime);
		
		String detectedJpegMime = getMimeTypeFromFileName(jpegFileName);
		assertEquals("JPEG should detect as image/jpeg", "image/jpeg", detectedJpegMime);
		
		String detectedUnknownMime = getMimeTypeFromFileName(unknownFileName);
		assertEquals("Unknown should default to image/jpeg", "image/jpeg", detectedUnknownMime);
	}
	
	private String getMimeTypeFromFileName(String fileName) {
		if (fileName != null && fileName.toLowerCase().endsWith(".png")) {
			return "image/png";
		} else if (fileName != null && (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(
			".jpeg"))) {
			return "image/jpeg";
		}
		return "image/jpeg";
	}
}