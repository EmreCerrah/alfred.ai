package ai.alfred.llm;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClaudeLlmAdapterFailureTests {

	final LlmRequest request = new LlmRequest(UUID.randomUUID(), "You are Alfred.", List.of(),
			List.of(new LlmMessage.User("Merhaba")));

	HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void failsWithLlmUnavailableWithoutAnApiKey() {
		LlmPort port = new ClaudeLlmAdapter(properties("", null));

		assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request))
			.withMessageContaining("ANTHROPIC_API_KEY");
	}

	@Test
	void failsWithLlmUnavailableWhenClaudeIsUnreachable() {
		LlmPort port = new ClaudeLlmAdapter(properties("test-key", "http://127.0.0.1:1"));

		assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request));
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 404, 413, 422 })
	void reportsAProviderRejectionAsLlmRequestRejected(int status) throws IOException {
		LlmPort port = new ClaudeLlmAdapter(properties("test-key", respondingWith(status)));

		assertThatExceptionOfType(LlmRequestRejected.class).isThrownBy(() -> port.complete(request));
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 403, 429, 500, 529 })
	void reportsCredentialsRateLimitsAndOutagesAsLlmUnavailable(int status) throws IOException {
		LlmPort port = new ClaudeLlmAdapter(properties("test-key", respondingWith(status)));

		assertThatExceptionOfType(LlmUnavailable.class).isThrownBy(() -> port.complete(request));
	}

	/** A local stand-in for the API that answers every request with {@code status} and an Anthropic error body. */
	private String respondingWith(int status) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] body = """
					{"type": "error", "error": {"type": "invalid_request_error", "message": "stub"}}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private static LlmProperties properties(String apiKey, String baseUrl) {
		return new LlmProperties("claude-haiku-5-5", "medium", 1024, apiKey, baseUrl, 0, Duration.ofSeconds(5),
				20000);
	}

}
