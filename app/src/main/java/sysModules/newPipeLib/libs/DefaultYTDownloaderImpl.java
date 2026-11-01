package sysModules.newPipeLib.libs;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import coreUtils.library.process.LoggerUtils;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class DefaultYTDownloaderImpl extends Downloader {

    private final LoggerUtils logger = LoggerUtils.from(DefaultYTDownloaderImpl.class);
    private static final byte[] EMPTY_BODY = new byte[0];
    private final OkHttpClient okHttpClient;

    public DefaultYTDownloaderImpl(@NonNull OkHttpClient httpClient) {
        this.okHttpClient = httpClient;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        String method = request.httpMethod().toUpperCase(Locale.ROOT);
        logger.debug("Executing " + method + " request: " + request.url());
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url());

        applyHeaders(builder, request.headers());
        applyMethod(builder, method, request.dataToSend());

        try (okhttp3.Response okResponse = okHttpClient.newCall(builder.build()).execute()) {
            String responseBody = okResponse.body().string();
            logger.debug("Response code=" + okResponse.code() +
                    ", bodyLength=" + responseBody.length());

            return new Response(
                    okResponse.code(),
                    okResponse.message(),
                    okResponse.headers().toMultimap(),
                    responseBody,
                    okResponse.request().url().toString()
            );
        }
    }

    private void applyHeaders(@NonNull okhttp3.Request.Builder builder,
                              Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            if (values == null) continue;
            for (String value : values) {
                builder.addHeader(key, value);
            }
        }
    }

    private void applyMethod(@NonNull okhttp3.Request.Builder builder,
                             @NonNull String method, byte[] requestData) {
        RequestBody body = createBody(requestData);
        switch (method) {
            case "HEAD" -> builder.head();
            case "GET" -> builder.get();
            case "POST" -> builder.post(body);
            case "PUT" -> builder.put(body);
            case "DELETE" -> {
                if (requestData != null) builder.delete(body);
                else builder.delete();
            }
            default -> {
                logger.debug("Unknown HTTP method: " + method);
                builder.get();
            }
        }
    }

    @NonNull
    private RequestBody createBody(byte[] data) {
        byte[] safeData = data != null ? data : EMPTY_BODY;
        return RequestBody.create(safeData, null);
    }
}