package ai.alfred.llm;

/**
 * The only way the rest of alfred talks to a language model. Tool calls come back as data; nothing here executes them.
 */
public interface LlmPort {

	/**
	 * Sends the conversation and returns the model's next assistant turn.
	 * @throws LlmUnavailable when the model cannot be reached or no credentials are configured; no Action may follow
	 * @throws LlmRequestRejected when the provider refuses the request itself (a bug on our side, never retried)
	 */
	LlmResponse complete(LlmRequest request);

}
