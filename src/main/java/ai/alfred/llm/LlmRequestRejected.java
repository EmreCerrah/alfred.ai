package ai.alfred.llm;

/**
 * The provider rejected the request itself, for example an edited history under preserved thinking. Retrying the same
 * request fails the same way.
 */
public class LlmRequestRejected extends RuntimeException {

	public LlmRequestRejected(String message, Throwable cause) {
		super(message, cause);
	}

}
