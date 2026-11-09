package ai.alfred.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.UnprocessableEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * The raw model call on the Anthropic Java SDK (beta Messages API); reached only through {@link MeteredLlmPort}. The
 * client is created only when an API key is configured, so the daemon starts without one and each call fails with
 * {@link LlmUnavailable} instead.
 */
@Component
class ClaudeLlmAdapter implements ModelCall, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(ClaudeLlmAdapter.class);

	private static final int REQUEST_TOO_LARGE = 413;

	/** Request JSON is mostly ASCII-ish text; two characters per token is a generous upper estimate. */
	private static final int CHARS_PER_TOKEN_LOWER_BOUND = 2;

	private final LlmProperties properties;

	private final ClaudeRequestMapper mapper;

	private final AnthropicClient client;

	ClaudeLlmAdapter(LlmProperties properties) {
		this.properties = properties;
		this.mapper = new ClaudeRequestMapper(properties);
		this.client = properties.hasApiKey() ? createClient(properties) : null;
		if (client == null) {
			log.warn("ANTHROPIC_API_KEY is not set: every Claude call will fail with LlmUnavailable");
		}
	}

	@Override
	public String model() {
		return properties.model();
	}

	@Override
	public long maxTokens() {
		return properties.maxTokens();
	}

	@Override
	public long estimatePromptTokens(LlmRequest request) {
		return mapper.bodyLength(request) / CHARS_PER_TOKEN_LOWER_BOUND + 1;
	}

	@Override
	public LlmResponse call(LlmRequest request) {
		if (client == null) {
			throw new LlmUnavailable("No Anthropic API key configured: set ANTHROPIC_API_KEY");
		}
		try {
			return mapper.toResponse(client.beta().messages().create(mapper.toParams(request)));
		}
		catch (BadRequestException | NotFoundException | UnprocessableEntityException ex) {
			throw rejected(ex);
		}
		catch (AnthropicServiceException ex) {
			if (ex.statusCode() == REQUEST_TOO_LARGE) {
				throw rejected(ex);
			}
			// 401/403 (credentials), 429 after retries, 5xx and anything unexpected: Claude is not usable right now.
			throw new LlmUnavailable("Claude is unavailable (HTTP %d)".formatted(ex.statusCode()), ex);
		}
		catch (AnthropicIoException ex) {
			throw new LlmUnavailable("Claude is unreachable: " + ex.getMessage(), ex);
		}
		catch (AnthropicException ex) {
			// Includes a response the SDK cannot read.
			throw new LlmUnavailable("Claude call failed: " + ex.getMessage(), ex);
		}
	}

	@Override
	public void destroy() {
		if (client != null) {
			client.close();
		}
	}

	private static LlmRequestRejected rejected(AnthropicServiceException ex) {
		return new LlmRequestRejected("Claude rejected the request (HTTP %d)".formatted(ex.statusCode()), ex);
	}

	private static AnthropicClient createClient(LlmProperties properties) {
		AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
			.apiKey(properties.apiKey())
			.maxRetries(properties.maxRetries())
			.timeout(properties.timeout());
		if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
			builder.baseUrl(properties.baseUrl());
		}
		return builder.build();
	}

}
