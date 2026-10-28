package ai.alfred.llm;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code alfred.llm.*}. The API key comes from {@code ANTHROPIC_API_KEY} only (AD-15) and is never logged. Invalid
 * values fail at startup, not on the first call.
 */
@ConfigurationProperties("alfred.llm")
record LlmProperties(String model, String effort, long maxTokens, String apiKey, String baseUrl, int maxRetries,
		Duration timeout, int maxToolResultChars) {

	static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

	LlmProperties {
		Objects.requireNonNull(model, "alfred.llm.model");
		if (!EFFORTS.contains(effort)) {
			throw new IllegalArgumentException("alfred.llm.effort must be one of " + EFFORTS + ", was: " + effort);
		}
		if (maxTokens <= 0) {
			throw new IllegalArgumentException("alfred.llm.max-tokens must be positive");
		}
		if (maxRetries < 0) {
			throw new IllegalArgumentException("alfred.llm.max-retries must not be negative");
		}
		Objects.requireNonNull(timeout, "alfred.llm.timeout");
		if (maxToolResultChars <= 0) {
			throw new IllegalArgumentException("alfred.llm.max-tool-result-chars must be positive");
		}
		// A trailing newline from an env file would otherwise turn every call into a 401.
		apiKey = apiKey == null ? null : apiKey.strip();
	}

	boolean hasApiKey() {
		return apiKey != null && !apiKey.isEmpty();
	}

	@Override
	public String toString() {
		return "LlmProperties[model=%s, effort=%s, maxTokens=%d, apiKey=%s, baseUrl=%s, maxRetries=%d, timeout=%s, maxToolResultChars=%d]"
			.formatted(model, effort, maxTokens, hasApiKey() ? "***" : "<unset>", baseUrl, maxRetries, timeout,
					maxToolResultChars);
	}

}
