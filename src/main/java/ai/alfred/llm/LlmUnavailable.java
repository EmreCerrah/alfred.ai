package ai.alfred.llm;

/**
 * The model cannot be reached (no credentials, network, auth, rate limit or provider outage). No Action may be taken
 * on its behalf (FR-9).
 */
public class LlmUnavailable extends RuntimeException {

	public LlmUnavailable(String message) {
		super(message);
	}

	public LlmUnavailable(String message, Throwable cause) {
		super(message, cause);
	}

}
