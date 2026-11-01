package coreUtils.library.networks;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

/**
 * A thread-safe singleton provider for OkHttpClient instances with configurable timeouts.
 * <p>
 * This utility class provides a centralized way to obtain {@link OkHttpClient} instances
 * across the application, ensuring efficient connection reuse and consistent configuration.
 * It implements double-checked locking for lazy initialization and supports creating
 * customized clients with different timeout values while reusing core components.
 * </p>
 *
 * <p><b>Core Configuration:</b>
 * <ul>
 *   <li>Connection pool with 20 keep-alive connections for 5 minutes</li>
 *   <li>Dispatcher with 64 max requests and 16 requests per host</li>
 *   <li>HTTP/2 with fallback to HTTP/1.1 protocol support</li>
 *   <li>Automatic follow of HTTP and SSL redirects</li>
 *   <li>Default timeouts: 5s connect, 10s read, 10s write</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * // Get client with custom timeouts (30s connect, 60s read)
 * OkHttpClient client = HttpClientProvider.getOkHttpClient(30, 60);
 *
 * // All settings except timeouts are shared across instances
 * OkHttpClient another = HttpClientProvider.getOkHttpClient(15, 30);
 * </pre>
 * </p>
 *
 * @see OkHttpClient
 * @see ConnectionPool
 * @see Dispatcher
 */
public final class HttpClientProvider {
	
	/**
	 * Private constructor to prevent instantiation.
	 * <p>
	 * This utility class is not meant to be instantiated as it only provides
	 * static factory methods for obtaining OkHttpClient instances.
	 * </p>
	 */
	private HttpClientProvider() {}
	
	/**
	 * Volatile reference to the singleton OkHttpClient instance.
	 * <p>
	 * Volatility ensures visibility of the fully initialized instance across
	 * multiple threads when using double-checked locking pattern.
	 * </p>
	 */
	private static volatile OkHttpClient okHttpClient;
	
	/**
	 * Provides a singleton OkHttpClient instance with configurable timeouts.
	 * <p>
	 * This method lazily initializes a default OkHttpClient with optimized settings
	 * (connection pool, dispatcher, protocols, redirects) on first invocation.
	 * Subsequent calls may return either the singleton instance or a customized
	 * copy based on the requested timeout values.
	 * </p>
	 *
	 * <p><b>Timeout Handling:</b>
	 * <ul>
	 *   <li>Connect and read timeouts are enforced to have a minimum of 5 seconds</li>
	 *   <li>If requested timeouts match the default client's values, the singleton is returned</li>
	 *   <li>If timeouts differ, a new client is created via {@link OkHttpClient#newBuilder()},
	 *       inheriting all other settings (connection pool, dispatcher, etc.) from the singleton</li>
	 * </ul>
	 * </p>
	 *
	 * <p><b>Default Values:</b>
	 * <ul>
	 *   <li>Connect Timeout: 5 seconds</li>
	 *   <li>Read Timeout: 10 seconds</li>
	 *   <li>Write Timeout: 10 seconds</li>
	 *   <li>Redirects: Both HTTP and SSL redirects are automatically followed</li>
	 * </ul>
	 * </p>
	 *
	 * @param connectTimeout the connection timeout in seconds (minimum 5 seconds)
	 * @param readTimeout    the read timeout in seconds (minimum 5 seconds)
	 * @return an OkHttpClient instance configured with the specified timeouts
	 */
	public static OkHttpClient getOkHttpClient(int connectTimeout, int readTimeout) {
		if (okHttpClient == null) {
			synchronized (HttpClientProvider.class) {
				if (okHttpClient == null) {
					okHttpClient = new OkHttpClient.Builder()
						.followRedirects(true)
						.followSslRedirects(true)
						.retryOnConnectionFailure(true)
						.protocols(defaultProtocols())
						.connectTimeout(15, TimeUnit.SECONDS)
						.readTimeout(20, TimeUnit.SECONDS)
						.writeTimeout(20, TimeUnit.SECONDS)
						.connectionPool(getConnectionPool())
						.dispatcher(getDispatcher())
						.build();
				}
			}
		}
		
		int finalConnectTimeout = Math.max(connectTimeout, 1);
		int finalReadTimeout = Math.max(readTimeout, 1);
		
		if (okHttpClient.connectTimeoutMillis() != (long) finalConnectTimeout * 1000 ||
			okHttpClient.readTimeoutMillis() != (long) finalReadTimeout * 1000) {
			return okHttpClient.newBuilder()
				.connectTimeout(finalConnectTimeout, TimeUnit.SECONDS)
				.readTimeout(finalReadTimeout, TimeUnit.SECONDS)
				.build();
		}
		
		return okHttpClient;
	}
	
	/**
	 * Returns the list of supported HTTP protocols in order of preference.
	 * <p>
	 * HTTP/2 is prioritized for better performance (multiplexing, header compression,
	 * server push), with HTTP/1.1 as fallback for servers that don't support HTTP/2.
	 * </p>
	 *
	 * @return a list containing HTTP/2 and HTTP/1.1 protocols
	 */
	@NonNull
	private static List<Protocol> defaultProtocols() {
		return Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1);
	}
	
	/**
	 * Creates and configures the connection pool for reusing HTTP connections.
	 * <p>
	 * The pool maintains up to 20 idle connections for 5 minutes, reducing latency
	 * for repeated requests to the same server by avoiding TCP handshake overhead.
	 * </p>
	 *
	 * @return a ConnectionPool with 20 connections kept alive for 5 minutes
	 */
	@NonNull
	private static ConnectionPool getConnectionPool() {
		return new ConnectionPool(20, 5, TimeUnit.MINUTES);
	}
	
	/**
	 * Creates and configures the dispatcher for managing asynchronous request execution.
	 * <p>
	 * The dispatcher limits concurrent requests to 64 total and 16 per host,
	 * preventing resource exhaustion while allowing efficient parallel downloads
	 * and API calls. These limits can be tuned based on network conditions and
	 * expected usage patterns.
	 * </p>
	 *
	 * @return a Dispatcher with 64 max requests and 16 requests per host
	 */
	@NonNull
	private static Dispatcher getDispatcher() {
		Dispatcher dispatcher = new Dispatcher();
		dispatcher.setMaxRequests(64);
		dispatcher.setMaxRequestsPerHost(16);
		return dispatcher;
	}
}